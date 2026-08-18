package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Builds and sends server-authoritative runtime content snapshots.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RuntimeContentSync {
    private RuntimeContentSync() {
    }

    public static RuntimeContentSnapshot createSnapshot() {
        return new RuntimeContentSnapshot(
                MachineStructureRegistry.dynamicSnapshot(),
                RecipeRegistry.dynamicSnapshot(),
                ControllerSpecSync.createSnapshot(),
                ControllerSpecSync.createAppearanceSnapshot(),
                RecipeRegistry.reloadVersion());
    }

    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new PktRuntimeContentPayload(createSnapshot()));
    }

    public static void sendToAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTo(player);
        }
    }
}
