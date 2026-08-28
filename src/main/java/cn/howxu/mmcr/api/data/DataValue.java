package cn.howxu.mmcr.api.data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

/** Immutable tagged scalar value for machine data storage.
 * @author howxu <dev@howxu.cn>
 */
public final class DataValue {
    private final DataValueType type;
    private final Object value;

    private DataValue(DataValueType type, Object value) {
        this.type = type;
        this.value = value;
    }

    public static DataValue of(boolean value) {
        return new DataValue(DataValueType.BOOLEAN, value);
    }

    public static DataValue of(String value) {
        return new DataValue(DataValueType.STRING, requireValue(value));
    }

    public static DataValue of(byte value) {
        return new DataValue(DataValueType.BYTE, value);
    }

    public static DataValue of(short value) {
        return new DataValue(DataValueType.SHORT, value);
    }

    public static DataValue of(int value) {
        return new DataValue(DataValueType.INT, value);
    }

    public static DataValue of(long value) {
        return new DataValue(DataValueType.LONG, value);
    }

    public static DataValue of(float value) {
        requireFinite(value);
        return new DataValue(DataValueType.FLOAT, value);
    }

    public static DataValue of(double value) {
        requireFinite(value);
        return new DataValue(DataValueType.DOUBLE, value);
    }

    public static DataValue of(BigInteger value) {
        return new DataValue(DataValueType.BIG_INTEGER, requireValue(value));
    }

    public static DataValue of(BigDecimal value) {
        return new DataValue(DataValueType.BIG_DECIMAL, requireValue(value));
    }

    public DataValueType type() {
        return type;
    }

    public Object value() {
        return value;
    }

    public Optional<Boolean> asBoolean() {
        return as(DataValueType.BOOLEAN, Boolean.class);
    }

    public Optional<String> asString() {
        return as(DataValueType.STRING, String.class);
    }

    public Optional<Byte> asByte() {
        return as(DataValueType.BYTE, Byte.class);
    }

    public Optional<Short> asShort() {
        return as(DataValueType.SHORT, Short.class);
    }

    public Optional<Integer> asInt() {
        return as(DataValueType.INT, Integer.class);
    }

    public Optional<Long> asLong() {
        return as(DataValueType.LONG, Long.class);
    }

    public Optional<Float> asFloat() {
        return as(DataValueType.FLOAT, Float.class);
    }

    public Optional<Double> asDouble() {
        return as(DataValueType.DOUBLE, Double.class);
    }

    public Optional<BigInteger> asBigInteger() {
        return as(DataValueType.BIG_INTEGER, BigInteger.class);
    }

    public Optional<BigDecimal> asBigDecimal() {
        return as(DataValueType.BIG_DECIMAL, BigDecimal.class);
    }

    public boolean booleanValue() {
        return exact(DataValueType.BOOLEAN, Boolean.class);
    }

    public String stringValue() {
        return exact(DataValueType.STRING, String.class);
    }

    public byte byteValue() {
        return exact(DataValueType.BYTE, Byte.class);
    }

    public short shortValue() {
        return exact(DataValueType.SHORT, Short.class);
    }

    public int intValue() {
        return exact(DataValueType.INT, Integer.class);
    }

    public long longValue() {
        return exact(DataValueType.LONG, Long.class);
    }

    public float floatValue() {
        return exact(DataValueType.FLOAT, Float.class);
    }

    public double doubleValue() {
        return exact(DataValueType.DOUBLE, Double.class);
    }

    public BigInteger bigIntegerValue() {
        return exact(DataValueType.BIG_INTEGER, BigInteger.class);
    }

    public BigDecimal bigDecimalValue() {
        return exact(DataValueType.BIG_DECIMAL, BigDecimal.class);
    }

    private <T> Optional<T> as(DataValueType expected, Class<T> valueType) {
        return type == expected ? Optional.of(valueType.cast(value)) : Optional.empty();
    }

    private <T> T exact(DataValueType expected, Class<T> valueType) {
        if (type != expected) throw new IllegalStateException("Expected " + expected + ", got " + type);
        return valueType.cast(value);
    }

    private static <T> T requireValue(T value) {
        if (value == null) throw new IllegalArgumentException("value must not be null");
        return value;
    }

    private static void requireFinite(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("value must be finite");
    }

    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("value must be finite");
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof DataValue other)) return false;
        return type == other.type && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }
}
