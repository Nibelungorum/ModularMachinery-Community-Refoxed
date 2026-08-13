package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class BlockArrayCache {

    private static volatile Map<Key, BlockArray> CACHE = Map.of();

    private BlockArrayCache() {
    }

    public static BlockArray get(BlockArray pattern, Direction facing) {
        return get(pattern, facing, Direction.SOUTH);
    }

    public static synchronized BlockArray get(BlockArray pattern, Direction facing, Direction rollFacing) {
        if (pattern.isEmpty()) return pattern;
        Direction normalizedRoll = BlockRotator.normalizedRoll(facing, rollFacing);
        Key key = new Key(pattern, facing, normalizedRoll);
        BlockArray cached = CACHE.get(key);
        if (cached != null) return cached;
        BlockArray rotated = rotate(key);
        Map<Key, BlockArray> replacement = new LinkedHashMap<>(CACHE);
        replacement.put(key, rotated);
        CACHE = Map.copyOf(replacement);
        return rotated;
    }

    public static void buildCache(Collection<Machine> machines) {
        CACHE = buildCacheSnapshot(machines);
    }

    static Map<Key, BlockArray> buildCacheSnapshot(Collection<Machine> machines) {
        Map<Key, BlockArray> replacement = new LinkedHashMap<>();
        for (Machine machine : machines) {
            for (MachineStructureStage stage : machine.structureStages()) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    add(replacement, stage.pattern(), facing);
                    for (DynamicPatternSpec dynamicPattern : stage.dynamicPatterns()) {
                        add(replacement, dynamicPattern.startPattern(), facing);
                        if (dynamicPattern.endPattern() != null) add(replacement, dynamicPattern.endPattern(), facing);
                    }
                }
            }
        }
        return Map.copyOf(replacement);
    }

    static void installCache(Map<Key, BlockArray> cache) {
        CACHE = Map.copyOf(cache);
    }

    static BlockArray get(Map<Key, BlockArray> cache, BlockArray pattern, Direction facing) {
        Key key = new Key(pattern, facing, Direction.SOUTH);
        BlockArray cached = cache.get(key);
        return cached != null ? cached : rotate(key);
    }

    public static void clear() {
        CACHE = Map.of();
    }

    /** Test-only helper. Never call from production code. */
    public static void clearForTesting() {
        clear();
    }

    private static BlockArray rotate(Key key) {
        Map<BlockPos, BlockPredicate> rotated = new LinkedHashMap<>();
        for (var entry : key.pattern().pattern().entrySet()) {
            rotated.put(BlockRotator.rotateSouthTo(entry.getKey(), key.facing(), key.rollFacing()), entry.getValue());
        }
        Map<BlockPos, List<String>> rotatedTags = new LinkedHashMap<>();
        for (var entry : key.pattern().tagsByPosition().entrySet()) {
            rotatedTags.put(BlockRotator.rotateSouthTo(entry.getKey(), key.facing(), key.rollFacing()), entry.getValue());
        }
        return new BlockArray(Map.copyOf(rotated), Map.copyOf(rotatedTags));
    }

    private static void add(Map<Key, BlockArray> cache, BlockArray pattern, Direction facing) {
        Key key = new Key(pattern, facing, Direction.SOUTH);
        cache.put(key, rotate(key));
    }

    record Key(BlockArray pattern, Direction facing, Direction rollFacing) {
    }
}
