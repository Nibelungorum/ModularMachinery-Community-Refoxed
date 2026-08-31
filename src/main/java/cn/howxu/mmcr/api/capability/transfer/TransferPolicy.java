package cn.howxu.mmcr.api.capability.transfer;

import cn.howxu.mmcr.api.capability.MachineCapability;
import net.minecraft.core.Direction;

/**
 * Capability-level automatic input/output transfer contract.
 *
 * @author howxu <dev@howxu.cn>
 */
@FunctionalInterface
public interface TransferPolicy {
    TransferResult transfer(TransferContext context);

    default boolean hasWork(MachineCapability capability) {
        return true;
    }

    default boolean hasAdjacentTarget(MachineCapability capability, Direction side) {
        return true;
    }

    default TransferResult eject(TransferContext context) {
        return transfer(context.asEjection());
    }
}
