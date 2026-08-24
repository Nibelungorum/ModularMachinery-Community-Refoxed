package cn.howxu.mmcr.api.capability.plan;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies atomic execution of prepared capability operations.
 *
 * @author howxu <dev@howxu.cn>
 */
class CraftingPlanTest {
    private static final ExecutionStatus FIRST_FAILURE = new ExecutionStatus(
            Identifier.fromNamespaceAndPath("mmcr_test", "first_failure"),
            StatusSeverity.FAILURE,
            Identifier.fromNamespaceAndPath("mmcr_test", "test"),
            java.util.Map.of("reason", "test"));

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

        try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            operation(value, true).commit(transaction);
        }

        assertThat(value.value).isZero();
    }

    @Test
    void publishes_the_first_structured_failure() {
        ExecutionStatus secondFailure = new ExecutionStatus(
                Identifier.fromNamespaceAndPath("mmcr_test", "second_failure"),
                StatusSeverity.FAILURE,
                Identifier.fromNamespaceAndPath("mmcr_test", "test"),
                java.util.Map.of());
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
    }

    @Test
    void reports_structured_failure_when_failed_operation_has_no_status() {
        CraftingPlan plan = plan(transaction -> new CapabilityResult(false, null));

        assertThat(plan.commit()).isFalse();
        assertThat(plan.failure()).isNotNull();
        assertThat(plan.failure().severity()).isEqualTo(StatusSeverity.FAILURE);
    }

    @Test
    void direct_materialization_uses_the_final_parallelism_as_maximum() {
        CapabilityOperation operation = new CapabilityOperation() {
            @Override
            public CapabilityResult commit(TransactionContext transaction) {
                return CapabilityResult.successful();
            }

            @Override
            public CapabilityOperation forParallelism(int parallelism) {
                return this;
            }
        };

        RequirementPlan resolved = new RequirementPlan(0, 10, List.of(operation), null)
                .preparedAt(10)
                .materialize(3, new PlanningReservations(), FIRST_FAILURE);

        assertThat(resolved.maxParallelism()).isEqualTo(3);
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
