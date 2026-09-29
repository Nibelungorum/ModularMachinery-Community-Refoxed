package cn.howxu.mmcr.compat.mekanism;

import net.minecraft.resources.ResourceLocation;

/**
 * Stable recipe type identities used by the optional Mekanism bridge.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MekanismRecipeTypes {
    public static final ResourceLocation CHEMICAL = ResourceLocation.fromNamespaceAndPath("mekanism", "chemical");
    public static final ResourceLocation HEAT_TEMPERATURE = ResourceLocation.fromNamespaceAndPath("mekanism", "temperature");
    public static final ResourceLocation HEAT = ResourceLocation.fromNamespaceAndPath("mekanism", "heat");

    private MekanismRecipeTypes() {
    }

    public static void register() {
        MekanismBridge.get().registerRecipeTypes(CHEMICAL, HEAT_TEMPERATURE, HEAT);
    }
}
