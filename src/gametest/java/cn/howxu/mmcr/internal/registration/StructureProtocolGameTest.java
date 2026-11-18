package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.internal.network.PktRuntimeContentPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.PacketFlow;
import net.neoforged.neoforge.network.negotiation.NegotiableNetworkComponent;
import net.neoforged.neoforge.network.negotiation.NetworkComponentNegotiator;

import java.util.List;
import java.util.Optional;

/** Runs the configuration-phase version check before the structure codec is allowed to decode content.
 * @author howxu <dev@howxu.cn>
 */
public final class StructureProtocolGameTest {
    private StructureProtocolGameTest() {}
    public static void negotiate(GameTestHelper helper) {
        var id = PktRuntimeContentPayload.TYPE.id();
        var current = new NegotiableNetworkComponent(id, ModEventRegistration.PAYLOAD_PROTOCOL_VERSION,
                Optional.of(PacketFlow.CLIENTBOUND), false);
        var legacy = new NegotiableNetworkComponent(id, "14", current.flow(), false);
        helper.assertTrue(NetworkComponentNegotiator.negotiate(List.of(current), List.of(current)).success(),
                "Matching structure layouts must negotiate");
        var mismatch = NetworkComponentNegotiator.negotiate(List.of(current), List.of(legacy));
        helper.assertTrue(!mismatch.success() && mismatch.failureReasons().containsKey(id),
                "The old structure layout is rejected for the runtime content channel");
        helper.succeed();
    }
}
