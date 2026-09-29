package cn.howxu.mmcr.api.data;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies typed values and transactional data storage semantics.
 * @author howxu <dev@howxu.cn>
 */
class DataStorageTest {
    @Test
    void retains_exact_scalar_types_and_decimal_scale() {
        assertThat(DataValue.of(true).type()).isEqualTo(DataValueType.BOOLEAN);
        assertThat(DataValue.of("value").asString()).contains("value");
        assertThat(DataValue.of((byte) 1).asByte()).contains((byte) 1);
        assertThat(DataValue.of((short) 2).asShort()).contains((short) 2);
        assertThat(DataValue.of(3).asInt()).contains(3);
        assertThat(DataValue.of(4L).asLong()).contains(4L);
        assertThat(DataValue.of(1.5F).asFloat()).contains(1.5F);
        assertThat(DataValue.of(2.5D).asDouble()).contains(2.5D);
        assertThat(DataValue.of(new BigInteger("12345678901234567890")).asBigInteger())
                .contains(new BigInteger("12345678901234567890"));
        assertThat(DataValue.of(new BigDecimal("1.2300")).asBigDecimal())
                .contains(new BigDecimal("1.2300"));
    }

    @Test
    void reports_type_mismatch_without_numeric_coercion() {
        DataValue value = DataValue.of(1L);

        assertThat(value.asInt()).isEmpty();
        assertThatThrownBy(value::intValue).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void direct_changes_notify_and_map_views_are_immutable() {
        List<Map<String, DataValue>> changes = new ArrayList<>();
        DataStorage storage = new DataStorage(changes::add);

        storage.set("answer", DataValue.of(42));
        assertThat(storage.contains("answer")).isTrue();
        assertThat(storage.get("answer")).contains(DataValue.of(42));
        assertThat(changes).hasSize(1);
        assertThatThrownBy(() -> storage.values().clear()).isInstanceOf(UnsupportedOperationException.class);

        assertThat(storage.remove("answer")).contains(DataValue.of(42));
        assertThat(changes).hasSize(2);
    }

    @Test
    void values_cache_invalidates_after_mutations() {
        List<Map<String, DataValue>> notifications = new ArrayList<>();
        DataStorage storage = new DataStorage(notifications::add);
        Map<String, DataValue> initial = storage.values();

        storage.set("payload", DataValue.map(Map.of("ready", DataValue.of(true))));
        assertThat(initial).doesNotContainKey("payload");
        assertThat(storage.get("payload")).contains(DataValue.map(Map.of("ready", DataValue.of(true))));
        assertThat(notifications).hasSize(1);

        storage.set("payload", DataValue.map(Map.of("ready", DataValue.of(true))));
        assertThat(notifications).hasSize(1);
        storage.remove("payload");
        assertThat(storage.values()).isEmpty();
        storage.remove("payload");
        assertThat(notifications).hasSize(2);

        assertThatThrownBy(initial::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejects_invalid_keys_values_and_non_finite_numbers() {
        DataStorage storage = new DataStorage(null);
        assertThatThrownBy(() -> storage.get(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.set(" ", DataValue.of(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DataValue.of(Float.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DataValue.of(Double.POSITIVE_INFINITY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DataValue.of((String) null)).isInstanceOf(IllegalArgumentException.class);
    }
}
