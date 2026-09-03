package cn.howxu.mmcr.api.network;

import cn.howxu.mmcr.api.data.DataValue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable values carried by a machine network request.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RequestBody {
    private final Map<String, DataValue> values;

    private RequestBody(Map<String, DataValue> values) {
        this.values = values;
    }

    public static RequestBody of(Map<String, DataValue> values) {
        Objects.requireNonNull(values, "values");
        Map<String, DataValue> copy = new LinkedHashMap<>();
        for (Map.Entry<String, DataValue> entry : values.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) throw new IllegalArgumentException("request body key must not be blank");
            copy.put(key, Objects.requireNonNull(entry.getValue(), "request body value"));
        }
        return new RequestBody(Collections.unmodifiableMap(copy));
    }

    public Map<String, DataValue> values() {
        return values;
    }

    public Optional<DataValue> get(String key) {
        return Optional.ofNullable(values.get(key));
    }
}
