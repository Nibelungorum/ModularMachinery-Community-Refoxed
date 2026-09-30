package cn.howxu.mmcr.api.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/** Ordered, typed machine data storage.
 * @author howxu <dev@howxu.cn>
 */
public final class DataStorage {
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

    public boolean set(String key, DataValue value, Transaction transaction) {
        requireValidKey(key);
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(transaction, "transaction");
        return transaction.set(this, key, value);
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

    private void invalidateValuesCache() {
        immutableValuesCache = null;
    }

    private void apply(Map<String, DataValue> replacement) {
        if (values.equals(replacement)) return;
        values.clear();
        values.putAll(replacement);
        invalidateValuesCache();
        changeListener.accept(values());
    }

    private static void requireValidKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key must not be null or blank");
    }

    /** Delays data writes until the associated machine I/O plan commits successfully. */
    public static final class Transaction {
        private final Map<DataStorage, Map<String, DataValue>> staged = new LinkedHashMap<>();
        private boolean closed;

        public static Transaction create() {
            return new Transaction();
        }

        private boolean set(DataStorage storage, String key, DataValue value) {
            if (closed) throw new IllegalStateException("Data transaction is closed");
            Map<String, DataValue> values = staged.computeIfAbsent(storage,
                    ignored -> new LinkedHashMap<>(storage.values));
            if (value.equals(values.get(key))) return false;
            values.put(key, value);
            return true;
        }

        public void commit() {
            if (closed) throw new IllegalStateException("Data transaction is closed");
            closed = true;
            staged.forEach(DataStorage::apply);
        }
    }
}
