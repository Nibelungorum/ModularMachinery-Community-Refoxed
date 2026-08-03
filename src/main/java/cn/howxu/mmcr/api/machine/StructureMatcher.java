package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class StructureMatcher {

    private StructureMatcher() {}

    /**
     * mmce 风格:pattern 相对 controller 固定摆放,controller 的 FACING 只影响方块状态/渲染,
     * 不参与结构坐标旋转,也不应导致已摆好的结构不成型。
     */
    public static boolean matches(BlockArray pattern, Level level, BlockPos ctrlPos, Direction ctrlFacing) {
        if (pattern.isEmpty()) return false;

        for (var entry : pattern.pattern().entrySet()) {
            BlockPos worldPos = ctrlPos.offset(entry.getKey());
            if (!entry.getValue().matches(level.getBlockState(worldPos))) return false;
        }
        return true;
    }
}
