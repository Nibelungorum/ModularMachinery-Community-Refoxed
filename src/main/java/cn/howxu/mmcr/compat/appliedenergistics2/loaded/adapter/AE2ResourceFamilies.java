package cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.port.PortDefinition;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import java.util.ArrayList;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import java.util.function.Predicate;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import java.util.ServiceLoader;
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

    private static final List<InterfaceFamilyContributor> CONTRIBUTORS = ServiceLoader.load(
            InterfaceFamilyContributor.class, InterfaceFamilyContributor.class.getClassLoader()).stream()
            .map(ServiceLoader.Provider::get).toList();

    private static final InterfaceFamilyContributor NATIVE = new InterfaceFamilyContributor() {
        public boolean owns(AEKeyType type) { return type == AEKeyType.items() || type == AEKeyType.fluids(); }
        public boolean exposesLocalSlots() { return true; }
        public boolean acceptsPatternKey(AEKey key) { return key instanceof AEItemKey || key instanceof AEFluidKey; }
        public MachineOutput patternOutput(AEKey key, long amount) {
            if (amount <= 0 || amount > Integer.MAX_VALUE) return null;
            if (key instanceof AEItemKey item) return new MachineOutput.ItemOutput(item.toStack((int) amount), 1F);
            if (key instanceof AEFluidKey fluid) return new MachineOutput.FluidOutput(fluid.toStack((int) amount), 1F);
            return null;
        }
        public Predicate<AEKey> inputMatcher(MachineRequirement value) {
            if (value instanceof ItemRequirement item) return k -> k instanceof AEItemKey key && item.item() != null
                    && item.item().test(key.toStack()) && item.components().matches(key.toStack());
            if (value instanceof FluidRequirement fluid) return k -> k instanceof AEFluidKey key && fluid.fluid() != null
                    && fluid.fluid().test(key.toStack(1));
            return null;
        }
        public List<MachineCapability> requestCapabilities(GenericStackInv inventory, KeyCounter[] holders) {
            return List.of(new ItemBusCapability(AE2NativeAdapters.items(inventory), IOType.INPUT),
                    new FluidHatchCapability(AE2NativeAdapters.fluids(inventory), IOType.INPUT));
        }
    };

    public static InterfaceFamilyContributor forType(AEKeyType type) {
        if (type == null) return null;
        InterfaceFamilyContributor found = NATIVE.owns(type) ? NATIVE : null;
        for (var family : CONTRIBUTORS) {
            if (!family.owns(type)) continue;
            if (found != null) throw new IllegalStateException("Ambiguous interface family: " + type.getId());
            found = family;
        }
        return found;
    }

    public static boolean exposesLocalSlots(AEKeyType type) {
        var family = forType(type);
        return family != null && family.exposesLocalSlots();
    }

    public static Predicate<AEKey> inputMatcher(MachineRequirement value) {
        var found = NATIVE.inputMatcher(value);
        for (var family : CONTRIBUTORS) {
            var matcher = family.inputMatcher(value);
            if (matcher == null) continue;
            if (found != null) throw new IllegalArgumentException("Ambiguous pattern matcher for " + value.type().id());
            found = matcher;
        }
        return found;
    }

    public static List<MachineCapability> requestCapabilities(GenericStackInv inventory, KeyCounter[] holders) {
        var result = new ArrayList<>(NATIVE.requestCapabilities(inventory, holders));
        for (var family : CONTRIBUTORS) result.addAll(family.requestCapabilities(inventory, holders));
        return List.copyOf(result);
    }

    public static long flush(IOPortBlockEntity host, long budget) {
        long moved = 0;
        for (var family : CONTRIBUTORS) {
            if (moved >= budget) break;
            moved += family.flush(host, budget - moved);
        }
        return moved;
    }

    private static List<PortFamilyDescriptor> families(List<PortFamilyDescriptor> base) {
        for (var contributor : CONTRIBUTORS) base = List.copyOf(contributor.families(base));
        return base;
    }

    private AE2ResourceFamilies() {
    }

    public static PortDefinition definition(ResourceLocation id, List<CapabilityBinding> bindings) {
        CapabilityBinding primary = bindings.getFirst();
        for (var contributor : CONTRIBUTORS)
            bindings = List.copyOf(contributor.bindings(bindings, primary.directions(), primary.nativeTransferExposure()));
        return PortDefinition.of(id, bindings);
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
        return families(List.of(ITEM_INPUT, FLUID_INPUT));
    }

    public static List<PortFamilyDescriptor> outputFamilies() {
        return families(List.of(ITEM_OUTPUT, FLUID_OUTPUT));
    }

    public static List<PortFamilyDescriptor> patternFamilies() {
        return families(List.of(ITEM_INPUT, FLUID_INPUT, ITEM_OUTPUT, FLUID_OUTPUT));
    }
}
