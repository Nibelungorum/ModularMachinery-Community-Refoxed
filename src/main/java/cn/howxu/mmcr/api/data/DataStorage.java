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

    private static void requireValidKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key must not be null or blank");
    }
}
