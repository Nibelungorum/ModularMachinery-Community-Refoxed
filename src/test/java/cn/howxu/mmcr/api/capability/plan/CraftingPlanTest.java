package cn.howxu.mmcr.api.capability.plan;

import cn.howxu.mmcr.api.data.DataStorage;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies atomic execution of prepared capability operations.
 *
 * @author howxu <dev@howxu.cn>
 */
class CraftingPlanTest {
    private static final ExecutionStatus FIRST_FAILURE = new ExecutionStatus(
            ResourceLocation.fromNamespaceAndPath("mmcr_test", "first_failure"),
            StatusSeverity.FAILURE,
            ResourceLocation.fromNamespaceAndPath("mmcr_test", "test"),
            FailureOccurrence.at(BuiltinFailureReasons.UNKNOWN,
                    ResourceLocation.fromNamespaceAndPath("mmcr_test", "test"), FailurePhase.CAPABILITY_COMMIT,
                    null, null, Map.of()));

    @Test
    void commits_all_operations_in_requirement_order() {
        JournalValue first = new JournalValue();
        JournalValue second = new JournalValue();

        CraftingPlan plan = plan(operation(first, true), operation(second, true));

        assertThat(plan.commit()).isTrue();
        assertThat(first.value).isEqualTo(1);
        assertThat(second.value).isEqualTo(1);
        assertThat(plan.failure()).isNull();
    }

    @Test
    void rolls_back_prior_operations_when_a_later_operation_fails() {
        JournalValue first = new JournalValue();
        JournalValue second = new JournalValue();

        CraftingPlan plan = plan(operation(first, true), transaction -> CapabilityResult.failure(FIRST_FAILURE));

        assertThat(plan.commit()).isFalse();
        assertThat(first.value).isZero();
        assertThat(second.value).isZero();
        assertThat(plan.failure()).isSameAs(FIRST_FAILURE);
    }

    @Test
    void uncommitted_transaction_does_not_mutate_capability_state() {
        JournalValue value = new JournalValue();

        try (var transaction = Transaction.openRoot()) {
            operation(value, true).commit(transaction);
        }

        assertThat(value.value).isZero();
    }

    @Test
    void publishes_the_first_structured_failure() {
        ResourceLocation source = ResourceLocation.fromNamespaceAndPath("mmcr_test", "test");
        ExecutionStatus secondFailure = new ExecutionStatus(
                ResourceLocation.fromNamespaceAndPath("mmcr_test", "second_failure"),
                StatusSeverity.FAILURE, source,
                FailureOccurrence.at(BuiltinFailureReasons.UNKNOWN, source, FailurePhase.CAPABILITY_COMMIT,
                        null, null, Map.of()));
        CraftingPlan plan = plan(
                transaction -> CapabilityResult.failure(FIRST_FAILURE),
                transaction -> CapabilityResult.failure(secondFailure));

        assertThat(plan.commit()).isFalse();
        assertThat(plan.failure()).isSameAs(FIRST_FAILURE);
    }

    @Test
    void reports_structured_failure_when_operation_returns_null() {
        CraftingPlan plan = plan(transaction -> null);

        assertThat(plan.commit()).isFalse();
        assertThat(plan.failure()).isNotNull();
        assertThat(plan.failure().severity()).isEqualTo(StatusSeverity.FAILURE);
        assertThat(plan.failure().reason()).isEqualTo(BuiltinFailureReasons.OPERATION_FAILED_WITHOUT_STATUS);
    }

    @Test
    void reports_structured_failure_when_failed_operation_has_no_status() {
        CraftingPlan plan = plan(transaction -> new CapabilityResult(false, null));

        assertThat(plan.commit()).isFalse();
        assertThat(plan.failure()).isNotNull();
        assertThat(plan.failure().severity()).isEqualTo(StatusSeverity.FAILURE);
        assertThat(plan.failure().reason()).isEqualTo(BuiltinFailureReasons.OPERATION_FAILED_WITHOUT_STATUS);
    }

    @Test
    void commits_capability_operations_and_data_storage_in_the_same_transaction() {
        JournalValue value = new JournalValue();
        DataStorage storage = new DataStorage();

        CraftingPlan plan = plan(operation(value, true));

        assertThat(plan.commit(transaction -> storage.set("value", DataValue.of(1), transaction))).isTrue();
        assertThat(value.value).isEqualTo(1);
        assertThat(storage.get("value")).contains(DataValue.of(1));
    }

    @Test
    void input_operations_can_join_caller_owned_transaction() {
        LongValueStorage storage = new LongValueStorage(10L, 10L, null);
        CapabilityOperation input = transaction -> {
            storage.insert(3L, transaction);
            return CapabilityResult.successful();
        };
        CapabilityOperation output = transaction -> {
            storage.insert(4L, transaction);
            return CapabilityResult.successful();
        };
        CraftingPlan plan = new CraftingPlan(
                List.of(new RequirementPlan(0, 1, List.of(input), null),
                        new RequirementPlan(1, 1, List.of(output), null)),
                1,
                Map.of(0, RecipeModifier.IOType.INPUT, 1, RecipeModifier.IOType.OUTPUT));

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(plan.commitInputs(transaction)).isTrue();
            assertThat(storage.amount()).isEqualTo(3L);
            transaction.commit();
        }

