package cn.howxu.mmcr.api.capability.plan;

import net.neoforged.neoforge.energy.IEnergyStorage;

import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.internal.capability.NativeStackSync;
import cn.howxu.mmcr.internal.capability.NativeReservationAccess;
import cn.howxu.mmcr.internal.storage.LongEnergyHandler;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import java.util.HashMap;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Tracks resource reservations made while materializing a crafting plan.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PlanningReservations {
    private Map<Object, Map<Integer, ResourceReservation>> resources;
    private Map<Object, Map<Object, Long>> outputReservations;
    private Map<Object, Long> values;
    private Map<Object, Map<Object, NativeSlotState>> nativeSlots;

    public ItemStack item(IItemHandler handler, int slot) {
        if (handler instanceof NativeReservationAccess access) {
            return (ItemStack) access.resource(nativeKey(access.reservationIdentity(),
                    access.reservationSlot(slot), access.storedKey(slot)));
        }
        ResourceReservation reservation = reservation(handler, slot, false);
        return reservation == null || reservation.insertedResource == null
                ? handler.getStackInSlot(slot) : ((ItemStack) reservation.insertedResource).copy();
    }

    public FluidStack fluid(IFluidHandler handler, int tank) {
        if (handler instanceof NativeReservationAccess access) {
            return (FluidStack) access.resource(nativeKey(access.reservationIdentity(),
                    access.reservationSlot(tank), access.storedKey(tank)));
        }
        ResourceReservation reservation = reservation(handler, tank, false);
        return reservation == null || reservation.insertedResource == null
                ? handler.getFluidInTank(tank) : ((FluidStack) reservation.insertedResource).copy();
    }

    public long itemAmount(IItemHandler handler, int slot) {
        if (handler instanceof NativeReservationAccess access) {
            return item(handler, slot).isEmpty() ? 0L : nativeAmount(access.reservationIdentity(),
                    access.reservationSlot(slot), access.storedAmount(slot));
        }
        long amount = handler instanceof LongItemStorage storage ? storage.amount(slot)
                : handler instanceof NativeStackSync.Item sync ? sync.amount(slot)
                : handler.getStackInSlot(slot).getCount();
        return virtualAmount(handler, slot, amount);
    }

    public long fluidAmount(IFluidHandler handler, int tank) {
        if (handler instanceof NativeReservationAccess access) {
            return fluid(handler, tank).isEmpty() ? 0L : nativeAmount(access.reservationIdentity(),
                    access.reservationSlot(tank), access.storedAmount(tank));
        }
        long amount = handler instanceof LongFluidStorage storage ? storage.amount(tank)
                : handler instanceof NativeStackSync.Fluid sync ? sync.amount(tank)
                : handler.getFluidInTank(tank).getAmount();
        return virtualAmount(handler, tank, amount);
    }

    public boolean reserveItemExtract(IItemHandler handler, int slot, ItemStack stack, long amount) {
        if (handler instanceof NativeReservationAccess access) {
            return reserveNativeExtract(access.reservationIdentity(), access.reservationSlot(slot),
                    access.resourceKey(stack), access.storedKey(slot), access.storedAmount(slot), amount);
        }
        ItemStack current = item(handler, slot);
        if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, stack)) return false;
        return reserveExtract(handler, slot, stack, amount, ItemStack::isSameItemSameComponents,
                (storage, reservedSlot) -> itemAmount((IItemHandler) storage, reservedSlot));
    }

    public boolean reserveItemInsert(IItemHandler handler, int slot, ItemStack stack, long amount, long capacity) {
        if (handler instanceof NativeReservationAccess access) {
            return reserveNativeInsert(access.reservationIdentity(), access.reservationSlot(slot),
                    access.resourceKey(stack), access.storedKey(slot), access.storedAmount(slot), capacity, amount);
        }
        return reserveInsert(handler, slot, stack, amount, capacity, ItemStack::isSameItemSameComponents,
                (storage, reservedSlot) -> itemAmount((IItemHandler) storage, reservedSlot));
    }

    public boolean reserveFluidExtract(IFluidHandler handler, int tank, FluidStack stack, long amount) {
        if (handler instanceof NativeReservationAccess access) {
            return reserveNativeExtract(access.reservationIdentity(), access.reservationSlot(tank),
                    access.resourceKey(stack), access.storedKey(tank), access.storedAmount(tank), amount);
        }
        FluidStack current = fluid(handler, tank);
        if (current.isEmpty() || !FluidStack.isSameFluidSameComponents(current, stack)) return false;
        return reserveExtract(handler, tank, stack, amount, FluidStack::isSameFluidSameComponents,
                (storage, reservedTank) -> fluidAmount((IFluidHandler) storage, reservedTank));
    }

    public boolean reserveFluidInsert(IFluidHandler handler, int tank, FluidStack stack, long amount, long capacity) {
        if (handler instanceof NativeReservationAccess access) {
            return reserveNativeInsert(access.reservationIdentity(), access.reservationSlot(tank),
                    access.resourceKey(stack), access.storedKey(tank), access.storedAmount(tank), capacity, amount);
        }
        return reserveInsert(handler, tank, stack, amount, capacity, FluidStack::isSameFluidSameComponents,
                (storage, reservedTank) -> fluidAmount((IFluidHandler) storage, reservedTank));
    }

    public Object nativeKey(Object identity, Object slot, Object storedKey) {
        Map<Object, NativeSlotState> slots = nativeSlots == null ? null : nativeSlots.get(identity);
        NativeSlotState state = slots == null ? null : slots.get(slot);
        return state == null ? storedKey : state.key();
    }

    public long nativeAmount(Object identity, Object slot, long storedAmount) {
        Map<Object, NativeSlotState> slots = nativeSlots == null ? null : nativeSlots.get(identity);
        NativeSlotState state = slots == null ? null : slots.get(slot);
        return state == null ? Math.max(0L, storedAmount) : state.amount();
    }

    public boolean reserveNativeInsert(Object identity, Object slot, Object key, Object storedKey,
                                       long storedAmount, long capacity, long amount) {
        if (amount <= 0L || capacity < 0L || key == null) return false;
        long current = nativeAmount(identity, slot, storedAmount);
        Object currentKey = nativeKey(identity, slot, storedKey);
        if (current > capacity || amount > capacity - current
                || current > 0L && !Objects.equals(currentKey, key)) return false;
        if (nativeSlots == null) nativeSlots = new IdentityHashMap<>();
        nativeSlots.computeIfAbsent(identity, ignored -> new HashMap<>())
                .put(slot, new NativeSlotState(key, current + amount));
        return true;
    }

    public boolean reserveNativeExtract(Object identity, Object slot, Object key, Object storedKey,
                                        long storedAmount, long amount) {
        long current = nativeAmount(identity, slot, storedAmount);
        Object currentKey = nativeKey(identity, slot, storedKey);
        if (key == null || amount <= 0L || amount > current || !Objects.equals(currentKey, key)) return false;
        long remaining = current - amount;
        if (nativeSlots == null) nativeSlots = new IdentityHashMap<>();
        nativeSlots.computeIfAbsent(identity, ignored -> new HashMap<>())
                .put(slot, new NativeSlotState(remaining == 0L ? null : key, remaining));
        return true;
    }

    public long outputAvailable(Object identity, Object key, long capacity) {
        checkOutputReservationKey(identity, key);
        if (capacity < 0L) throw new IllegalArgumentException("capacity must be non-negative");
        Map<Object, Long> byKey = outputReservations == null ? null : outputReservations.get(identity);
        long reserved = byKey == null ? 0L : byKey.getOrDefault(key, 0L);
        try {
            return Math.max(0L, Math.subtractExact(capacity, reserved));
        } catch (ArithmeticException ignored) {
            return 0L;
        }
    }

    public boolean reserveOutput(Object identity, Object key, long amount) {
        checkOutputReservationKey(identity, key);
        if (amount <= 0L) return false;
        Map<Object, Long> byKey = outputReservations == null ? null : outputReservations.get(identity);
        long reserved = byKey == null ? 0L : byKey.getOrDefault(key, 0L);
        long next;
        try {
            next = Math.addExact(reserved, amount);
        } catch (ArithmeticException ignored) {
            return false;
        }
        if (outputReservations == null) outputReservations = new IdentityHashMap<>();
        if (byKey == null) {
            byKey = new HashMap<>();
            outputReservations.put(identity, byKey);
        }
        byKey.put(key, next);
        return true;
    }

    public long valueAvailable(LongValueStorage storage, boolean insert) {
        return valueAvailable(storage, storage.capacity(), storage.amount(), insert);
    }

    public long valueAvailable(IEnergyStorage storage, boolean insert) {
        return storage instanceof LongEnergyHandler energy
                ? valueAvailable(storage, energy.getCapacityAsLong(), energy.getAmountAsLong(), insert)
                : valueAvailable(storage, storage.getMaxEnergyStored(), storage.getEnergyStored(), insert);
    }

    public boolean reserveValue(LongValueStorage storage, long amount, boolean insert) {
        return reserveValue(storage, storage.transferLimit(), storage.capacity(), storage.amount(), amount, insert,
                true);
    }

    public boolean reserveValue(IEnergyStorage storage, long amount, boolean insert) {
        return storage instanceof LongEnergyHandler energy
                ? reserveValue(storage, energy.getTransferLimit(), energy.getCapacityAsLong(), energy.getAmountAsLong(), amount, insert, true)
                : reserveValue(storage, Integer.MAX_VALUE, storage.getMaxEnergyStored(), storage.getEnergyStored(), amount, insert, true);
    }

    public boolean reserveValueTotal(LongValueStorage storage, long amount, boolean insert) {
        return reserveValue(storage, storage.transferLimit(), storage.capacity(), storage.amount(), amount, insert,
                false);
    }

    public boolean reserveValueTotal(IEnergyStorage storage, long amount, boolean insert) {
        return storage instanceof LongEnergyHandler energy
                ? reserveValue(storage, energy.getTransferLimit(), energy.getCapacityAsLong(), energy.getAmountAsLong(), amount, insert, false)
                : reserveValue(storage, Integer.MAX_VALUE, storage.getMaxEnergyStored(), storage.getEnergyStored(), amount, insert, false);
    }

    private long valueAvailable(Object storage, long capacity, long amount, boolean insert) {
        long reserved = values == null ? 0L : values.getOrDefault(storage, 0L);
        long available;
        try {
            available = insert
                    ? Math.subtractExact(Math.subtractExact(capacity, amount), reserved)
                    : Math.addExact(amount, reserved);
        } catch (ArithmeticException ignored) {
            return 0L;
        }
        return Math.max(0L, available);
    }

    private boolean reserveValue(Object storage, long transferLimit, long capacity, long currentAmount,
                                 long amount, boolean insert, boolean enforceTransferLimit) {
        if (amount <= 0L || enforceTransferLimit && amount > transferLimit
                || valueAvailable(storage, capacity, currentAmount, insert) < amount) return false;
        long reserved = values == null ? 0L : values.getOrDefault(storage, 0L);
        long next;
        try {
            next = Math.addExact(reserved, insert ? amount : -amount);
        } catch (ArithmeticException ignored) {
            return false;
        }
        if (values == null) values = new IdentityHashMap<>();
        values.put(storage, next);
        return true;
    }

    private long virtualAmount(Object storage, int slot, long amount) {
        ResourceReservation reservation = reservation(storage, slot, false);
        if (reservation == null) return amount;
        try {
            return Math.addExact(Math.subtractExact(amount, reservation.extracted), reservation.inserted);
        } catch (ArithmeticException ignored) {
            return 0L;
        }
    }

    public PlanningReservations copy() {
        PlanningReservations copy = new PlanningReservations();
        if (resources != null) {
            copy.resources = new IdentityHashMap<>();
            for (Map.Entry<Object, Map<Integer, ResourceReservation>> entry : resources.entrySet()) {
                Map<Integer, ResourceReservation> copiedSlots = new HashMap<>();
                for (Map.Entry<Integer, ResourceReservation> slot : entry.getValue().entrySet()) {
                    ResourceReservation source = slot.getValue();
                    ResourceReservation copied = new ResourceReservation();
                    copied.extracted = source.extracted;
                    copied.inserted = source.inserted;
                    copied.insertedResource = source.insertedResource;
                    copiedSlots.put(slot.getKey(), copied);
                }
                copy.resources.put(entry.getKey(), copiedSlots);
            }
        }
        if (nativeSlots != null) {
            copy.nativeSlots = new IdentityHashMap<>();
            nativeSlots.forEach((identity, slots) -> copy.nativeSlots.put(identity, new HashMap<>(slots)));
        }
        if (values != null) copy.values = new IdentityHashMap<>(values);
        if (outputReservations != null) {
            copy.outputReservations = new IdentityHashMap<>();
            for (Map.Entry<Object, Map<Object, Long>> entry : outputReservations.entrySet()) {
                copy.outputReservations.put(entry.getKey(), new HashMap<>(entry.getValue()));
            }
        }
        return copy;
    }

    private static void checkOutputReservationKey(Object identity, Object key) {
        if (identity == null) throw new IllegalArgumentException("identity must not be null");
        if (key == null) throw new IllegalArgumentException("key must not be null");
    }

    private <R> boolean reserveExtract(Object storage, int slot, R resource, long amount,
                                       ResourceMatcher<R> matcher, AmountReader reader) {
        if (amount <= 0L) return false;
        ResourceReservation reservation = reservation(storage, slot, false);
        Object current = reservation == null || reservation.insertedResource == null
                ? null : reservation.insertedResource;
        if (current != null && !matcher.matches(resource, (R) current) || reader.amount(storage, slot) < amount) return false;
        if (reservation == null) reservation = reservation(storage, slot, true);
        reservation.extracted += amount;
        return true;
    }

    private <R> boolean reserveInsert(Object storage, int slot, R resource, long amount, long capacity,
                                      ResourceMatcher<R> matcher, AmountReader reader) {
        if (amount <= 0L || capacity < 0L || reader.amount(storage, slot) > capacity) return false;
        ResourceReservation reservation = reservation(storage, slot, false);
        Object current = reservation == null ? null : reservation.insertedResource;
        if (current != null && !matcher.matches(resource, (R) current)) return false;
        if (amount > capacity - reader.amount(storage, slot)) return false;
        if (reservation == null) reservation = reservation(storage, slot, true);
        reservation.insertedResource = resource;
        reservation.inserted += amount;
        return true;
    }

    private ResourceReservation reservation(Object storage, int slot, boolean create) {
        Map<Integer, ResourceReservation> bySlot = resources == null ? null : resources.get(storage);
        if (bySlot == null) {
            if (!create) return null;
            if (resources == null) resources = new IdentityHashMap<>();
            bySlot = new HashMap<>();
            resources.put(storage, bySlot);
        }
        ResourceReservation reservation = bySlot.get(slot);
        if (reservation == null && create) {
            reservation = new ResourceReservation();
            bySlot.put(slot, reservation);
        }
        return reservation;
    }

    private record NativeSlotState(Object key, long amount) {
    }

    private static final class ResourceReservation {
        private long extracted;
        private long inserted;
        private Object insertedResource;
    }

    @FunctionalInterface
    private interface ResourceMatcher<R> {
        boolean matches(R first, R second);
    }

    @FunctionalInterface
    private interface AmountReader {
        long amount(Object storage, int slot);
    }
}
