package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.internal.multiblock.ModuleConnectionCoordinator;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionRefreshQueue;
import cn.howxu.mmcr.internal.multiblock.NetworkInterfaceBindingCoordinator;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.network.NetworkServerState;
import cn.howxu.mmcr.compat.appliedenergistics2.AE2Bridge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Drives shared IO resolution on the server-level lifecycle.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SharedIoEvents {

    private SharedIoEvents() {
    }

    public static void onLevelPreTick(LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level) {
            SharedIoCoordinator.get(level).beginExecutionRound(level.getGameTime(),
                    level.tickRateManager().runsNormally());
        }
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            completeLevelTick(level);
        }
    }

    public static void completeLevelTick(ServerLevel level) {
        IOPortBlockEntity.flushStorageChanges(level);
        AE2Bridge.get().tickPendingPatterns(level);
        ModuleConnectionCoordinator.tick(level);
        SharedIoCoordinator sharedIo = SharedIoCoordinator.get(level);
        MachineAsyncCoordinator async = MachineAsyncCoordinator.get(level);
        sharedIo.drainReceiptAcknowledgements(level);
        long gameTime = level.getGameTime();
        sharedIo.beginLevelTick(gameTime);
        async.beginLevelTick(gameTime);
        MachineControllerBlockEntity.runWithBatchedAsyncRuntimeState(level, () -> {
            async.completeTick(() -> sharedIo.resolvePost(level));
            sharedIo.resolvePost(level);
            async.completeTick(() -> sharedIo.resolvePost(level));
        });
        IOPortBlockEntity.flushStorageChanges(level);
        async.pumpReceiptDrains();
        if (level.getServer() != null) NetworkInterfaceBindingCoordinator.heartbeat(level);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        NetworkServerState.get(server).dispatch(server, server.getTickCount());
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            ModuleConnectionRefreshQueue.discard(level);
            StructureClaimRegistry.discard(level);
            MachineControllerBlockEntity.clearFormedControllerIndex(level);
        }
    }
}
