package cn.howxu.mmcr.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Immutable payload for a smart-interface binding or value update.
 *
 * @author howxu <dev@howxu.cn>
 */
public record SmartInterfaceUpdateEventJS(BlockPos interfacePos, BlockPos controllerPos, Identifier machineId,
        String type, @Nullable Float oldValue, @Nullable Float newValue) implements KubeEvent {
    public SmartInterfaceUpdateEventJS {
        interfacePos = interfacePos.immutable();
        controllerPos = controllerPos.immutable();
    }
}
