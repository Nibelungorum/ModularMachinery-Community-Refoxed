package cn.howxu.mmcr.api.publicapi.data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable typed value for public machine data storage access.
 * @author howxu <dev@howxu.cn>
 */
public final class DataValue {
    private final Object value;

    private DataValue(Object value) {
        this.value = value;
    }

    public static DataValue of(boolean value) { return new DataValue(value); }
    public static DataValue of(String value) { return new DataValue(Objects.requireNonNull(value, "value")); }
    public static DataValue of(byte value) { return new DataValue(value); }
    public static DataValue of(short value) { return new DataValue(value); }
    public static DataValue of(int value) { return new DataValue(value); }
    public static DataValue of(long value) { return new DataValue(value); }
    public static DataValue of(float value) { return new DataValue(finite(value)); }
    public static DataValue of(double value) { return new DataValue(finite(value)); }
    public static DataValue of(BigInteger value) { return new DataValue(Objects.requireNonNull(value, "value")); }
    public static DataValue of(BigDecimal value) { return new DataValue(Objects.requireNonNull(value, "value")); }

    public static DataValue list(List<DataValue> values) {
        return new DataValue(List.copyOf(Objects.requireNonNull(values, "values")));
    }

    public static DataValue map(Map<String, DataValue> values) {
        Map<String, DataValue> copy = new LinkedHashMap<>();
        Objects.requireNonNull(values, "values").forEach((key, value) -> {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("map key must not be blank");
            copy.put(key, Objects.requireNonNull(value, "map value"));
        });
        return new DataValue(Collections.unmodifiableMap(copy));
    }

    public static DataValue from(Object value) {
        if (value == null) throw new IllegalArgumentException("value must not be null");
        if (value instanceof DataValue dataValue) return dataValue;
        if (value instanceof Map<?, ?> map) {
            Map<String, DataValue> converted = new LinkedHashMap<>();
            map.forEach((key, entry) -> {
                if (!(key instanceof String stringKey) || stringKey.isBlank()) {
                    throw new IllegalArgumentException("map key must be a non-blank string");
                }
                converted.put(stringKey, from(entry));
            });
            return map(converted);
        }
        if (value instanceof Collection<?> collection) {
            return list(collection.stream().map(DataValue::from).toList());
        }
        if (value.getClass().isArray()) {
            List<DataValue> converted = new ArrayList<>(Array.getLength(value));
            for (int index = 0; index < Array.getLength(value); index++) {
                converted.add(from(Array.get(value, index)));
            }
            return list(converted);
        }
        if (value instanceof Boolean booleanValue) return of(booleanValue);
        if (value instanceof String stringValue) return of(stringValue);
        if (value instanceof Byte byteValue) return of(byteValue);
        if (value instanceof Short shortValue) return of(shortValue);
        if (value instanceof Integer integerValue) return of(integerValue);
        if (value instanceof Long longValue) return of(longValue);
        if (value instanceof Float floatValue) return of(floatValue);
        if (value instanceof Double doubleValue) return of(doubleValue);
        if (value instanceof BigInteger bigInteger) return of(bigInteger);
        if (value instanceof BigDecimal bigDecimal) return of(bigDecimal);
        throw new IllegalArgumentException("Unsupported data value: " + value.getClass().getName());
    }

    public Object value() { return value; }

    public DataValueType type() {
        if (value instanceof Boolean) return DataValueType.BOOLEAN;
        if (value instanceof String) return DataValueType.STRING;
        if (value instanceof Byte) return DataValueType.BYTE;
        if (value instanceof Short) return DataValueType.SHORT;
        if (value instanceof Integer) return DataValueType.INT;
        if (value instanceof Long) return DataValueType.LONG;
        if (value instanceof Float) return DataValueType.FLOAT;
        if (value instanceof Double) return DataValueType.DOUBLE;
        if (value instanceof BigInteger) return DataValueType.BIG_INTEGER;
        if (value instanceof BigDecimal) return DataValueType.BIG_DECIMAL;
        if (value instanceof List<?>) return DataValueType.LIST;
        return DataValueType.MAP;
    }
    public Optional<Boolean> asBoolean() { return as(Boolean.class); }
    public Optional<String> asString() { return as(String.class); }
    public Optional<Byte> asByte() { return as(Byte.class); }
    public Optional<Short> asShort() { return as(Short.class); }
    public Optional<Integer> asInt() { return as(Integer.class); }
    public Optional<Long> asLong() { return as(Long.class); }
    public Optional<Float> asFloat() { return as(Float.class); }
    public Optional<Double> asDouble() { return as(Double.class); }
    public Optional<BigInteger> asBigInteger() { return as(BigInteger.class); }
    public Optional<BigDecimal> asBigDecimal() { return as(BigDecimal.class); }

