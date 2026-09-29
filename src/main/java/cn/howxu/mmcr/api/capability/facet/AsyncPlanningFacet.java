package cn.howxu.mmcr.api.capability.facet;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityPlanner;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
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

    /**
     * Commits an operation against live storage. This method is main-thread-only.
     *
     * @param operation logical operation produced by {@link AsyncCapabilityPlanner#plan(AsyncCapabilitySnapshot, cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest)}
     * @param transaction transaction used to apply the operation
     * @return the operation result
     */
    public final CapabilityResult commit(AsyncCapabilityOperation operation, TransactionContext transaction) {
        requireServerThread("commit");
        return commitOnServerThread(Objects.requireNonNull(operation, "operation"),
                Objects.requireNonNull(transaction, "transaction"));
    }

    /**
     * Applies an operation without a Transfer transaction for native runtime execution.
     * Compatibility facets keep the transaction overload until Task 7/8.
     */
    public final CapabilityResult commit(AsyncCapabilityOperation operation) {
        requireServerThread("commit");
        if (!supportsNativeExecution()) {
            throw new UnsupportedOperationException("Async operation requires the legacy TransactionContext boundary");
        }
        return commitNativeOnServerThread(Objects.requireNonNull(operation, "operation"));
    }

    /** Whether this facet can apply worker operations through the built-in no-transaction runtime path. */
    public boolean supportsNativeExecution() {
        return false;
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

    /**
     * Commits an operation after {@link #commit(AsyncCapabilityOperation, TransactionContext)} has checked the server thread.
     *
     * @param operation logical operation produced from worker-safe values
     * @param transaction transaction used to apply the operation
     * @return the operation result
     */
    protected abstract CapabilityResult commitOnServerThread(AsyncCapabilityOperation operation,
                                                              TransactionContext transaction);

    /** Applies an operation after the no-transaction entry point checked the server thread. */
    protected CapabilityResult commitNativeOnServerThread(AsyncCapabilityOperation operation) {
        throw new UnsupportedOperationException("Async operation requires the legacy TransactionContext boundary");
    }

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
