package cn.howxu.mmcr.api.data;

import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/** Ordered, typed, transaction-aware machine data storage.
 * @author howxu <dev@howxu.cn>
 */
public final class DataStorage extends SnapshotJournal<Map<String, DataValue>> {
    private final Map<String, DataValue> values = new LinkedHashMap<>();
    private final Consumer<Map<String, DataValue>> changeListener;
    private @Nullable Map<String, DataValue> immutableValuesCache;

    public DataStorage() {
        this(null);
    }

    public DataStorage(Consumer<Map<String, DataValue>> changeListener) {
        this.changeListener = changeListener == null ? ignored -> { } : changeListener;
    }

    public Optional<DataValue> get(String key) {
        requireValidKey(key);
        return Optional.ofNullable(values.get(key));
    }

    public boolean contains(String key) {
        requireValidKey(key);
        return values.containsKey(key);
    }

    public Map<String, DataValue> values() {
        Map<String, DataValue> cached = immutableValuesCache;
        if (cached == null) {
            cached = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            immutableValuesCache = cached;
        }
        return cached;
    }

    public void set(String key, DataValue value) {
        requireValidKey(key);
        Objects.requireNonNull(value, "value");
        if (value.equals(values.get(key))) return;
        values.put(key, value);
        invalidateValuesCache();
        changeListener.accept(values());
    }

    public boolean set(String key, DataValue value, TransactionContext transaction) {
        requireValidKey(key);
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(transaction, "transaction");
        if (value.equals(values.get(key))) return false;
        updateSnapshots(transaction);
        values.put(key, value);
        invalidateValuesCache();
        return true;
    }

    public Optional<DataValue> remove(String key) {
        requireValidKey(key);
        DataValue previous = values.remove(key);
        if (previous != null) {
            invalidateValuesCache();
            changeListener.accept(values());
        }
        return Optional.ofNullable(previous);
    }

    public Object contentFingerprint() {
        return values();
    }

    @Override
    protected Map<String, DataValue> createSnapshot() {
        return new LinkedHashMap<>(values);
    }

    @Override
    protected void revertToSnapshot(Map<String, DataValue> snapshot) {
        values.clear();
        if (snapshot != null) values.putAll(snapshot);
        invalidateValuesCache();
    }

    @Override
    protected void onRootCommit(Map<String, DataValue> originalState) {
        if (!Objects.equals(originalState, values)) {
            invalidateValuesCache();
            changeListener.accept(values());
        }
    }

    private void invalidateValuesCache() {
        immutableValuesCache = null;
    }

    private static void requireValidKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key must not be null or blank");
    }
}
