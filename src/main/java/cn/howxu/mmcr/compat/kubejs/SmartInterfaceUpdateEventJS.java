package cn.howxu.mmcr.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * Immutable payload for a smart-interface binding or value update.
 *
 * @author howxu <dev@howxu.cn>
 */
public record SmartInterfaceUpdateEventJS(BlockPos interfacePos, Identifier machineId, String type,
        @Nullable Float oldValue, @Nullable Float newValue, List<BlockPos> controllerPositions) implements KubeEvent {
    public SmartInterfaceUpdateEventJS {
        interfacePos = interfacePos.immutable();
        controllerPositions = controllerPositions == null ? List.of()
                : controllerPositions.stream()
                .map(BlockPos::immutable)
                .sorted(Comparator.comparingLong(BlockPos::asLong))
                .toList();
    }

    public int controllerCount() {
        return controllerPositions.size();
    }

    public @Nullable BlockPos controllerPos() {
        return controllerPositions.isEmpty() ? null : controllerPositions.getFirst();
    }
}
