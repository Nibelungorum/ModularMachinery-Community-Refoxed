package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.registry.MMCRRegistries;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.ArrayList;
import java.util.List;

public final class MMCRCapabilities {
    private MMCRCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                MMCRRegistries.ITEM_BUS_BE.get(),
                (be, side) -> be instanceof ItemBusBlockEntity ib ? new LegacyItemHandlerAdapter(ib.getItemStackHandler(side)) : null);
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,
                MMCRRegistries.FLUID_HATCH_BE.get(),
                (be, side) -> be instanceof FluidHatchBlockEntity fh ? new LegacyFluidHandlerAdapter(fh.getFluidTank(side)) : null);
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK,
                MMCRRegistries.ENERGY_HATCH_BE.get(),
                (be, side) -> be instanceof EnergyHatchBlockEntity eh ? new LegacyEnergyHandlerAdapter(eh.getMutableEnergyStorage(side)) : null);
    }

    private static final class LegacyFluidHandlerAdapter extends SnapshotJournal<FluidStack> implements ResourceHandler<FluidResource> {
        private final FluidTank handler;

        LegacyFluidHandlerAdapter(FluidTank handler) {
            this.handler = handler;
        }

        @Override
        public int size() {
            return handler.getTanks();
        }

        @Override
        public FluidResource getResource(int slot) {
            FluidStack stack = handler.getFluidInTank(slot);
            return stack.isEmpty() ? FluidResource.EMPTY : FluidResource.of(stack);
        }

        @Override
        public long getAmountAsLong(int slot) {
            return handler.getFluidInTank(slot).getAmount();
        }

        @Override
        public long getCapacityAsLong(int slot, FluidResource resource) {
            return handler.getTankCapacity(slot);
        }

        @Override
        public boolean isValid(int slot, FluidResource resource) {
            return handler.isFluidValid(slot, resource.toStack(1));
        }

        @Override
        public int insert(int slot, FluidResource resource, int amount, TransactionContext tx) {
            updateSnapshots(tx);
            return handler.fill(resource.toStack(amount), IFluidHandler.FluidAction.EXECUTE);
        }

        @Override
        public int extract(int slot, FluidResource resource, int amount, TransactionContext tx) {
            updateSnapshots(tx);
            return handler.drain(resource.toStack(amount), IFluidHandler.FluidAction.EXECUTE).getAmount();
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
        private final EnergyStorage storage;

        LegacyEnergyHandlerAdapter(EnergyStorage storage) {
            this.storage = storage;
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
            updateSnapshots(tx);
            return storage.receiveEnergy(amount, false);
        }

        @Override
        public int extract(int amount, TransactionContext tx) {
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
    }

    private static final class LegacyItemHandlerAdapter extends SnapshotJournal<List<ItemStack>> implements ResourceHandler<ItemResource> {
        private final ItemStackHandler handler;

        LegacyItemHandlerAdapter(ItemStackHandler handler) {
            this.handler = handler;
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
            return Math.min(handler.getSlotLimit(slot), resource.getMaxStackSize());
        }

        @Override
        public boolean isValid(int slot, ItemResource resource) {
            return handler.isItemValid(slot, resource.toStack(1));
        }

        @Override
        public int insert(int slot, ItemResource resource, int amount, TransactionContext tx) {
            updateSnapshots(tx);
            ItemStack remainder = handler.insertItem(slot, resource.toStack(amount), false);
            return amount - remainder.getCount();
        }

        @Override
        public int extract(int slot, ItemResource resource, int amount, TransactionContext tx) {
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
