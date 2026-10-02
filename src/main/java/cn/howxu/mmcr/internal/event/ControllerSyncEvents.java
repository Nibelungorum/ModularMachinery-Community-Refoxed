package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;

/**
 * Completes controller baselines only after the client receives the owning chunk.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ControllerSyncEvents {
    private ControllerSyncEvents() { }

    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        for (var blockEntity : event.getChunk().getBlockEntities().values()) {
            if (blockEntity instanceof MachineControllerBlockEntity controller) {
                controller.sendClientStateBaseline(event.getPlayer());
            }
        }
    }

    public static void onChunkUnWatch(ChunkWatchEvent.UnWatch event) {
        var pos = event.getPos();
        var chunk = event.getLevel().getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk == null) return;
        for (var blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof MachineControllerBlockEntity controller) {
                controller.forgetClientStateBaseline(event.getPlayer());
            }
        }
    }
}
