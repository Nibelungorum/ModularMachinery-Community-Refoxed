package cn.howxu.mmcr.compat.ars_nouveau.loaded;

import com.hollingsworth.arsnouveau.common.capability.SourceStorage;

/**
 * Single native source store shared by the port's internal and external views.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SourcePortStorage {
    private final SourceStorage storage;
    private final Runnable changed;

    public SourcePortStorage(Runnable changed) {
        this.changed = changed;
        storage = new SourceStorage(10_000, 10_000) {
            @Override
            public void onContentsChanged() {
                changed.run();
            }
        };
    }

    public int amount() {
        return storage.getSource();
    }

    public int capacity() {
        return storage.getSourceCapacity();
    }

    public Object identity() {
        return storage;
    }

    public long move(long requested, boolean insert, boolean simulate) {
        long available = insert ? capacity() - amount() : amount();
        int bounded = (int) Math.min(Math.max(0L, requested), available);
        if (bounded <= 0) return 0L;
        return insert ? storage.receiveSource(bounded, simulate) : storage.extractSource(bounded, simulate);
    }

    /** Assigns NBT/sync state; this is not a transfer rollback operation. */
    public void setAmount(long amount) {
        int next = (int) Math.min(capacity(), Math.max(0L, amount));
        if (next == storage.getSource()) return;
        storage.setSource(next);
        changed.run();
    }
}
