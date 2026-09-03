package cn.howxu.mmcr.internal.network;

import net.minecraft.server.MinecraftServer;
import cn.howxu.mmcr.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;

/** Holds server-thread-only state for network interface connections.
 * @author howxu <dev@howxu.cn>
 */
public final class NetworkServerState {
    private static final Logger LOG = LoggerFactory.getLogger(NetworkServerState.class);
    private static final Map<MinecraftServer, NetworkServerState> STATES = new IdentityHashMap<>();

    private long nextConnectionSequence = 1L;
    private final ArrayDeque<PendingRequest> pendingRequests = new ArrayDeque<>();
    private long enqueueTick = Long.MIN_VALUE;
    private int enqueuedThisTick;
    private boolean overloaded;
    private long lastDispatchTick = Long.MIN_VALUE;

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

    public void enqueue(PendingRequest request) {
        if (request.enqueueTick() != enqueueTick) {
            enqueueTick = request.enqueueTick();
            enqueuedThisTick = 0;
        }
        pendingRequests.addLast(request);
        enqueuedThisTick++;
        int budget = Config.MAX_REQUESTS_PER_TICK.get();
        if (enqueuedThisTick > budget && !overloaded) {
            overloaded = true;
            LOG.warn("Machine network request queue overloaded: pending={}, enqueuedThisTick={}, budget={}",
                    pendingRequests.size(), enqueuedThisTick, budget);
        }
    }

    public void dispatch(MinecraftServer server, long tick) {
        if (lastDispatchTick == tick) return;
        lastDispatchTick = tick;
        NetworkRequestDispatcher dispatcher = new NetworkRequestDispatcher(server);
        int budget = Config.MAX_REQUESTS_PER_TICK.get();
        for (int count = 0; count < budget && !pendingRequests.isEmpty(); count++) {
            dispatcher.dispatch(pendingRequests.removeFirst());
        }
        if (pendingRequests.size() <= budget) overloaded = false;
    }
}
