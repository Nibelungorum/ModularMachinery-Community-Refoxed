package cn.howxu.mmcr.api.compat.mekanism;

import cn.howxu.mmcr.MMCR;
import net.minecraft.resources.ResourceLocation;

/**
 * Stable recipe and port family identifiers used by the optional Mekanism bridge.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MekanismPortFamilies {
    public static final ResourceLocation CHEMICAL = ResourceLocation.fromNamespaceAndPath("mekanism", "chemical");
    public static final ResourceLocation RADIOACTIVE_CHEMICAL = MMCR.id("mekanism_radioactive_chemical");
    public static final ResourceLocation HEAT_TEMPERATURE = ResourceLocation.fromNamespaceAndPath("mekanism", "temperature");
    public static final ResourceLocation HEAT = ResourceLocation.fromNamespaceAndPath("mekanism", "heat");

    private MekanismPortFamilies() {
    }
}
