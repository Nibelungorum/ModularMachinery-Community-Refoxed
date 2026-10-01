package cn.howxu.mmcr.publicapi.data;

import cn.howxu.mmcr.internal.api.facade.data.StorageAdapters;
import org.jetbrains.annotations.ApiStatus;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Immutable typed data value supplied by MMCR; consumers must not implement this view.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface DataKey {
    static DataKey of(boolean value) { return StorageAdapters.key(value); }
    static DataKey of(String value) { return StorageAdapters.key(value); }
    static DataKey of(byte value) { return StorageAdapters.key(value); }
    static DataKey of(short value) { return StorageAdapters.key(value); }
    static DataKey of(int value) { return StorageAdapters.key(value); }
    static DataKey of(long value) { return StorageAdapters.key(value); }
    static DataKey of(float value) { return StorageAdapters.key(value); }
    static DataKey of(double value) { return StorageAdapters.key(value); }
    static DataKey of(BigInteger value) { return StorageAdapters.key(value); }
    static DataKey of(BigDecimal value) { return StorageAdapters.key(value); }
    static DataKey list(List<DataKey> values) { return StorageAdapters.list(values); }
    static DataKey map(Map<String, DataKey> values) { return StorageAdapters.map(values); }
    DataKind type();
    Optional<Boolean> asBoolean();
    Optional<String> asString();
    Optional<Byte> asByte();
    Optional<Short> asShort();
    Optional<Integer> asInt();
    Optional<Long> asLong();
    Optional<Float> asFloat();
    Optional<Double> asDouble();
    Optional<BigInteger> asBigInteger();
    Optional<BigDecimal> asBigDecimal();
    Optional<List<DataKey>> asList();
    Optional<Map<String, DataKey>> asMap();
    boolean booleanValue();
    String stringValue();
    byte byteValue();
    short shortValue();
    int intValue();
    long longValue();
    float floatValue();
    double doubleValue();
    BigInteger bigIntegerValue();
    BigDecimal bigDecimalValue();
}
