package cn.howxu.mmcr.internal.recipe;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable versions that identify one recipe-search failure context.
 *
 * @author howxu <dev@howxu.cn>
 */
public record RecipeSearchContextKey(long structureVersion,
                                     long capabilityVersion,
                                     long modifierVersion,
                                     long componentStateVersion,
                                     long catalogVersion,
                                     long resourceAvailabilityEpoch,
                                     @Nullable Identifier lockedRecipeId,
                                     long coreRecipeSetVersion) {
}
