package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.data.DataValueType;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the bounded machine data storage payload codec.
 *
 * @author howxu <dev@howxu.cn>
 */
class DataValuePayloadCodecTest {
    @Test
    void roundTripsNestedDataValues() {
        Map<String, DataValue> values = new LinkedHashMap<>();
        values.put("enabled", DataValue.of(true));
        values.put("numbers", DataValue.list(List.of(DataValue.of(1), DataValue.of(2L))));
        values.put("nested", DataValue.map(Map.of("name", DataValue.of("generator"))));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);

        DataValuePayloadCodec.writeMap(buffer, values);

        assertThat(DataValuePayloadCodec.readMap(buffer)).isEqualTo(values);
        buffer.release();
    }

    @Test
    void decoder_rejects_invalid_map_count_before_allocation() {
        RegistryFriendlyByteBuf buffer = buffer();
        buffer.writeVarInt(DataValuePayloadCodec.MAX_ENTRIES + 1);

        assertThatThrownBy(() -> DataValuePayloadCodec.readMap(buffer))
                .isInstanceOf(IllegalArgumentException.class);
        buffer.release();
    }

    @Test
    void decoder_rejects_invalid_type_ordinal() {
        RegistryFriendlyByteBuf buffer = buffer();
        buffer.writeVarInt(1);
        buffer.writeUtf("value", PktMachineStatePayload.MAX_STRING_LENGTH);
        buffer.writeVarInt(DataValueType.values().length);

        assertThatThrownBy(() -> DataValuePayloadCodec.readMap(buffer))
                .isInstanceOf(IllegalArgumentException.class);
        buffer.release();
    }

    @Test
    void decoder_rejects_nested_values_over_the_depth_limit_before_recursion() {
        RegistryFriendlyByteBuf buffer = buffer();
        writeNestedMap(buffer, DataValuePayloadCodec.MAX_DEPTH + 2);

        assertThatThrownBy(() -> DataValuePayloadCodec.readMap(buffer))
                .isInstanceOf(IllegalArgumentException.class);
        buffer.release();
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }

    private static void writeNestedMap(RegistryFriendlyByteBuf buffer, int mapCount) {
        buffer.writeVarInt(1);
        buffer.writeUtf("value", PktMachineStatePayload.MAX_STRING_LENGTH);
        if (mapCount == 1) {
            buffer.writeVarInt(DataValueType.STRING.ordinal());
            buffer.writeUtf("leaf", PktMachineStatePayload.MAX_STRING_LENGTH);
        } else {
            buffer.writeVarInt(DataValueType.MAP.ordinal());
            writeNestedMap(buffer, mapCount - 1);
        }
    }
}
