package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.data.DataValueType;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded stream codec for immutable machine data storage values.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class DataValuePayloadCodec {
    public static final int MAX_ENTRIES = 1024;
    public static final int MAX_DEPTH = 16;

    private DataValuePayloadCodec() {
    }

    public static void writeMap(RegistryFriendlyByteBuf buf, Map<String, DataValue> values) {
        if (buf == null || values == null) throw new IllegalArgumentException("Buffer and values must not be null");
        writeMap(buf, values, 0);
    }

    public static Map<String, DataValue> readMap(RegistryFriendlyByteBuf buf) {
        if (buf == null) throw new IllegalArgumentException("Buffer must not be null");
        return readMap(buf, 0);
    }

    private static void writeMap(RegistryFriendlyByteBuf buf, Map<String, DataValue> values, int depth) {
        validateDepth(depth);
        writeCount(buf, values.size(), "map");
        for (Map.Entry<String, DataValue> entry : values.entrySet()) {
            writeKey(buf, entry.getKey());
            writeValue(buf, entry.getValue(), depth);
        }
    }

    private static Map<String, DataValue> readMap(RegistryFriendlyByteBuf buf, int depth) {
        validateDepth(depth);
        int count = readCount(buf, "map");
        Map<String, DataValue> values = new LinkedHashMap<>(count);
        for (int i = 0; i < count; i++) {
            String key = readKey(buf);
            if (values.containsKey(key)) throw new IllegalArgumentException("Duplicate data value map key: " + key);
            values.put(key, readValue(buf, depth));
        }
        return Collections.unmodifiableMap(values);
    }

    private static void writeList(RegistryFriendlyByteBuf buf, List<DataValue> values, int depth) {
        validateDepth(depth);
        writeCount(buf, values.size(), "list");
        for (DataValue value : values) writeValue(buf, value, depth);
    }

    private static List<DataValue> readList(RegistryFriendlyByteBuf buf, int depth) {
        validateDepth(depth);
        int count = readCount(buf, "list");
        List<DataValue> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(readValue(buf, depth));
        return List.copyOf(values);
    }

    private static void writeValue(RegistryFriendlyByteBuf buf, DataValue value, int depth) {
        if (value == null || value.type() == null) throw new IllegalArgumentException("Data value must not be null");
        DataValueType type = value.type();
        if (type == DataValueType.LIST || type == DataValueType.MAP) validateDepth(depth + 1);
        buf.writeVarInt(type.ordinal());
        switch (type) {
            case BOOLEAN -> buf.writeBoolean(value.booleanValue());
            case STRING -> writeString(buf, value.stringValue());
            case BYTE -> buf.writeByte(value.byteValue());
            case SHORT -> buf.writeShort(value.shortValue());
            case INT -> buf.writeInt(value.intValue());
            case LONG -> buf.writeLong(value.longValue());
            case FLOAT -> buf.writeFloat(value.floatValue());
            case DOUBLE -> buf.writeDouble(value.doubleValue());
            case BIG_INTEGER -> writeString(buf, value.bigIntegerValue().toString());
            case BIG_DECIMAL -> writeString(buf, value.bigDecimalValue().toString());
            case LIST -> writeList(buf, value.asList().orElseThrow(), depth + 1);
            case MAP -> writeMap(buf, value.asMap().orElseThrow(), depth + 1);
        }
    }

    private static DataValue readValue(RegistryFriendlyByteBuf buf, int depth) {
        int ordinal = buf.readVarInt();
        DataValueType[] types = DataValueType.values();
        if (ordinal < 0 || ordinal >= types.length) {
            throw new IllegalArgumentException("Invalid data value type: " + ordinal);
        }
        return switch (types[ordinal]) {
            case BOOLEAN -> DataValue.of(buf.readBoolean());
            case STRING -> DataValue.of(readString(buf));
            case BYTE -> DataValue.of(buf.readByte());
            case SHORT -> DataValue.of(buf.readShort());
            case INT -> DataValue.of(buf.readInt());
            case LONG -> DataValue.of(buf.readLong());
            case FLOAT -> DataValue.of(buf.readFloat());
            case DOUBLE -> DataValue.of(buf.readDouble());
            case BIG_INTEGER -> DataValue.of(new BigInteger(readString(buf)));
            case BIG_DECIMAL -> DataValue.of(new BigDecimal(readString(buf)));
            case LIST -> {
                validateDepth(depth + 1);
                yield DataValue.list(readList(buf, depth + 1));
            }
            case MAP -> {
                validateDepth(depth + 1);
                yield DataValue.map(readMap(buf, depth + 1));
            }
        };
    }

    private static void writeKey(RegistryFriendlyByteBuf buf, String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Data value map key must not be null or blank");
        writeString(buf, key);
    }

    private static String readKey(RegistryFriendlyByteBuf buf) {
        String key = readString(buf);
        if (key.isBlank()) throw new IllegalArgumentException("Data value map key must not be blank");
        return key;
    }

    private static void writeString(RegistryFriendlyByteBuf buf, String value) {
        if (value == null) throw new IllegalArgumentException("Data value string must not be null");
        buf.writeUtf(value, PktMachineStatePayload.MAX_STRING_LENGTH);
    }

    private static String readString(RegistryFriendlyByteBuf buf) {
        return buf.readUtf(PktMachineStatePayload.MAX_STRING_LENGTH);
    }

    private static void writeCount(RegistryFriendlyByteBuf buf, int count, String name) {
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid " + name + " count: " + count);
        buf.writeVarInt(count);
    }

    private static int readCount(RegistryFriendlyByteBuf buf, String name) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid " + name + " count: " + count);
        return count;
    }

    private static void validateDepth(int depth) {
        if (depth < 0 || depth > MAX_DEPTH) throw new IllegalArgumentException("Invalid data value depth: " + depth);
    }
}
