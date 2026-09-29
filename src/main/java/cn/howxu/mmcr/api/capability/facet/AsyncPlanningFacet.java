package cn.howxu.mmcr.api.capability.facet;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityPlanner;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Captures capability values on the main thread for planning on a worker thread.
 *
 * @author howxu <dev@howxu.cn>
 */
public abstract class AsyncPlanningFacet implements CapabilityFacet {
    private static final ThreadLocal<Map<Object, AsyncCapabilitySnapshot>> CAPTURE_SCOPE = new ThreadLocal<>();

    /** Identity used to deduplicate snapshots that represent the same physical storage. */
    public Object planningIdentity() {
        return this;
    }

    /**
     * Captures an immutable snapshot. This method and all capability storage access are main-thread-only.
     *
     * @return worker-safe capability values
     */
    public final AsyncCapabilitySnapshot captureSnapshot() {
        requireServerThread("captureSnapshot");
        Map<Object, AsyncCapabilitySnapshot> scope = CAPTURE_SCOPE.get();
        if (scope == null) return captureSnapshotOnServerThread();
        return scope.computeIfAbsent(planningIdentity(), ignored -> captureSnapshotOnServerThread());
    }

    public static CaptureScope beginCaptureScope() {
        Map<Object, AsyncCapabilitySnapshot> previous = CAPTURE_SCOPE.get();
        CAPTURE_SCOPE.set(new IdentityHashMap<>());
        return () -> {
            if (previous == null) CAPTURE_SCOPE.remove();
            else CAPTURE_SCOPE.set(previous);
        };
    }

    /**
     * Exports a worker-safe planner. This method is main-thread-only.
     *
     * @return pure planner that accepts and returns only asynchronous value objects
     */
    public final AsyncCapabilityPlanner workerPlanner() {
        requireServerThread("workerPlanner");
        return workerPlannerOnServerThread();
    }

    /** Applies an operation against live native storage on the server thread. */
    public final CapabilityResult commit(AsyncCapabilityOperation operation) {
        requireServerThread("commit");
        return commitNativeOnServerThread(Objects.requireNonNull(operation, "operation"));
    }

    /**
     * Captures live capability values after {@link #captureSnapshot()} has checked the server thread.
     *
     * @return worker-safe capability values
     */
    protected abstract AsyncCapabilitySnapshot captureSnapshotOnServerThread();

    /**
     * Exports the pure worker planner after {@link #workerPlanner()} has checked the server thread.
     *
     * @return pure planner that must not retain this facet or live capability state
     */
    protected abstract AsyncCapabilityPlanner workerPlannerOnServerThread();

    /** Applies an operation after the server-thread boundary has been checked. */
    protected abstract CapabilityResult commitNativeOnServerThread(AsyncCapabilityOperation operation);

    private static void requireServerThread(String operation) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException(operation + " requires the server thread");
        }
    }

    @FunctionalInterface
    public interface CaptureScope extends AutoCloseable {
        @Override
        void close();
    }
}
