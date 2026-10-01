package cn.howxu.mmcr.publicapi.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** MMCR-provided read-only controller view. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface MachineView {
    @Nullable ResourceLocation machineId();
    BlockPos controllerPos();
    boolean formed();
    long countStructureBlocks(Block block);
    long countStructureBlocks(String blockId);
}
