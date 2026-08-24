package cn.howxu.mmcr.internal.runtime;

import org.jetbrains.annotations.Nullable;

/**
 * Immutable factory lane state used by presentation and network snapshots.
 *
 * @author howxu <dev@howxu.cn>
 */
public record FactoryLaneSnapshot(
        CraftingStateSnapshot state,
        boolean baseThread,
        boolean coreThread,
        boolean active,
        int tick,
        int totalTick,
        int parallelism,
        @Nullable String lastFailureUnloc,
        boolean locked,
        String lockedRecipeId) {

    public FactoryLaneSnapshot {
        if (state == null) throw new IllegalArgumentException("state must not be null");
        if (tick < 0 || totalTick < 0 || tick > totalTick) {
            throw new IllegalArgumentException("Invalid factory lane tick range");
        }
        if (parallelism < 1) throw new IllegalArgumentException("parallelism must be positive");
        lastFailureUnloc = lastFailureUnloc == null ? "" : lastFailureUnloc;
        lockedRecipeId = locked ? lockedRecipeId == null ? "" : lockedRecipeId : "";
    }
}
