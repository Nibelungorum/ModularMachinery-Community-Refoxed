package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.api.port.MachinePort;
import net.minecraft.world.level.block.Block;

/**
 * Identifies multiblock interfaces for structure previews and material selection.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructureInterfaceBlocks {
    private StructureInterfaceBlocks() {}

    public static boolean isInterfaceBlock(Block block) {
        return block instanceof ModuleCouplerBlock || blockPriority(block) != 0;
    }

    public static int blockPriority(Block block) {
        if (block instanceof FactorySchedulerBlock) return 4;
        if (block instanceof ParallelControllerBlock) return 3;
        if (block instanceof SmartInterfaceBlock) return 2;
        if (block instanceof MachinePort || block instanceof DataStorageBlock
                || block instanceof NetworkInterfaceBlock || block instanceof UpgradeBusBlock) return 1;
        return 0;
    }
}
