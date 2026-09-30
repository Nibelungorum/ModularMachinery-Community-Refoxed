package cn.howxu.mmcr.internal.autoio;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.internal.storage.LongEnergyHandler;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.util.IOType;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Built-in automatic IO policies registered by capability identity.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CapabilityTransferPolicies {
    private static final Map<cn.howxu.mmcr.api.capability.CapabilityType, AutoIoHandler> HANDLERS = new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean frozen;

    static {
        register(BuiltinCapabilityDefinitions.ITEM_TYPE, new ItemPolicy());
        register(BuiltinCapabilityDefinitions.FLUID_TYPE, new FluidPolicy());
        register(BuiltinCapabilityDefinitions.ENERGY_TYPE, new EnergyPolicy());
    }

    private CapabilityTransferPolicies() {
    }

    public static void ensureRegistered() {
        MekanismBridge.get().registerTransferPolicies();
    }

    public static synchronized void register(cn.howxu.mmcr.api.capability.CapabilityType type, AutoIoHandler handler) {
        if (frozen) throw new IllegalStateException("Automatic I/O registry is frozen");
        if (HANDLERS.putIfAbsent(type, handler) != null) throw new IllegalArgumentException("Duplicate automatic I/O handler: " + type.id());
    }

    public static Optional<AutoIoHandler> handlerFor(MachineCapability capability) {
        if (capability == null || capability.type() == null
                || capability.facet(TransferFacet.class).isEmpty()) return Optional.empty();
        return Optional.ofNullable(HANDLERS.get(capability.type()));
    }

    public static boolean isRegistered(cn.howxu.mmcr.api.capability.CapabilityType type) {
        return type != null && HANDLERS.containsKey(type);
    }

    public static synchronized void freeze() {
        frozen = true;
    }

    private static AutoIoResult blocked(FailureReason reason) {
        FailureOccurrence occurrence = FailureOccurrence.at(reason, MMCR.id("auto_io"),
                FailurePhase.CAPABILITY_COMMIT, null, null, Map.of());
        return AutoIoResult.blocked(ExecutionStatus.blocked(MMCR.id("auto_io"), MMCR.id("auto_io"), occurrence));
    }

    private static boolean canWork(Level level, Direction side) {
        return level != null && !level.isClientSide() && side != null;
    }

    private static BlockPos adjacent(BlockPos position, Direction side) {
        return position.relative(side);
    }

    private static final class ItemPolicy implements AutoIoHandler {
        @Override
        public boolean hasWork(MachineCapability capability) {
            IItemHandler handler = CapabilityFactories.itemHandler(capability);
            if (handler == null) return false;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (capability.directions().supports(IOType.OUTPUT)) {
                    if (!stack.isEmpty() && itemAmount(handler, slot) > 0L) return true;
                } else if (!stack.isEmpty() ? acceptedItems(handler, stack.copyWithCount(1), true) > 0
                        : handler.getSlotLimit(slot) > 0) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean hasAdjacentTarget(MachineCapability capability, Direction side) {
            return adjacentItem(capability, side) != null;
        }

        @Override
        public List<Object> ejectionResources(MachineCapability capability) {
            IItemHandler handler = CapabilityFactories.itemHandler(capability);
            if (handler == null) return List.of();
            LinkedHashSet<Object> resources = new LinkedHashSet<>();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && itemAmount(handler, slot) > 0L) resources.add(stack.copyWithCount(1));
            }
            return List.copyOf(resources);
        }

        @Override
        public AutoIoResult transfer(MachineCapability capability, Direction side, Object selectedResource, long ejectionLimit) {
            IItemHandler internal = CapabilityFactories.itemHandler(capability);
            TransferFacet transfer = transferFacet(capability);
            if (internal == null || transfer == null) return blocked(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
            boolean eject = ejectionLimit > 0L;
            if (eject ? !hasStoredContents(internal) : !hasWork(capability)) {
                return blocked(BuiltinFailureReasons.NO_WORK);
            }
            IItemHandler adjacent = adjacentItem(capability, side);
            if (adjacent == null) return blocked(BuiltinFailureReasons.NO_TARGET);
            long limit = eject ? ejectionLimit : transfer.transferLimit();
            Predicate<ItemStack> filter = ejectionItemFilter(selectedResource);
            long moved = eject ? moveItems(internal, adjacent, filter, limit, false)
                    : capability.directions().supports(IOType.INPUT)
                    ? moveItems(adjacent, internal, stack -> true, limit, false)
                    : moveItems(internal, adjacent, stack -> true, limit, false);
            return AutoIoResult.moved(moved);
        }

        private static boolean hasStoredContents(IItemHandler handler) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (!handler.getStackInSlot(slot).isEmpty() && itemAmount(handler, slot) > 0L) return true;
            }
            return false;
        }

        private static IItemHandler adjacentItem(MachineCapability capability, Direction side) {
            TransferFacet transfer = transferFacet(capability);
            if (transfer == null || !canWork(transfer.level(), side)) return null;
            return transfer.level().getCapability(ModCapabilities.ITEM_BLOCK,
                    adjacent(transfer.position(), side), side.getOpposite());
        }
    }

    private static final class FluidPolicy implements AutoIoHandler {
        @Override
        public boolean hasWork(MachineCapability capability) {
            IFluidHandler handler = CapabilityFactories.fluidHandler(capability);
            if (handler == null) return false;
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack stack = handler.getFluidInTank(tank);
                if (capability.directions().supports(IOType.OUTPUT)) {
                    if (!stack.isEmpty() && fluidAmount(handler, tank) > 0L) return true;
                } else if (!stack.isEmpty() ? handler.fill(stack.copyWithAmount(1), IFluidHandler.FluidAction.SIMULATE) > 0
                        : handler.getTankCapacity(tank) > 0) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean hasAdjacentTarget(MachineCapability capability, Direction side) {
            return adjacentFluid(capability, side) != null;
        }

        @Override
        public List<Object> ejectionResources(MachineCapability capability) {
            IFluidHandler handler = CapabilityFactories.fluidHandler(capability);
            if (handler == null) return List.of();
            LinkedHashSet<Object> resources = new LinkedHashSet<>();
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack stack = handler.getFluidInTank(tank);
                if (!stack.isEmpty() && fluidAmount(handler, tank) > 0L) resources.add(stack.copyWithAmount(1));
            }
            return List.copyOf(resources);
        }

        @Override
        public AutoIoResult transfer(MachineCapability capability, Direction side, Object selectedResource, long ejectionLimit) {
            IFluidHandler internal = CapabilityFactories.fluidHandler(capability);
            TransferFacet transfer = transferFacet(capability);
            if (internal == null || transfer == null) return blocked(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
            boolean eject = ejectionLimit > 0L;
            if (eject ? !hasStoredContents(internal) : !hasWork(capability)) {
                return blocked(BuiltinFailureReasons.NO_WORK);
            }
            IFluidHandler adjacent = adjacentFluid(capability, side);
            if (adjacent == null) return blocked(BuiltinFailureReasons.NO_TARGET);
            long limit = eject ? ejectionLimit : transfer.transferLimit();
            Predicate<FluidStack> filter = ejectionFluidFilter(selectedResource);
            long moved = eject ? moveFluids(internal, adjacent, filter, limit, false)
                    : capability.directions().supports(IOType.INPUT)
                    ? moveFluids(adjacent, internal, stack -> true, limit, false)
                    : moveFluids(internal, adjacent, stack -> true, limit, false);
            return AutoIoResult.moved(moved);
        }

        private static boolean hasStoredContents(IFluidHandler handler) {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                if (!handler.getFluidInTank(tank).isEmpty() && fluidAmount(handler, tank) > 0L) return true;
            }
            return false;
        }

        private static IFluidHandler adjacentFluid(MachineCapability capability, Direction side) {
            TransferFacet transfer = transferFacet(capability);
            if (transfer == null || !canWork(transfer.level(), side)) return null;
            return transfer.level().getCapability(ModCapabilities.FLUID_BLOCK,
                    adjacent(transfer.position(), side), side.getOpposite());
        }
    }

    private static final class EnergyPolicy implements AutoIoHandler {
        @Override
        public boolean hasWork(MachineCapability capability) {
            IEnergyStorage storage = CapabilityFactories.energyStorage(capability);
            if (storage == null) return false;
            return capability.directions().supports(IOType.OUTPUT)
                    ? energyAmount(storage) > 0L : energyAmount(storage) < energyCapacity(storage);
        }

        @Override
        public boolean hasAdjacentTarget(MachineCapability capability, Direction side) {
            return adjacentEnergy(capability, side) != null;
        }

        @Override
        public AutoIoResult transfer(MachineCapability capability, Direction side, Object selectedResource, long ejectionLimit) {
            IEnergyStorage internal = CapabilityFactories.energyStorage(capability);
            TransferFacet transfer = transferFacet(capability);
            if (internal == null || transfer == null) return blocked(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
            boolean eject = ejectionLimit > 0L;
            if (eject ? energyAmount(internal) <= 0L : !hasWork(capability)) {
                return blocked(BuiltinFailureReasons.NO_WORK);
            }
            IEnergyStorage adjacent = adjacentEnergy(capability, side);
            if (adjacent == null) return blocked(BuiltinFailureReasons.NO_TARGET);
            long limit = eject ? ejectionLimit : transfer.transferLimit();
            long moved = eject ? moveEnergy(internal, adjacent, limit, false)
                    : capability.directions().supports(IOType.INPUT)
                    ? moveEnergy(adjacent, internal, limit, false) : moveEnergy(internal, adjacent, limit, false);
            return AutoIoResult.moved(moved);
        }

        private static IEnergyStorage adjacentEnergy(MachineCapability capability, Direction side) {
            TransferFacet transfer = transferFacet(capability);
            if (transfer == null || !canWork(transfer.level(), side)) return null;
            return transfer.level().getCapability(ModCapabilities.ENERGY_BLOCK,
                    adjacent(transfer.position(), side), side.getOpposite());
        }
    }

    private static TransferFacet transferFacet(MachineCapability capability) {
        return capability == null ? null : capability.facet(TransferFacet.class).orElse(null);
    }

    private static Predicate<ItemStack> ejectionItemFilter(Object selected) {
        return selected instanceof ItemStack item ? stack -> ItemStack.isSameItemSameComponents(item, stack) : stack -> true;
    }

    private static Predicate<FluidStack> ejectionFluidFilter(Object selected) {
        return selected instanceof FluidStack fluid ? stack -> FluidStack.isSameFluidSameComponents(fluid, stack) : stack -> true;
    }

    private static long moveItems(IItemHandler from, IItemHandler to, Predicate<ItemStack> filter,
                                  long limit, boolean simulate) {
        long moved = 0L;
        for (int slot = 0; slot < from.getSlots() && moved < limit; slot++) {
            ItemStack present = from.getStackInSlot(slot).copy();
            if (present.isEmpty() || !filter.test(present)) continue;
            long available = Math.min(itemAmount(from, slot), limit - moved);
            while (available > 0L) {
                int requested = (int) Math.min(available, Integer.MAX_VALUE);
                ItemStack extracted = from.extractItem(slot, requested, true);
                int accepted = acceptedItems(to, extracted, true);
                if (accepted <= 0) break;
                if (simulate) {
                    moved += accepted;
                    available -= accepted;
                    continue;
                }
                ItemStack committed = from.extractItem(slot, accepted, false);
                ItemStack remainder = insertItems(to, committed, false);
                int inserted = committed.getCount() - remainder.getCount();
                if (!remainder.isEmpty()) from.insertItem(slot, remainder, false);
                moved += inserted;
                available -= inserted;
                if (inserted < accepted) break;
            }
        }
        return moved;
    }

    private static int acceptedItems(IItemHandler handler, ItemStack stack, boolean simulate) {
        return stack.isEmpty() ? 0 : stack.getCount() - insertItems(handler, stack, simulate).getCount();
    }

    private static ItemStack insertItems(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, simulate);
        }
        return remainder;
    }

    private static long moveFluids(IFluidHandler from, IFluidHandler to, Predicate<FluidStack> filter,
                                   long limit, boolean simulate) {
        long moved = 0L;
        for (int tank = 0; tank < from.getTanks() && moved < limit; tank++) {
            FluidStack present = from.getFluidInTank(tank).copy();
            if (present.isEmpty() || !filter.test(present)) continue;
            int requested = (int) Math.min(Math.min(limit - moved, fluidAmount(from, tank)), Integer.MAX_VALUE);
            if (requested <= 0) continue;
            FluidStack extracted = from.drain(present.copyWithAmount(requested), IFluidHandler.FluidAction.SIMULATE);
            int accepted = to.fill(extracted.copy(), IFluidHandler.FluidAction.SIMULATE);
            if (accepted <= 0) continue;
            if (simulate) {
                moved += accepted;
                continue;
            }
            FluidStack committed = from.drain(present.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
            int inserted = to.fill(committed.copy(), IFluidHandler.FluidAction.EXECUTE);
            if (inserted < committed.getAmount()) {
                from.fill(committed.copyWithAmount(committed.getAmount() - inserted), IFluidHandler.FluidAction.EXECUTE);
            }
            moved += inserted;
            if (inserted < accepted) break;
        }
        return moved;
    }

    private static long moveEnergy(IEnergyStorage from, IEnergyStorage to, long requested, boolean simulate) {
        if (requested <= 0L) return 0L;
        if (from instanceof LongEnergyHandler longFrom && to instanceof LongEnergyHandler longTo) {
            long amount = Math.min(requested, Math.min(longFrom.getAmountAsLong(),
                    Math.max(0L, longTo.getCapacityAsLong() - longTo.getAmountAsLong())));
            amount = Math.min(amount, Math.min(longFrom.getTransferLimit(), longTo.getTransferLimit()));
            if (amount <= 0L || longFrom.extractLong(amount, true) != amount || longTo.insertLong(amount, true) != amount) {
                return 0L;
            }
            if (simulate) return amount;
            long extracted = longFrom.extractLong(amount, false);
            long inserted = longTo.insertLong(extracted, false);
            if (inserted < extracted) longFrom.insertLong(extracted - inserted, false);
            return inserted;
        }
        int amount = (int) Math.min(requested, Integer.MAX_VALUE);
        int extracted = from.extractEnergy(amount, true);
        int accepted = to.receiveEnergy(extracted, true);
        if (accepted <= 0) return 0L;
        if (simulate) return accepted;
        int committed = from.extractEnergy(accepted, false);
        int inserted = to.receiveEnergy(committed, false);
        if (inserted < committed) from.receiveEnergy(committed - inserted, false);
        return inserted;
    }

    private static long itemAmount(IItemHandler handler, int slot) {
        return handler instanceof LongItemStorage storage ? storage.amount(slot) : handler.getStackInSlot(slot).getCount();
    }

    private static long fluidAmount(IFluidHandler handler, int tank) {
        return handler instanceof LongFluidStorage storage ? storage.amount(tank) : handler.getFluidInTank(tank).getAmount();
    }

    private static long energyAmount(IEnergyStorage storage) {
        return storage instanceof LongEnergyHandler longStorage ? longStorage.getAmountAsLong() : storage.getEnergyStored();
    }

    private static long energyCapacity(IEnergyStorage storage) {
        return storage instanceof LongEnergyHandler longStorage ? longStorage.getCapacityAsLong() : storage.getMaxEnergyStored();
    }
}
