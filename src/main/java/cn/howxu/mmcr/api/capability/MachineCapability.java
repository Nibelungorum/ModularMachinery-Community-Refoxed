package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.util.IOType;

/**
 * Provides access to a machine capability and prepares its operations.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface MachineCapability {
    CapabilityType type();

    IOType ioType();

    CapabilityView view();

    CapabilityOperation prepare(CapabilityRequest request);
}
