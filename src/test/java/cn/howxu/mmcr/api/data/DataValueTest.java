package cn.howxu.mmcr.api.data;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies recursive data value semantics.
 * @author howxu <dev@howxu.cn>
 */
class DataValueTest {
    @Test
    void recursiveValuesPreserveMixedTypesAndEquality() {
        DataValue nested = DataValue.map(Map.of(
                "name", DataValue.of("machine"),
                "values", DataValue.list(List.of(DataValue.of(3), DataValue.map(Map.of("ok", DataValue.of(true)))))));

        assertEquals(DataValueType.MAP, nested.type());
        assertEquals("machine", nested.asMap().orElseThrow().get("name").stringValue());
        assertEquals(nested, DataValue.map(Map.of(
                "name", DataValue.of("machine"),
                "values", DataValue.list(List.of(DataValue.of(3), DataValue.map(Map.of("ok", DataValue.of(true))))))));
    }

    @Test
    void recursiveCollectionsAreImmutableAndValidateMapKeys() {
        DataValue list = DataValue.list(List.of(DataValue.of(1)));
        DataValue map = DataValue.map(Map.of("list", list));

        assertThrows(UnsupportedOperationException.class,
                () -> list.asList().orElseThrow().add(DataValue.of(2)));
        assertThrows(UnsupportedOperationException.class,
                () -> map.asMap().orElseThrow().put("other", DataValue.of(2)));
        assertThrows(IllegalArgumentException.class, () -> DataValue.map(Map.of(" ", DataValue.of(1))));
        assertThrows(IllegalArgumentException.class, () -> DataValue.list(Arrays.asList(DataValue.of(1), null)));
    }

    @Test
    void scalarAccessorsDoNotAcceptRecursiveValues() {
        DataValue value = DataValue.list(List.of(DataValue.of(1)));

        assertTrue(value.asInt().isEmpty());
        assertThrows(IllegalStateException.class, value::intValue);
    }
}
