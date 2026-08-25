package cn.howxu.mmcr.internal.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the final port-eject payload boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
class PktEjectPortContentsPayloadTest {
    @Test
    void payload_round_trips_its_target_position() {
        var buffer = Unpooled.buffer();
        PktEjectPortContentsPayload payload = new PktEjectPortContentsPayload(new BlockPos(3, 4, 5));

        PktEjectPortContentsPayload.STREAM_CODEC.encode(buffer, payload);
        PktEjectPortContentsPayload decoded = PktEjectPortContentsPayload.STREAM_CODEC.decode(buffer);

        assertThat(decoded).isEqualTo(payload);
        buffer.release();
    }

    @Test
    void server_handler_rejects_missing_request_context() {
        assertThat(PktEjectPortContentsPayload.ejectOnServer(null, null)).isFalse();
        assertThat(PktEjectPortContentsPayload.ejectOnServer(null,
                new PktEjectPortContentsPayload(BlockPos.ZERO))).isFalse();
    }
}
