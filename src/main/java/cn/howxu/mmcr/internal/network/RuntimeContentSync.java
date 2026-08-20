package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import cn.howxu.mmcr.internal.sync.RuntimeContentVersion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.Consumer;

import java.util.Objects;

/**
 * Builds and sends server-authoritative runtime content snapshots.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RuntimeContentSync {
    private static Consumer<MinecraftServer> sender = RuntimeContentSync::sendToAllPlayers;

    private RuntimeContentSync() {
    }

    public static RuntimeContentSnapshot createSnapshot() {
        for (;;) {
            long version = RuntimeContentVersion.current();
            var structures = MachineStructureRegistry.effectiveSnapshot();
            var recipes = RecipeRegistry.effectiveSnapshot();
            var controllerSpecs = ControllerSpecSync.createSnapshot();
            var appearances = ControllerSpecSync.createAppearanceSnapshot();
            if (version == RuntimeContentVersion.current()) {
                return new RuntimeContentSnapshot(structures, recipes, controllerSpecs, appearances, version);
            }
        }
    }

    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new PktRuntimeContentPayload(createSnapshot()));
    }

    public static void sendToAll(MinecraftServer server) {
        sender.accept(server);
    }

    private static void sendToAllPlayers(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTo(player);
        }
    }

    public static void setSenderForTesting(Consumer<MinecraftServer> sender) {
        RuntimeContentSync.sender = Objects.requireNonNull(sender);
    }

    public static void resetSenderForTesting() {
        sender = RuntimeContentSync::sendToAllPlayers;
    }
}
