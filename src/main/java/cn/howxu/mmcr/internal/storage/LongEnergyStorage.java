package cn.howxu.mmcr.internal.storage;

import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Long-backed energy storage backing MMCR energy hatches.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class LongEnergyStorage extends SnapshotJournal<Long> implements EnergyHandler {
    private final LongValueStorage storage;
    private final long transferLimit;
    private final Runnable onChange;

    public LongEnergyStorage(long capacity, long transferLimit, Runnable onChange) {
        this.storage = new LongValueStorage(capacity, transferLimit, onChange);
        this.transferLimit = transferLimit;
        this.onChange = onChange == null ? () -> {} : onChange;
    }

    public long getCapacityAsLong() {
        return storage.capacity();
    }

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
        return (int) storage.insert(amount, false);
    }

    @Override
    public int extract(int amount, TransactionContext tx) {
        TransferPreconditions.checkNonNegative(amount);
        if (amount == 0) return 0;
        updateSnapshots(tx);
        return (int) storage.extract(amount, false);
    }

    @Override
    protected Long createSnapshot() {
        return storage.amount();
    }

    @Override
    protected void revertToSnapshot(Long snapshot) {
        storage.setAmount(snapshot == null ? 0L : snapshot);
    }

    @Override
    protected void onRootCommit(Long originalState) {
        if (originalState == null || originalState != storage.amount()) {
            onChange.run();
        }
    }
}
