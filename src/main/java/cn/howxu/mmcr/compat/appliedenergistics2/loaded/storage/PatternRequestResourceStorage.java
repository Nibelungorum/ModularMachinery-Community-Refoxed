package cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2KeyAdapter;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ephemeral transactional MMCR view over the inputs pushed by one AE2 pattern request.
 *
 * @param <R> resource type exposed by the view
 * @author howxu <dev@howxu.cn>
 */
public final class PatternRequestResourceStorage<R> extends SnapshotJournal<PatternRequestResourceStorage.Snapshot>
        implements ResourceStorage<R> {
    private final AE2KeyAdapter<R> adapter;
    private final PatternRequestState state;
    private final List<AEKey> keys;
    private final long[] amounts;
    private final long[] initialAmounts;
    private boolean active = true;

    public PatternRequestResourceStorage(KeyCounter[] inputHolders, AE2KeyAdapter<R> adapter) {
        this(new PatternRequestState(inputHolders, 1), adapter);
    }

    public PatternRequestResourceStorage(PatternRequestState state, AE2KeyAdapter<R> adapter) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.state = Objects.requireNonNull(state, "state");
        Map<AEKey, Long> totals = aggregate(state.inputHolders());
        List<AEKey> selectedKeys = new ArrayList<>();
        List<Long> selectedAmounts = new ArrayList<>();
        for (Map.Entry<AEKey, Long> entry : totals.entrySet()) {
            if (adapter.toResource(entry.getKey()).isPresent()) {
                selectedKeys.add(entry.getKey());
                selectedAmounts.add(entry.getValue());
            }
        }
        keys = List.copyOf(selectedKeys);
        amounts = new long[selectedAmounts.size()];
        for (int slot = 0; slot < amounts.length; slot++) amounts[slot] = selectedAmounts.get(slot);
        initialAmounts = amounts.clone();
    }

    /** A hosted start debits actual extractions only; the remainder stays with its original owner. */
    public void collectConsumed(KeyCounter target) {
        for (int i = 0; i < amounts.length; i++) {
            long debit = initialAmounts[i] - amounts[i];
            if (debit > 0) target.add(keys.get(i), debit);
        }
    }

    @Override
    public Class<R> resourceType() {
        return adapter.resourceType();
    }

    @Override
    public int size() {
        requireActive();
        return keys.size();
    }

    @Override
    @Nullable
    public R resource(int slot) {
        requireActive();
        return adapter.toResource(keys.get(checkSlot(slot))).orElse(null);
    }

    @Override
    public long amount(int slot) {
        requireActive();
        return amounts[checkSlot(slot)];
    }

    @Override
    public long capacity(int slot, @Nullable R resource) {
        requireActive();
        slot = checkSlot(slot);
        return resource == null || keys.get(slot).equals(keyOf(resource)) ? amounts[slot] : 0L;
    }

    @Override
    public boolean isValid(int slot, R resource) {
        requireActive();
        return keys.get(checkSlot(slot)).equals(keyOf(resource));
    }

    @Override
    public long insert(int slot, R resource, long amount, TransactionContext transaction) {
        checkedKey(slot, resource, amount);
        return 0L;
    }

    @Override
    public long extract(int slot, R resource, long amount, TransactionContext transaction) {
        AEKey key = checkedKey(slot, resource, amount);
        if (amount == 0L || !keys.get(slot).equals(key)) return 0L;
        long extracted = Math.min(amount, amounts[slot]);
        if (extracted == 0L) return 0L;
        updateSnapshots(Objects.requireNonNull(transaction, "transaction"));
        amounts[slot] -= extracted;
        return extracted;
    }

    /** Invalidates this request after its surrounding pattern admission is rejected. */
    public void reject(TransactionContext transaction) {
        requireActive();
        updateSnapshots(Objects.requireNonNull(transaction, "transaction"));
        state.reject(transaction);
        active = false;
    }

    /**
     * Returns every unconsumed request amount to AE2's native return inventory and invalidates this request.
     *
     * @return whether the native return inventory could hold every unconsumed amount exactly
     */
    public boolean accept(PatternReturnResourceStorage<R> returns, TransactionContext transaction) {
        requireActive();
        Objects.requireNonNull(returns, "returns");
        transaction = Objects.requireNonNull(transaction, "transaction");
        if (!adapter.resourceType().equals(returns.resourceType())) {
            reject(transaction);
            return false;
        }

        boolean inserted;
        try (Transaction nested = Transaction.open(transaction)) {
            inserted = insertReturns(returns, nested);
            if (inserted) nested.commit();
        }
        if (!inserted) {
            reject(transaction);
            return false;
        }
        updateSnapshots(transaction);
        state.accept(keys, transaction);
        active = false;
        return true;
    }

    @Override
    protected Snapshot createSnapshot() {
        return new Snapshot(amounts.clone(), active);
    }

    @Override
    protected void revertToSnapshot(Snapshot snapshot) {
        System.arraycopy(snapshot.amounts(), 0, amounts, 0, amounts.length);
        active = snapshot.active();
    }

    private static Map<AEKey, Long> aggregate(KeyCounter[] inputHolders) {
        if (inputHolders == null) throw new IllegalArgumentException("inputHolders must not be null");
        Map<AEKey, Long> totals = new LinkedHashMap<>();
        for (KeyCounter inputHolder : inputHolders) {
            if (inputHolder == null) throw new IllegalArgumentException("inputHolder must not be null");
            for (var entry : inputHolder) {
                AEKey key = Objects.requireNonNull(entry.getKey(), "pattern input key");
                if (!isSupportedKey(key)) {
                    throw new IllegalArgumentException("Unsupported AE2 pattern input key: " + key);
                }
                long amount = entry.getLongValue();
                if (amount < 0L) throw new IllegalArgumentException("Pattern input amount must be non-negative: " + amount);
                if (amount == 0L) continue;
                try {
                    totals.merge(key, amount, Math::addExact);
                } catch (ArithmeticException exception) {
                    throw new IllegalArgumentException("Pattern input amount exceeds long range", exception);
                }
            }
        }
        return totals;
    }

    private static boolean isSupportedKey(AEKey key) {
        return AE2ResourceFamilies.supportsKey(key);
    }

    private boolean insertReturns(PatternReturnResourceStorage<R> returns, TransactionContext transaction) {
        for (int requestSlot = 0; requestSlot < keys.size(); requestSlot++) {
            long remaining = amounts[requestSlot];
            if (remaining == 0L) continue;
            R resource = adapter.toResource(keys.get(requestSlot)).orElseThrow(() ->
                    new IllegalStateException("Pattern request key does not match its resource family"));
            for (int returnSlot = 0; returnSlot < returns.size() && remaining > 0L; returnSlot++) {
                remaining -= returns.insert(returnSlot, resource, remaining, transaction);
            }
            if (remaining > 0L) return false;
        }
        return true;
    }

    private AEKey checkedKey(int slot, R resource, long amount) {
        requireActive();
        checkSlot(slot);
        if (amount < 0L) throw new IllegalArgumentException("Expected value to be non-negative: " + amount);
        return keyOf(resource);
    }

    private AEKey keyOf(R resource) {
        if (resource == null || !adapter.resourceType().isInstance(resource)) {
            throw new IllegalArgumentException("Expected resource of type " + adapter.resourceType().getName());
        }
        AEKey key = adapter.toKey(resource);
        if (key == null) throw new IllegalArgumentException("Expected resource to be non-empty: " + resource);
        return key;
    }

    private int checkSlot(int slot) {
        if (slot < 0 || slot >= keys.size()) throw new IndexOutOfBoundsException(slot);
        return slot;
    }

    private void requireActive() {
        if (!active) throw new IllegalStateException("Pattern request view is no longer active");
        state.requireActive();
    }

    protected record Snapshot(long[] amounts, boolean active) {
    }

}
