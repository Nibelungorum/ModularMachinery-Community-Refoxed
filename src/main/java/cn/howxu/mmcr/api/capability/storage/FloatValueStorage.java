package cn.howxu.mmcr.api.capability.storage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Named float values used by smart-interface capabilities.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FloatValueStorage implements CapabilityStorage {
    private final Map<String, Float> values = new LinkedHashMap<>();
    private final Consumer<Map<String, Float>> rootCommitListener;

    public FloatValueStorage() {
        this(null);
    }

    public FloatValueStorage(Consumer<Map<String, Float>> rootCommitListener) {
        this.rootCommitListener = rootCommitListener == null ? ignored -> {} : rootCommitListener;
    }

    public Optional<Float> value(String key) {
        return Optional.ofNullable(values.get(key));
    }

    public Map<String, Float> values() {
        return snapshot(values);
    }

    public void replace(Map<String, Float> nextValues) {
        Map<String, Float> replacement = nextValues == null ? Map.of() : snapshot(nextValues);
        if (values.equals(replacement)) return;
        values.clear();
        values.putAll(replacement);
        rootCommitListener.accept(snapshot(values));
    }

    @Override
    public Object contentFingerprint() {
        return snapshot(values);
    }

    public void set(String key, float value) {
        if (key == null || key.isBlank() || !Float.isFinite(value)) {
            throw new IllegalArgumentException("invalid value");
        }
        values.put(key, value);
        rootCommitListener.accept(snapshot(values));
    }

    public boolean setExisting(String key, float value) {
        if (!values.containsKey(key) || !Float.isFinite(value)) return false;
        if (Float.compare(values.get(key), value) == 0) return true;
        values.put(key, value);
        rootCommitListener.accept(snapshot(values));
        return true;
    }

    private static Map<String, Float> snapshot(Map<String, Float> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
