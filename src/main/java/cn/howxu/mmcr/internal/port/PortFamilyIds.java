package cn.howxu.mmcr.internal.port;

import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import net.minecraft.resources.ResourceLocation;

/**
 * Built-in port family identifiers.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PortFamilyIds {
    public static final ResourceLocation ITEM = BuiltinCapabilityDefinitions.ITEM_TYPE.id();
    public static final ResourceLocation FLUID = BuiltinCapabilityDefinitions.FLUID_TYPE.id();
    public static final ResourceLocation ENERGY = BuiltinCapabilityDefinitions.ENERGY_TYPE.id();

    private PortFamilyIds() {
    }
}
