package cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.compat.ae2lt.LightningBridge;
import cn.howxu.mmcr.compat.ae2lt.LightningOutput;
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

    public static List<AE2ResourceFamily<?>> all() {
        return Stream.concat(Stream.<AE2ResourceFamily<?>>of(ITEM, FLUID),
                LightningBridge.get().resourceFamilies().stream()).toList();
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
        return key != null && all().stream().anyMatch(family -> family.adapter().toResource(key).isPresent());
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

    /** Item/fluid retain their existing external exposure declarations in each output kind. */
    public static List<CapabilityBinding> additionalOutputBindings() {
        return all().stream().filter(family -> family != ITEM && family != FLUID)
                .map(AE2ResourceFamilies::outputBinding).toList();
    }

    private static <R> CapabilityBinding outputBinding(AE2ResourceFamily<R> family) {
        return internalStorage(family.outputBinding(host -> ((OutputInterfaceBlockEntity) host).resourceStorage(family), false));
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
        if (stack == null || stack.amount() <= 0L || stack.amount() > Integer.MAX_VALUE) return Optional.empty();
        int amount = (int) stack.amount();
        if (stack.what() instanceof AEItemKey item) {
            return Optional.of(new MachineOutput.ItemOutput(item.toStack(amount), 1F));
        }
        if (stack.what() instanceof AEFluidKey fluid) {
            return Optional.of(new MachineOutput.FluidOutput(fluid.toStack(amount), 1F));
        }
        for (AE2ResourceFamily<?> family : all()) {
            Object resource = family.adapter().toResource(stack.what()).orElse(null);
            if (resource instanceof LightningTier tier) {
                return Optional.of(new LightningOutput(tier, stack.amount(), 1F));
            }
        }
        return Optional.empty();
    }
}
