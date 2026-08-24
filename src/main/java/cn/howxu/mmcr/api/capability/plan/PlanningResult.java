package cn.howxu.mmcr.api.capability.plan;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Result of preparing a complete recipe plan.
 *
 * @param plan the prepared plan, or {@code null} when planning failed
 * @param failure the structured planning failure, or {@code null} on success
 * @author howxu <dev@howxu.cn>
 */
public record PlanningResult(@Nullable CraftingPlan plan, @Nullable ExecutionStatus failure) {
    public boolean successful() {
        return plan != null && failure == null;
    }
}
