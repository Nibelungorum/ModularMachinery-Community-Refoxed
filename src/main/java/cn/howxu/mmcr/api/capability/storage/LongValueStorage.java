package cn.howxu.mmcr.api.capability.storage;

/**
 * Long-backed value storage with capacity and per-operation transfer limits.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class LongValueStorage implements CapabilityStorage {
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

    public long extract(long requested, boolean simulate) {
        return extractInternal(requested, simulate, true, true);
    }

    public void setAmount(long value) {
        long clamped = clamp(value);
        if (amount == clamped) return;
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

    private long clamp(long value) {
        if (value < 0) return 0L;
        return Math.min(value, capacity);
    }

    private record LongStorageFingerprint(long capacity, long transferLimit, long amount) { }
}
