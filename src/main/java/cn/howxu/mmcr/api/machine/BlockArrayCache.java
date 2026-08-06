package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockArrayCache {

    private static final Map<Key, BlockArray> CACHE = new ConcurrentHashMap<>();

    private BlockArrayCache() {
    }

    public static BlockArray get(BlockArray pattern, Direction facing) {
        return get(pattern, facing, Direction.SOUTH);
    }

    public static BlockArray get(BlockArray pattern, Direction facing, Direction rollFacing) {
        if (pattern.isEmpty()) return pattern;
        Direction normalizedRoll = facing.getAxis().isVertical() ? rollFacing : Direction.SOUTH;
        return CACHE.computeIfAbsent(new Key(pattern, facing, normalizedRoll), BlockArrayCache::rotate);
    }

    public static void buildCache(Collection<Machine> machines) {
        CACHE.clear();
        for (Machine machine : machines) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                get(machine.pattern(), facing);
            }
        }
    }

    public static void clear() {
        CACHE.clear();
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

    private record Key(BlockArray pattern, Direction facing, Direction rollFacing) {
    }
}
