package cn.howxu.mmcr.internal.api.facade.data;

import cn.howxu.mmcr.api.data.view.DataRepository;
import cn.howxu.mmcr.api.data.view.DataRepositoryContext;
import cn.howxu.mmcr.api.data.view.DataRepositoryRequest;
import cn.howxu.mmcr.api.data.view.DataReservation;
import cn.howxu.mmcr.api.data.view.DataStorage;
import cn.howxu.mmcr.api.data.view.DataValue;
import cn.howxu.mmcr.api.data.view.DataValueType;
import cn.howxu.mmcr.publicapi.data.DataKey;
import cn.howxu.mmcr.publicapi.data.DataKind;
import cn.howxu.mmcr.publicapi.data.DataStore;
import cn.howxu.mmcr.publicapi.data.Repository;
import cn.howxu.mmcr.publicapi.data.RepositoryContext;
import net.minecraft.resources.ResourceLocation;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Typed internal data boundary; delegates validation and transactional writes to core views.
 * @author howxu <dev@howxu.cn>
 */
public final class StorageAdapters {
    private StorageAdapters() {}
    public static DataStore wrap(DataStorage value) { return new StoreAdapter(Objects.requireNonNull(value)); }
    public static DataStorage unwrap(DataStore value) { return ((StoreAdapter) value).delegate; }
    public static DataStore.Transaction wrap(DataStorage.Transaction value) { return new TransactionAdapter(value); }
    public static DataStore.Transaction wrap(cn.howxu.mmcr.api.data.DataStorage.Transaction value) { return wrap(DataStorage.Transaction.view(value)); }
    public static DataStorage.Transaction unwrap(DataStore.Transaction value) { return ((TransactionAdapter) value).delegate; }
    public static DataKey wrap(DataValue value) { return new KeyAdapter(Objects.requireNonNull(value)); }
    public static DataValue unwrap(DataKey value) { return ((KeyAdapter) value).delegate; }
    public static DataKind wrap(DataValueType kind) {
        return switch (kind) {
            case BOOLEAN -> DataKind.BOOLEAN; case STRING -> DataKind.STRING;
            case BYTE -> DataKind.BYTE; case SHORT -> DataKind.SHORT; case INT -> DataKind.INT;
            case LONG -> DataKind.LONG; case FLOAT -> DataKind.FLOAT; case DOUBLE -> DataKind.DOUBLE;
            case BIG_INTEGER -> DataKind.BIG_INTEGER; case BIG_DECIMAL -> DataKind.BIG_DECIMAL;
            case LIST -> DataKind.LIST; case MAP -> DataKind.MAP;
        };
    }
    public static DataValueType unwrap(DataKind kind) {
        return switch (kind) {
            case BOOLEAN -> DataValueType.BOOLEAN; case STRING -> DataValueType.STRING;
            case BYTE -> DataValueType.BYTE; case SHORT -> DataValueType.SHORT; case INT -> DataValueType.INT;
            case LONG -> DataValueType.LONG; case FLOAT -> DataValueType.FLOAT; case DOUBLE -> DataValueType.DOUBLE;
            case BIG_INTEGER -> DataValueType.BIG_INTEGER; case BIG_DECIMAL -> DataValueType.BIG_DECIMAL;
            case LIST -> DataValueType.LIST; case MAP -> DataValueType.MAP;
        };
    }
    public static DataKey key(boolean v) { return wrap(DataValue.of(v)); }
    public static DataKey key(String v) { return wrap(DataValue.of(v)); }
    public static DataKey key(byte v) { return wrap(DataValue.of(v)); }
    public static DataKey key(short v) { return wrap(DataValue.of(v)); }
    public static DataKey key(int v) { return wrap(DataValue.of(v)); }
    public static DataKey key(long v) { return wrap(DataValue.of(v)); }
    public static DataKey key(float v) { return wrap(DataValue.of(v)); }
    public static DataKey key(double v) { return wrap(DataValue.of(v)); }
    public static DataKey key(BigInteger v) { return wrap(DataValue.of(v)); }
    public static DataKey key(BigDecimal v) { return wrap(DataValue.of(v)); }
    public static DataKey list(List<DataKey> v) { return wrap(DataValue.list(v.stream().map(StorageAdapters::unwrap).toList())); }
    public static DataKey map(Map<String, DataKey> v) {
        Map<String, DataValue> converted = new LinkedHashMap<>();
        v.forEach((key, value) -> converted.put(key, unwrap(value)));
        return wrap(DataValue.map(converted));
    }
    public static Map<String, DataKey> values(Map<String, DataValue> v) {
        Map<String, DataKey> converted = new LinkedHashMap<>();
        v.forEach((key, value) -> converted.put(key, wrap(value)));
        return Map.copyOf(converted);
    }
    public static DataRepository toCore(Repository repository) {
        Objects.requireNonNull(repository, "repository");
        return new DataRepository() {
            public ResourceLocation id() { return repository.id(); }
            public DataRepositoryRequest request(DataRepositoryContext context) {
                var result = repository.request(new RepositoryContext(context.machineId(), context.controllerPos(),
                        context.key(), wrap(context.requestedType())));
                Optional<DataReservation> reservation = result.reservation().map(value -> new DataReservation() {
                    public boolean commit() { return value.commit(); }
                    public void cancel() { value.cancel(); }
                });
                return new DataRepositoryRequest(result.repositoryId(), result.controllerPos(), result.key(),
                        unwrap(result.requestedType()), unwrap(result.requestedValue()), reservation);
            }
        };
    }
    private record TransactionAdapter(DataStorage.Transaction delegate) implements DataStore.Transaction {
        private TransactionAdapter { Objects.requireNonNull(delegate); }
    }
    private record StoreAdapter(DataStorage delegate) implements DataStore {
        public Optional<DataKey> get(String key) { return delegate.get(key).map(StorageAdapters::wrap); }
        public boolean contains(String key) { return delegate.contains(key); }
        public Map<String, DataKey> values() { return StorageAdapters.values(delegate.values()); }
        public void set(String key, DataKey value) { delegate.set(key, unwrap(value)); }
        public boolean set(String key, DataKey value, Transaction transaction) { return delegate.set(key, unwrap(value), unwrap(transaction)); }
        public Optional<DataKey> remove(String key) { return delegate.remove(key).map(StorageAdapters::wrap); }
    }
    private record KeyAdapter(DataValue delegate) implements DataKey {
        public DataKind type() { return wrap(delegate.type()); }
        public Optional<Boolean> asBoolean() { return delegate.asBoolean(); }
        public Optional<String> asString() { return delegate.asString(); }
        public Optional<Byte> asByte() { return delegate.asByte(); }
        public Optional<Short> asShort() { return delegate.asShort(); }
        public Optional<Integer> asInt() { return delegate.asInt(); }
        public Optional<Long> asLong() { return delegate.asLong(); }
        public Optional<Float> asFloat() { return delegate.asFloat(); }
        public Optional<Double> asDouble() { return delegate.asDouble(); }
        public Optional<BigInteger> asBigInteger() { return delegate.asBigInteger(); }
        public Optional<BigDecimal> asBigDecimal() { return delegate.asBigDecimal(); }
        public Optional<List<DataKey>> asList() { return delegate.asList().map(v -> v.stream().map(StorageAdapters::wrap).toList()); }
        public Optional<Map<String, DataKey>> asMap() { return delegate.asMap().map(StorageAdapters::values); }
        public boolean booleanValue() { return delegate.booleanValue(); }
        public String stringValue() { return delegate.stringValue(); }
        public byte byteValue() { return delegate.byteValue(); }
        public short shortValue() { return delegate.shortValue(); }
        public int intValue() { return delegate.intValue(); }
        public long longValue() { return delegate.longValue(); }
        public float floatValue() { return delegate.floatValue(); }
        public double doubleValue() { return delegate.doubleValue(); }
        public BigInteger bigIntegerValue() { return delegate.bigIntegerValue(); }
        public BigDecimal bigDecimalValue() { return delegate.bigDecimalValue(); }
    }
}
