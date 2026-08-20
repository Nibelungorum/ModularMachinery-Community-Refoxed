package cn.howxu.mmcr.internal.network;

import net.minecraft.server.MinecraftServer;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Tracks the current logical server for optional integrations that cannot receive it directly.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RuntimeContentServerBridge {
    private static volatile MinecraftServer currentServer;

    private RuntimeContentServerBridge() {
    }

    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        currentServer = event.getServer();
        RuntimeContentSync.sendToAll(currentServer);
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        if (currentServer == event.getServer()) currentServer = null;
    }

    public static boolean sendToCurrentServer() {
        MinecraftServer server = currentServer;
        if (server == null) return false;
        RuntimeContentSync.sendToAll(server);
        return true;
    }

    public static boolean sendToCurrentServer(RuntimeContentSnapshot snapshot) {
        MinecraftServer server = currentServer;
        if (server == null) return false;
        RuntimeContentSync.sendToAll(server, snapshot);
        return true;
    }

    public static void clearForTesting() {
        currentServer = null;
    }
}
