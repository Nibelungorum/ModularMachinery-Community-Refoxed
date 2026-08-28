package cn.howxu.mmcr.internal.storage;

import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Long-backed energy storage backing MMCR energy hatches.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class LongEnergyStorage extends SnapshotJournal<Long> implements LongEnergyHandler {
    private final LongValueStorage storage;
    private final long transferLimit;
    private final Runnable onChange;
    private boolean restoring;

    public LongEnergyStorage(long capacity, long transferLimit, Runnable onChange) {
        this.transferLimit = transferLimit;
        this.onChange = onChange == null ? () -> {} : onChange;
        this.storage = new LongValueStorage(capacity, transferLimit, () -> {
            if (!restoring) this.onChange.run();
        });
    }

    public long getCapacityAsLong() {
        return storage.capacity();
    }

    @Override
    public long getTransferLimit() {
        return transferLimit;
    }

    public long getAmountAsLong() {
        return storage.amount();
    }

    public LongValueStorage storage() {
        return storage;
    }

    public void setAmount(long value) {
        storage.setAmount(value);
    }

    public long forceInsert(long requested, boolean simulate) {
        if (requested <= 0L) return 0L;
        long moved = Math.min(requested, storage.capacity() - storage.amount());
        if (!simulate && moved > 0L) storage.setAmount(storage.amount() + moved);
        return moved;
    }

    public long forceExtract(long requested, boolean simulate) {
        if (requested <= 0L) return 0L;
        long moved = Math.min(requested, storage.amount());
        if (!simulate && moved > 0L) storage.setAmount(storage.amount() - moved);
        return moved;
    }

    @Override
    public int insert(int amount, TransactionContext tx) {
        TransferPreconditions.checkNonNegative(amount);
        if (amount == 0) return 0;
        updateSnapshots(tx);
        return (int) storage.insert(amount, tx);
    }

    @Override
    public int extract(int amount, TransactionContext tx) {
        TransferPreconditions.checkNonNegative(amount);
        if (amount == 0) return 0;
        updateSnapshots(tx);
        return (int) storage.extract(amount, tx);
    }

    @Override
    public long insertLong(long amount, TransactionContext tx) {
        if (amount < 0L) throw new IllegalArgumentException("amount must be non-negative");
        if (amount == 0) return 0L;
        updateSnapshots(tx);
        return storage.insert(amount, tx);
    }

    @Override
    public long extractLong(long amount, TransactionContext tx) {
        if (amount < 0L) throw new IllegalArgumentException("amount must be non-negative");
        if (amount == 0) return 0L;
        updateSnapshots(tx);
        return storage.extract(amount, tx);
    }

    @Override
    protected Long createSnapshot() {
        return storage.amount();
    }

    @Override
    protected void revertToSnapshot(Long snapshot) {
        boolean wasRestoring = restoring;
        restoring = true;
        try {
            storage.setAmount(snapshot == null ? 0L : snapshot);
        } finally {
            restoring = wasRestoring;
        }
    }

    @Override
    protected void onRootCommit(Long originalState) { }
}
