package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.util.IOType;

/**
 * Describes a requested capability operation.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface CapabilityRequest {
    CapabilityType type();

    IOType ioType();

    long parallelism();
}
