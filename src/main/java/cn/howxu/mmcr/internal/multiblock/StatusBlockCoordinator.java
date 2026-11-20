package cn.howxu.mmcr.internal.multiblock;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.registry.ModBlocks;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Server-level, transient ownership of visual structure members. All world access is on the server thread.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StatusBlockCoordinator {
    private static final Map<ServerLevel, StatusBlockCoordinator> LEVELS = new WeakHashMap<>();
    private final Map<BlockPos, Work> controllers = new HashMap<>();
    private final Long2ObjectOpenHashMap<Set<StatusBlockBatch>> owners = new Long2ObjectOpenHashMap<>();
    private final Map<ChunkPos, LongOpenHashSet> deferred = new HashMap<>();
    private final Map<ChunkPos, Integer> readyChunks = new HashMap<>();
    private Set<Work> pending = new LinkedHashSet<>();
    private long lastTick = Long.MIN_VALUE;
    private boolean hasStatusBlocks = tagPopulated();

    private static boolean tagPopulated() {
        return BuiltInRegistries.BLOCK.getTag(ModBlocks.STATUS_BLOCK).filter(tag -> tag.size() > 0).isPresent();
    }

    private static final class Work {
        final BlockPos origin;
        final ArrayDeque<StatusBlockBatch> cleanup = new ArrayDeque<>();
        StatusBlockBatch current;
        boolean dirty;

        Work(BlockPos origin) {
            this.origin = origin.immutable();
        }

        void retire() {
            if (current == null) return;
            current.retire();
            if (current.busy()) cleanup.addLast(current);
            current = null;
        }
    }

    private StatusBlockCoordinator() {}

    public static StatusBlockCoordinator get(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, ignored -> new StatusBlockCoordinator());
    }

    public static void discard(ServerLevel level) {
        LEVELS.remove(level);
    }

    public void formed(BlockPos controller, BlockArray pattern, boolean active, boolean changed) {
        Work work = controllers.computeIfAbsent(controller.immutable(), Work::new);
        if (work.current == null || changed || work.dirty) {
            work.retire();
            work.current = new StatusBlockBatch(controller, pattern, active, hasStatusBlocks);
            work.dirty = false;
        } else {
            work.current.target(active);
        }
        if (work.current.busy() || !work.cleanup.isEmpty()) pending.add(work);
    }

    public void target(BlockPos controller, boolean active) {
        Work work = controllers.get(controller);
        if (work == null || work.current == null) return;
        work.current.target(active);
        if (work.current.busy()) pending.add(work);
    }

    public void remove(BlockPos controller) {
        Work work = controllers.get(controller);
        if (work == null) return;
        work.retire();
        if (work.cleanup.isEmpty()) {
            pending.remove(work);
            controllers.remove(controller);
        } else {
            pending.add(work);
        }
    }

    public void membersChanged(ServerLevel level, BlockPos controller, Set<BlockPos> positions) {
        Work work = controllers.get(controller);
        if (!hasStatusBlocks || work == null || work.current == null || work.dirty) return;
        for (BlockPos pos : positions) {
            if (work.current.pattern.get(pos.subtract(controller)) == null) continue;
            var existing = owners.get(pos.asLong());
            if ((existing != null && existing.contains(work.current)) || eligible(loadedState(level, pos))) {
                work.dirty = true;
                return;
            }
        }
    }

    public void reload() {
        hasStatusBlocks = tagPopulated();
        for (Work work : controllers.values()) {
            if (work.current != null) formed(work.origin, work.current.pattern, work.current.active(), true);
        }
    }

    public void chunkLoaded(ChunkPos chunk) {
        LongOpenHashSet positions = deferred.get(chunk);
        if (positions != null) readyChunks.put(chunk, StatusBlockBatch.visitBudget(positions.size()));
    }

    public void chunkUnloaded(ChunkPos chunk) {
        readyChunks.remove(chunk);
    }

    public void tick(ServerLevel level) {
        if ((pending.isEmpty() && readyChunks.isEmpty()) || level.getGameTime() == lastTick) return;
        lastTick = level.getGameTime();
        if (!pending.isEmpty()) {
            Set<Work> jobs = pending;
            pending = new LinkedHashSet<>();
            for (Work work : jobs) {
                StatusBlockBatch batch = work.current;
                if (batch == null || !batch.busy()) batch = work.cleanup.peekFirst();
                if (batch != null) {
                    StatusBlockBatch generation = batch;
                    batch.step(pos -> claim(level, generation, pos), pos -> visit(level, generation, pos));
                    if (batch.retired() && !batch.busy()) work.cleanup.removeFirstOccurrence(batch);
                }
                if (!work.cleanup.isEmpty() || (work.current != null && work.current.busy())) pending.add(work);
                else if (work.current == null) controllers.remove(work.origin, work);
            }
        }
        var chunks = readyChunks.entrySet().iterator();
        while (chunks.hasNext()) {
            var entry = chunks.next();
            ChunkPos chunk = entry.getKey();
            if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) continue;
            var positions = deferred.get(chunk).iterator();
            for (int i = 0; positions.hasNext() && i < entry.getValue(); i++) {
                long pos = positions.nextLong();
                positions.remove();
                reconcile(level, pos);
            }
            if (!positions.hasNext()) {
                deferred.remove(chunk);
                chunks.remove();
            }
        }
    }

    private boolean claim(ServerLevel level, StatusBlockBatch batch, long pos) {
        if (!eligible(loadedState(level, BlockPos.of(pos)))) return false;
        owners.computeIfAbsent(pos, ignored -> new ReferenceArraySet<>(1)).add(batch);
        return true;
    }

    private void visit(ServerLevel level, StatusBlockBatch batch, long pos) {
        if (batch.retired()) {
            var members = owners.get(pos);
            members.remove(batch);
            if (members.isEmpty()) owners.remove(pos);
        }
        reconcile(level, pos);
    }

    private void reconcile(ServerLevel level, long packed) {
        BlockPos pos = BlockPos.of(packed);
        BlockState state = loadedState(level, pos);
        if (state == null) {
            deferred.computeIfAbsent(new ChunkPos(pos), ignored -> new LongOpenHashSet()).add(packed);
            return;
        }
        if (!eligible(state)) return;
        boolean lit = false;
        var members = owners.get(packed);
        if (members != null) {
            for (var member : members) {
                if (member.active()) {
                    lit = true;
                    break;
                }
            }
        }
        if (state.getValue(BlockStateProperties.LIT) != lit) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private static @Nullable BlockState loadedState(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockState(pos);
    }

    private static boolean eligible(@Nullable BlockState state) {
        return state != null && state.is(ModBlocks.STATUS_BLOCK) && state.hasProperty(BlockStateProperties.LIT);
    }
}
