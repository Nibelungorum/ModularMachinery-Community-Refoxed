package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.ModuleCouplerBlockEntity;

import cn.howxu.mmcr.internal.multiblock.ModuleConnectionCoordinator;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionRefreshQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
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
        enqueueCoupler(event.getLevel(), event.getPos());
        markAdjacentAutoIOPortsDirty(event.getLevel(), event.getPos());
    }

    public static void onBlocksPlaced(BlockEvent.EntityMultiPlaceEvent event) {
        for (var snapshot : event.getReplacedBlockSnapshots()) {
            MachineControllerBlockEntity.markStructureDirty(snapshot.getLevel(), snapshot.getPos());
            enqueueCoupler(snapshot.getLevel(), snapshot.getPos());
            markAdjacentAutoIOPortsDirty(snapshot.getLevel(), snapshot.getPos());
        }
    }

    public static void onFluidPlaced(BlockEvent.FluidPlaceBlockEvent event) {
        MachineControllerBlockEntity.markStructureDirty(event.getLevel(), event.getPos());
        enqueueCoupler(event.getLevel(), event.getPos());
        markAdjacentAutoIOPortsDirty(event.getLevel(), event.getPos());
    }

    public static void onBlockBroken(BreakBlockEvent event) {
        MachineControllerBlockEntity.markStructureDirty(event.getLevel(), event.getPos());
        enqueueCoupler(event.getLevel(), event.getPos());
        markAdjacentAutoIOPortsDirty(event.getLevel(), event.getPos());
    }

    public static void onChunkUnloaded(ChunkEvent.Unload event) {
        MachineControllerBlockEntity.markStructureChunkDirty(event.getLevel(), event.getChunk().getPos());
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            ModuleConnectionCoordinator.enqueueCouplersInChunk(
                    serverLevel, event.getChunk().getPos());
        }
    }

    public static void onChunkLoaded(ChunkEvent.Load event) {
        MachineControllerBlockEntity.markStructureChunkDirty(event.getLevel(), event.getChunk().getPos());
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            ModuleConnectionCoordinator.enqueueCouplersInChunk(
                    serverLevel, event.getChunk().getPos());
        }
    }

    private static void markAdjacentAutoIOPortsDirty(LevelAccessor level, BlockPos pos) {
        if (level.isClientSide()) return;
        if (level.getBlockEntity(pos) instanceof IOPortBlockEntity port) {
            port.markAutoIOCacheDirty();
        }
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(pos.relative(direction)) instanceof IOPortBlockEntity port) {
                port.markAutoIOCacheDirty();
            }
        }
    }

    private static void enqueueCoupler(LevelAccessor level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof ModuleCouplerBlockEntity) {
            ModuleConnectionRefreshQueue.enqueue(serverLevel, pos);
        }
    }
}
