package cn.howxu.mmcr.internal.autoio;

import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import net.minecraft.core.Direction;

/**
 * Performs one capability-specific Auto IO transfer attempt for a port.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface AutoIOTransferHandler {
    AutoIOCapabilityType type();

    boolean supports(IOPortBlockEntity port);

    boolean hasAdjacentTarget(IOPortBlockEntity port, Direction side);

    boolean transfer(IOPortBlockEntity port, Direction side);
}
