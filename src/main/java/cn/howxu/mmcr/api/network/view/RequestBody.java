package cn.howxu.mmcr.api.network.view;

import cn.howxu.mmcr.api.data.view.DataValue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable values carried by a public machine network request.
 * @author howxu <dev@howxu.cn>
 */
public final class RequestBody {
    private final cn.howxu.mmcr.api.network.RequestBody body;

    private RequestBody(cn.howxu.mmcr.api.network.RequestBody body) { this.body = Objects.requireNonNull(body, "body"); }

    public static RequestBody of(Map<String, DataValue> values) {
        Map<String, cn.howxu.mmcr.api.data.DataValue> converted = new LinkedHashMap<>();
        Objects.requireNonNull(values, "values").forEach((key, value) ->
                converted.put(key, DataValue.toInternal(Objects.requireNonNull(value, "request body value"))));
        return new RequestBody(cn.howxu.mmcr.api.network.RequestBody.of(converted));
    }

    public Map<String, DataValue> values() {
        Map<String, DataValue> converted = new LinkedHashMap<>();
        body.values().forEach((key, value) -> converted.put(key, DataValue.fromInternal(value)));
        return Map.copyOf(converted);
    }
    public Optional<DataValue> get(String key) { return body.get(key).map(DataValue::fromInternal); }

    public static RequestBody fromInternal(cn.howxu.mmcr.api.network.RequestBody body) {
        return new RequestBody(body);
    }

    /** Internal bridge value for MMCR adapters. */
    public Object bridgeValue() {
        return toInternal();
    }

    public cn.howxu.mmcr.api.network.RequestBody toInternal() {
        return body;
    }
}
