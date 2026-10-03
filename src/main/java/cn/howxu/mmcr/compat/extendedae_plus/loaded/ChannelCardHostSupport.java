package cn.howxu.mmcr.compat.extendedae_plus.loaded;

import appeng.api.networking.IManagedGridNode;
import appeng.helpers.InterfaceLogicHost;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.PatternLogicKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBaseBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.StockingInterfaceBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import com.extendedae_plus.ae.wireless.endpoint.GenericNodeEndpointImpl;
import com.extendedae_plus.api.bridge.InterfaceWirelessLinkBridge;
import com.extendedae_plus.util.wireless.ChannelCardConnectionController;
import org.jetbrains.annotations.Nullable;

/**
 * Adapts MMCR hosts to EAEP's channel-card controller without depending on AE grid ticker activity.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ChannelCardHostSupport {
    private ChannelCardHostSupport() {
    }

    @Nullable
    public static InterfaceWirelessLinkBridge wirelessLogic(IOPortBlockEntity host) {
        Object logic;
        if (host instanceof InputInterfaceBlockEntity input) {
            logic = input.getInterfaceLogic();
        } else if (host instanceof StockingInterfaceBlockEntity stocking) {
            logic = stocking.getInterfaceLogic();
        } else if (host instanceof OutputInterfaceBaseBlockEntity output) {
            logic = output.getInterfaceLogic();
        } else if (host instanceof PatternInterfaceBlockEntity pattern
                && !((PatternLogicKind) pattern.kind()).readOnlyPatterns()) {
            logic = pattern.getLogic();
        } else {
            return null;
        }
        return logic instanceof InterfaceWirelessLinkBridge bridge ? bridge : null;
    }

    public static void tick(@Nullable InterfaceWirelessLinkBridge bridge) {
        if (bridge != null && bridge.eap$shouldKeepTicking()) bridge.eap$handleDelayedInit();
    }

    public static void loaded(IOPortBlockEntity host) {
        InterfaceWirelessLinkBridge bridge = wirelessLogic(host);
        if (bridge == null) return;
        ChannelCardConnectionController controller = bridge.eap$getChannelCardController();
        if (controller == null) return;
        // unloadFor removes the registry entry, so reused block entities must register again.
        ChannelCardConnectionController.register(host, controller);
        controller.onLoaded();
    }

    public static void unloaded(IOPortBlockEntity host) {
        if (wirelessLogic(host) != null) ChannelCardConnectionController.unloadFor(host);
    }

    public static ChannelCardConnectionController createController(InterfaceLogicHost host, IManagedGridNode node) {
        return new ChannelCardConnectionController(
                host::getUpgrades,
                () -> node.getNode() == null ? null : node.getNode().getOwningPlayerProfileId(),
                () -> new GenericNodeEndpointImpl(host::getBlockEntity, node::getNode),
                host::saveChanges,
                () -> node.ifPresent((grid, gridNode) -> grid.getTickManager().wakeDevice(gridNode)),
                () -> host.getBlockEntity() != null && host.getBlockEntity().getLevel() != null
                        && host.getBlockEntity().getLevel().isClientSide());
    }
}
