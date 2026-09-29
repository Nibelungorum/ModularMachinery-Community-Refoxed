package cn.howxu.mmcr.internal.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Fixed-slot, long-backed storage for resources with one resource identity per slot.
 *
 * @param <R> stored resource type
 * @author howxu <dev@howxu.cn>
 */
public class LongSlotStorage<R> {
    private final long capacity;
    private final Predicate<R> empty;
    private final UnaryOperator<R> copy;
    private final BiPredicate<R, R> matches;
    private final Runnable onChange;
    private final List<R> resources;
    private final long[] amounts;

    public LongSlotStorage(int slots, long capacity, Predicate<R> empty, UnaryOperator<R> copy,
                               BiPredicate<R, R> matches, Runnable onChange) {
        if (slots <= 0) throw new IllegalArgumentException("slots must be positive");
        if (capacity < 0L) throw new IllegalArgumentException("capacity must be non-negative");
        if (empty == null || copy == null || matches == null) {
            throw new IllegalArgumentException("resource functions must not be null");
        }
        this.capacity = capacity;
        this.empty = empty;
        this.copy = copy;
        this.matches = matches;
        this.onChange = onChange == null ? () -> {} : onChange;
        this.resources = new ArrayList<>(Collections.nCopies(slots, null));
        this.amounts = new long[slots];
    }

    public int size() {
        return amounts.length;
    }

    public R resource(int slot) {
        checkSlot(slot);
        R resource = resources.get(slot);
        return resource == null ? null : copy.apply(resource);
    }

    public long amount(int slot) {
        checkSlot(slot);
        return amounts[slot];
    }

    public long capacity(int slot) {
        checkSlot(slot);
        return capacity;
    }

    public boolean isValid(int slot, R resource) {
        checkSlot(slot);
        checkResource(resource);
        R stored = resources.get(slot);
        return stored == null || matches.test(stored, resource);
    }

    /** Sets a slot directly for persistence and native handler adapters. */
    public void setContents(int slot, R resource, long amount) {
        checkSlot(slot);
        long storedAmount = Math.min(Math.max(amount, 0L), capacity);
        R storedResource = storedAmount == 0L || isEmptyResource(resource) ? null : copyResource(resource);
        if (storedResource == null) storedAmount = 0L;

        R previous = resources.get(slot);
        if (amounts[slot] == storedAmount && sameResource(previous, storedResource)) return;
        resources.set(slot, storedResource);
        amounts[slot] = storedAmount;
        onChange.run();
    }

    protected final long insertDirect(int slot, R resource, long requested, boolean simulate) {
        checkSlot(slot);
        checkResource(resource);
        if (requested <= 0L || !isValid(slot, resource)) return 0L;

        long inserted = Math.min(requested, capacity - amounts[slot]);
        if (!simulate && inserted > 0L) {
            if (resources.get(slot) == null) resources.set(slot, copyResource(resource));
            amounts[slot] += inserted;
            onChange.run();
        }
        return inserted;
    }

    protected final long extractDirect(int slot, R resource, long requested, boolean simulate) {
        checkSlot(slot);
        checkResource(resource);
        R stored = resources.get(slot);
        if (requested <= 0L || amounts[slot] == 0L || stored == null || !matches.test(stored, resource)) {
            return 0L;
        }

        long extracted = Math.min(requested, amounts[slot]);
        if (!simulate && extracted > 0L) {
            amounts[slot] -= extracted;
            if (amounts[slot] == 0L) resources.set(slot, null);
            onChange.run();
        }
        return extracted;
    }

    private R copyResource(R resource) {
        checkResource(resource);
        R copied = copy.apply(resource);
        if (isEmptyResource(copied)) throw new IllegalArgumentException("Resource copy must be non-empty");
        return copied;
    }

    private boolean sameResource(R first, R second) {
        return first == second || first != null && second != null && matches.test(first, second);
    }

    private boolean isEmptyResource(R resource) {
        return resource == null || empty.test(resource);
    }

    private void checkResource(R resource) {
        if (isEmptyResource(resource)) throw new IllegalArgumentException("Expected a non-empty resource");
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= amounts.length) throw new IndexOutOfBoundsException(slot);
    }
}
