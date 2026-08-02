package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.registry.MMCRRegistries;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public final class MMCRCapabilities {
    private MMCRCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                MMCRRegistries.ITEM_BUS_BE.get(),
                (be, side) -> be instanceof ItemBusBlockEntity ib ? new LegacyItemHandlerAdapter(ib.getItemHandler(side)) : null);
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,
                MMCRRegistries.FLUID_HATCH_BE.get(),
                (be, side) -> be instanceof FluidHatchBlockEntity fh ? new LegacyFluidHandlerAdapter(fh.getFluidHandler(side)) : null);
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK,
                MMCRRegistries.ENERGY_HATCH_BE.get(),
                (be, side) -> be instanceof EnergyHatchBlockEntity eh ? new LegacyEnergyHandlerAdapter(eh.getEnergyStorage(side)) : null);
    }

    private static final class LegacyFluidHandlerAdapter implements ResourceHandler<FluidResource> {
        private final IFluidHandler handler;

        LegacyFluidHandlerAdapter(IFluidHandler handler) {
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
            return handler.fill(resource.toStack(amount), IFluidHandler.FluidAction.EXECUTE);
        }

        @Override
        public int extract(int slot, FluidResource resource, int amount, TransactionContext tx) {
            return handler.drain(resource.toStack(amount), IFluidHandler.FluidAction.EXECUTE).getAmount();
        }
    }

    private static final class LegacyEnergyHandlerAdapter implements EnergyHandler {
        private final IEnergyStorage storage;

        LegacyEnergyHandlerAdapter(IEnergyStorage storage) {
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
            return storage.receiveEnergy(amount, false);
        }

        @Override
        public int extract(int amount, TransactionContext tx) {
            return storage.extractEnergy(amount, false);
        }
    }

    private static final class LegacyItemHandlerAdapter implements ResourceHandler<ItemResource> {
        private final IItemHandler handler;

        LegacyItemHandlerAdapter(IItemHandler handler) {
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
            ItemStack remainder = handler.insertItem(slot, resource.toStack(amount), false);
            return amount - remainder.getCount();
        }

        @Override
        public int extract(int slot, ItemResource resource, int amount, TransactionContext tx) {
            ItemStack extracted = handler.extractItem(slot, amount, false);
            return extracted.getCount();
        }
    }
}
