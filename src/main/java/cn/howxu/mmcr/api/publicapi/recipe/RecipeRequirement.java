package cn.howxu.mmcr.api.publicapi.recipe;

/** Public immutable recipe requirement boundary.
 * @author howxu <dev@howxu.cn>
 */
public sealed interface RecipeRequirement permits ItemRequirement, FluidRequirement, EnergyRequirement,
        SmartInterfaceRequirement, CustomRecipeIo {
    /**
     * Creates a validated codec-backed recipe IO declaration.
     *
     * @param typeId registered requirement or output type identifier
     * @param ioType recipe IO direction
     * @param payload codec payload
     * @return validated custom recipe IO
     */
    static CustomRecipeIo custom(net.minecraft.resources.Identifier typeId, RecipeIo ioType,
                                 com.google.gson.JsonElement payload) {
        return cn.howxu.mmcr.api.publicapi.RecipeApi.custom(typeId, ioType, payload);
    }
}
