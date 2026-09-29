package cn.howxu.mmcr.internal.recipe;

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
                                      long coreRecipeSetVersion) {
}
