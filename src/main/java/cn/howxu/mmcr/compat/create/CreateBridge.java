package cn.howxu.mmcr.compat.create;

import net.neoforged.bus.api.IEventBus;
import cn.howxu.mmcr.internal.port.IOPortKind;
import java.util.List;

/** Optional Create entry point; signatures deliberately contain no Create classes.
 * @author howxu <dev@howxu.cn>
 */
public interface CreateBridge {
    static CreateBridge get() {
        return CreateBridgeBootstrap.bridge();
    }

    boolean available();

    /** Loaded implementations read Create's configured maximum rotation speed. */
    default double maxRpm() {
        return 256D;
    }

    /** Registers optional native ports with the mod event bus during startup. */
    default void registerPorts(IEventBus modBus) {
    }

    default List<IOPortKind> portKinds() {
        return List.of();
    }
}
