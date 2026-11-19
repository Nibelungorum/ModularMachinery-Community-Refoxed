package cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import java.util.ArrayList;
import java.util.ServiceLoader;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.ItemResourceStorage;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.FluidResourceStorage;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.network.FluidNetworkResourceStorage;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.network.ItemNetworkResourceStorage;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.StockingInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.AsyncOutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.util.IOType;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The startup-stable resource families supported by MMCR's AE2 interfaces.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class AE2ResourceFamilies {
    public static final AE2ResourceFamily<ItemResource> ITEM = new AE2ResourceFamily<>(
            BuiltinCapabilityDefinitions.ITEM_TYPE,
            new AE2KeyAdapter<>() {
                @Override
                public AEKeyType keyType() {
                    return AEKeyType.items();
                }

                @Override public MachineOutput patternOutput(ItemResource resource, long amount) {
                    return amount > Integer.MAX_VALUE ? null : new MachineOutput.ItemOutput(resource.toStack((int) amount), 1F);
                }
                @Override public ItemResource patternResource(MachineOutput output) {
                    return output instanceof MachineOutput.ItemOutput value && !value.components().hasNonExactValues()
                            ? ItemResource.of(value.resolvedStack()) : null;
                }
                @Override public Predicate<ItemResource> patternInput(MachineRequirement value) {
                    if (!(value instanceof ItemRequirement r) || !r.io().isInput()) return null;
                    return resource -> r.item() != null
                            && r.item().test(resource.toStack(1)) && r.components().matches(resource.toStack(1));
                }
                @Override public CapabilityBinding.ExternalExposure<?> outputExposure() {
                    return new CapabilityBinding.ExternalExposure<>(Capabilities.Item.BLOCK.name(), ResourceHandler.asClass(),
                            (host, _, _) -> ModCapabilities.resourceStorageHandler(((OutputInterfaceBlockEntity) host).itemStorage(), false, true));
                }

                @Override
                public Class<ItemResource> resourceType() {
                    return ItemResource.class;
                }

                @Override
                public AEKey toKey(ItemResource resource) {
                    return AEItemKey.of(resource);
                }

                @Override
                public Optional<ItemResource> toResource(AEKey key) {
                    return key instanceof AEItemKey itemKey
                            ? Optional.of(itemKey.toResource())
                            : Optional.empty();
                }
            },
            new PortFamilyDescriptor(PortFamilyIds.ITEM, IOType.INPUT, ItemBusSize.values().length,
                    List.of("item_input_bus")),
            new PortFamilyDescriptor(PortFamilyIds.ITEM, IOType.OUTPUT, ItemBusSize.LUDICROUS.ordinal() + 1,
                    List.of("item_output_bus")),
            ItemResourceStorage::new,
            ItemNetworkResourceStorage::new,
            ItemBusCapability::new);

    public static final AE2ResourceFamily<FluidResource> FLUID = new AE2ResourceFamily<>(
            BuiltinCapabilityDefinitions.FLUID_TYPE,
            new AE2KeyAdapter<>() {
                @Override
                public AEKeyType keyType() {
                    return AEKeyType.fluids();
                }

                @Override public MachineOutput patternOutput(FluidResource resource, long amount) {
                    return amount > Integer.MAX_VALUE ? null : new MachineOutput.FluidOutput(resource.toStack((int) amount), 1F);
                }
                @Override public FluidResource patternResource(MachineOutput output) {
                    return output instanceof MachineOutput.FluidOutput value
                            ? FluidResource.of(value.stack()) : null;
                }
                @Override public Predicate<FluidResource> patternInput(MachineRequirement value) {
                    if (!(value instanceof FluidRequirement r) || !r.io().isInput()) return null;
                    return resource -> r.fluid() != null
                            && r.fluid().test(resource.toStack(1));
                }
                @Override public CapabilityBinding.ExternalExposure<?> outputExposure() {
                    return new CapabilityBinding.ExternalExposure<>(Capabilities.Fluid.BLOCK.name(), ResourceHandler.asClass(),
                            (host, _, _) -> ModCapabilities.resourceStorageHandler(((OutputInterfaceBlockEntity) host).fluidStorage(), false, true));
                }

                @Override
                public Class<FluidResource> resourceType() {
                    return FluidResource.class;
                }

                @Override
                public AEKey toKey(FluidResource resource) {
                    return AEFluidKey.of(resource);
                }

                @Override
                public Optional<FluidResource> toResource(AEKey key) {
                    return key instanceof AEFluidKey fluidKey
                            ? Optional.of(fluidKey.toResource())
                            : Optional.empty();
                }
            },
            new PortFamilyDescriptor(PortFamilyIds.FLUID, IOType.INPUT, FluidHatchSize.values().length,
                    List.of("fluid_input_hatch")),
            new PortFamilyDescriptor(PortFamilyIds.FLUID, IOType.OUTPUT, FluidHatchSize.VACUUM.ordinal() + 1,
                    List.of("fluid_output_hatch")),
            FluidResourceStorage::new,
            FluidNetworkResourceStorage::new,
            FluidHatchCapability::new);

    private AE2ResourceFamilies() {
    }

    private static final List<AE2ResourceFamily<?>> declarations = new ArrayList<>(List.of(ITEM, FLUID));
    private static volatile List<AE2ResourceFamily<?>> frozen;

    public static synchronized void register(AE2ResourceFamily<?> family) {
        if (frozen != null) throw new IllegalStateException("AE2 resource families are frozen");
        if (declarations.stream().anyMatch(existing -> existing.capabilityType().equals(family.capabilityType()))) {
            throw new IllegalArgumentException("Duplicate AE2 resource family " + family.capabilityType().id());
        }
        declarations.add(family);
    }

    public static List<AE2ResourceFamily<?>> all() {
        var result = frozen;
        if (result != null) return result;
        synchronized (AE2ResourceFamilies.class) {
            if (frozen == null) {
                for (var contributor : ServiceLoader.load(AE2FamilyContributor.class, AE2FamilyContributor.class.getClassLoader())) {
                    contributor.contribute(AE2ResourceFamilies::register);
                }
                frozen = List.copyOf(declarations);
            }
            return frozen;
        }
    }

    public static synchronized void resetForTesting() {
        frozen = null;
        declarations.clear();
        declarations.addAll(List.of(ITEM, FLUID));
    }

    public static List<PortFamilyDescriptor> inputFamilies() {
        return all().stream().map(AE2ResourceFamily::inputFamily).toList();
    }

    public static List<PortFamilyDescriptor> outputFamilies() {
        return all().stream().map(AE2ResourceFamily::outputFamily).toList();
    }

    public static List<PortFamilyDescriptor> patternFamilies() {
        return Stream.concat(inputFamilies().stream(), outputFamilies().stream()).toList();
    }

    public static boolean supportsKey(AEKey key) {
        return forKey(key).isPresent();
    }

    public static Optional<AE2ResourceFamily<?>> forKey(AEKey key) {
        if (key == null) return Optional.empty();
        AE2ResourceFamily<?> result = null;
        for (var family : all()) {
            if (family.adapter().toResource(key).isEmpty()) continue;
            if (result != null) throw new IllegalStateException("Ambiguous AE2 key: " + key + " for "
                    + result.capabilityType().id() + " and " + family.capabilityType().id());
            result = family;
        }
        return Optional.ofNullable(result);
    }

    public static List<CapabilityBinding> inputBindings() {
        return all().stream().map(AE2ResourceFamilies::inputBinding).toList();
    }

    private static <R> CapabilityBinding inputBinding(AE2ResourceFamily<R> family) {
        return internalStorage(family.inputBinding(host -> ((InputInterfaceBlockEntity) host).resourceStorage(family), false));
    }

    public static List<CapabilityBinding> stockingInputBindings() {
        return all().stream().map(AE2ResourceFamilies::stockingInputBinding).toList();
    }

    private static <R> CapabilityBinding stockingInputBinding(AE2ResourceFamily<R> family) {
        return internalStorage(family.inputBinding(host -> ((StockingInterfaceBlockEntity) host).resourceStorage(family), false));
    }

    public static List<CapabilityBinding> outputBindings() {
        return all().stream().map(AE2ResourceFamilies::outputBinding).toList();
    }

    private static <R> CapabilityBinding outputBinding(AE2ResourceFamily<R> family) {
        var exposure = family.adapter().outputExposure();
        var binding = family.binding(IOType.OUTPUT, CapabilityDirections.output(),
                host -> ((OutputInterfaceBlockEntity) host).resourceStorage(family), exposure != null, exposure);
        return exposure == null ? internalStorage(binding) : binding;
    }

    public static List<CapabilityBinding> asyncOutputBindings() {
        return all().stream().map(AE2ResourceFamilies::asyncOutputBinding).toList();
    }

    private static <R> CapabilityBinding asyncOutputBinding(AE2ResourceFamily<R> family) {
        return internalStorage(family.outputBinding(host -> ((AsyncOutputInterfaceBlockEntity) host).resourceStorage(family), false));
    }

    public static List<CapabilityBinding> patternBindings() {
        return all().stream().map(AE2ResourceFamilies::patternBinding).toList();
    }

    private static <R> CapabilityBinding patternBinding(AE2ResourceFamily<R> family) {
        return internalStorage(family.binding(IOType.OUTPUT, CapabilityDirections.bidirectional(),
                host -> ((PatternInterfaceBlockEntity) host).resourceStorage(family), false, null));
    }

    // These bindings see the machine's full inventory, not AE2's externally accessible slots.
    private static CapabilityBinding internalStorage(CapabilityBinding binding) {
        return CapabilityBinding.internalOnly(binding.type(), binding.directions(), binding.factory(), binding.tierPolicy());
    }

    public static Optional<MachineOutput> patternOutput(GenericStack stack) {
        if (stack == null || stack.amount() <= 0L) return Optional.empty();
        return forKey(stack.what()).flatMap(family -> decode(family.adapter(), stack));
    }

    private static <R> Optional<MachineOutput> decode(AE2KeyAdapter<R> adapter, GenericStack stack) {
        return adapter.toResource(stack.what()).map(resource -> adapter.patternOutput(resource, stack.amount()));
    }

    public static Optional<AEKey> patternKey(MachineOutput output) {
        AEKey result = null;
        for (var family : all()) {
            AEKey key = encode(family.adapter(), output);
            if (key == null) continue;
            if (result != null || forKey(key).orElse(null) != family) throw new IllegalArgumentException("Ambiguous pattern output");
            result = key;
        }
        return Optional.ofNullable(result);
    }

    private static <R> AEKey encode(AE2KeyAdapter<R> adapter, MachineOutput output) {
        R resource = adapter.patternResource(output);
        return resource == null ? null : adapter.toKey(resource);
    }
}
