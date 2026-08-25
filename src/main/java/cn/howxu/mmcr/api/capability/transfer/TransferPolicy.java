package cn.howxu.mmcr.api.capability.transfer;

import cn.howxu.mmcr.api.capability.MachineCapability;
import net.minecraft.core.Direction;

/**
 * Capability-level automatic input/output transfer contract.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface TransferPolicy {
    boolean hasWork(MachineCapability capability);

    boolean hasAdjacentTarget(MachineCapability capability, Direction side);

    TransferResult transfer(MachineCapability capability, Direction side);

    TransferResult eject(MachineCapability capability, Direction side);
}
