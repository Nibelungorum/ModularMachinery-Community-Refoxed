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
        long modifierVersion) {

    public CraftingStateSnapshot {
        status = copyStatus(status);
    }

    public static CraftingStateSnapshot empty(long structureVersion, long capabilityVersion, long modifierVersion) {
        return new CraftingStateSnapshot(null, CraftingStatus.IDLE, null,
                structureVersion, capabilityVersion, modifierVersion);
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
