package cn.howxu.mmcr.internal.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Deduplicates module connection refresh requests by server level and coupler or chunk position.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ModuleConnectionRefreshQueue {
    private static final Map<ServerLevel, Set<BlockPos>> PENDING = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<ChunkPos>> PENDING_CHUNKS = new WeakHashMap<>();

    private ModuleConnectionRefreshQueue() {
    }

    public static synchronized void enqueue(ServerLevel level, BlockPos couplerPos) {
        if (level == null || couplerPos == null) return;
        PENDING.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(couplerPos.immutable());
    }

    public static synchronized void enqueueChunk(ServerLevel level, ChunkPos chunkPos) {
        if (level == null || chunkPos == null) return;
        PENDING_CHUNKS.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(chunkPos);
    }

    static synchronized Set<ChunkPos> drainChunks(ServerLevel level) {
        Set<ChunkPos> pending = PENDING_CHUNKS.remove(level);
        return pending == null ? Set.of() : Set.copyOf(pending);
    }

    static synchronized Set<BlockPos> drain(ServerLevel level) {
        Set<BlockPos> pending = PENDING.remove(level);
        return pending == null ? Set.of() : Set.copyOf(pending);
    }

    public static synchronized void discard(ServerLevel level) {
        PENDING.remove(level);
        PENDING_CHUNKS.remove(level);
    }
}
