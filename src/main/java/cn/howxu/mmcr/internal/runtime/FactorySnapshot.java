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
        int activeLaneCount,
        int maxParallelism,
        boolean paused,
        List<FactoryRuntime.ThreadSnapshot> presentationLanes,
        @Nullable ExecutionStatus failure) {

    public FactorySnapshot {
        lanes = List.copyOf(lanes == null ? List.of() : lanes);
        if (activeParallelism < 0) throw new IllegalArgumentException("activeParallelism must not be negative");
        if (laneLimit < 1) throw new IllegalArgumentException("laneLimit must be positive");
        if (activeLaneCount < 0) throw new IllegalArgumentException("activeLaneCount must not be negative");
        if (maxParallelism < 1) throw new IllegalArgumentException("maxParallelism must be positive");
        presentationLanes = List.copyOf(presentationLanes == null ? List.of() : presentationLanes);
    }

    public static FactorySnapshot empty() {
        return new FactorySnapshot(false, List.of(), 0, 1, 0, 1, false, List.of(), null);
    }
}
