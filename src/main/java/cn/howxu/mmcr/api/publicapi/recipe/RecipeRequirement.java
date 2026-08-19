package cn.howxu.mmcr.api.publicapi.recipe;

/** Public immutable recipe requirement boundary.
 * @author howxu <dev@howxu.cn>
 */
public sealed interface RecipeRequirement permits ItemRequirement, FluidRequirement, EnergyRequirement,
        SmartInterfaceRequirement {
}
