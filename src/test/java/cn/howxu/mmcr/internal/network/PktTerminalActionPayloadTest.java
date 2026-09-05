package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.internal.item.TerminalAction;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * @author howxu <dev@howxu.cn>
 */
class PktTerminalActionPayloadTest {
    @Test
    void round_trip_preserves_action_value_and_ids() {
        PktTerminalActionPayload payload = new PktTerminalActionPayload(TerminalAction.SET_LEVEL, 2,
                Identifier.parse("test:coils"), Identifier.parse("test:iron"));
        var buffer = Unpooled.buffer();

        PktTerminalActionPayload.STREAM_CODEC.encode(buffer, payload);

        assertThat(PktTerminalActionPayload.STREAM_CODEC.decode(buffer)).isEqualTo(payload);
        buffer.release();
    }

    @Test
    void rejects_a_value_outside_the_terminal_data_range() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PktTerminalActionPayload(
                TerminalAction.SET_PREVIEW_LAYER, 1 << 25, null, null));
    }
}
