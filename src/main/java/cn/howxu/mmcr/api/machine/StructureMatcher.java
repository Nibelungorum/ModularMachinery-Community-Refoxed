package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class StructureMatcher {

    private StructureMatcher() {}

    public static boolean matches(BlockArray pattern, Level level, BlockPos ctrlPos, Direction ctrlFacing) {
        return matchesRotated(BlockArrayCache.get(pattern, ctrlFacing), level, ctrlPos);
    }

    public static boolean matchesRotated(BlockArray pattern, Level level, BlockPos ctrlPos) {
        if (pattern.isEmpty()) return false;

        for (var entry : pattern.pattern().entrySet()) {
            BlockPos worldPos = ctrlPos.offset(entry.getKey());
            if (!entry.getValue().matches(level.getBlockState(worldPos))) return false;
        }
        return true;
    }
}