        assertThat(storage.amount()).isEqualTo(3L);
    }

    @Test
    void caller_owned_transaction_rolls_back_uncommitted_input_operations() {
        LongValueStorage storage = new LongValueStorage(10L, 10L, null);
        CapabilityOperation input = transaction -> {
            storage.insert(3L, transaction);
            return CapabilityResult.successful();
        };
        CraftingPlan plan = new CraftingPlan(
                List.of(new RequirementPlan(0, 1, List.of(input), null)),
                1,
                Map.of(0, RecipeModifier.IOType.INPUT));

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(plan.commitInputs(transaction)).isTrue();
            assertThat(storage.amount()).isEqualTo(3L);
        }

        assertThat(storage.amount()).isZero();
    }

    @Test
    void rolls_back_capability_operations_and_data_storage_when_transaction_callback_fails() {
        JournalValue value = new JournalValue();
        DataStorage storage = new DataStorage();

        CraftingPlan plan = plan(operation(value, true));

        assertThatThrownBy(() -> plan.commit(transaction -> {
            storage.set("value", DataValue.of(1), transaction);
            throw new IllegalStateException("callback failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(value.value).isZero();
        assertThat(storage.get("value")).isEmpty();
    }

    @Test
    void direct_materialization_uses_the_final_parallelism_as_maximum() {
        CapabilityOperation operation = new CapabilityOperation() {
            @Override
            public CapabilityResult commit(TransactionContext transaction) {
                return CapabilityResult.successful();
            }

            @Override
            public CapabilityOperation forParallelism(long parallelism) {
                return this;
            }
        };

        RequirementPlan resolved = new RequirementPlan(0, 10, List.of(operation), null)
                .preparedAt(10)
                .materialize(3, new PlanningReservations(), FIRST_FAILURE);

        assertThat(resolved.maxParallelism()).isEqualTo(3);
    }

    @Test
    void keeps_the_legacy_null_factory_constructor_unambiguous() {
        RequirementPlan plan = new RequirementPlan(0, 1, List.of(), null, null);

        assertThat(plan.operationFactory()).isNull();
        assertThat(plan.outputSimulation()).isNull();
    }

    @Test
    void preserves_output_simulation_when_direct_operation_cannot_scale() {
        CapabilityOperation operation = new CapabilityOperation() {
            @Override
            public CapabilityResult commit(TransactionContext transaction) {
                return CapabilityResult.successful();
            }

            @Override
            public CapabilityOperation forParallelism(long parallelism) {
                return null;
            }
        };
        OutputSimulation simulation = new OutputSimulation(4L, 2L, OutputFit.PARTIAL);
        ExecutionStatus unsafeFailure = new ExecutionStatus(
                ResourceLocation.fromNamespaceAndPath("mmcr_test", "unsafe_scale"),
                StatusSeverity.FAILURE,
                ResourceLocation.fromNamespaceAndPath("mmcr_test", "test"),
                FailureOccurrence.at(BuiltinFailureReasons.UNKNOWN,
                        ResourceLocation.fromNamespaceAndPath("mmcr_test", "test"), FailurePhase.CAPABILITY_COMMIT,
                        null, null, Map.of()));

        RequirementPlan resolved = RequirementPlan.withOutputSimulation(0, 2, List.of(operation), null, simulation)
                .preparedAt(2)
                .materialize(1, new PlanningReservations(), unsafeFailure);

        assertThat(resolved.failure()).isSameAs(unsafeFailure);
        assertThat(resolved.outputSimulation()).isSameAs(simulation);
    }

    @Test
    void returns_immutable_output_simulation_collections() {
        OutputSimulation simulation = new OutputSimulation(1L, 1L, OutputFit.FULL);
        CraftingPlan plan = new CraftingPlan(
                List.of(RequirementPlan.withOutputSimulation(0, 1, List.of(), null, simulation)), 1);

        assertThatThrownBy(() -> plan.outputSimulations().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static CraftingPlan plan(CapabilityOperation... operations) {
        return new CraftingPlan(List.of(new RequirementPlan(0, 1, List.of(operations), null)), 1);
    }

    private static CapabilityOperation operation(JournalValue value, boolean success) {
        return transaction -> {
            if (!success) return CapabilityResult.failure(FIRST_FAILURE);
            value.journal.updateSnapshots(transaction);
            value.pending++;
            return CapabilityResult.successful();
        };
    }

    private static final class JournalValue {
        private final SnapshotJournal<Long> journal = new SnapshotJournal<>() {
            @Override
            protected Long createSnapshot() {
                return pending;
            }

            @Override
            protected void revertToSnapshot(Long snapshot) {
                pending = snapshot;
            }

            @Override
            protected void onRootCommit(Long originalState) {
                value += pending;
                pending = 0;
            }
        };
        private int value;
        private long pending;
    }
}