    public boolean booleanValue() { return exact(Boolean.class); }
    public String stringValue() { return exact(String.class); }
    public byte byteValue() { return exact(Byte.class); }
    public short shortValue() { return exact(Short.class); }
    public int intValue() { return exact(Integer.class); }
    public long longValue() { return exact(Long.class); }
    public float floatValue() { return exact(Float.class); }
    public double doubleValue() { return exact(Double.class); }
    public BigInteger bigIntegerValue() { return exact(BigInteger.class); }
    public BigDecimal bigDecimalValue() { return exact(BigDecimal.class); }

    @SuppressWarnings("unchecked")
    public Optional<List<DataValue>> asList() {
        return value instanceof List<?> values ? Optional.of((List<DataValue>) values) : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public Optional<Map<String, DataValue>> asMap() {
        return value instanceof Map<?, ?> values ? Optional.of((Map<String, DataValue>) values) : Optional.empty();
    }

    public static DataValue fromInternal(cn.howxu.mmcr.api.data.DataValue value) {
        Object internal = value.value();
        if (internal instanceof List<?> values) {
            return list(values.stream().map(cn.howxu.mmcr.api.data.DataValue.class::cast)
                    .map(DataValue::fromInternal).toList());
        }
        if (internal instanceof Map<?, ?> values) {
            Map<String, DataValue> converted = new LinkedHashMap<>();
            values.forEach((key, entry) -> converted.put((String) key,
                    fromInternal((cn.howxu.mmcr.api.data.DataValue) entry)));
            return map(converted);
        }
        return new DataValue(internal);
    }

    static cn.howxu.mmcr.api.data.DataValue toInternal(DataValue value) {
        Object publicValue = value.value;
        if (publicValue instanceof Boolean valueBoolean) return cn.howxu.mmcr.api.data.DataValue.of(valueBoolean);
        if (publicValue instanceof String valueString) return cn.howxu.mmcr.api.data.DataValue.of(valueString);
        if (publicValue instanceof Byte valueByte) return cn.howxu.mmcr.api.data.DataValue.of(valueByte);
        if (publicValue instanceof Short valueShort) return cn.howxu.mmcr.api.data.DataValue.of(valueShort);
        if (publicValue instanceof Integer valueInt) return cn.howxu.mmcr.api.data.DataValue.of(valueInt);
        if (publicValue instanceof Long valueLong) return cn.howxu.mmcr.api.data.DataValue.of(valueLong);
        if (publicValue instanceof Float valueFloat) return cn.howxu.mmcr.api.data.DataValue.of(valueFloat);
        if (publicValue instanceof Double valueDouble) return cn.howxu.mmcr.api.data.DataValue.of(valueDouble);
        if (publicValue instanceof BigInteger valueBigInteger) return cn.howxu.mmcr.api.data.DataValue.of(valueBigInteger);
        if (publicValue instanceof BigDecimal valueBigDecimal) return cn.howxu.mmcr.api.data.DataValue.of(valueBigDecimal);
        if (publicValue instanceof List<?> values) return cn.howxu.mmcr.api.data.DataValue.list(values.stream()
                .map(DataValue.class::cast).map(DataValue::toInternal).toList());
        Map<String, cn.howxu.mmcr.api.data.DataValue> values = new LinkedHashMap<>();
        value.asMap().orElseThrow().forEach((key, entry) -> values.put(key, toInternal(entry)));
        return cn.howxu.mmcr.api.data.DataValue.map(values);
    }

    private <T> Optional<T> as(Class<T> type) {
        return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
    }

    private <T> T exact(Class<T> type) {
        if (!type.isInstance(value)) throw new IllegalStateException("Expected " + type.getSimpleName());
        return type.cast(value);
    }

    private static float finite(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("value must be finite");
        return value;
    }

    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("value must be finite");
        return value;
    }

    @Override public boolean equals(Object object) {
        return object instanceof DataValue other && value.equals(other.value);
    }

    @Override public int hashCode() { return value.hashCode(); }
}
