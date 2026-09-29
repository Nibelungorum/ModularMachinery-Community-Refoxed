package cn.howxu.mmcr.internal.autoio;

import cn.howxu.mmcr.api.capability.MachineCapability;
import java.util.List;
import net.minecraft.core.Direction;

/**
 * Performs one direct native handler transfer for automatic I/O.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface AutoIoHandler {
    boolean hasWork(MachineCapability capability);

    boolean hasAdjacentTarget(MachineCapability capability, Direction side);

    AutoIoResult transfer(MachineCapability capability, Direction side, Object selectedResource, long limit);

    default List<Object> ejectionResources(MachineCapability capability) {
        return List.of();
    }
}
