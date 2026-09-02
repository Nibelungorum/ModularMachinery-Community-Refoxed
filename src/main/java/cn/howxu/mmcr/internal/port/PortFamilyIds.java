package cn.howxu.mmcr.internal.port;

import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import net.minecraft.resources.Identifier;

/**
 * Built-in port family identifiers.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PortFamilyIds {
    public static final Identifier ITEM = BuiltinCapabilityDefinitions.ITEM_TYPE.id();
    public static final Identifier FLUID = BuiltinCapabilityDefinitions.FLUID_TYPE.id();
    public static final Identifier ENERGY = BuiltinCapabilityDefinitions.ENERGY_TYPE.id();

    private PortFamilyIds() {
    }
}
