package cn.howxu.mmcr.api.capability.storage;

import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Transactional named float values used by smart-interface capabilities.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FloatValueStorage extends SnapshotJournal<Map<String, Float>> implements CapabilityStorage {
    private final Map<String, Float> values = new LinkedHashMap<>();

    public Optional<Float> value(String key) {
        return Optional.ofNullable(values.get(key));
    }

    public Map<String, Float> values() {
        return Map.copyOf(values);
    }

    @Override
    public Object contentFingerprint() {
        return Map.copyOf(values);
    }

    public void set(String key, float value) {
        if (key == null || key.isBlank() || !Float.isFinite(value)) {
            throw new IllegalArgumentException("invalid value");
        }
        values.put(key, value);
    }

    public boolean set(String key, float value, TransactionContext transaction) {
        if (!values.containsKey(key) || !Float.isFinite(value)) return false;
        updateSnapshots(transaction);
        values.put(key, value);
        return true;
    }

    @Override
    protected Map<String, Float> createSnapshot() {
        return new LinkedHashMap<>(values);
    }

    @Override
    protected void revertToSnapshot(Map<String, Float> snapshot) {
        values.clear();
        if (snapshot != null) values.putAll(snapshot);
    }

    @Override
    protected void onRootCommit(Map<String, Float> originalState) {
    }
}
