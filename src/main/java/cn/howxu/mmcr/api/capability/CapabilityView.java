package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.util.IOType;

/**
 * Read-only identity and direction information for a capability.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface CapabilityView {
    CapabilityType type();

    IOType ioType();
}
