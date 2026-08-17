package cn.howxu.mmcr.internal.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Deduplicates local module connection refresh requests by server level and coupler position.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ModuleConnectionRefreshQueue {
    private static final Map<ServerLevel, Set<BlockPos>> PENDING = new WeakHashMap<>();

    private ModuleConnectionRefreshQueue() {
    }

    public static synchronized void enqueue(ServerLevel level, BlockPos couplerPos) {
        if (level == null || couplerPos == null) return;
        PENDING.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(couplerPos.immutable());
    }

    static synchronized Set<BlockPos> drain(ServerLevel level) {
        Set<BlockPos> pending = PENDING.remove(level);
        return pending == null ? Set.of() : Set.copyOf(pending);
    }

    public static synchronized void discard(ServerLevel level) {
        PENDING.remove(level);
    }
}
