package cn.howxu.mmcr.api.capability.plan;

import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import java.util.HashMap;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Tracks resource reservations made while materializing a crafting plan.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PlanningReservations {
    private final Map<Object, Map<Integer, ResourceReservation>> resources = new IdentityHashMap<>();
    private final Map<Object, Map<Object, Long>> outputReservations = new IdentityHashMap<>();
    private final Map<LongValueStorage, Long> values = new IdentityHashMap<>();

    public ItemStack item(IItemHandler handler, int slot) {
        ResourceReservation reservation = reservation(handler, slot, false);
        return reservation == null || reservation.insertedResource == null
                ? handler.getStackInSlot(slot) : ((ItemStack) reservation.insertedResource).copy();
    }

    public FluidStack fluid(IFluidHandler handler, int tank) {
        ResourceReservation reservation = reservation(handler, tank, false);
        return reservation == null || reservation.insertedResource == null
                ? handler.getFluidInTank(tank) : ((FluidStack) reservation.insertedResource).copy();
    }

    public long itemAmount(IItemHandler handler, int slot) {
        return virtualAmount(handler, slot, handler.getStackInSlot(slot).getCount());
    }

    public long fluidAmount(IFluidHandler handler, int tank) {
        return virtualAmount(handler, tank, handler.getFluidInTank(tank).getAmount());
    }

    public boolean reserveItemExtract(IItemHandler handler, int slot, ItemStack stack, long amount) {
        ItemStack current = item(handler, slot);
        if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, stack)) return false;
        return reserveExtract(handler, slot, stack, amount, ItemStack::isSameItemSameComponents,
                (storage, reservedSlot) -> itemAmount((IItemHandler) storage, reservedSlot));
    }

    public boolean reserveItemInsert(IItemHandler handler, int slot, ItemStack stack, long amount, long capacity) {
        return reserveInsert(handler, slot, stack, amount, capacity, ItemStack::isSameItemSameComponents,
                (storage, reservedSlot) -> itemAmount((IItemHandler) storage, reservedSlot));
    }

    public boolean reserveFluidExtract(IFluidHandler handler, int tank, FluidStack stack, long amount) {
        FluidStack current = fluid(handler, tank);
        if (current.isEmpty() || !FluidStack.isSameFluidSameComponents(current, stack)) return false;
        return reserveExtract(handler, tank, stack, amount, FluidStack::isSameFluidSameComponents,
                (storage, reservedTank) -> fluidAmount((IFluidHandler) storage, reservedTank));
    }

    public boolean reserveFluidInsert(IFluidHandler handler, int tank, FluidStack stack, long amount, long capacity) {
        return reserveInsert(handler, tank, stack, amount, capacity, FluidStack::isSameFluidSameComponents,
                (storage, reservedTank) -> fluidAmount((IFluidHandler) storage, reservedTank));
    }

    public long outputAvailable(Object identity, Object key, long capacity) {
        checkOutputReservationKey(identity, key);
        if (capacity < 0L) throw new IllegalArgumentException("capacity must be non-negative");
        Map<Object, Long> byKey = outputReservations.get(identity);
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
        Map<Object, Long> byKey = outputReservations.get(identity);
        long reserved = byKey == null ? 0L : byKey.getOrDefault(key, 0L);
        long next;
        try {
            next = Math.addExact(reserved, amount);
        } catch (ArithmeticException ignored) {
            return false;
        }
        if (byKey == null) {
            byKey = new HashMap<>();
            outputReservations.put(identity, byKey);
        }
        byKey.put(key, next);
        return true;
    }

    public long valueAvailable(LongValueStorage storage, boolean insert) {
        long reserved = values.getOrDefault(storage, 0L);
        long available;
        try {
            available = insert
                    ? Math.subtractExact(Math.subtractExact(storage.capacity(), storage.amount()), reserved)
                    : Math.addExact(storage.amount(), reserved);
        } catch (ArithmeticException ignored) {
            return 0L;
        }
        return Math.max(0L, available);
    }

    public boolean reserveValue(LongValueStorage storage, long amount, boolean insert) {
        return reserveValue(storage, amount, insert, true);
    }

    public boolean reserveValueTotal(LongValueStorage storage, long amount, boolean insert) {
        return reserveValue(storage, amount, insert, false);
    }

    private boolean reserveValue(LongValueStorage storage, long amount, boolean insert,
                                 boolean enforceTransferLimit) {
        if (amount <= 0L || enforceTransferLimit && amount > storage.transferLimit()
                || valueAvailable(storage, insert) < amount) return false;
        long reserved = values.getOrDefault(storage, 0L);
        long next;
        try {
            next = Math.addExact(reserved, insert ? amount : -amount);
        } catch (ArithmeticException ignored) {
            return false;
        }
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
        copy.values.putAll(values);
        for (Map.Entry<Object, Map<Object, Long>> entry : outputReservations.entrySet()) {
            copy.outputReservations.put(entry.getKey(), new HashMap<>(entry.getValue()));
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
        Map<Integer, ResourceReservation> bySlot = resources.get(storage);
        if (bySlot == null) {
            if (!create) return null;
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
