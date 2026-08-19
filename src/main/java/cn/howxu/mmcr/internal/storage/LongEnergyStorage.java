package cn.howxu.mmcr.internal.storage;

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

    private final long capacity;
    private final long transferLimit;
    private long amount;
    private final Runnable onChange;

    public LongEnergyStorage(long capacity, long transferLimit, Runnable onChange) {
        if (capacity < 0) throw new IllegalArgumentException("capacity must be non-negative");
        if (transferLimit < 0) throw new IllegalArgumentException("transferLimit must be non-negative");
        this.capacity = capacity;
        this.transferLimit = transferLimit;
        this.amount = 0L;
        this.onChange = onChange == null ? () -> {} : onChange;
    }

    public long getCapacityAsLong() {
        return capacity;
    }

    public long getTransferLimit() {
        return transferLimit;
    }

    public long getAmountAsLong() {
        return amount;
    }

    public void setAmount(long value) {
        amount = clamp(value);
        onChange.run();
    }

    public long forceInsert(long requested, boolean simulate) {
        if (requested <= 0) return 0L;
        long room = capacity - amount;
        long moved = Math.min(requested, room);
        if (!simulate && moved > 0) {
            amount += moved;
            onChange.run();
        }
        return moved;
    }

    public long forceExtract(long requested, boolean simulate) {
        if (requested <= 0) return 0L;
        long moved = Math.min(requested, amount);
        if (!simulate && moved > 0) {
            amount -= moved;
            onChange.run();
        }
        return moved;
    }

    @Override
    public int insert(int amount, TransactionContext tx) {
        TransferPreconditions.checkNonNegative(amount);
        if (amount == 0) return 0;
        updateSnapshots(tx);
        return (int) Math.min(Integer.MAX_VALUE, forceInsert(Math.min(amount, transferLimit), false));
    }

    @Override
    public int extract(int amount, TransactionContext tx) {
        TransferPreconditions.checkNonNegative(amount);
        if (amount == 0) return 0;
        updateSnapshots(tx);
        return (int) Math.min(Integer.MAX_VALUE, forceExtract(Math.min(amount, transferLimit), false));
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
        if (value < 0) return 0;
        if (value > capacity) return capacity;
        return value;
    }
}