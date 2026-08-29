package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Immutable state published for a factory controller.
 *
 * @author howxu <dev@howxu.cn>
 */
public record FactorySnapshot(
        boolean formed,
        boolean active,
        List<CraftingStateSnapshot> lanes,
        int laneLimit,
        int activeLaneCount,
        long maxParallelism,
        boolean paused,
        List<FactoryRuntime.ThreadSnapshot> presentationLanes,
        String machineName,
        int parallelSlots,
        @Nullable ExecutionStatus failure,
        List<String> foundLevelIds) {

    public FactorySnapshot {
        machineName = machineName == null ? "" : machineName;
        lanes = List.copyOf(lanes == null ? List.of() : lanes);
        if (laneLimit < 1) throw new IllegalArgumentException("laneLimit must be positive");
        if (activeLaneCount < 0) throw new IllegalArgumentException("activeLaneCount must not be negative");
        if (maxParallelism < 1) throw new IllegalArgumentException("maxParallelism must be positive");
        presentationLanes = List.copyOf(presentationLanes == null ? List.of() : presentationLanes);
        if (parallelSlots < 0) throw new IllegalArgumentException("parallelSlots must not be negative");
        foundLevelIds = List.copyOf(foundLevelIds == null ? List.of() : foundLevelIds);
    }

    public static FactorySnapshot empty() {
        return new FactorySnapshot(false, false, List.of(), 1, 0, 1L,
                false, List.of(), "", 0, null, List.of());
    }
}
