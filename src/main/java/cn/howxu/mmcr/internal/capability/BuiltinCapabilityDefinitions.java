package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.ResourceFacet;
import cn.howxu.mmcr.api.capability.facet.ScalarFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.type.CapabilityCreationContext;
import cn.howxu.mmcr.api.capability.type.CapabilityDefinition;
import cn.howxu.mmcr.api.capability.type.CapabilityRegistry;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import net.minecraft.resources.Identifier;

import java.util.Set;

/**
 * Registered definitions and creation contracts for MMCR's built-in capabilities.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class BuiltinCapabilityDefinitions {
    public static final CapabilityType ITEM_TYPE = new CapabilityType(Identifier.fromNamespaceAndPath("minecraft", "item"));
    public static final CapabilityType FLUID_TYPE = new CapabilityType(Identifier.fromNamespaceAndPath("minecraft", "fluid"));
    public static final CapabilityType ENERGY_TYPE = new CapabilityType(Identifier.fromNamespaceAndPath("neoforge", "energy"));

    private BuiltinCapabilityDefinitions() {
    }

    public static void register() {
        CapabilityRegistry.register(new CapabilityDefinition(
                ITEM_TYPE,
                Set.of(ResourceFacet.class, OperationFacet.class, PresentationFacet.class, SyncFacet.class,
                        TransferFacet.class),
                BuiltinCapabilityDefinitions::createItem));
        CapabilityRegistry.register(new CapabilityDefinition(
                FLUID_TYPE,
                Set.of(ResourceFacet.class, OperationFacet.class, PresentationFacet.class, SyncFacet.class,
                        TransferFacet.class),
                BuiltinCapabilityDefinitions::createFluid));
        CapabilityRegistry.register(new CapabilityDefinition(
                ENERGY_TYPE,
                Set.of(ScalarFacet.class, ValueFacet.class, SyncFacet.class, TransferFacet.class,
                        OperationFacet.class, PresentationFacet.class),
                BuiltinCapabilityDefinitions::createEnergy));
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
