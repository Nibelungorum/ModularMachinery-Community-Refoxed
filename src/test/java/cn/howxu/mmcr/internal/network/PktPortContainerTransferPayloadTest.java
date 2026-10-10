package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.compat.mekanism.MekanismBridgeBootstrap;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;
import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the ordinary port container request codec and safe unavailable bridge.
 *
 * @author howxu <dev@howxu.cn>
 */
class PktPortContainerTransferPayloadTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void packet_preserves_menu_identity_and_second_tank_index() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            var payload = new PktPortContainerTransferPayload(17, 1);
            PktPortContainerTransferPayload.STREAM_CODEC.encode(buffer, payload);
            assertThat(PktPortContainerTransferPayload.STREAM_CODEC.decode(buffer)).isEqualTo(payload);
            assertThat(payload.type()).isSameAs(PktPortContainerTransferPayload.TYPE);
        } finally {
            buffer.release();
        }
    }

    @Test
    void missing_player_or_payload_is_inert() {
        assertThat(PktPortContainerTransferPayload.interactOnServer(null,
                new PktPortContainerTransferPayload(17, 0))).isZero();
        assertThat(PktPortContainerTransferPayload.interactOnServer(null, null)).isZero();
    }

    @Test
    void unavailable_bridge_preserves_fluid_handler_and_rejects_chemical_transfer() {
        MekanismBridge bridge = MekanismBridgeBootstrap.selectForTesting(false);
        var storage = new FluidBucketWrapper(ItemStack.EMPTY);
        assertThat(bridge.manualFluidContainerHandler(storage)).isSameAs(storage);
        assertThat(bridge.manualFluidContainerHandler(null)).isNull();
        assertThat(bridge.transferChemicalContainer(null, null, 0)).isZero();
    }
}
