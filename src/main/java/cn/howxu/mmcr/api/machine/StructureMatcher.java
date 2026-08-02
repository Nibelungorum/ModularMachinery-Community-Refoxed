package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class StructureMatcher {

    private StructureMatcher() {}

    /**
     * Checks whether the pattern matches the world at ctrlPos with +Z aligned to facing.
     */
    public static boolean matches(BlockArray pattern, Level level, BlockPos ctrlPos, Direction facing) {
        if (pattern.isEmpty()) return false;

        for (var entry : pattern.pattern().entrySet()) {
            BlockPos worldPos = ctrlPos.offset(rotatePos(entry.getKey(), facing));
            if (!entry.getValue().matches(level.getBlockState(worldPos))) return false;
        }
        return true;
    }

    private static BlockPos rotatePos(BlockPos pos, Direction facing) {
        return switch (facing) {
            case NORTH -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
            case SOUTH -> pos;
            case WEST -> new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
            case EAST -> new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
            default -> pos;
        };
    }
}
