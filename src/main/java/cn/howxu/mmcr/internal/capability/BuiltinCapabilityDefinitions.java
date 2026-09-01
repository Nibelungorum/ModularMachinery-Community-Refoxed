package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplayRegistry;
import cn.howxu.mmcr.api.publicapi.machine.DisplayStack;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.ResourceFacet;
import cn.howxu.mmcr.api.capability.facet.ScalarFacet;
import cn.howxu.mmcr.api.capability.type.CapabilityCreationContext;
import cn.howxu.mmcr.api.capability.type.CapabilityDefinition;
import cn.howxu.mmcr.api.capability.type.CapabilityRegistry;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;

import java.util.Set;
import java.util.List;
import java.util.Optional;

/**
 * Registered definitions and creation contracts for MMCR's built-in capabilities.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class BuiltinCapabilityDefinitions {
    public static final CapabilityType ITEM_TYPE = new CapabilityType(MMCR.id("item"));
    public static final CapabilityType FLUID_TYPE = new CapabilityType(MMCR.id("fluid"));
    public static final CapabilityType ENERGY_TYPE = new CapabilityType(MMCR.id("energy"));

    private BuiltinCapabilityDefinitions() {
    }

    public static void register() {
        CapabilityRegistry.register(new CapabilityDefinition(
                ITEM_TYPE,
                Set.of(ResourceFacet.class, OperationFacet.class),
                BuiltinCapabilityDefinitions::createItem));
        CapabilityRegistry.register(new CapabilityDefinition(
                FLUID_TYPE,
                Set.of(ResourceFacet.class, OperationFacet.class),
                BuiltinCapabilityDefinitions::createFluid));
        CapabilityRegistry.register(new CapabilityDefinition(
                ENERGY_TYPE,
                Set.of(ScalarFacet.class, OperationFacet.class),
                BuiltinCapabilityDefinitions::createEnergy));
        registerDisplays();
    }

    private static void registerDisplays() {
        CapabilityDisplayRegistry registry = CapabilityDisplayRegistry.global();
        registry.register(ITEM_TYPE, capability -> {
            ItemBusCapability item = (ItemBusCapability) capability;
            return java.util.stream.IntStream.range(0, item.storage().size())
                    .mapToObj(slot -> itemDisplay(item, slot))
                    .toList();
        });
        registry.register(FLUID_TYPE, capability -> {
            FluidHatchCapability fluid = (FluidHatchCapability) capability;
            return java.util.stream.IntStream.range(0, fluid.storage().size())
                    .mapToObj(slot -> new CapabilityDisplay("fluid", Long.toString(fluid.storage().amount(slot)), "mB",
                            Optional.empty()))
                    .toList();
        });
        registry.register(ENERGY_TYPE, capability -> {
            EnergyHatchCapability energy = (EnergyHatchCapability) capability;
            return List.of(new CapabilityDisplay("energy", Long.toString(energy.storage().amount()), "FE", Optional.empty()));
        });
        registry.register(new CapabilityType(MMCR.id("smart_interface")), capability -> {
            SmartInterfaceCapability smart = (SmartInterfaceCapability) capability;
            return smart.storage().values().entrySet().stream()
                    .map(entry -> new CapabilityDisplay(entry.getKey(), Float.toString(entry.getValue()), "value", Optional.empty()))
                    .toList();
        });
    }

    private static CapabilityDisplay itemDisplay(ItemBusCapability item, int slot) {
        var resource = item.storage().resource(slot);
        Optional<DisplayStack> icon = resource == null ? Optional.empty() : DisplayStack.optional(resource.toStack(1));
        return new CapabilityDisplay("item", Long.toString(item.storage().amount(slot)), "item", icon);
    }

    private static MachineCapability createItem(CapabilityCreationContext context) {
        IOPortBlockEntity port = port(context);
        return new ItemBusCapability(port, port.itemStorage(), context.ioType());
    }

    private static MachineCapability createFluid(CapabilityCreationContext context) {
        IOPortBlockEntity port = port(context);
        return new FluidHatchCapability(port, port.fluidStorage(), context.ioType());
    }

    private static MachineCapability createEnergy(CapabilityCreationContext context) {
        IOPortBlockEntity port = port(context);
        return new EnergyHatchCapability(port, port.getEnergyStorage(), context.ioType());
    }

    private static IOPortBlockEntity port(CapabilityCreationContext context) {
        if (context.host() instanceof IOPortBlockEntity port) return port;
        throw new IllegalArgumentException("Built-in port capability requires an IOPortBlockEntity host");
    }
}
