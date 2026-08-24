package cn.howxu.mmcr.api.capability.storage;

import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

/**
 * Long-backed transactional storage for a resource type.
 *
 * @param <R> stored resource type
 * @author howxu <dev@howxu.cn>
 */
public interface ResourceStorage<R> {
    int size();

    R resource(int slot);

    long amount(int slot);

    long capacity(int slot, @Nullable R resource);

    boolean isValid(int slot, R resource);

    long insert(int slot, R resource, long amount, TransactionContext transaction);

    long extract(int slot, R resource, long amount, TransactionContext transaction);
}
