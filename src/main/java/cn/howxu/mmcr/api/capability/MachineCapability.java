package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.storage.CapabilityStorage;
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

    /**
     * Returns the capability's backing storage protocol for requirement handlers.
     *
     * @return the backing storage, or {@code null} for non-storage capabilities
     */
    default CapabilityStorage storage() {
        return null;
    }

    CapabilityOperation prepare(CapabilityRequest request);
}
