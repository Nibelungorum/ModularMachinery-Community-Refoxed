package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.Direction;
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

import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ModCapabilities {
    public static final BlockCapability<ResourceHandler<ItemResource>, Direction> ITEM_BLOCK =
            Capabilities.Item.BLOCK;
    public static final BlockCapability<ResourceHandler<FluidResource>, Direction> FLUID_BLOCK =
            Capabilities.Fluid.BLOCK;
    public static final BlockCapability<EnergyHandler, Direction> ENERGY_BLOCK =
            Capabilities.Energy.BLOCK;
    private static final CapabilityType ITEM_TYPE = new CapabilityType(PortFamilyIds.ITEM);
    private static final CapabilityType FLUID_TYPE = new CapabilityType(PortFamilyIds.FLUID);
    private static final CapabilityType ENERGY_TYPE = new CapabilityType(PortFamilyIds.ENERGY);

    private ModCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        for (IOPortKind kind : nativeCapabilityPorts()) {
            registerNativePort(event, kind);
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
                .filter(kind -> !kind.capabilityFactories().isEmpty())
                .toList();
    }

    private static void registerNativePort(RegisterCapabilitiesEvent event, IOPortKind kind) {
        if (kind.capabilityFactories().contains(CapabilityFactories.ITEM_BUS)) {
            registerItemPort(event, kind);
        }
        if (kind.capabilityFactories().contains(CapabilityFactories.FLUID_HATCH)) {
            registerFluidPort(event, kind);
        }
        if (kind.capabilityFactories().contains(CapabilityFactories.ENERGY_HATCH)) {
            registerEnergyPort(event, kind);
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

    private static final class DirectionalEnergyHandler implements EnergyHandler {
        private final EnergyHandler handler;
        private final boolean canInsert;
        private final boolean canExtract;

        DirectionalEnergyHandler(EnergyHandler handler, boolean canInsert, boolean canExtract) {
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
    }

    private static final class EnergyHandlerAdapter implements EnergyHandler {
        private final LongValueStorage storage;

        private EnergyHandlerAdapter(LongValueStorage storage) {
            this.storage = storage;
        }

        @Override public long getAmountAsLong() { return storage.amount(); }
        @Override public long getCapacityAsLong() { return storage.capacity(); }
        @Override public int insert(int amount, TransactionContext tx) {
            storage.updateSnapshots(tx);
            return (int) storage.insert(amount, false);
        }
        @Override public int extract(int amount, TransactionContext tx) {
            storage.updateSnapshots(tx);
            return (int) storage.extract(amount, false);
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
            updateSnapshots(tx);
            ItemStack remainder = handler.insertItem(slot, resource.toStack(amount), false);
            return amount - remainder.getCount();
        }

        @Override
        public int extract(int slot, ItemResource resource, int amount, TransactionContext tx) {
            if (!canExtract) return 0;
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || !ItemResource.of(stack).equals(resource)) return 0;
            updateSnapshots(tx);
            ItemStack extracted = handler.extractItem(slot, amount, false);
            return extracted.getCount();
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
