package cn.howxu.mmcr.internal.autoio;

import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Built-in Auto IO transfer handlers.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class AutoIOTransferHandlers {
    private static final List<AutoIOTransferHandler> HANDLERS = List.of(new ItemHandler(), new FluidHandler(), new EnergyHandler());

    private AutoIOTransferHandlers() {}

    public static Optional<AutoIOTransferHandler> handlerFor(IOPortBlockEntity port) {
        return HANDLERS.stream().filter(handler -> handler.supports(port)).findFirst();
    }

    private static final class ItemHandler implements AutoIOTransferHandler {
        @Override public AutoIOCapabilityType type() { return AutoIOCapabilityType.ITEM; }
        @Override public boolean supports(IOPortBlockEntity port) { return port instanceof ItemBusBlockEntity; }
        @Override public boolean hasAdjacentTarget(IOPortBlockEntity port, Direction side) { return adjacentItemHandler(port, side) != null; }
        @Override public boolean transfer(IOPortBlockEntity port, Direction side) {
            if (!canWork(port) || !(port instanceof ItemBusBlockEntity itemBus)) return false;
            ResourceHandler<ItemResource> adjacent = adjacentItemHandler(port, side);
            if (adjacent == null) return false;
            IItemHandler internal = itemBus.getItemStackHandler(side);
            if (port.ioType() == IOType.INPUT) {
                return ResourceHandlerUtil.move(adjacent, new ItemHandlerAdapter(internal), resource -> true, port.autoIoTransferLimit(), null) > 0;
            }
            return ResourceHandlerUtil.move(new ItemHandlerAdapter(internal), adjacent, resource -> true, port.autoIoTransferLimit(), null) > 0;
        }
    }

    private static final class FluidHandler implements AutoIOTransferHandler {
        @Override public AutoIOCapabilityType type() { return AutoIOCapabilityType.FLUID; }
        @Override public boolean supports(IOPortBlockEntity port) { return port instanceof FluidHatchBlockEntity; }
        @Override public boolean hasAdjacentTarget(IOPortBlockEntity port, Direction side) { return adjacentFluidHandler(port, side) != null; }
        @Override public boolean transfer(IOPortBlockEntity port, Direction side) {
            if (!canWork(port) || !(port instanceof FluidHatchBlockEntity fluidHatch)) return false;
            ResourceHandler<FluidResource> adjacent = adjacentFluidHandler(port, side);
            if (adjacent == null) return false;
            ResourceHandler<FluidResource> internal = new FluidHandlerAdapter(fluidHatch.getFluidTank(side));
            if (port.ioType() == IOType.INPUT) {
                return ResourceHandlerUtil.move(adjacent, internal, resource -> true, port.autoIoTransferLimit(), null) > 0;
            }
            return ResourceHandlerUtil.move(internal, adjacent, resource -> true, port.autoIoTransferLimit(), null) > 0;
        }
    }

    private static final class EnergyHandler implements AutoIOTransferHandler {
        @Override public AutoIOCapabilityType type() { return AutoIOCapabilityType.ENERGY; }
        @Override public boolean supports(IOPortBlockEntity port) { return port instanceof EnergyHatchBlockEntity; }
        @Override public boolean hasAdjacentTarget(IOPortBlockEntity port, Direction side) { return adjacentEnergyHandler(port, side) != null; }
        @Override public boolean transfer(IOPortBlockEntity port, Direction side) {
            if (!canWork(port) || !(port instanceof EnergyHatchBlockEntity energyHatch)) return false;
            net.neoforged.neoforge.transfer.energy.EnergyHandler adjacent = adjacentEnergyHandler(port, side);
            if (adjacent == null) return false;
            net.neoforged.neoforge.transfer.energy.EnergyHandler internal = new EnergyStorageAdapter(energyHatch.getMutableEnergyStorage(side));
            if (port.ioType() == IOType.INPUT) {
                return EnergyHandlerUtil.move(adjacent, internal, port.autoIoTransferLimit(), null) > 0;
            }
            return EnergyHandlerUtil.move(internal, adjacent, port.autoIoTransferLimit(), null) > 0;
        }
    }

    private static boolean canWork(IOPortBlockEntity port) {
        return port.getLevel() != null && !port.getLevel().isClientSide();
    }

    private static Direction adjacentSide(Direction side) {
        return side.getOpposite();
    }

    private static BlockPos adjacentPos(IOPortBlockEntity port, Direction side) {
        return port.getBlockPos().relative(side);
    }

    private static ResourceHandler<ItemResource> adjacentItemHandler(IOPortBlockEntity port, Direction side) {
        if (!canWork(port)) return null;
        return port.getLevel().getCapability(ModCapabilities.ITEM_BLOCK, adjacentPos(port, side), adjacentSide(side));
    }

    private static ResourceHandler<FluidResource> adjacentFluidHandler(IOPortBlockEntity port, Direction side) {
        if (!canWork(port)) return null;
        return port.getLevel().getCapability(ModCapabilities.FLUID_BLOCK, adjacentPos(port, side), adjacentSide(side));
    }

    private static net.neoforged.neoforge.transfer.energy.EnergyHandler adjacentEnergyHandler(IOPortBlockEntity port, Direction side) {
        if (!canWork(port)) return null;
        return port.getLevel().getCapability(ModCapabilities.ENERGY_BLOCK, adjacentPos(port, side), adjacentSide(side));
    }

    private static final class ItemHandlerAdapter extends SnapshotJournal<List<ItemStack>> implements ResourceHandler<ItemResource> {
        private final IItemHandler handler;

        private ItemHandlerAdapter(IItemHandler handler) {
            this.handler = handler;
        }

        @Override public int size() { return handler.getSlots(); }
        @Override public ItemResource getResource(int slot) {
            var stack = handler.getStackInSlot(slot);
            return stack.isEmpty() ? ItemResource.EMPTY : ItemResource.of(stack);
        }
        @Override public long getAmountAsLong(int slot) { return handler.getStackInSlot(slot).getCount(); }
        @Override public long getCapacityAsLong(int slot, ItemResource resource) { return Math.min(handler.getSlotLimit(slot), resource.getMaxStackSize()); }
        @Override public boolean isValid(int slot, ItemResource resource) { return handler.isItemValid(slot, resource.toStack(1)); }
        @Override public int insert(int slot, ItemResource resource, int amount, TransactionContext tx) {
            updateSnapshots(tx);
            var remainder = handler.insertItem(slot, resource.toStack(amount), false);
            return amount - remainder.getCount();
        }
        @Override public int extract(int slot, ItemResource resource, int amount, TransactionContext tx) {
            var stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, resource.toStack(1))) return 0;
            updateSnapshots(tx);
            return handler.extractItem(slot, amount, false).getCount();
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
            if (!(handler instanceof net.neoforged.neoforge.items.IItemHandlerModifiable modifiable)) return;
            for (int slot = 0; slot < snapshot.size(); slot++) {
                modifiable.setStackInSlot(slot, snapshot.get(slot).copy());
            }
        }
    }

    private static final class FluidHandlerAdapter extends SnapshotJournal<FluidStack> implements ResourceHandler<FluidResource> {
        private final IFluidHandler handler;

        private FluidHandlerAdapter(IFluidHandler handler) {
            this.handler = handler;
        }

        @Override public int size() { return handler.getTanks(); }
        @Override public FluidResource getResource(int tank) {
            FluidStack stack = handler.getFluidInTank(tank);
            return stack.isEmpty() ? FluidResource.EMPTY : FluidResource.of(stack);
        }
        @Override public long getAmountAsLong(int tank) { return handler.getFluidInTank(tank).getAmount(); }
        @Override public long getCapacityAsLong(int tank, FluidResource resource) { return handler.getTankCapacity(tank); }
        @Override public boolean isValid(int tank, FluidResource resource) { return handler.isFluidValid(tank, resource.toStack(1)); }
        @Override public int insert(int tank, FluidResource resource, int amount, TransactionContext tx) {
            updateSnapshots(tx);
            return handler.fill(resource.toStack(amount), IFluidHandler.FluidAction.EXECUTE);
        }
        @Override public int extract(int tank, FluidResource resource, int amount, TransactionContext tx) {
            updateSnapshots(tx);
            return handler.drain(resource.toStack(amount), IFluidHandler.FluidAction.EXECUTE).getAmount();
        }

        @Override
        protected FluidStack createSnapshot() {
            return handler.getFluidInTank(0).copy();
        }

        @Override
        protected void revertToSnapshot(FluidStack snapshot) {
            if (handler instanceof net.neoforged.neoforge.fluids.capability.templates.FluidTank tank) {
                tank.setFluid(snapshot == null ? FluidStack.EMPTY : snapshot);
            }
        }
    }

    private static final class EnergyStorageAdapter extends SnapshotJournal<Integer> implements net.neoforged.neoforge.transfer.energy.EnergyHandler {
        private final IEnergyStorage storage;

        private EnergyStorageAdapter(IEnergyStorage storage) {
            this.storage = storage;
        }

        @Override public long getAmountAsLong() { return storage.getEnergyStored(); }
        @Override public long getCapacityAsLong() { return storage.getMaxEnergyStored(); }
        @Override public int insert(int amount, TransactionContext tx) {
            updateSnapshots(tx);
            return storage.receiveEnergy(amount, false);
        }
        @Override public int extract(int amount, TransactionContext tx) {
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
}
