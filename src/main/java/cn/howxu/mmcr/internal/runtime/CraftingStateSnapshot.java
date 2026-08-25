package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable published crafting state and the runtime versions it was built from.
 *
 * @author howxu <dev@howxu.cn>
 */
public record CraftingStateSnapshot(
        @Nullable Identifier recipeId,
        CraftingStatus status,
        @Nullable ExecutionStatus failure,
        long structureVersion,
        long capabilityVersion,
        long modifierVersion,
        int tick,
        int totalTick,
        int parallelism,
        int maxParallelism,
        boolean recipeLocked,
        String lockedRecipeId) {

    public CraftingStateSnapshot {
        status = copyStatus(status);
        if (tick < 0 || totalTick < 0 || tick > totalTick) {
            throw new IllegalArgumentException("Invalid crafting progress: " + tick + "/" + totalTick);
        }
        if (parallelism < 0 || maxParallelism < 1) {
            throw new IllegalArgumentException("Invalid crafting parallelism");
        }
        lockedRecipeId = recipeLocked && lockedRecipeId != null ? lockedRecipeId : "";
    }

    public static CraftingStateSnapshot empty(long structureVersion, long capabilityVersion, long modifierVersion) {
        return new CraftingStateSnapshot(null, CraftingStatus.IDLE, null,
                structureVersion, capabilityVersion, modifierVersion, 0, 0, 0, 1, false, "");
    }

    @Override
    public CraftingStatus status() {
        return copyStatus(status);
    }

    private static CraftingStatus copyStatus(@Nullable CraftingStatus status) {
        if (status == null) return CraftingStatus.IDLE;
        return new CraftingStatus(status.getStatus(), status.getUnlocMessage());
    }
}
