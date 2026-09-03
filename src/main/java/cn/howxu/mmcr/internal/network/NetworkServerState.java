package cn.howxu.mmcr.internal.network;

import net.minecraft.server.MinecraftServer;

import java.util.IdentityHashMap;
import java.util.Map;

/** Holds server-thread-only state for network interface connections.
 * @author howxu <dev@howxu.cn>
 */
public final class NetworkServerState {
    private static final Map<MinecraftServer, NetworkServerState> STATES = new IdentityHashMap<>();

    private long nextConnectionSequence = 1L;

    private NetworkServerState() {
    }

    public static NetworkServerState get(MinecraftServer server) {
        return STATES.computeIfAbsent(server, ignored -> new NetworkServerState());
    }

    public static void discard(MinecraftServer server) {
        STATES.remove(server);
    }

    public long nextConnectionSequence() {
        return nextConnectionSequence++;
    }
}
