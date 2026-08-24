package cn.howxu.mmcr.api.capability.plan;

import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;

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
        ResourceReservation reservation = reservation(storage, slot, false);
        return storage.amount(slot) - (reservation == null ? 0L : reservation.extracted)
                + (reservation == null ? 0L : reservation.inserted);
    }

    public boolean reserveExtract(ResourceStorage<?> storage, int slot, Object resource, long amount) {
        if (amount <= 0L || !storage.resourceType().isInstance(resource)) return false;
        ResourceReservation reservation = reservation(storage, slot, true);
        Object current = resource(storage, slot);
        if (!storage.resourceType().isInstance(current) || !current.equals(resource)
                || this.amount(storage, slot) < amount) return false;
        reservation.extracted += amount;
        return true;
    }

    public boolean reserveInsert(ResourceStorage<?> storage, int slot, Object resource, long amount) {
        if (amount <= 0L || !storage.resourceType().isInstance(resource)
                || !storage.isValidResource(slot, resource)) return false;
        ResourceReservation reservation = reservation(storage, slot, true);
        Object current = resource(storage, slot);
        long currentAmount = this.amount(storage, slot);
        if (currentAmount > 0L && !current.equals(resource)) return false;
        if (currentAmount + amount > storage.capacityResource(slot, resource)) return false;
        if (reservation.insertedResource != null && !reservation.insertedResource.equals(resource)) return false;
        reservation.insertedResource = resource;
        reservation.inserted += amount;
        return true;
    }

    public long valueAvailable(LongValueStorage storage, boolean insert) {
        long reserved = values.getOrDefault(storage, 0L);
        return insert ? Math.max(0L, storage.capacity() - storage.amount() - reserved)
                : Math.max(0L, storage.amount() + reserved);
    }

    public boolean reserveValue(LongValueStorage storage, long amount, boolean insert) {
        if (amount <= 0L) return false;
        long moved = insert ? storage.insert(amount, true) : storage.extract(amount, true);
        if (moved < amount || valueAvailable(storage, insert) < amount) return false;
        values.merge(storage, insert ? amount : -amount, Long::sum);
        return true;
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
