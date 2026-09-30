package cn.howxu.mmcr.api.publicapi.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Public view of a machine's typed data storage.
 * @author howxu <dev@howxu.cn>
 */
public final class DataStorage {
    private final cn.howxu.mmcr.api.data.DataStorage storage;

    private DataStorage(cn.howxu.mmcr.api.data.DataStorage storage) {
        this.storage = storage;
    }

    public static DataStorage view(Object storage) {
        if (!(storage instanceof cn.howxu.mmcr.api.data.DataStorage dataStorage)) {
            throw new IllegalArgumentException("storage must be a machine data storage");
        }
        return new DataStorage(dataStorage);
    }

    public Optional<DataValue> get(String key) {
        return storage.get(key).map(DataValue::fromInternal);
    }

    public boolean contains(String key) { return storage.contains(key); }

    public Map<String, DataValue> values() {
        Map<String, DataValue> values = new LinkedHashMap<>();
        storage.values().forEach((key, value) -> values.put(key, DataValue.fromInternal(value)));
        return Map.copyOf(values);
    }

    public void set(String key, DataValue value) { storage.set(key, DataValue.toInternal(value)); }

    public void set(String key, Object value) { set(key, DataValue.from(value)); }

    public Optional<DataValue> remove(String key) { return storage.remove(key).map(DataValue::fromInternal); }

    /** Internal bridge value for MMCR adapters. */
    public Object bridgeValue() { return storage; }

}
