package cn.howxu.mmcr.client.preview;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Finite lazy chunk source for a virtual structure preview.
 *
 * @author howxu <dev@howxu.cn>
 */
final class PreviewChunkSource extends ChunkSource {
    private final PreviewLevel level;
    private final Map<Long, PreviewChunk> chunks = new ConcurrentHashMap<>();
    private final LevelLightEngine lightEngine;

    PreviewChunkSource(PreviewLevel level) {
        this.level = level;
        this.lightEngine = new LevelLightEngine(this, true, true);
    }

    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean load) {
        if (!level.isPreviewChunk(x, z)) return null;
        long key = ChunkPos.pack(x, z);
        PreviewChunk cached = chunks.get(key);
        if (cached != null) return cached;
        level.assertRenderThread();
        PreviewChunk created = new PreviewChunk(level, new ChunkPos(x, z));
        PreviewChunk existing = chunks.putIfAbsent(key, created);
        return existing == null ? created : existing;
    }

    @Override
    public void tick(BooleanSupplier hasTimeLeft, boolean runAllTasks) {
    }

    @Override
    public String gatherStats() {
        return "Preview chunks: " + chunks.size();
    }

    @Override
    public int getLoadedChunksCount() {
        return chunks.size();
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return lightEngine;
    }

    @Override
    public BlockGetter getLevel() {
        return level;
    }
}
