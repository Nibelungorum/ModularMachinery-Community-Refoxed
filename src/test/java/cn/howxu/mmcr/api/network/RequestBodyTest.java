package cn.howxu.mmcr.api.network;

import cn.howxu.mmcr.api.data.DataValue;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies request-body value semantics.
 *
 * @author howxu <dev@howxu.cn>
 */
class RequestBodyTest {
    @Test
    void bodyIsSnapshottedOnce() {
        Map<String, DataValue> source = new LinkedHashMap<>();
        source.put("value", DataValue.of(1));

        RequestBody body = RequestBody.of(source);
        source.put("value", DataValue.of(2));

        assertEquals(1, body.get("value").orElseThrow().intValue());
        assertThrows(UnsupportedOperationException.class,
                () -> body.values().put("other", DataValue.of(3)));
    }

    @Test
    void bodyRejectsInvalidEntries() {
        assertThrows(IllegalArgumentException.class, () -> RequestBody.of(Map.of("", DataValue.of(1))));
        Map<String, DataValue> nullValue = new LinkedHashMap<>();
        nullValue.put("value", null);
        assertThrows(NullPointerException.class, () -> RequestBody.of(nullValue));
    }
}
