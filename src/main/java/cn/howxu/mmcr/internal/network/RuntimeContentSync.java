package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import cn.howxu.mmcr.internal.registration.RuntimeContentCoordinator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import java.util.Objects;

/**
 * Builds and sends server-authoritative runtime content snapshots.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RuntimeContentSync {
    private static BiConsumer<MinecraftServer, RuntimeContentSnapshot> sender = RuntimeContentSync::sendToAllPlayers;
    private static boolean senderIsDefault = true;

    private RuntimeContentSync() {
    }

    public static RuntimeContentSnapshot createSnapshot() {
        return RuntimeContentCoordinator.createSnapshot();
    }

    public static void sendTo(ServerPlayer player) {
        sendTo(player, createSnapshot());
    }

    public static void sendTo(ServerPlayer player, RuntimeContentSnapshot snapshot) {
        PacketDistributor.sendToPlayer(player, new PktRuntimeContentPayload(snapshot));
    }

    public static void sendToAll(MinecraftServer server) {
        if (!senderIsDefault) {
            sender.accept(server, createSnapshot());
            return;
        }
        sendToAllPlayers(server, createSnapshot());
    }

    public static void sendToAll(MinecraftServer server, RuntimeContentSnapshot snapshot) {
        if (senderIsDefault) {
            sendToAllPlayers(server, snapshot);
        } else {
            sender.accept(server, snapshot);
        }
    }

    private static void sendToAllPlayers(MinecraftServer server) {
        if (server == null) return;
        sendToAllPlayers(server, createSnapshot());
    }

    private static void sendToAllPlayers(MinecraftServer server, RuntimeContentSnapshot snapshot) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTo(player, snapshot);
        }
    }

    public static void setSenderForTesting(Consumer<MinecraftServer> sender) {
        RuntimeContentSync.sender = (server, ignored) -> sender.accept(server);
        senderIsDefault = false;
    }

    public static void setSenderForTesting(BiConsumer<MinecraftServer, RuntimeContentSnapshot> sender) {
        RuntimeContentSync.sender = Objects.requireNonNull(sender);
        senderIsDefault = false;
    }

    public static void resetSenderForTesting() {
        sender = RuntimeContentSync::sendToAllPlayers;
        senderIsDefault = true;
    }
}
