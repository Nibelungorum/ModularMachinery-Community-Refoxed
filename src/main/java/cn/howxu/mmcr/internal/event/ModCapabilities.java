package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ModCapabilities {
    public static final BlockCapability<ResourceHandler<ItemResource>, Direction> ITEM_BLOCK =
            net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK;
    public static final BlockCapability<ResourceHandler<FluidResource>, Direction> FLUID_BLOCK =
            net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK;
    public static final BlockCapability<EnergyHandler, Direction> ENERGY_BLOCK =
            net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK;

    private ModCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        for (IOPortKind kind : nativeCapabilityPorts()) {
            registerNativePort(event, kind);
        }
        event.registerBlockEntity(
                ITEM_BLOCK,
                ModBlockEntities.BES.get("factory_controller").get(),
                (be, side) -> be instanceof FactorySchedulerBlockEntity scheduler
                        ? new LegacyItemHandlerAdapter(scheduler.getItemStackHandler(side), true, true)
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
                .filter(kind -> kind.itemBusSize().isPresent()
                        || kind.fluidHatchSize().isPresent()
                        || kind.energyHatchSize().isPresent())
                .toList();
    }

    private static void registerNativePort(RegisterCapabilitiesEvent event, IOPortKind kind) {
        if (kind.itemBusSize().isPresent()) {
            boolean canInsert = kind.ioType() == IOType.INPUT;
            event.registerBlockEntity(
                    ITEM_BLOCK,
                    ModBlockEntities.BES.get(kind.id()).get(),
                    (be, side) -> be instanceof ItemBusBlockEntity ib ? new LegacyItemHandlerAdapter(ib.getItemStackHandler(side), canInsert, true) : null);
        } else if (kind.fluidHatchSize().isPresent()) {
            boolean canInsert = kind.ioType() == IOType.INPUT;
            event.registerBlockEntity(
                    FLUID_BLOCK,
                    ModBlockEntities.BES.get(kind.id()).get(),
                    (be, side) -> be instanceof FluidHatchBlockEntity fh ? new LegacyFluidHandlerAdapter(fh.getFluidTank(side), canInsert, true) : null);
        } else if (kind.energyHatchSize().isPresent()) {
            boolean canInsert = kind.ioType() == IOType.INPUT;
            event.registerBlockEntity(
                    ENERGY_BLOCK,
                    ModBlockEntities.BES.get(kind.id()).get(),
                    (be, side) -> be instanceof EnergyHatchBlockEntity eh ? new LegacyEnergyHandlerAdapter(eh, canInsert, !canInsert) : null);
        }
    }

    private static final class LegacyFluidHandlerAdapter extends SnapshotJournal<FluidStack> implements ResourceHandler<FluidResource> {
        private final FluidTank handler;
        private final boolean canInsert;
        private final boolean canExtract;

        LegacyFluidHandlerAdapter(FluidTank handler, boolean canInsert, boolean canExtract) {
            this.handler = handler;
            this.canInsert = canInsert;
            this.canExtract = canExtract;
        }

        @Override
        public int size() {
            return handler.getTanks();
        }

        @Override
        public FluidResource getResource(int slot) {
            checkSlot(slot);
            FluidStack stack = handler.getFluidInTank(slot);
            return stack.isEmpty() ? FluidResource.EMPTY : FluidResource.of(stack);
        }

        @Override
        public long getAmountAsLong(int slot) {
            checkSlot(slot);
            return handler.getFluidInTank(slot).getAmount();
        }

        @Override
        public long getCapacityAsLong(int slot, FluidResource resource) {
            checkSlot(slot);
            return handler.getTankCapacity(slot);
        }

        @Override
        public boolean isValid(int slot, FluidResource resource) {
            checkSlot(slot);
            TransferPreconditions.checkNonEmpty(resource);
            return handler.isFluidValid(slot, resource.toStack(1));
        }

        @Override
        public int insert(int slot, FluidResource resource, int amount, TransactionContext tx) {
            checkSlot(slot);
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (!canInsert) return 0;
            updateSnapshots(tx);
            return handler.fill(resource.toStack(amount), IFluidHandler.FluidAction.EXECUTE);
        }

        @Override
        public int extract(int slot, FluidResource resource, int amount, TransactionContext tx) {
            checkSlot(slot);
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (!canExtract) return 0;
            updateSnapshots(tx);
            return handler.drain(resource.toStack(amount), IFluidHandler.FluidAction.EXECUTE).getAmount();
        }

        private void checkSlot(int slot) {
            if (slot < 0 || slot >= handler.getTanks()) {
                throw new IndexOutOfBoundsException(slot);
            }
        }

        @Override
        protected FluidStack createSnapshot() {
            return handler.getFluid().copy();
        }

        @Override
        protected void revertToSnapshot(FluidStack snapshot) {
            handler.setFluid(snapshot == null ? FluidStack.EMPTY : snapshot);
        }
    }

    private static final class LegacyEnergyHandlerAdapter extends SnapshotJournal<Integer> implements EnergyHandler {
        private final EnergyHatchBlockEntity hatch;
        private final EnergyStorage storage;
        private final boolean canInsert;
        private final boolean canExtract;

        LegacyEnergyHandlerAdapter(EnergyHatchBlockEntity hatch, boolean canInsert, boolean canExtract) {
            this.hatch = hatch;
            this.storage = hatch.getMutableEnergyStorage(null);
            this.canInsert = canInsert;
            this.canExtract = canExtract;
        }

        @Override
        public long getAmountAsLong() {
            return storage.getEnergyStored();
        }

        @Override
        public long getCapacityAsLong() {
            return storage.getMaxEnergyStored();
        }

        @Override
        public int insert(int amount, TransactionContext tx) {
            TransferPreconditions.checkNonNegative(amount);
            if (!canInsert) return 0;
            updateSnapshots(tx);
            return storage.receiveEnergy(amount, false);
        }

        @Override
        public int extract(int amount, TransactionContext tx) {
            TransferPreconditions.checkNonNegative(amount);
            if (!canExtract) return 0;
            updateSnapshots(tx);
            return storage.extractEnergy(amount, false);
        }

        @Override
        protected Integer createSnapshot() {
            return storage.getEnergyStored();
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            int target = snapshot == null ? 0 : snapshot;
            int current = storage.getEnergyStored();
            if (current > target) {
                storage.extractEnergy(current - target, false);
            } else if (current < target) {
                storage.receiveEnergy(target - current, false);
            }
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            if (originalState == null || originalState != storage.getEnergyStored()) {
                hatch.setChanged();
            }
        }
    }

    private static final class LegacyItemHandlerAdapter extends SnapshotJournal<List<ItemStack>> implements ResourceHandler<ItemResource> {
        private final ItemStackHandler handler;
        private final boolean canInsert;
        private final boolean canExtract;

        LegacyItemHandlerAdapter(ItemStackHandler handler, boolean canInsert, boolean canExtract) {
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
