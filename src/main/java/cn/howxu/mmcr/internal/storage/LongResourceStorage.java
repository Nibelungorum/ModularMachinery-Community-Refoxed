package cn.howxu.mmcr.internal.storage;

import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Fixed-slot, long-backed storage for resources with one resource identity per slot.
 *
 * @param <R> stored resource type
 * @author howxu <dev@howxu.cn>
 */
public class LongResourceStorage<R> extends SnapshotJournal<LongResourceStorage.Snapshot<R>>
        implements ResourceStorage<R> {
    private final Class<R> resourceType;
    private final long capacity;
    private final Predicate<R> empty;
    private final Runnable onChange;
    private final List<R> resources;
    private final long[] amounts;

    public LongResourceStorage(Class<R> resourceType, int slots, long capacity,
                               Predicate<R> empty, Runnable onChange) {
        if (resourceType == null) throw new IllegalArgumentException("resourceType must not be null");
        if (slots <= 0) throw new IllegalArgumentException("slots must be positive");
        if (capacity < 0L) throw new IllegalArgumentException("capacity must be non-negative");
        if (empty == null) throw new IllegalArgumentException("empty must not be null");
        this.resourceType = resourceType;
        this.capacity = capacity;
        this.empty = empty;
        this.onChange = onChange == null ? () -> {} : onChange;
        this.resources = new ArrayList<>(Collections.nCopies(slots, null));
        this.amounts = new long[slots];
    }

    @Override
    public Class<R> resourceType() {
        return resourceType;
    }

    @Override
    public int size() {
        return amounts.length;
    }

    @Override
    @Nullable
    public R resource(int slot) {
        checkSlot(slot);
        return resources.get(slot);
    }

    @Override
    public long amount(int slot) {
        checkSlot(slot);
        return amounts[slot];
    }

    @Override
    public long capacity(int slot, @Nullable R resource) {
        checkSlot(slot);
        return capacity;
    }

    @Override
    public boolean isValid(int slot, R resource) {
        checkSlot(slot);
        checkResource(resource);
        return isEmptyResource(resources.get(slot)) || resources.get(slot).equals(resource);
    }

    @Override
    public long insert(int slot, R resource, long amount, TransactionContext transaction) {
        checkSlot(slot);
        checkResource(resource);
        checkNonNegative(amount);
        if (amount == 0L || !isValid(slot, resource)) return 0L;

        long inserted = Math.min(amount, capacity - amounts[slot]);
        if (inserted > 0L) {
            updateSnapshots(transaction);
            if (isEmptyResource(resources.get(slot))) resources.set(slot, resource);
            amounts[slot] += inserted;
        }
        return inserted;
    }

    @Override
    public long extract(int slot, R resource, long amount, TransactionContext transaction) {
        checkSlot(slot);
        checkResource(resource);
        checkNonNegative(amount);
        if (amount == 0L || amounts[slot] == 0L || !resource.equals(resources.get(slot))) return 0L;

        long extracted = Math.min(amount, amounts[slot]);
        if (extracted > 0L) {
            updateSnapshots(transaction);
            amounts[slot] -= extracted;
            if (amounts[slot] == 0L) resources.set(slot, null);
        }
        return extracted;
    }

    /**
     * Sets a slot directly for persistence and compatibility adapters.
     */
    public void setContents(int slot, @Nullable R resource, long amount) {
        checkSlot(slot);
        long storedAmount = Math.min(amount, capacity);
        if (storedAmount <= 0L || isEmptyResource(resource)) {
            resources.set(slot, null);
            amounts[slot] = 0L;
        } else {
            checkResource(resource);
            resources.set(slot, resource);
            amounts[slot] = storedAmount;
        }
        onChange.run();
    }

    protected final long slotCapacity() {
        return capacity;
    }

    protected final long insertDirect(int slot, R resource, long requested, boolean simulate) {
        checkSlot(slot);
        checkResource(resource);
        if (requested <= 0L || !isValid(slot, resource)) return 0L;

        long inserted = Math.min(requested, capacity - amounts[slot]);
        if (!simulate && inserted > 0L) {
            if (isEmptyResource(resources.get(slot))) resources.set(slot, resource);
            amounts[slot] += inserted;
            onChange.run();
        }
        return inserted;
    }

    protected final long extractDirect(int slot, R resource, long requested, boolean simulate) {
        checkSlot(slot);
        checkResource(resource);
        if (requested <= 0L || amounts[slot] == 0L || !resource.equals(resources.get(slot))) return 0L;

        long extracted = Math.min(requested, amounts[slot]);
        if (!simulate && extracted > 0L) {
            amounts[slot] -= extracted;
            if (amounts[slot] == 0L) resources.set(slot, null);
            onChange.run();
        }
        return extracted;
    }

    @Override
    protected Snapshot<R> createSnapshot() {
        return new Snapshot<>(Collections.unmodifiableList(new ArrayList<>(resources)), amounts.clone());
    }

    @Override
    protected void revertToSnapshot(Snapshot<R> snapshot) {
        resources.clear();
        if (snapshot != null) {
            resources.addAll(snapshot.resources());
            System.arraycopy(snapshot.amounts(), 0, amounts, 0, amounts.length);
        } else {
            resources.addAll(Collections.nCopies(amounts.length, null));
            Arrays.fill(amounts, 0L);
        }
        if (snapshot == null || !snapshot.resources().equals(resources) || !Arrays.equals(snapshot.amounts(), amounts)) {
            onChange.run();
        }
    }

    @Override
    protected void onRootCommit(Snapshot<R> originalState) {
        if (originalState == null
                || !originalState.resources().equals(resources)
                || !Arrays.equals(originalState.amounts(), amounts)) {
            onChange.run();
        }
    }

    private boolean isEmptyResource(@Nullable R resource) {
        return resource == null || empty.test(resource);
    }

    private void checkResource(R resource) {
        if (resource == null || !resourceType.isInstance(resource) || empty.test(resource)) {
            throw new IllegalArgumentException("Expected resource to be non-empty: " + resource);
        }
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= amounts.length) throw new IndexOutOfBoundsException(slot);
    }

    private void checkNonNegative(long amount) {
        if (amount < 0L) throw new IllegalArgumentException("Expected value to be non-negative: " + amount);
    }

    protected record Snapshot<R>(List<R> resources, long[] amounts) {}
}
