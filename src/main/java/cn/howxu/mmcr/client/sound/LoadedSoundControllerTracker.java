package cn.howxu.mmcr.client.sound;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks sound candidates in the current client world on the client main thread.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class LoadedSoundControllerTracker {
    private final Map<BlockPos, MachineControllerBlockEntity> controllers = new HashMap<>();

    public void loaded(MachineControllerBlockEntity controller) {
        controllers.put(controller.getBlockPos().immutable(), controller);
    }

    public void removed(MachineControllerBlockEntity controller) {
        controllers.remove(controller.getBlockPos(), controller);
    }

    public void unloadChunk(ChunkPos pos) {
        controllers.entrySet().removeIf(entry -> new ChunkPos(entry.getKey()).equals(pos));
    }

    void prune(ClientChunkCache chunks) {
        // ClientChunkCache radius changes can evict chunks without any unload callback.
        controllers.entrySet().removeIf(entry -> {
            ChunkPos pos = new ChunkPos(entry.getKey());
            var chunk = chunks.getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
            return entry.getValue().isRemoved() || chunk == null
                    || chunk.getBlockEntities().get(entry.getKey()) != entry.getValue();
        });
    }

    public void clear() {
        controllers.clear();
    }

    public Collection<MachineControllerBlockEntity> controllers() {
        return Collections.unmodifiableCollection(controllers.values());
    }
}
