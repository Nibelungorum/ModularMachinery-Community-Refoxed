package cn.howxu.mmcr.api.capability.type;

import cn.howxu.mmcr.api.capability.MachineCapability;

/**
 * Creates a capability for a host and its current creation context.
 *
 * @author howxu <dev@howxu.cn>
 */
@FunctionalInterface
public interface CapabilityFactory {
    MachineCapability create(CapabilityCreationContext context);
}
