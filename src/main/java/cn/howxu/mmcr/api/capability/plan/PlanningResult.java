package cn.howxu.mmcr.api.capability.plan;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Result of preparing a complete recipe plan.
 *
 * @param plan the prepared plan, or {@code null} when planning failed
 * @param failure the structured planning failure, or {@code null} on success
 * @param outputSimulations simulated output fits from the planning pass
 * @param failureRequirementIndex the original recipe index that caused failure, or {@code null}
 * @author howxu <dev@howxu.cn>
 */
public record PlanningResult(@Nullable CraftingPlan plan, @Nullable ExecutionStatus failure,
                             List<OutputSimulation> outputSimulations,
                             @Nullable Integer failureRequirementIndex) {
    public PlanningResult(@Nullable CraftingPlan plan, @Nullable ExecutionStatus failure) {
        this(plan, failure, plan == null ? List.of() : plan.outputSimulations(), null);
    }

    public PlanningResult {
        outputSimulations = List.copyOf(outputSimulations == null ? List.of() : outputSimulations);
    }

    public boolean successful() {
        return plan != null && failure == null;
    }
}
