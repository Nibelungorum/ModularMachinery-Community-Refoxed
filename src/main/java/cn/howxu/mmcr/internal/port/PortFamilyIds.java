package cn.howxu.mmcr.internal.port;

import cn.howxu.mmcr.MMCR;
import net.minecraft.resources.Identifier;

/**
 * Built-in port family identifiers.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PortFamilyIds {
    public static final Identifier ITEM = MMCR.id("item");
    public static final Identifier FLUID = MMCR.id("fluid");
    public static final Identifier ENERGY = MMCR.id("energy");

    private PortFamilyIds() {
    }
}
