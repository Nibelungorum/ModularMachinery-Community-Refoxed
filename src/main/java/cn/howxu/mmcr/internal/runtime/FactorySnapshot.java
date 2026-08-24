package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Immutable aggregate state published for a factory controller.
 *
 * @author howxu <dev@howxu.cn>
 */
public record FactorySnapshot(
        boolean active,
        List<CraftingStateSnapshot> lanes,
        int activeParallelism,
        int laneLimit,
        @Nullable ExecutionStatus failure) {

    public FactorySnapshot {
        lanes = List.copyOf(lanes == null ? List.of() : lanes);
        if (activeParallelism < 0) throw new IllegalArgumentException("activeParallelism must not be negative");
        if (laneLimit < 1) throw new IllegalArgumentException("laneLimit must be positive");
    }

    public static FactorySnapshot empty() {
        return new FactorySnapshot(false, List.of(), 0, 1, null);
    }
}
