package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.external.ExternalCapabilityAdapter;
import cn.howxu.mmcr.api.capability.external.ExternalCapabilityContext;
import cn.howxu.mmcr.api.capability.external.ExternalCapabilityRegistry;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.internal.storage.LongEnergyHandler;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public final class ModCapabilities {
    public static final BlockCapability<ResourceHandler<ItemResource>, Direction> ITEM_BLOCK =
            Capabilities.Item.BLOCK;
    public static final BlockCapability<ResourceHandler<FluidResource>, Direction> FLUID_BLOCK =
            Capabilities.Fluid.BLOCK;
    public static final BlockCapability<EnergyHandler, Direction> ENERGY_BLOCK =
            Capabilities.Energy.BLOCK;
    private static final CapabilityType ITEM_TYPE = BuiltinCapabilityDefinitions.ITEM_TYPE;
    private static final CapabilityType FLUID_TYPE = BuiltinCapabilityDefinitions.FLUID_TYPE;
    private static final CapabilityType ENERGY_TYPE = BuiltinCapabilityDefinitions.ENERGY_TYPE;
    private static final Map<Identifier, BiConsumer<RegisterCapabilitiesEvent, IOPortKind>> NATIVE_REGISTRATIONS =
            Map.of(
                    PortFamilyIds.ITEM, ModCapabilities::registerItemPort,
                    PortFamilyIds.FLUID, ModCapabilities::registerFluidPort,
                    PortFamilyIds.ENERGY, ModCapabilities::registerEnergyPort);

    private static boolean externalAdaptersInitialized;

    private ModCapabilities() {}

    /** Ensures built-in capability adapters are installed before event listeners are exposed. */
    public static synchronized void initializeExternalAdapters() {
        if (externalAdaptersInitialized) return;
        ExternalCapabilityRegistry.global().register(new NeoForgeCapabilityAdapter());
        externalAdaptersInitialized = true;
    }

    public static void register(RegisterCapabilitiesEvent event) {
        initializeExternalAdapters();
        ExternalCapabilityContext context = new ExternalCapabilityContext();
        ExternalCapabilityRegistry.global().freeze(context);
        for (IOPortKind kind : nativeCapabilityPorts()) {
            registerNativePort(event, kind, context);
        }
        event.registerBlockEntity(
                ITEM_BLOCK,
                ModBlockEntities.BES.get("factory_controller").get(),
                (be, side) -> be instanceof FactorySchedulerBlockEntity scheduler
                        ? new ItemStackResourceHandler(scheduler.getItemStackHandler(side), true, true)
                        : null);
    }

    static Set<String> nativeCapabilityPortIds() {
        Set<String> ids = new LinkedHashSet<>();
        nativeCapabilityPorts().forEach(kind -> ids.add(kind.id()));
        return Set.copyOf(ids);
    }

    static Set<String> nativeCapabilityBlockEntityIds() {
        Set<String> ids = new LinkedHashSet<>(nativeCapabilityPortIds());
        ids.add("factory_controller");
        return Set.copyOf(ids);
    }

    private static List<IOPortKind> nativeCapabilityPorts() {
        return PortKinds.all().stream()
                .filter(kind -> !kind.families().isEmpty() || !externalBindings(kind).isEmpty())
                .toList();
    }

    private static void registerNativePort(RegisterCapabilitiesEvent event, IOPortKind kind,
                                           ExternalCapabilityContext context) {
        List<CapabilityBinding> externalBindings = externalBindings(kind);
        Set<CapabilityType> externallyExposed = externalBindings.stream()
                .filter(binding -> !context.bindings(binding.type()).isEmpty())
                .map(CapabilityBinding::type)
                .collect(java.util.stream.Collectors.toSet());
        for (CapabilityBinding binding : externalBindings) {
            context.bindings(binding.type()).forEach(exposure -> registerExternalPort(event, kind, binding, exposure));
        }
        kind.families().stream()
                .filter(family -> !externallyExposed.contains(new CapabilityType(family.familyId())))
                .map(PortFamilyDescriptor::familyId)
                .map(NATIVE_REGISTRATIONS::get)
                .filter(registration -> registration != null)
                .forEach(registration -> registration.accept(event, kind));
    }

    static List<CapabilityBinding> externalBindings(IOPortKind kind) {
        if (kind == null) return List.of();
        return kind.definition().bindings().stream()
                .filter(binding -> binding.externalExposure().isPresent())
                .toList();
    }

    private static Map<CapabilityType, CapabilityBinding.ExternalExposure<?>> externalExposuresByType() {
        Map<CapabilityType, CapabilityBinding.ExternalExposure<?>> exposures = new java.util.LinkedHashMap<>();
        PortKinds.all().forEach(kind -> externalBindings(kind).forEach(binding ->
                binding.externalExposure().ifPresent(exposure -> exposures.putIfAbsent(binding.type(), exposure))));
        return Map.copyOf(exposures);
    }

    private static <T> void registerExternalPort(RegisterCapabilitiesEvent event, IOPortKind kind,
                                                  CapabilityBinding binding,
                                                  CapabilityBinding.ExternalExposure<T> exposure) {
        BlockCapability<T, Direction> capability = BlockCapability.createSided(exposure.id(), exposure.valueType());
        event.registerBlockEntity(
                capability,
                ModBlockEntities.BES.get(kind.id()).get(),
                (be, side) -> {
                    if (!(be instanceof IOPortBlockEntity port)
                            || port.ioType() != binding.ioType()
                            || !port.isAutoIOSideExposed(binding.type(), side)
                            || port.capability(binding.type()) == null) return null;
                    return exposure.resolver().resolve(port, binding.ioType(), side);
                });
    }

    /** Bridges the generic MMCR external exposure declaration to NeoForge block capabilities.
     * @author howxu <dev@howxu.cn>
     */
    private static final class NeoForgeCapabilityAdapter implements ExternalCapabilityAdapter {
        @Override
        public Identifier id() {
            return MMCR.id("neoforge");
        }

        @Override
        public Set<CapabilityType> capabilityTypes() {
            return externalExposuresByType().keySet();
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void register(ExternalCapabilityContext context) {
            externalExposuresByType().forEach(context::bind);
        }
    }

    private static void registerItemPort(RegisterCapabilitiesEvent event, IOPortKind kind) {
        boolean canInsert = kind.ioType() == IOType.INPUT;
        event.registerBlockEntity(
                ITEM_BLOCK,
                ModBlockEntities.BES.get(kind.id()).get(),
                (be, side) -> {
                    if (!(be instanceof IOPortBlockEntity port) || !port.isAutoIOSideExposed(ITEM_TYPE, side)) return null;
                    MachineCapability capability = port.capability(ITEM_TYPE);
                    if (!(capability instanceof ItemBusCapability item)) return null;
                    if (be instanceof ItemBusBlockEntity ib) {
                        return new ItemStackResourceHandler(ib.getItemStackHandler(side), canInsert, true);
                    }
                    return resourceStorageHandler(item.storage(), canInsert, true);
                });
    }

    private static void registerFluidPort(RegisterCapabilitiesEvent event, IOPortKind kind) {
        boolean canInsert = kind.ioType() == IOType.INPUT;
        event.registerBlockEntity(
                FLUID_BLOCK,
                ModBlockEntities.BES.get(kind.id()).get(),
                (be, side) -> {
                    if (!(be instanceof IOPortBlockEntity port) || !port.isAutoIOSideExposed(FLUID_TYPE, side)) return null;
                    MachineCapability capability = port.capability(FLUID_TYPE);
                    if (capability == null || !(capability.storage() instanceof ResourceStorage<?> storage)) return null;
                    return resourceStorageHandler(fluidStorage(storage), canInsert, !canInsert);
                });
    }

    private static void registerEnergyPort(RegisterCapabilitiesEvent event, IOPortKind kind) {
        boolean canInsert = kind.ioType() == IOType.INPUT;
        event.registerBlockEntity(
                ENERGY_BLOCK,
                ModBlockEntities.BES.get(kind.id()).get(),
                (be, side) -> {
                    if (!(be instanceof IOPortBlockEntity port) || !port.isAutoIOSideExposed(ENERGY_TYPE, side)) return null;
                    MachineCapability capability = port.capability(ENERGY_TYPE);
                    if (!(capability instanceof EnergyHatchCapability energy)) return null;
                    return new DirectionalEnergyHandler(new EnergyHandlerAdapter(energy.storage()), canInsert, !canInsert);
                });
    }

    @SuppressWarnings("unchecked")
    private static ResourceStorage<FluidResource> fluidStorage(ResourceStorage<?> storage) {
        return (ResourceStorage<FluidResource>) storage;
    }

    private static <R extends Resource> ResourceHandler<R> resourceStorageHandler(ResourceStorage<R> storage,
                                                                                    boolean canInsert,
                                                                                    boolean canExtract) {
        return new ResourceStorageHandler<>(storage, canInsert, canExtract);
    }

    private static final class DirectionalEnergyHandler implements LongEnergyHandler {
        private final LongEnergyHandler handler;
        private final boolean canInsert;
        private final boolean canExtract;

        DirectionalEnergyHandler(LongEnergyHandler handler, boolean canInsert, boolean canExtract) {
            this.handler = handler;
            this.canInsert = canInsert;
            this.canExtract = canExtract;
        }

        @Override
        public long getAmountAsLong() {
            return handler.getAmountAsLong();
        }

        @Override
        public long getCapacityAsLong() {
            return handler.getCapacityAsLong();
        }

        @Override
        public long getTransferLimit() {
            return handler.getTransferLimit();
        }

        @Override
        public int insert(int amount, TransactionContext tx) {
            TransferPreconditions.checkNonNegative(amount);
            if (!canInsert) return 0;
            return handler.insert(amount, tx);
        }

        @Override
        public int extract(int amount, TransactionContext tx) {
            TransferPreconditions.checkNonNegative(amount);
            if (!canExtract) return 0;
            return handler.extract(amount, tx);
        }

        @Override
        public long insertLong(long amount, TransactionContext tx) {
            if (amount < 0L) throw new IllegalArgumentException("amount must be non-negative");
            if (!canInsert) return 0L;
            return handler.insertLong(amount, tx);
        }

        @Override
        public long extractLong(long amount, TransactionContext tx) {
            if (amount < 0L) throw new IllegalArgumentException("amount must be non-negative");
            if (!canExtract) return 0L;
            return handler.extractLong(amount, tx);
        }
    }

    private static final class EnergyHandlerAdapter implements LongEnergyHandler {
        private final LongValueStorage storage;

        private EnergyHandlerAdapter(LongValueStorage storage) {
            this.storage = storage;
        }

        @Override public long getAmountAsLong() { return storage.amount(); }
        @Override public long getCapacityAsLong() { return storage.capacity(); }
        @Override public long getTransferLimit() { return storage.transferLimit(); }
        @Override public int insert(int amount, TransactionContext tx) {
            return (int) storage.insert(amount, tx);
        }
        @Override public int extract(int amount, TransactionContext tx) {
            return (int) storage.extract(amount, tx);
        }
        @Override public long insertLong(long amount, TransactionContext tx) {
            if (amount < 0L) throw new IllegalArgumentException("amount must be non-negative");
            return storage.insert(amount, tx);
        }
        @Override public long extractLong(long amount, TransactionContext tx) {
            if (amount < 0L) throw new IllegalArgumentException("amount must be non-negative");
            return storage.extract(amount, tx);
        }
    }

    private static final class ResourceStorageHandler<R extends Resource> implements ResourceHandler<R> {
        private final ResourceStorage<R> storage;
        private final boolean canInsert;
        private final boolean canExtract;

        private ResourceStorageHandler(ResourceStorage<R> storage, boolean canInsert, boolean canExtract) {
            this.storage = storage;
            this.canInsert = canInsert;
            this.canExtract = canExtract;
        }

        @Override public int size() { return storage.size(); }
        @Override public R getResource(int slot) {
            R resource = storage.resource(slot);
            return resource == null ? emptyResource() : resource;
        }
        @Override public long getAmountAsLong(int slot) { return storage.amount(slot); }
        @Override public long getCapacityAsLong(int slot, R resource) { return storage.capacity(slot, resource); }
        @Override public boolean isValid(int slot, R resource) {
            TransferPreconditions.checkNonEmpty(resource);
            return storage.isValid(slot, resource);
        }
        @Override public int insert(int slot, R resource, int amount, TransactionContext tx) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            return canInsert ? (int) storage.insert(slot, resource, amount, tx) : 0;
        }
        @Override public int extract(int slot, R resource, int amount, TransactionContext tx) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            return canExtract ? (int) storage.extract(slot, resource, amount, tx) : 0;
        }

        @SuppressWarnings("unchecked")
        private R emptyResource() {
            if (storage.resourceType() == ItemResource.class) return (R) ItemResource.EMPTY;
            if (storage.resourceType() == FluidResource.class) return (R) FluidResource.EMPTY;
            throw new IllegalStateException("Missing empty resource for " + storage.resourceType().getName());
        }
    }

    private static final class ItemStackResourceHandler extends SnapshotJournal<List<ItemStack>> implements ResourceHandler<ItemResource> {
        private final ItemStackHandler handler;
        private final boolean canInsert;
        private final boolean canExtract;

        ItemStackResourceHandler(ItemStackHandler handler, boolean canInsert, boolean canExtract) {
            this.handler = handler;
            this.canInsert = canInsert;
            this.canExtract = canExtract;
        }

        @Override
        public int size() {
            return handler.getSlots();
        }

        @Override
        public ItemResource getResource(int slot) {
            ItemStack stack = handler.getStackInSlot(slot);
            return stack.isEmpty() ? ItemResource.EMPTY : ItemResource.of(stack);
        }

        @Override
        public long getAmountAsLong(int slot) {
            return handler.getStackInSlot(slot).getCount();
        }

        @Override
        public long getCapacityAsLong(int slot, ItemResource resource) {
            return resource.isEmpty()
                    ? handler.getSlotLimit(slot)
                    : Math.min(handler.getSlotLimit(slot), resource.getMaxStackSize());
        }

        @Override
        public boolean isValid(int slot, ItemResource resource) {
            return handler.isItemValid(slot, resource.toStack(1));
        }

        @Override
        public int insert(int slot, ItemResource resource, int amount, TransactionContext tx) {
            if (!canInsert) return 0;
            ResourceStorage<ItemResource> transactionalStorage = transactionalStorage();
            if (transactionalStorage != null) {
                return (int) transactionalStorage.insert(slot, resource, amount, tx);
            }
            updateSnapshots(tx);
            ItemStack remainder = handler.insertItem(slot, resource.toStack(amount), false);
            int inserted = amount - remainder.getCount();
            return inserted;
        }

        @Override
        public int extract(int slot, ItemResource resource, int amount, TransactionContext tx) {
            if (!canExtract) return 0;
            ResourceStorage<ItemResource> transactionalStorage = transactionalStorage();
            if (transactionalStorage != null) {
                return (int) transactionalStorage.extract(slot, resource, amount, tx);
            }
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || !ItemResource.of(stack).equals(resource)) return 0;
            updateSnapshots(tx);
            ItemStack extracted = handler.extractItem(slot, amount, false);
            return extracted.getCount();
        }

        @SuppressWarnings("unchecked")
        private @Nullable ResourceStorage<ItemResource> transactionalStorage() {
            return handler instanceof ResourceStorage<?> storage ? (ResourceStorage<ItemResource>) storage : null;
        }

        @Override
        protected List<ItemStack> createSnapshot() {
            List<ItemStack> stacks = new ArrayList<>();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                stacks.add(handler.getStackInSlot(slot).copy());
            }
            return stacks;
        }

        @Override
        protected void revertToSnapshot(List<ItemStack> snapshot) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                handler.setStackInSlot(slot, snapshot.get(slot).copy());
            }
        }
    }
}
