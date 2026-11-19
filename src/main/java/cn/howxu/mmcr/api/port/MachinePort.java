package cn.howxu.mmcr.api.port;

import cn.howxu.mmcr.internal.port.IOPortKind;

/** Structure identity shared by a port block and its block entity.
 * @author howxu <dev@howxu.cn>
 */
public interface MachinePort {
    IOPortKind kind();
}
