package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Structure-check request wire and owned-position boundary tests.
 * @author howxu <dev@howxu.cn>
 */
class PktControllerStructureCheckPayloadTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void request_round_trips_an_owned_controller_position() {
        BlockPos.MutableBlockPos source = new BlockPos.MutableBlockPos(3, 4, 5);
        var request = new PktControllerStructureCheckPayload(source);
        source.set(6, 7, 8);
        var buffer = Unpooled.buffer();
        try {
            PktControllerStructureCheckPayload.STREAM_CODEC.encode(buffer, request);
            var decoded = PktControllerStructureCheckPayload.STREAM_CODEC.decode(buffer);
            assertThat(decoded).isEqualTo(request);
            assertThat(decoded.controllerPos()).isEqualTo(new BlockPos(3, 4, 5));
            assertThat(buffer.isReadable()).isFalse();
        } finally {
            buffer.release();
        }
    }

    @Test
    void absent_target_or_player_cannot_request_a_check() {
        assertThatThrownBy(() -> new PktControllerStructureCheckPayload(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(PktControllerStructureCheckPayload.checkOnServer(null, null)).isFalse();
        assertThat(PktControllerStructureCheckPayload.checkOnServer(null,
                new PktControllerStructureCheckPayload(BlockPos.ZERO))).isFalse();
    }
}
