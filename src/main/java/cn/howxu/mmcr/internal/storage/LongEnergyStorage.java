package cn.howxu.mmcr.internal.storage;

/**
 * Long-backed native energy storage backing MMCR energy hatches.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class LongEnergyStorage implements LongEnergyHandler {
    private final long capacity;
    private final long transferLimit;
    private final Runnable onChange;
    private long amount;

    public LongEnergyStorage(long capacity, long transferLimit, Runnable onChange) {
        if (capacity < 0L || transferLimit < 0L) throw new IllegalArgumentException("capacity and transfer limit must be non-negative");
        this.capacity = capacity;
        this.transferLimit = transferLimit;
        this.onChange = onChange == null ? () -> {} : onChange;
    }

    @Override
    public long getTransferLimit() {
        return transferLimit;
    }

    @Override
    public long getAmountAsLong() {
        return amount;
    }

    @Override
    public long getCapacityAsLong() {
        return capacity;
    }

    public void setAmount(long value) {
        long clamped = Math.min(Math.max(value, 0L), capacity);
        if (amount == clamped) return;
        amount = clamped;
        onChange.run();
    }

    public long forceInsert(long requested, boolean simulate) {
        return moveIn(requested, simulate);
    }

    public long forceExtract(long requested, boolean simulate) {
        return moveOut(requested, simulate);
    }

    @Override
    public long insertLong(long requested, boolean simulate) {
        return moveIn(Math.min(Math.max(requested, 0L), transferLimit), simulate);
    }

    @Override
    public long extractLong(long requested, boolean simulate) {
        return moveOut(Math.min(Math.max(requested, 0L), transferLimit), simulate);
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        if (toReceive <= 0) return 0;
        return (int) insertLong(toReceive, simulate);
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        if (toExtract <= 0) return 0;
        return (int) extractLong(toExtract, simulate);
    }

    @Override
    public int getEnergyStored() {
        return (int) Math.min(amount, Integer.MAX_VALUE);
    }

    @Override
    public int getMaxEnergyStored() {
        return (int) Math.min(capacity, Integer.MAX_VALUE);
    }

    @Override
    public boolean canExtract() {
        return true;
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    private long moveIn(long requested, boolean simulate) {
        if (requested <= 0L) return 0L;
        long moved = Math.min(requested, capacity - amount);
        if (!simulate && moved > 0L) {
            amount += moved;
            onChange.run();
        }
        return moved;
    }

    private long moveOut(long requested, boolean simulate) {
        if (requested <= 0L) return 0L;
        long moved = Math.min(requested, amount);
        if (!simulate && moved > 0L) {
            amount -= moved;
            onChange.run();
        }
        return moved;
    }
}
