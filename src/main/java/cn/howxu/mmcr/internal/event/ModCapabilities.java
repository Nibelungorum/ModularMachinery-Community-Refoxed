package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
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
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
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
            boolean canInsert = kind.ioType() == IOType.INPUT;
            event.registerBlockEntity(
                    ITEM_BLOCK,
                    ModBlockEntities.BES.get(kind.id()).get(),
                    (be, side) -> be instanceof ItemBusBlockEntity ib && ib.isAutoIOSideExposed(side)
                            ? new ItemStackResourceHandler(ib.getItemStackHandler(side), canInsert, true)
                            : null);
        } else if (kind.capabilityFactories().contains(CapabilityFactories.FLUID_HATCH)) {
            boolean canInsert = kind.ioType() == IOType.INPUT;
            event.registerBlockEntity(
                    FLUID_BLOCK,
                    ModBlockEntities.BES.get(kind.id()).get(),
                    (be, side) -> be instanceof FluidHatchBlockEntity fh && fh.isAutoIOSideExposed(side)
                            ? new DirectionalFluidHandler(fh.getResourceHandler(side), canInsert, !canInsert)
                            : null);
        } else if (kind.capabilityFactories().contains(CapabilityFactories.ENERGY_HATCH)) {
            boolean canInsert = kind.ioType() == IOType.INPUT;
            event.registerBlockEntity(
                    ENERGY_BLOCK,
                    ModBlockEntities.BES.get(kind.id()).get(),
                    (be, side) -> be instanceof EnergyHatchBlockEntity eh && eh.isAutoIOSideExposed(side)
                            ? new DirectionalEnergyHandler(eh.getEnergyHandler(side), canInsert, !canInsert)
                            : null);
        }
    }

    private static final class DirectionalFluidHandler implements ResourceHandler<FluidResource> {
        private final ResourceHandler<FluidResource> handler;
        private final boolean canInsert;
        private final boolean canExtract;

        DirectionalFluidHandler(ResourceHandler<FluidResource> handler, boolean canInsert, boolean canExtract) {
            this.handler = handler;
            this.canInsert = canInsert;
            this.canExtract = canExtract;
        }

        @Override
        public int size() {
            return handler.size();
        }

        @Override
        public FluidResource getResource(int slot) {
            checkSlot(slot);
            return handler.getResource(slot);
        }

        @Override
        public long getAmountAsLong(int slot) {
            checkSlot(slot);
            return handler.getAmountAsLong(slot);
        }

        @Override
        public long getCapacityAsLong(int slot, FluidResource resource) {
            checkSlot(slot);
            return handler.getCapacityAsLong(slot, resource);
        }

        @Override
        public boolean isValid(int slot, FluidResource resource) {
            checkSlot(slot);
            TransferPreconditions.checkNonEmpty(resource);
            return handler.isValid(slot, resource);
        }

        @Override
        public int insert(int slot, FluidResource resource, int amount, TransactionContext tx) {
            checkSlot(slot);
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (!canInsert) return 0;
            return handler.insert(slot, resource, amount, tx);
        }

        @Override
        public int extract(int slot, FluidResource resource, int amount, TransactionContext tx) {
            checkSlot(slot);
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (!canExtract) return 0;
            return handler.extract(slot, resource, amount, tx);
        }

        private void checkSlot(int slot) {
            if (slot < 0 || slot >= handler.size()) {
                throw new IndexOutOfBoundsException(slot);
            }
        }
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
