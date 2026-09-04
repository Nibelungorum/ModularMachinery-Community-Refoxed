package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.internal.item.TerminalData;
import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class PktTerminalStatePayloadTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void round_trip_preserves_canonical_data_and_availability() {
        PktTerminalStatePayload payload = new PktTerminalStatePayload(TerminalData.DEFAULT, true, false,
                "message.mmcr.terminal.storage_unavailable");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);

        PktTerminalStatePayload.STREAM_CODEC.encode(buffer, payload);

        assertThat(PktTerminalStatePayload.STREAM_CODEC.decode(buffer)).isEqualTo(payload);
        buffer.release();
    }
}
