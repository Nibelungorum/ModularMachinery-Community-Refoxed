package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable published crafting state and the runtime versions it was built from.
 *
 * @author howxu <dev@howxu.cn>
 */
public record CraftingStateSnapshot(
        @Nullable ResourceLocation recipeId,
        CraftingStatus status,
        @Nullable ExecutionStatus failure,
        long structureVersion,
        long capabilityVersion,
        long modifierVersion,
        int tick,
        int totalTick,
        long parallelism,
        long maxParallelism) {

    public CraftingStateSnapshot {
        status = copyStatus(status);
        if (tick < 0 || totalTick < 0 || tick > totalTick) {
            throw new IllegalArgumentException("Invalid crafting progress: " + tick + "/" + totalTick);
        }
        if (parallelism < 0 || maxParallelism < 1) {
            throw new IllegalArgumentException("Invalid crafting parallelism");
        }
    }

    public static CraftingStateSnapshot empty(long structureVersion, long capabilityVersion, long modifierVersion) {
        return new CraftingStateSnapshot(null, CraftingStatus.IDLE, null,
                structureVersion, capabilityVersion, modifierVersion, 0, 0, 0L, 1L);
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
