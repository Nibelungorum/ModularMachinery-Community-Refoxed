package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Aggregate result produced by one factory runtime tick.
 *
 * @author howxu <dev@howxu.cn>
 */
public record FactoryTickResult(int activeLaneCount,
                                @Nullable ExecutionStatus factoryFailure,
                                boolean laneStateChanged,
                                boolean snapshotChanged) {
    public FactoryTickResult {
        if (activeLaneCount < 0) throw new IllegalArgumentException("activeLaneCount must not be negative");
    }
}
