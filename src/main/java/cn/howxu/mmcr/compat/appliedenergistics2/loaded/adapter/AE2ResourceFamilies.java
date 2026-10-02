package cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.compat.appmek.AppMekBridge;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Function;

/**
 * The two AE2 resource families supported by MMCR interfaces.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class AE2ResourceFamilies {
    private static final PortFamilyDescriptor ITEM_INPUT = new PortFamilyDescriptor(PortFamilyIds.ITEM, IOType.INPUT,
            ItemBusSize.values().length, List.of("item_input_bus"));
    private static final PortFamilyDescriptor ITEM_OUTPUT = new PortFamilyDescriptor(PortFamilyIds.ITEM, IOType.OUTPUT,
            ItemBusSize.LUDICROUS.ordinal() + 1, List.of("item_output_bus"));
    private static final PortFamilyDescriptor FLUID_INPUT = new PortFamilyDescriptor(PortFamilyIds.FLUID, IOType.INPUT,
            FluidHatchSize.values().length, List.of("fluid_input_hatch"));
    private static final PortFamilyDescriptor FLUID_OUTPUT = new PortFamilyDescriptor(PortFamilyIds.FLUID, IOType.OUTPUT,
            FluidHatchSize.VACUUM.ordinal() + 1, List.of("fluid_output_hatch"));

    private AE2ResourceFamilies() {
    }

    public static PortDefinition definition(ResourceLocation id, List<CapabilityBinding> bindings) {
        CapabilityBinding primary = bindings.getFirst();
        return PortDefinition.of(id, AppMekBridge.get().appendBindings(bindings,
                primary.directions(), primary.nativeTransferExposure()));
    }

    public static CapabilityBinding itemBinding(CapabilityDirections directions,
                                                 Function<IOPortBlockEntity, IItemHandler> factory) {
        return itemBinding(directions, factory, true);
    }

    public static CapabilityBinding itemBinding(CapabilityDirections directions,
                                                 Function<IOPortBlockEntity, IItemHandler> factory,
                                                 boolean nativeTransferExposure) {
        return new CapabilityBinding(BuiltinCapabilityDefinitions.ITEM_TYPE, directions, context -> {
            IOPortBlockEntity host = (IOPortBlockEntity) context.host();
            IOType direction = directions.supports(IOType.INPUT) && directions.supports(IOType.OUTPUT)
                    ? IOType.OUTPUT : context.ioType();
            return new ItemBusCapability(host, factory.apply(host), direction, nativeTransferExposure);
        }, (binding, tier) -> true, nativeTransferExposure);
    }

    public static CapabilityBinding fluidBinding(CapabilityDirections directions,
                                                  Function<IOPortBlockEntity, IFluidHandler> factory) {
        return fluidBinding(directions, factory, true);
    }

    public static CapabilityBinding fluidBinding(CapabilityDirections directions,
                                                  Function<IOPortBlockEntity, IFluidHandler> factory,
                                                  boolean nativeTransferExposure) {
        return new CapabilityBinding(BuiltinCapabilityDefinitions.FLUID_TYPE, directions, context -> {
            IOPortBlockEntity host = (IOPortBlockEntity) context.host();
            IOType direction = directions.supports(IOType.INPUT) && directions.supports(IOType.OUTPUT)
                    ? IOType.OUTPUT : context.ioType();
            return new FluidHatchCapability(host, factory.apply(host), direction, nativeTransferExposure);
        }, (binding, tier) -> true, nativeTransferExposure);
    }

    public static List<PortFamilyDescriptor> inputFamilies() {
        return AppMekBridge.get().appendFamilies(List.of(ITEM_INPUT, FLUID_INPUT));
    }

    public static List<PortFamilyDescriptor> outputFamilies() {
        return AppMekBridge.get().appendFamilies(List.of(ITEM_OUTPUT, FLUID_OUTPUT));
    }

    public static List<PortFamilyDescriptor> patternFamilies() {
        return AppMekBridge.get().appendFamilies(List.of(ITEM_INPUT, FLUID_INPUT, ITEM_OUTPUT, FLUID_OUTPUT));
    }
}
