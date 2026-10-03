package cn.howxu.mmcr.compat.extendedae_plus;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.upgrades.UpgradeInventories;
import appeng.helpers.InterfaceLogicHost;
import cn.howxu.mmcr.compat.extendedae_plus.loaded.ChannelCardHostSupport;
import com.extendedae_plus.ae.wireless.IWirelessEndpoint;
import com.extendedae_plus.api.bridge.InterfaceWirelessLinkBridge;
import com.extendedae_plus.util.wireless.ChannelCardConnectionController;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** @author howxu <dev@howxu.cn> */
class ChannelCardHostSupportTest {
    @Test
    void connectionMaintenanceDoesNotRequireAnActiveGridTicker() {
        ChannelCardConnectionController controller = ChannelCardHostSupport.createController(emptyHost(), idleNode());
        InterfaceWirelessLinkBridge bridge = bridge(controller);

        assertThat(controller.isInitialized()).isFalse();
        ChannelCardHostSupport.tick(bridge);

        assertThat(controller.isInitialized()).isTrue();
        assertThat(controller.shouldKeepTicking()).isFalse();
    }

    @Test
    void unloadedControllerCannotRestartUntilLoadedAgain() {
        ChannelCardConnectionController controller = ChannelCardHostSupport.createController(emptyHost(), idleNode());
        InterfaceWirelessLinkBridge bridge = bridge(controller);
        ChannelCardHostSupport.tick(bridge);
        controller.onUnloaded();
        // Destroying the node can notify the logic after the host has already closed its controller.
        controller.onNodeChanged();

        ChannelCardHostSupport.tick(bridge);
        assertThat(controller.isInitialized()).isFalse();

        controller.onLoaded();
        assertThat(controller.isInitialized()).isTrue();
    }

    @Test
    void endpointAndFallbackOwnerFollowTheWorkNodeInsteadOfInterfaceLogic() throws Exception {
        UUID owner = UUID.randomUUID();
        IGridNode workNode = (IGridNode) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{IGridNode.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getOwningPlayerProfileId")) return owner;
                    throw new AssertionError("Unexpected work-node access: " + method.getName());
                });
        IManagedGridNode managedNode = (IManagedGridNode) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{IManagedGridNode.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getNode")) return workNode;
                    throw new AssertionError("Unexpected managed-node access: " + method.getName());
                });
        InterfaceLogicHost host = emptyHost();
        ChannelCardConnectionController controller = ChannelCardHostSupport.createController(host, managedNode);

        Supplier<?> endpoints = field(controller, "endpoint");
        Supplier<?> fallbackOwner = field(controller, "fallbackOwner");
        assertThat(((IWirelessEndpoint) endpoints.get()).getGridNode()).isSameAs(workNode);
        assertThat(fallbackOwner.get()).isEqualTo(owner);
    }

    private static InterfaceLogicHost emptyHost() {
        return (InterfaceLogicHost) Proxy.newProxyInstance(InterfaceLogicHost.class.getClassLoader(),
                new Class<?>[]{InterfaceLogicHost.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUpgrades")) return UpgradeInventories.empty();
                    if (method.getName().equals("getBlockEntity")) return null;
                    throw new AssertionError("The UI logic must not be used: " + method.getName());
                });
    }

    private static Supplier<?> field(ChannelCardConnectionController controller, String name) throws Exception {
        Field field = ChannelCardConnectionController.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Supplier<?>) field.get(controller);
    }

    private static IManagedGridNode idleNode() {
        return (IManagedGridNode) Proxy.newProxyInstance(IManagedGridNode.class.getClassLoader(),
                new Class<?>[]{IManagedGridNode.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getNode")) return null;
                    if (method.getName().equals("ifPresent")) return false;
                    throw new AssertionError("Maintenance must not depend on an active AE ticker: " + method.getName());
                });
    }

    private static InterfaceWirelessLinkBridge bridge(ChannelCardConnectionController controller) {
        return new InterfaceWirelessLinkBridge() {
            @Override public ChannelCardConnectionController eap$getChannelCardController() { return controller; }
        };
    }
}
