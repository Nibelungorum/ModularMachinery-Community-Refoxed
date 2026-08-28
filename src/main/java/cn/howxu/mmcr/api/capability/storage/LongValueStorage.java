package cn.howxu.mmcr.api.capability.storage;

import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Long-backed value storage with capacity and per-operation transfer limits.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class LongValueStorage extends SnapshotJournal<Long> implements CapabilityStorage {
    private final long capacity;
    private final long transferLimit;
    private final Runnable onChange;
    private long amount;

    public LongValueStorage(long capacity, long transferLimit, Runnable onChange) {
        if (capacity < 0) throw new IllegalArgumentException("capacity must be non-negative");
        if (transferLimit < 0) throw new IllegalArgumentException("transferLimit must be non-negative");
        this.capacity = capacity;
        this.transferLimit = transferLimit;
        this.onChange = onChange == null ? () -> {} : onChange;
    }

    public long amount() {
        return amount;
    }

    public long capacity() {
        return capacity;
    }

    public long transferLimit() {
        return transferLimit;
    }

    @Override
    public Object contentFingerprint() {
        return new LongStorageFingerprint(capacity, transferLimit, amount);
    }

    public long insert(long requested, boolean simulate) {
        return insertInternal(requested, simulate, true, true);
    }

    public long insert(long requested, TransactionContext transaction) {
        updateSnapshots(transaction);
        return insertInternal(requested, false, true, false);
    }

    public long extract(long requested, boolean simulate) {
        return extractInternal(requested, simulate, true, true);
    }

    public long extract(long requested, TransactionContext transaction) {
        updateSnapshots(transaction);
        return extractInternal(requested, false, true, false);
    }

    public void setAmount(long value) {
        long clamped = clamp(value);
        amount = clamped;
        onChange.run();
    }

    private long insertInternal(long requested, boolean simulate, boolean limited, boolean notify) {
        if (requested <= 0) return 0L;
        long allowed = limited ? Math.min(requested, transferLimit) : requested;
        long moved = Math.min(allowed, capacity - amount);
        if (!simulate && moved > 0) {
            amount += moved;
            if (notify) onChange.run();
        }
        return moved;
    }

    private long extractInternal(long requested, boolean simulate, boolean limited, boolean notify) {
        if (requested <= 0) return 0L;
        long allowed = limited ? Math.min(requested, transferLimit) : requested;
        long moved = Math.min(allowed, amount);
        if (!simulate && moved > 0) {
            amount -= moved;
            if (notify) onChange.run();
        }
        return moved;
    }

    @Override
    protected Long createSnapshot() {
        return amount;
    }

    @Override
    protected void revertToSnapshot(Long snapshot) {
        amount = snapshot == null ? 0L : snapshot;
    }

    @Override
    protected void onRootCommit(Long originalState) {
        if (originalState == null || originalState != amount) {
            onChange.run();
        }
    }

    private long clamp(long value) {
        if (value < 0) return 0L;
        return Math.min(value, capacity);
    }

    private record LongStorageFingerprint(long capacity, long transferLimit, long amount) { }
}
