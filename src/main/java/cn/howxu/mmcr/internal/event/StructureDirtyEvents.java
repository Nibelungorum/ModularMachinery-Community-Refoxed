package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Marks formed machine controllers dirty when their cached structure area changes.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructureDirtyEvents {

    private StructureDirtyEvents() {
    }

    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        MachineControllerBlockEntity.markStructureDirty(event.getLevel(), event.getPos());
    }

    public static void onBlocksPlaced(BlockEvent.EntityMultiPlaceEvent event) {
        for (var snapshot : event.getReplacedBlockSnapshots()) {
            MachineControllerBlockEntity.markStructureDirty(snapshot.getLevel(), snapshot.getPos());
        }
    }

    public static void onFluidPlaced(BlockEvent.FluidPlaceBlockEvent event) {
        MachineControllerBlockEntity.markStructureDirty(event.getLevel(), event.getPos());
    }

    public static void onBlockBroken(BreakBlockEvent event) {
        MachineControllerBlockEntity.markStructureDirty(event.getLevel(), event.getPos());
    }

    public static void onChunkUnloaded(ChunkEvent.Unload event) {
        MachineControllerBlockEntity.markStructureChunkDirty(event.getLevel(), event.getChunk().getPos());
    }
}
