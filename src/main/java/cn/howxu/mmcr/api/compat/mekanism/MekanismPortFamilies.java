package cn.howxu.mmcr.api.compat.mekanism;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.port.PortFamily;
import net.minecraft.resources.Identifier;

/**
 * Stable recipe and port family identifiers used by the optional Mekanism bridge.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MekanismPortFamilies {
    public static final Identifier CHEMICAL = PortFamily.declare(Identifier.fromNamespaceAndPath("mekanism", "chemical"), true).id();
    public static final Identifier RADIOACTIVE_CHEMICAL = PortFamily.declare(MMCR.id("mekanism_radioactive_chemical"), true).id();
    public static final Identifier HEAT_TEMPERATURE = Identifier.fromNamespaceAndPath("mekanism", "temperature");
    public static final Identifier HEAT = PortFamily.declare(Identifier.fromNamespaceAndPath("mekanism", "heat"), false).id();

    private MekanismPortFamilies() {
    }
}
