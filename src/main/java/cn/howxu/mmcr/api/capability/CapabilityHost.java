package cn.howxu.mmcr.api.capability;

import java.util.List;

/**
 * Exposes the capabilities hosted by a machine.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface CapabilityHost {
    List<MachineCapability> capabilities();
}
