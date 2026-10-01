package cn.howxu.mmcr.api.capability.plan;

import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapabilityOperationTest {
    private static final ExecutionStatus FAILURE = new ExecutionStatus(
            ResourceLocation.fromNamespaceAndPath("mmcr_test", "native_operation_failure"),
            StatusSeverity.BLOCKED,
            ResourceLocation.fromNamespaceAndPath("mmcr_test", "native_operation"),
            FailureOccurrence.at(BuiltinFailureReasons.UNKNOWN,
                    ResourceLocation.fromNamespaceAndPath("mmcr_test", "native_operation"),
                    FailurePhase.CAPABILITY_COMMIT, null, null, Map.of()));

    @Test
    void native_operation_requires_explicit_parallelism_adaptation() {
        CapabilityOperation operation = CapabilityResult::successful;

        assertThat(operation.forParallelism(3L)).isNull();
        assertThatThrownBy(() -> operation.forParallelism(0L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void plan_executes_native_operations_once_in_declared_order() {
        AtomicInteger executed = new AtomicInteger();
        CapabilityOperation first = () -> {
            assertThat(executed.get()).isZero();
            executed.incrementAndGet();
            return CapabilityResult.successful();
        };
        CapabilityOperation second = () -> {
            assertThat(executed.get()).isEqualTo(1);
            executed.incrementAndGet();
            return CapabilityResult.successful();
        };
        CraftingPlan plan = new CraftingPlan(List.of(new RequirementPlan(0, 1L, List.of(first, second), null)), 1L);

        assertThat(plan.commit()).isTrue();
        assertThat(executed).hasValue(2);
        assertThat(plan.failure()).isNull();
    }

    @Test
    void plan_reports_native_failure_without_rollback_assumptions() {
        AtomicInteger executed = new AtomicInteger();
        CapabilityOperation first = () -> {
            executed.incrementAndGet();
            return CapabilityResult.successful();
        };
        CapabilityOperation failing = () -> CapabilityResult.failure(FAILURE);
        CapabilityOperation notReached = () -> {
            executed.incrementAndGet();
            return CapabilityResult.successful();
        };
        CraftingPlan plan = new CraftingPlan(List.of(
                new RequirementPlan(0, 1L, List.of(first, failing, notReached), null)), 1L);

        assertThat(plan.commit()).isFalse();
        assertThat(executed).hasValue(1);
        assertThat(plan.failure()).isSameAs(FAILURE);
    }
}
