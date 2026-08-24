package cn.howxu.mmcr.api.capability.plan;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Prepared operations and failure state for one machine requirement.
 *
 * @param requirementIndex the requirement's position in the recipe
 * @param maxParallelism the maximum parallelism supported by this plan
 * @param operations the capability operations prepared by the handler
 * @param failure the failure status, or {@code null} when planning succeeded
 * @author howxu <dev@howxu.cn>
 */
public record RequirementPlan(
        int requirementIndex,
        int maxParallelism,
        List<CapabilityOperation> operations,
        @Nullable ExecutionStatus failure) {
    public RequirementPlan {
        operations = List.copyOf(operations);
    }

    public boolean successful() {
        return failure == null;
    }
}
