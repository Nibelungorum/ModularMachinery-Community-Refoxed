package cn.howxu.mmcr.api.capability.storage;

import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;

/**
 * Long-backed value storage with capacity and per-operation transfer limits.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class LongValueStorage extends SnapshotJournal<Long> {
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

    public long insert(long requested, boolean simulate) {
        return insertInternal(requested, simulate, true);
    }

    public long extract(long requested, boolean simulate) {
        return extractInternal(requested, simulate, true);
    }

    public void setAmount(long value) {
        long clamped = clamp(value);
        amount = clamped;
        onChange.run();
    }

    private long insertInternal(long requested, boolean simulate, boolean limited) {
        if (requested <= 0) return 0L;
        long allowed = limited ? Math.min(requested, transferLimit) : requested;
        long moved = Math.min(allowed, capacity - amount);
        if (!simulate && moved > 0) {
            amount += moved;
            onChange.run();
        }
        return moved;
    }

    private long extractInternal(long requested, boolean simulate, boolean limited) {
        if (requested <= 0) return 0L;
        long allowed = limited ? Math.min(requested, transferLimit) : requested;
        long moved = Math.min(allowed, amount);
        if (!simulate && moved > 0) {
            amount -= moved;
            onChange.run();
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
        onChange.run();
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
}
