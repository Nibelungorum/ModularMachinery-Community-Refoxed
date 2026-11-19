package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.internal.port.MachinePort;
import net.minecraft.world.level.block.Block;

/**
 * Identifies multiblock interfaces for structure previews and material selection.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructureInterfaceBlocks {
    private StructureInterfaceBlocks() {}

    public static boolean isInterfaceBlock(Block block) {
        return block instanceof MachinePort || block instanceof ParallelControllerBlock
                || block instanceof FactorySchedulerBlock || block instanceof SmartInterfaceBlock
                || block instanceof DataStorageBlock || block instanceof NetworkInterfaceBlock
                || block instanceof UpgradeBusBlock || block instanceof ModuleCouplerBlock;
    }
}
