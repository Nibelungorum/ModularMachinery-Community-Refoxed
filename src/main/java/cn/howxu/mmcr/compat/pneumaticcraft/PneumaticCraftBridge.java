package cn.howxu.mmcr.compat.pneumaticcraft;

import cn.howxu.mmcr.internal.port.IOPortKind;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

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

    default void registerMenus(BiConsumer<String, Supplier<? extends MenuType<?>>> registrar) {
    }
}
