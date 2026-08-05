package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockArrayCache {

    private static final Map<Key, BlockArray> CACHE = new ConcurrentHashMap<>();

    private BlockArrayCache() {
    }

    public static BlockArray get(BlockArray pattern, Direction facing) {
        if (pattern.isEmpty()) return pattern;
        return CACHE.computeIfAbsent(new Key(pattern, facing), BlockArrayCache::rotate);
    }

    /** Test-only helper. Never call from production code. */
    public static void clearForTesting() {
        CACHE.clear();
    }

    private static BlockArray rotate(Key key) {
        Map<BlockPos, BlockPredicate> rotated = new LinkedHashMap<>();
        for (var entry : key.pattern().pattern().entrySet()) {
            rotated.put(BlockRotator.rotateYCCWSouthUntil(entry.getKey(), key.facing()), entry.getValue());
        }
        Map<BlockPos, List<String>> rotatedTags = new LinkedHashMap<>();
        for (var entry : key.pattern().tagsByPosition().entrySet()) {
            rotatedTags.put(BlockRotator.rotateYCCWSouthUntil(entry.getKey(), key.facing()), entry.getValue());
        }
        return new BlockArray(Map.copyOf(rotated), Map.copyOf(rotatedTags));
    }

    private record Key(BlockArray pattern, Direction facing) {
    }
}
