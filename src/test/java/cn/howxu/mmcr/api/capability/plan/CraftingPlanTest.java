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
