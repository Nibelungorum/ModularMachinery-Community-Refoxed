package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the server-owned recipe-pool selection payload boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
class PktRecipePoolSelectPayloadTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void payload_round_trips_controller_position_and_recipe_pool() {
        PktRecipePoolSelectPayload payload = new PktRecipePoolSelectPayload(
                new BlockPos(3, 4, 5), MMCR.id("pool_b"));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);

        PktRecipePoolSelectPayload.STREAM_CODEC.encode(buffer, payload);

        assertThat(PktRecipePoolSelectPayload.STREAM_CODEC.decode(buffer)).isEqualTo(payload);
        buffer.release();
    }

    @Test
    void server_handler_rejects_a_missing_player_or_payload() {
        assertThat(PktRecipePoolSelectPayload.selectOnServer(null, null)).isFalse();
        assertThat(PktRecipePoolSelectPayload.selectOnServer(null,
                new PktRecipePoolSelectPayload(BlockPos.ZERO, MMCR.id("pool")))).isFalse();
    }
}
