package cn.howxu.mmcr.internal.storage;

import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

/**
 * Single-resource, long-backed item storage.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class BulkItemStorage extends SnapshotJournal<BulkItemStorage.Snapshot>
        implements ResourceStorage<ItemResource> {
    private final long capacity;
    private final Runnable onChange;
    private ItemResource resource = ItemResource.EMPTY;
    private long amount;

    public BulkItemStorage(long capacity, Runnable onChange) {
        if (capacity < 0) throw new IllegalArgumentException("capacity must be non-negative");
        this.capacity = capacity;
        this.onChange = onChange == null ? () -> {} : onChange;
    }

    @Override
    public Class<ItemResource> resourceType() {
        return ItemResource.class;
    }

    public long insert(ItemResource resource, long amount, boolean simulate) {
        if (amount <= 0 || resource == null || resource.isEmpty()) return 0L;
        if (!this.resource.isEmpty() && !this.resource.equals(resource)) return 0L;
        long moved = Math.min(amount, Math.max(0L, stackCapacity(resource) - this.amount));
        if (!simulate && moved > 0) {
            if (this.resource.isEmpty()) this.resource = resource;
            this.amount += moved;
            onChange.run();
        }
        return moved;
    }

    public long extract(ItemResource resource, long amount, boolean simulate) {
        if (amount <= 0 || resource == null || resource.isEmpty() || this.amount == 0L
                || !this.resource.equals(resource)) return 0L;
        long moved = Math.min(amount, this.amount);
        if (!simulate && moved > 0) {
            this.amount -= moved;
            if (this.amount == 0L) this.resource = ItemResource.EMPTY;
            onChange.run();
        }
        return moved;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public ItemResource resource(int slot) {
        checkSlot(slot);
        return resource;
    }

    @Override
    public long amount(int slot) {
        checkSlot(slot);
        return amount;
    }

    @Override
    public long capacity(int slot, @Nullable ItemResource resource) {
        checkSlot(slot);
        return stackCapacity(resource);
    }

    @Override
    public boolean isValid(int slot, ItemResource resource) {
        checkSlot(slot);
        TransferPreconditions.checkNonEmpty(resource);
        return this.resource.isEmpty() || this.resource.equals(resource);
    }

    @Override
    public long insert(int slot, ItemResource resource, long amount, TransactionContext transaction) {
        checkSlot(slot);
        TransferPreconditions.checkNonEmpty(resource);
        checkNonNegative(amount);
        if (amount == 0L || !isValid(slot, resource)) return 0L;
        long moved = Math.min(amount, Math.max(0L, stackCapacity(resource) - this.amount));
        if (moved > 0L) {
            updateSnapshots(transaction);
            if (this.resource.isEmpty()) this.resource = resource;
            this.amount += moved;
        }
        return moved;
    }

    @Override
    public long extract(int slot, ItemResource resource, long amount, TransactionContext transaction) {
        checkSlot(slot);
        TransferPreconditions.checkNonEmpty(resource);
        checkNonNegative(amount);
        if (amount == 0L || this.amount == 0L || !this.resource.equals(resource)) return 0L;
        long moved = Math.min(amount, this.amount);
        if (moved > 0L) {
            updateSnapshots(transaction);
            this.amount -= moved;
            if (this.amount == 0L) this.resource = ItemResource.EMPTY;
        }
        return moved;
    }

    @Override
    protected Snapshot createSnapshot() {
        return new Snapshot(resource, amount);
    }

    @Override
    protected void revertToSnapshot(Snapshot snapshot) {
        resource = snapshot.resource;
        amount = snapshot.amount;
    }

    @Override
    protected void onRootCommit(Snapshot originalState) {
        if (originalState == null || originalState.amount != amount || !originalState.resource.equals(resource)) {
            onChange.run();
        }
    }

    private void checkSlot(int slot) {
        if (slot != 0) throw new IndexOutOfBoundsException(slot);
    }

    private void checkNonNegative(long amount) {
        if (amount < 0L) throw new IllegalArgumentException("Expected value to be non-negative: " + amount);
    }

    private long stackCapacity(@Nullable ItemResource resource) {
        return resource == null ? capacity : Math.min(capacity, resource.getMaxStackSize());
    }

    record Snapshot(ItemResource resource, long amount) {}
}
