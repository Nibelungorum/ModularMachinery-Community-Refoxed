package cn.howxu.mmcr.internal.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the final server-owned recipe-lock payload boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
class PktRecipeLockPayloadTest {
    @Test
    void payload_round_trips_controller_position_and_thread_index() {
        PktRecipeLockPayload payload = new PktRecipeLockPayload(new BlockPos(3, 4, 5), 7);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);

        PktRecipeLockPayload.STREAM_CODEC.encode(buffer, payload);
        PktRecipeLockPayload decoded = PktRecipeLockPayload.STREAM_CODEC.decode(buffer);

        assertThat(decoded).isEqualTo(payload);
        buffer.release();
    }

    @Test
    void server_handler_rejects_a_missing_player_or_payload() {
        assertThat(PktRecipeLockPayload.toggleOnServer(null, null)).isFalse();
        assertThat(PktRecipeLockPayload.toggleOnServer(null,
                new PktRecipeLockPayload(BlockPos.ZERO, 0))).isFalse();
    }
}
