package cn.howxu.mmcr.internal.storage;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;


/**
 * Single-slot long-backed fluid storage backing MMCR fluid hatches.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class LongFluidStorage extends SnapshotJournal<LongFluidStorage.Snapshot> implements ResourceHandler<FluidResource> {

    private final long capacity;
    private FluidResource resource;
    private long amount;
    private final Runnable onChange;

    public LongFluidStorage(long capacity, Runnable onChange) {
        if (capacity < 0) throw new IllegalArgumentException("capacity must be non-negative");
        this.capacity = capacity;
        this.resource = FluidResource.EMPTY;
        this.amount = 0L;
        this.onChange = onChange == null ? () -> {} : onChange;
    }

    public long getCapacityAsLong() {
        return capacity;
    }

    public long getAmountAsLong() {
        return amount;
    }

    public FluidResource getResource() {
        return resource;
    }

    public FluidStack getFluidStack() {
        if (isEmpty()) return FluidStack.EMPTY;
        return resource.toStack((int) Math.min(amount, Integer.MAX_VALUE));
    }

    public boolean isEmpty() {
        return amount <= 0 || resource.isEmpty();
    }

    public void setFluid(FluidStack stack) {
        if (stack == null || stack.isEmpty()) {
            resource = FluidResource.EMPTY;
            amount = 0L;
        } else {
            resource = FluidResource.of(stack);
            amount = Math.min(stack.getAmount(), capacity);
        }
        onChange.run();
    }

    public void setContents(FluidResource resource, long amount) {
        if (resource == null || resource.isEmpty() || amount <= 0) {
            this.resource = FluidResource.EMPTY;
            this.amount = 0L;
        } else {
            this.resource = resource;
            this.amount = Math.min(amount, capacity);
        }
        onChange.run();
    }

    public void clearContent() {
        setFluid(FluidStack.EMPTY);
    }

    public long forceInsert(FluidStack stack, boolean simulate) {
        if (stack == null || stack.isEmpty()) return 0L;
        return forceInsertAmount(FluidResource.of(stack), stack.getAmount(), simulate);
    }

    public long forceExtract(long max, boolean simulate) {
        if (max <= 0 || isEmpty()) return 0L;
        long moved = Math.min(max, amount);
        if (!simulate) {
            amount -= moved;
            if (amount <= 0) {
                resource = FluidResource.EMPTY;
                amount = 0L;
            }
            onChange.run();
        }
        return moved;
    }

    private long forceInsertAmount(FluidResource incoming, long requested, boolean simulate) {
        if (requested <= 0) return 0L;
        if (incoming.isEmpty()) return 0L;
        long room;
        if (resource.isEmpty()) {
            room = capacity;
        } else if (!resource.equals(incoming)) {
            return 0L;
        } else {
            room = capacity - amount;
        }
        long moved = Math.min(requested, room);
        if (!simulate && moved > 0) {
            if (resource.isEmpty()) {
                resource = incoming;
            }
            amount += moved;
            onChange.run();
        }
        return moved;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int slot) {
        checkSlot(slot);
        return resource;
    }

    @Override
    public long getAmountAsLong(int slot) {
        checkSlot(slot);
        return amount;
    }

    @Override
    public long getCapacityAsLong(int slot, FluidResource resource) {
        checkSlot(slot);
        return capacity;
    }

    @Override
    public boolean isValid(int slot, FluidResource resource) {
        checkSlot(slot);
        TransferPreconditions.checkNonEmpty(resource);
        return this.resource.isEmpty() || this.resource.equals(resource);
    }

    @Override
    public int insert(int slot, FluidResource resource, int amount, TransactionContext tx) {
        checkSlot(slot);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (amount == 0) return 0;
        if (!isValid(slot, resource)) return 0;
        long inserted = Math.min(amount, capacity - this.amount);
        if (inserted > 0) {
            updateSnapshots(tx);
            if (this.resource.isEmpty()) this.resource = resource;
            this.amount += inserted;
        }
        return (int) inserted;
    }

    @Override
    public int extract(int slot, FluidResource resource, int amount, TransactionContext tx) {
        checkSlot(slot);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (amount == 0 || isEmpty()) return 0;
        if (!this.resource.equals(resource)) return 0;
        int extracted = (int) Math.min(amount, this.amount);
        if (extracted > 0) {
            updateSnapshots(tx);
            this.amount -= extracted;
            if (this.amount == 0) this.resource = FluidResource.EMPTY;
        }
        return extracted;
    }

    @Override
    protected Snapshot createSnapshot() {
        return new Snapshot(resource, amount);
    }

    @Override
    protected void revertToSnapshot(Snapshot snapshot) {
        if (snapshot == null) {
            resource = FluidResource.EMPTY;
            amount = 0L;
        } else {
            resource = snapshot.resource;
            amount = snapshot.amount;
        }
        onChange.run();
    }

    @Override
    protected void onRootCommit(Snapshot originalState) {
        if (originalState == null || originalState.amount != amount || originalState.resource != resource) {
            onChange.run();
        }
    }

    private void checkSlot(int slot) {
        if (slot != 0) throw new IndexOutOfBoundsException(slot);
    }

    public record Snapshot(FluidResource resource, long amount) {}
}
