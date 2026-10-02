package cn.howxu.mmcr.internal.port;

import net.minecraft.core.BlockPos;

/**
 * Common port identity for ordinary and native integration blocks and entities.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface MachinePort {
    IOPortKind kind();

    default void onMachineFormed(BlockPos controllerPos) {}

    default void onMachineUnformed(BlockPos controllerPos) {}
}
