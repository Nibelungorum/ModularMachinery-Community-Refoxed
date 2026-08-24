package cn.howxu.mmcr.api.capability.storage;

import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

/**
 * Long-backed transactional storage for a resource type.
 *
 * @param <R> stored resource type
 * @author howxu <dev@howxu.cn>
 */
public interface ResourceStorage<R> extends CapabilityStorage {
    Class<R> resourceType();
    int size();

    R resource(int slot);

    long amount(int slot);

    long capacity(int slot, @Nullable R resource);

    boolean isValid(int slot, R resource);

    long insert(int slot, R resource, long amount, TransactionContext transaction);

    long extract(int slot, R resource, long amount, TransactionContext transaction);

    default long capacityResource(int slot, Object resource) {
        return resourceType().isInstance(resource) ? capacity(slot, resourceType().cast(resource)) : 0;
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
}
