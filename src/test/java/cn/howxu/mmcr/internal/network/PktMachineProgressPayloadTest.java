package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the progress-only wire layout without output registries or codecs.
 *
 * @author howxu <dev@howxu.cn>
 */
class PktMachineProgressPayloadTest {
    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void codec_writes_only_position_recipe_and_varint_progress_and_round_trips() {
        for (int tick : new int[]{0, 127, 128, Integer.MAX_VALUE}) {
            var payload = new PktMachineProgressPayload(new BlockPos(-17, 255, 42), "mmcr:recipe", tick, Integer.MAX_VALUE);
            RegistryFriendlyByteBuf actual = buffer();
            RegistryFriendlyByteBuf expected = buffer();
            try {
                expected.writeBlockPos(payload.pos());
                expected.writeUtf(payload.recipeName(), PktMachineStatePayload.maxStringLength());
                expected.writeVarInt(tick);
                expected.writeVarInt(Integer.MAX_VALUE);
                PktMachineProgressPayload.STREAM_CODEC.encode(actual, payload);
                assertThat(actual).isEqualTo(expected);
                assertThat(PktMachineProgressPayload.STREAM_CODEC.decode(actual)).isEqualTo(payload);
                assertThat(actual.readableBytes()).isZero();
            } finally {
                actual.release();
                expected.release();
            }
        }
    }

    @Test
    void constructor_and_decoder_reject_invalid_progress_and_oversized_recipe() {
        assertThatThrownBy(() -> new PktMachineProgressPayload(BlockPos.ZERO, "", -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PktMachineProgressPayload(BlockPos.ZERO, "", 11, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PktMachineProgressPayload(BlockPos.ZERO, "", 0, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PktMachineProgressPayload(BlockPos.ZERO,
                "a".repeat(PktMachineStatePayload.maxStringLength() + 1), 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            buffer.writeBlockPos(BlockPos.ZERO);
            buffer.writeUtf("mmcr:recipe");
            buffer.writeVarInt(5);
            buffer.writeVarInt(4);
            assertThatThrownBy(() -> PktMachineProgressPayload.STREAM_CODEC.decode(buffer))
                    .isInstanceOf(IllegalArgumentException.class);
            buffer.clear();
            buffer.writeBlockPos(BlockPos.ZERO);
            buffer.writeUtf("a".repeat(PktMachineStatePayload.maxStringLength() + 1));
            assertThatThrownBy(() -> PktMachineProgressPayload.STREAM_CODEC.decode(buffer))
                    .isInstanceOf(io.netty.handler.codec.DecoderException.class);
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }
}
