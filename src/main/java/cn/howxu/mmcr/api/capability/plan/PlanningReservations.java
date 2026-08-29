package cn.howxu.mmcr.api.capability.plan;

import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Tracks resource reservations made while materializing a crafting plan.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PlanningReservations {
    private final Map<ResourceStorage<?>, Map<Integer, ResourceReservation>> resources = new IdentityHashMap<>();
    private final Map<LongValueStorage, Long> values = new IdentityHashMap<>();

    public Object resource(ResourceStorage<?> storage, int slot) {
        ResourceReservation reservation = reservation(storage, slot, false);
        if (reservation == null) return storage.resource(slot);
        return reservation.insertedResource == null ? storage.resource(slot) : reservation.insertedResource;
    }

    public long amount(ResourceStorage<?> storage, int slot) {
        Long amount = virtualAmount(storage, slot);
        return amount == null ? 0L : amount;
    }

    public boolean reserveExtract(ResourceStorage<?> storage, int slot, Object resource, long amount) {
        if (amount <= 0L || !storage.resourceType().isInstance(resource)) return false;
        Object current = resource(storage, slot);
        Long currentAmount = virtualAmount(storage, slot);
        if (!storage.resourceType().isInstance(current) || !current.equals(resource)
                || currentAmount == null || currentAmount < amount) return false;
        ResourceReservation reservation = reservation(storage, slot, false);
        long extracted;
        try {
            extracted = Math.addExact(reservation == null ? 0L : reservation.extracted, amount);
        } catch (ArithmeticException ignored) {
            return false;
        }
        if (reservation == null) reservation = reservation(storage, slot, true);
        reservation.extracted = extracted;
        return true;
    }

    public boolean reserveInsert(ResourceStorage<?> storage, int slot, Object resource, long amount) {
        if (amount <= 0L || !storage.resourceType().isInstance(resource)
                || !storage.isValidResource(slot, resource)) return false;
        ResourceReservation reservation = reservation(storage, slot, false);
        Object current = resource(storage, slot);
        Long currentAmount = virtualAmount(storage, slot);
        long capacity = storage.capacityResource(slot, resource);
        if (currentAmount == null || currentAmount < 0L || capacity < 0L || currentAmount > capacity
                || mismatchedNonEmptyResource(current, resource)
                || amount > capacity - currentAmount) return false;
        if (reservation == null) reservation = reservation(storage, slot, true);
        if (reservation.insertedResource != null && !reservation.insertedResource.equals(resource)) return false;
        long inserted;
        try {
            inserted = Math.addExact(reservation.inserted, amount);
        } catch (ArithmeticException ignored) {
            return false;
        }
        reservation.insertedResource = resource;
        reservation.inserted = inserted;
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

    private Long virtualAmount(ResourceStorage<?> storage, int slot) {
        ResourceReservation reservation = reservation(storage, slot, false);
        try {
            long amount = Math.subtractExact(storage.amount(slot), reservation == null ? 0L : reservation.extracted);
            return Math.addExact(amount, reservation == null ? 0L : reservation.inserted);
        } catch (ArithmeticException ignored) {
            return null;
        }
    }

    public PlanningReservations copy() {
        PlanningReservations copy = new PlanningReservations();
        for (Map.Entry<ResourceStorage<?>, Map<Integer, ResourceReservation>> entry : resources.entrySet()) {
            Map<Integer, ResourceReservation> copiedSlots = new java.util.HashMap<>();
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
        return copy;
    }

    private static boolean mismatchedNonEmptyResource(Object current, Object requested) {
        if (current instanceof ItemResource item) return !item.isEmpty() && !item.equals(requested);
        if (current instanceof FluidResource fluid) return !fluid.isEmpty() && !fluid.equals(requested);
        return false;
    }

    private ResourceReservation reservation(ResourceStorage<?> storage, int slot, boolean create) {
        Map<Integer, ResourceReservation> bySlot = resources.get(storage);
        if (bySlot == null) {
            if (!create) return null;
            bySlot = new java.util.HashMap<>();
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
}
