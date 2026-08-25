package cn.howxu.mmcr.api.capability.storage;

import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Long-backed transactional storage for a resource type.
 *
 * @param <R> stored resource type
 * @author howxu <dev@howxu.cn>
 */
public interface ResourceStorage<R> extends CapabilityStorage {
    Class<R> resourceType();
    int size();

    /**
     * Returns the resource in a slot, or {@code null} for an empty slot.
     */
    @Nullable
    R resource(int slot);

    long amount(int slot);

    long capacity(int slot, @Nullable R resource);

    boolean isValid(int slot, R resource);

    long insert(int slot, R resource, long amount, TransactionContext transaction);

    long extract(int slot, R resource, long amount, TransactionContext transaction);

    default long capacityResource(int slot, Object resource) {
        return resource == null
                ? capacity(slot, null)
                : resourceType().isInstance(resource) ? capacity(slot, resourceType().cast(resource)) : 0;
    }

    @Override
    default Object contentFingerprint() {
        List<SlotFingerprint> slots = new ArrayList<>(size());
        for (int slot = 0; slot < size(); slot++) {
            R resource = resource(slot);
            long capacity = capacity(slot, resource);
            slots.add(new SlotFingerprint(resource, amount(slot), capacity));
        }
        return new ResourceStorageFingerprint(resourceType().getName(), List.copyOf(slots));
    }

    default boolean isValidResource(int slot, Object resource) {
        return resourceType().isInstance(resource) && isValid(slot, resourceType().cast(resource));
    }

    default long insertResource(int slot, Object resource, long amount, TransactionContext transaction) {
        return resourceType().isInstance(resource)
                ? insert(slot, resourceType().cast(resource), amount, transaction) : 0;
    }

    default long extractResource(int slot, Object resource, long amount, TransactionContext transaction) {
        return resourceType().isInstance(resource)
                ? extract(slot, resourceType().cast(resource), amount, transaction) : 0;
    }

    record ResourceStorageFingerprint(String resourceType, List<SlotFingerprint> slots) { }

    record SlotFingerprint(Object resource, long amount, long capacity) { }
}
