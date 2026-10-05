package cn.howxu.mmcr.compat.pneumaticcraft;

import cn.howxu.mmcr.internal.port.IOPortKind;
import java.util.List;
import net.neoforged.bus.api.IEventBus;

/** Optional integration entry point with neutral signatures only.
 * @author howxu <dev@howxu.cn>
 */
public interface PneumaticCraftBridge {
    static PneumaticCraftBridge get() {
        return PneumaticCraftBridgeBootstrap.bridge();
    }

    boolean available();

    default List<IOPortKind> portKinds() {
        return List.of();
    }

    default void registerPorts(IEventBus modBus) {
    }
}
