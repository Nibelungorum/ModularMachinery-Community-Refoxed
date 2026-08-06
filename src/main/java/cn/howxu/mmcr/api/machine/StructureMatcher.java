package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Optional;

public final class StructureMatcher {

    private StructureMatcher() {}

    public static boolean matches(BlockArray pattern, Level level, BlockPos ctrlPos, Direction ctrlFacing) {
        return matchesRotated(BlockArrayCache.get(pattern, ctrlFacing), level, ctrlPos);
    }

    public static boolean matchesCompiled(CompiledMachinePattern compiled, Direction facing, Level level, BlockPos ctrlPos) {
        if (!isAreaLoaded(compiled, facing, level, ctrlPos)) return false;
        return matchesRotated(compiled.rotatedPattern(facing), level, ctrlPos);
    }

    public static boolean matchesRotated(BlockArray pattern, Level level, BlockPos ctrlPos) {
        if (pattern.isEmpty()) return false;

        return firstMismatch(pattern, level, ctrlPos).isEmpty();
    }

    public static Optional<Mismatch> firstMismatch(BlockArray pattern, Level level, BlockPos ctrlPos) {
        for (var entry : pattern.pattern().entrySet()) {
            BlockPos worldPos = ctrlPos.offset(entry.getKey());
            BlockState actualState = level.getBlockState(worldPos);
            if (!entry.getValue().matches(actualState)) return Optional.of(new Mismatch(entry.getKey(), worldPos, entry.getValue(), actualState));
        }
        return Optional.empty();
    }

    public static boolean isAreaLoaded(CompiledMachinePattern compiled, Direction facing, Level level, BlockPos ctrlPos) {
        BoundingBox box = compiled.boundingBox(facing);
        if (box == null) return false;
        int minChunkX = (ctrlPos.getX() + box.minX()) >> 4;
        int maxChunkX = (ctrlPos.getX() + box.maxX()) >> 4;
        int minChunkZ = (ctrlPos.getZ() + box.minZ()) >> 4;
        int maxChunkZ = (ctrlPos.getZ() + box.maxZ()) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) return false;
            }
        }
        return true;
    }

    public record Mismatch(BlockPos relativePos, BlockPos worldPos, BlockPredicate expected, BlockState actualState) {
    }
}
