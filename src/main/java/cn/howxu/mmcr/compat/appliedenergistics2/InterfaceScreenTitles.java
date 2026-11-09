package cn.howxu.mmcr.compat.appliedenergistics2;

import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Nameable;

/**
 * Resolves AE2 interface screen titles for MMCR port hosts.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class InterfaceScreenTitles {
    private InterfaceScreenTitles() {
    }

    public static Component titleFor(Object host, Component fallback) {
        if (host instanceof Nameable nameable && nameable.hasCustomName()) return nameable.getCustomName();
        return host instanceof IOPortBlockEntity port
                ? Component.translatable("container.mmcr." + port.kind().id())
                : fallback;
    }
}
