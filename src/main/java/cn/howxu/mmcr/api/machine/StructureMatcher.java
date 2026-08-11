package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.api.machine.level.LevelMismatch;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;

public final class StructureMatcher {

    private StructureMatcher() {}

    public static boolean matches(BlockArray pattern, Level level, BlockPos ctrlPos, Direction ctrlFacing) {
        return matches(pattern, level, ctrlPos, ctrlFacing, Map.of());
    }

    public static boolean matches(BlockArray pattern, Level level, BlockPos ctrlPos, Direction ctrlFacing,
                                   Map<BlockPos, List<SingleBlockModifierReplacement>> replacements) {
        return matchesRotated(BlockArrayCache.get(pattern, ctrlFacing), level, ctrlPos,
                rotateReplacements(replacements, ctrlFacing));
    }

    private static Map<BlockPos, List<SingleBlockModifierReplacement>> rotateReplacements(
            Map<BlockPos, List<SingleBlockModifierReplacement>> replacements, Direction facing) {
        if (replacements == null || replacements.isEmpty()) return Map.of();
        Map<BlockPos, List<SingleBlockModifierReplacement>> rotated = new LinkedHashMap<>();
        for (var entry : replacements.entrySet()) {
            BlockPos rotatedPos = BlockRotator.rotateSouthTo(entry.getKey(), facing);
            rotated.put(rotatedPos, entry.getValue().stream()
                    .map(replacement -> replacement.copyAt(rotatedPos))
                    .toList());
        }
        return Map.copyOf(rotated);
    }

    public static boolean matchesCompiled(CompiledMachinePattern compiled, Direction facing, Level level, BlockPos ctrlPos) {
        return matchesCompiled(compiled, facing, Direction.SOUTH, level, ctrlPos);
    }

    public static boolean matchesCompiled(CompiledMachinePattern compiled, Direction facing, Direction rollFacing, Level level, BlockPos ctrlPos) {
        if (!isAreaLoaded(compiled, facing, level, ctrlPos)) return false;
        return matchesRotated(compiled.rotatedPattern(facing), level, ctrlPos,
                compiled.modifierReplacements(facing, rollFacing));
    }

    public static boolean matchesRotated(BlockArray pattern, Level level, BlockPos ctrlPos) {
        return matchesRotated(pattern, level, ctrlPos, Map.of());
    }

    public static boolean matchesRotated(BlockArray pattern, Level level, BlockPos ctrlPos,
                                         Map<BlockPos, List<SingleBlockModifierReplacement>> replacements) {
        if (pattern.isEmpty()) return false;

        return firstMismatch(pattern, level, ctrlPos, replacements).isEmpty();
    }

    public static Optional<Mismatch> firstMismatch(BlockArray pattern, Level level, BlockPos ctrlPos) {
        return firstMismatch(pattern, level, ctrlPos, Map.of());
    }

    public static Optional<Mismatch> firstMismatch(BlockArray pattern, Level level, BlockPos ctrlPos,
                                                   Map<BlockPos, List<SingleBlockModifierReplacement>> replacements) {
        for (var entry : pattern.pattern().entrySet()) {
            BlockPos worldPos = ctrlPos.offset(entry.getKey());
            BlockState actualState = level.getBlockState(worldPos);
            if (!matchesEntry(entry.getValue(), actualState, replacements.getOrDefault(entry.getKey(), List.of()))) {
                return Optional.of(new Mismatch(entry.getKey(), worldPos, entry.getValue(), actualState));
            }
        }
        return Optional.empty();
    }

    public static LevelResolution resolveLevels(Map<BlockPos, Identifier> levelSlots, Level level, BlockPos ctrlPos) {
        Map<Identifier, MachineLevel> foundLevels = new LinkedHashMap<>();
        for (var entry : levelSlots.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<BlockPos, Identifier> entry) -> entry.getKey().getX())
                        .thenComparingInt(entry -> entry.getKey().getY())
                        .thenComparingInt(entry -> entry.getKey().getZ()))
                .toList()) {
            BlockPos worldPos = ctrlPos.offset(entry.getKey());
            MachineLevel actual = MachineLevelRegistry.findLevel(level.getBlockState(worldPos))
                    .orElseThrow(() -> new IllegalStateException("Level slot did not resolve: " + worldPos));
            MachineLevel expected = foundLevels.putIfAbsent(entry.getValue(), actual);
            if (expected != null && !expected.equals(actual)) {
                return new LevelResolution(Map.of(), new LevelMismatch(entry.getValue(), expected, actual, worldPos));
            }
        }
        return new LevelResolution(Map.copyOf(foundLevels), null);
    }

    private static boolean matchesEntry(
            BlockPredicate expected,
            BlockState actual,
            List<SingleBlockModifierReplacement> replacements) {
        if (expected.matches(actual)) return true;
        for (SingleBlockModifierReplacement replacement : replacements) {
            if (replacement.getReplacement().matches(actual)) return true;
        }
        return false;
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

    public record LevelResolution(Map<Identifier, MachineLevel> foundLevels, LevelMismatch mismatch) {
        public LevelResolution {
            foundLevels = Map.copyOf(foundLevels);
        }
    }
}
