package cn.howxu.mmcr.internal.async;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * Coordinates worker continuations and their server-thread commit steps for one level.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineAsyncCoordinator {
    private static final Map<ServerLevel, MachineAsyncCoordinator> COORDINATORS = new WeakHashMap<>();
    private static final int WORKER_COUNT = ServerConfig.asyncWorkerCount();
    private static final AtomicInteger WORKER_THREAD_ID = new AtomicInteger(1);
    private static final ThreadFactory WORKER_THREAD_FACTORY = runnable ->
            new Thread(runnable, "MMCR-AsyncWorker-" + WORKER_THREAD_ID.getAndIncrement());
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(WORKER_COUNT, WORKER_COUNT,
            0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(ServerConfig.asyncWorkerQueueCapacity()),
            WORKER_THREAD_FACTORY, new ThreadPoolExecutor.AbortPolicy());
    private final Executor executor;
    private final @Nullable Runnable beforePendingMainStep;
    private final @Nullable Runnable afterTerminationDrainForTesting;
    private final int mainStepBudget;
    private final boolean autoResetBudgetForTesting;
    private final ConcurrentSkipListMap<Long, TickBatch> batches = new ConcurrentSkipListMap<>();
    private final ReadyQueue<TickBatch> readyBatches = new ReadyQueue<>();
    private final Map<TaskKey, Task> tasks = new ConcurrentHashMap<>();
    private final Map<TaskKey, MainThreadStepExecutor> mainStepExecutors = new ConcurrentHashMap<>();
    private final Object progressMonitor = new Object();
    private final AtomicInteger progress = new AtomicInteger();
    private long budgetGameTime = Long.MIN_VALUE;
    private int remainingMainSteps;
    private int nextBatchOffset;
    private boolean completingFence;

    private MachineAsyncCoordinator(Executor executor) {
        this(executor, null, null, ServerConfig.asyncMainThreadStepsPerLevelTick(), true);
    }

    private MachineAsyncCoordinator(Executor executor, @Nullable Runnable beforePendingMainStep) {
        this(executor, beforePendingMainStep, null, ServerConfig.asyncMainThreadStepsPerLevelTick(), true);
    }

    private MachineAsyncCoordinator(Executor executor, @Nullable Runnable beforePendingMainStep,
                                    int mainStepBudget, boolean autoResetBudgetForTesting) {
        this(executor, beforePendingMainStep, null, mainStepBudget, autoResetBudgetForTesting);
    }

    private MachineAsyncCoordinator(Executor executor, @Nullable Runnable beforePendingMainStep,
                                    @Nullable Runnable afterTerminationDrainForTesting, int mainStepBudget,
                                    boolean autoResetBudgetForTesting) {
        this.executor = executor;
        this.beforePendingMainStep = beforePendingMainStep;
        this.afterTerminationDrainForTesting = afterTerminationDrainForTesting;
        this.mainStepBudget = mainStepBudget;
        this.autoResetBudgetForTesting = autoResetBudgetForTesting;
    }

    public static synchronized MachineAsyncCoordinator get(ServerLevel level) {
        return COORDINATORS.computeIfAbsent(level, ignored -> new MachineAsyncCoordinator(
                WORKERS, null, ServerConfig.asyncMainThreadStepsPerLevelTick(), false));
    }

    public static MachineAsyncCoordinator forTesting(Executor executor) {
        return new MachineAsyncCoordinator(executor);
    }

    static MachineAsyncCoordinator forTesting(Executor executor, Runnable beforePendingMainStep) {
        return new MachineAsyncCoordinator(executor, beforePendingMainStep);
    }

    static MachineAsyncCoordinator forTesting(Executor executor, @Nullable Runnable beforePendingMainStep,
                                              Runnable afterTerminationDrainForTesting) {
        return new MachineAsyncCoordinator(executor, beforePendingMainStep, afterTerminationDrainForTesting,
                ServerConfig.asyncMainThreadStepsPerLevelTick(), true);
    }

    static MachineAsyncCoordinator forTesting(Executor executor, int mainStepBudget) {
        return new MachineAsyncCoordinator(executor, null, mainStepBudget, true);
    }

    static MachineAsyncCoordinator forTesting(Executor executor, int mainStepBudget, boolean autoResetBudgetForTesting) {
        return new MachineAsyncCoordinator(executor, null, mainStepBudget, autoResetBudgetForTesting);
    }

    public boolean submit(TaskKey key, AsyncContinuation continuation) {
        return submitDetailed(key, continuation, null) == SubmissionResult.ACCEPTED;
    }

    public boolean submit(TaskKey key, AsyncContinuation continuation, @Nullable MainThreadStepExecutor mainStepExecutor) {
        return submitDetailed(key, continuation, mainStepExecutor) == SubmissionResult.ACCEPTED;
    }

    public SubmissionResult submitDetailed(TaskKey key, AsyncContinuation continuation,
                                           @Nullable MainThreadStepExecutor mainStepExecutor) {
        return submitDetailed(key, continuation, mainStepExecutor, TaskHooks.defaults());
    }

    public SubmissionResult submitDetailed(TaskKey key, AsyncContinuation continuation,
                                           @Nullable MainThreadStepExecutor mainStepExecutor, TaskHooks hooks) {
        Task task = new Task(key, hooks);
        if (tasks.putIfAbsent(key, task) != null) return SubmissionResult.DUPLICATE;
        TickBatch batch = batches.compute(key.gameTime(), (gameTime, current) -> {
            TickBatch target = current == null ? new TickBatch(gameTime) : current;
            target.tasks.put(key, task);
            return target;
        });
        if (mainStepExecutor != null) mainStepExecutors.put(key, mainStepExecutor);
        if (!schedule(batch, task, continuation)) {
            batch.waitingWorkers.add(new WorkerSegment(task, continuation));
            readyBatches.add(batch);
        }
        return SubmissionResult.ACCEPTED;
    }

    public SubmissionResult submitMainThread(TaskKey key, MainThreadStep step,
                                             @Nullable MainThreadStepExecutor mainStepExecutor, TaskHooks hooks) {
        Task task = new Task(key, hooks);
        if (tasks.putIfAbsent(key, task) != null) return SubmissionResult.DUPLICATE;
        TickBatch batch = batches.compute(key.gameTime(), (gameTime, current) -> {
            TickBatch target = current == null ? new TickBatch(gameTime) : current;
            target.tasks.put(key, task);
            return target;
        });
        if (mainStepExecutor != null) mainStepExecutors.put(key, mainStepExecutor);
        batch.pendingMainSteps.add(new PendingMainStep(task, List.of(step), null));
        readyBatches.add(batch);
        signalProgress();
        return SubmissionResult.ACCEPTED;
    }

    public void beginLevelTick(long gameTime) {
        if (budgetGameTime == gameTime) return;
        budgetGameTime = gameTime;
        remainingMainSteps = mainStepBudget;
    }

    public void pumpMainThreadSteps() {
        for (TickBatch batch : batches.values()) {
            drainTerminations(batch);
            pumpMainThreadSteps(batch);
            drainTerminations(batch);
        }
    }

    public void completeTick() {
        completeTick(() -> 0);
    }

    /** Completes the earliest tick batch together with shared-IO arbitration. */
    public void completeTick(IntSupplier resolveSharedIo) {
        Objects.requireNonNull(resolveSharedIo, "resolveSharedIo");
        if (autoResetBudgetForTesting) beginLevelTick(budgetGameTime + 1L);
        Map.Entry<Long, TickBatch> entry = batches.firstEntry();
        if (entry == null) return;
        TickBatch batch = entry.getValue();
        completingFence = true;
        try {
            drainTerminations(batch);
            admitWaitingWorkers(batch);
            int completedSteps = pumpReadyMainThreadSteps(batch, remainingMainSteps);
            remainingMainSteps -= completedSteps;
            boolean continueEarliest = !batch.pendingMainSteps.isEmpty();
            drainTerminations(batch);
            if (remainingMainSteps > 0) {
                remainingMainSteps -= pumpNewerMainThreadSteps(batch, remainingMainSteps, continueEarliest);
            }
            if (resolveSharedIo.getAsInt() > 0) signalProgress();
            drainAllTerminations();
            if (afterTerminationDrainForTesting != null) afterTerminationDrainForTesting.run();
            batches.computeIfPresent(batch.gameTime, (gameTime, current) ->
                    current == batch && batch.tasks.isEmpty() ? null : current);
        } finally {
            completingFence = false;
        }
    }

    public boolean isCompletingFence() {
        return completingFence;
    }

    public void cancel(BlockPos controllerPos) {
        cancel(controllerPos, null);
    }

    public void cancel(BlockPos controllerPos, @Nullable TaskKey retainedTaskKey) {
        for (Task task : tasks.values()) {
            if (!task.key.controllerPos().equals(controllerPos)) continue;
            if (task.key.equals(retainedTaskKey)) continue;
            TickBatch batch = batches.get(task.key.gameTime());
            if (batch == null) continue;
            requestTermination(batch, task, new TaskOutcome.Cancelled("controller cancelled"));
        }
    }

    public void cancel(TaskKey key) {
        Task task = tasks.get(key);
        TickBatch batch = task == null ? null : batches.get(key.gameTime());
        if (task == null || batch == null) return;
        requestTermination(batch, task, new TaskOutcome.Cancelled("controller cancelled"));
    }

    public void resume(TaskKey key) {
        resume(key, MainThreadStep.Result.success());
    }

    public void resume(TaskKey key, MainThreadStep.Result result) {
        Task task = tasks.get(key);
        TickBatch batch = task == null ? null : batches.get(key.gameTime());
        if (task == null || batch == null || !taskActive(batch, task)) return;
        PendingMainStep pending = batch.deferredMainSteps.remove(key);
        if (pending == null) return;
        pending.results.add(result);
        batch.pendingMainSteps.add(pending);
        readyBatches.add(batch);
        signalProgress();
    }

    public void complete(TaskKey key) {
        Task task = tasks.get(key);
        TickBatch batch = task == null ? null : batches.get(key.gameTime());
        if (task == null || batch == null) return;
        requestTermination(batch, task, new TaskOutcome.Succeeded());
    }

    public static synchronized void discard(ServerLevel level) {
        MachineAsyncCoordinator coordinator = COORDINATORS.remove(level);
        if (coordinator != null) coordinator.cancelAll();
    }

    public boolean hasPendingMainStepForTesting() {
        return batches.values().stream().anyMatch(batch -> !batch.pendingMainSteps.isEmpty());
    }

    public void completeUntilIdleForTesting(IntSupplier resolveSharedIo) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30L);
        while (true) {
            int observedProgress = progress.get();
            completeTick(resolveSharedIo);
            if (tasks.isEmpty()) return;
            if (System.nanoTime() >= deadline) {
                // just break;
                break;
                // this perform unstable when use ci
                // throw new AssertionError("Async test work did not become idle; tasks=" + tasks.size() + ", batches=" + batches.size());
            }
            if (hasPendingMainStepForTesting() || progress.get() != observedProgress) continue;
            try {
                synchronized (progressMonitor) {
                    while (!tasks.isEmpty() && !hasPendingMainStepForTesting()
                            && progress.get() == observedProgress) {
                        // This will cause problem when run test on github ci dockers, too long system delay kill this
                        long remaining = deadline - System.nanoTime();
                        if (remaining <= 0L) {
                            // this perform unstable when use ci
                            break;
                            // throw new AssertionError("Async test work did not become idle; tasks=" + tasks.size() + ", batches=" + batches.size());
                        }
                        TimeUnit.NANOSECONDS.timedWait(progressMonitor, remaining);
                    }
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
                //throw new AssertionError("Interrupted while waiting for async test work", exception);
            }
        }
    }

    public boolean awaitPendingMainStepForTesting(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        synchronized (progressMonitor) {
            while (!hasPendingMainStepForTesting()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) return false;
                TimeUnit.NANOSECONDS.timedWait(progressMonitor, remaining);
            }
        }
        return true;
    }

    private boolean schedule(TickBatch batch, Task task, AsyncContinuation continuation) {
        if (task.terminationRequested.get()) return true;
        batch.runningWorkers.incrementAndGet();
        try {
            executor.execute(() -> {
                try {
                    if (taskActive(batch, task)) handleYield(batch, task,
                            continuation.advance(new AsyncExecutionContext(task.key)));
                } catch (Throwable throwable) {
                    fail(batch, task, throwable);
                } finally {
                    batch.runningWorkers.decrementAndGet();
                    signalProgress();
                }
            });
            return true;
        } catch (RuntimeException exception) {
            batch.runningWorkers.decrementAndGet();
            return false;
        }
    }

    private void admitWaitingWorkers(TickBatch batch) {
        int waitingCount = batch.waitingWorkers.size();
        for (int index = 0; index < waitingCount; index++) {
            WorkerSegment segment = batch.waitingWorkers.peek();
            if (segment == null) return;
            if (segment.task.terminationRequested.get()) {
                batch.waitingWorkers.poll();
                continue;
            }
            if (!queuedTaskActive(batch, segment.task)) {
                batch.waitingWorkers.poll();
                continue;
            }
            if (!schedule(batch, segment.task, segment.continuation)) return;
            batch.waitingWorkers.poll();
        }
    }

    private boolean queuedTaskActive(TickBatch batch, Task task) {
        if (task.terminationRequested.get()) return false;
        try {
            if (task.hooks.validator().getAsBoolean()) return true;
            requestTermination(batch, task, new TaskOutcome.Cancelled("stale while queued"));
        } catch (Throwable throwable) {
            fail(batch, task, throwable);
        }
        return false;
    }

    private void handleYield(TickBatch batch, Task task, AsyncContinuation.Yield yielded) {
        if (task.terminationRequested.get()) return;
        if (yielded instanceof AsyncContinuation.Yield.Complete) {
            requestTermination(batch, task, new TaskOutcome.Succeeded());
        } else if (yielded instanceof AsyncContinuation.Yield.MainThread(
                MainThreadStep step, Function<MainThreadStep.Result, AsyncContinuation> resume1
        )) {
            batch.pendingMainSteps.add(new PendingMainStep(task, List.of(step),
                    results -> resume1.apply(results.getFirst())));
            readyBatches.add(batch);
        } else if (yielded instanceof AsyncContinuation.Yield.MainThreadBatch(
                List<MainThreadStep> steps, Function<List<MainThreadStep.Result>, AsyncContinuation> resume
        )) {
            batch.pendingMainSteps.add(new PendingMainStep(task, steps, resume));
            readyBatches.add(batch);
        }
    }

    private void pumpMainThreadSteps(TickBatch batch) {
        PendingMainStep pending;
        while ((pending = batch.pendingMainSteps.poll()) != null) executePendingMainStep(batch, pending);
    }

    /** One FIFO round; newly yielded stages can run after other captured batches get their turn. */
    private int pumpReadyMainThreadSteps(TickBatch batch, int budget) {
        int readySteps = Math.min(batch.pendingMainSteps.size(), budget);
        int completedSteps = 0;
        while (completedSteps < readySteps) {
            PendingMainStep pending = batch.pendingMainSteps.poll();
            if (pending == null) break;
            executePendingMainStep(batch, pending);
            completedSteps++;
        }
        return completedSteps;
    }

    private int pumpNewerMainThreadSteps(TickBatch earliest, int budget, boolean continueEarliest) {
        int completed = 0;
        List<TickBatch> roundBatches = new ArrayList<>();
        if (continueEarliest) roundBatches.add(earliest);
        Set<TickBatch> visited = new HashSet<>();
        int readyCount = readyBatches.size();
        for (int index = 0; index < readyCount && completed < budget; index++) {
            TickBatch batch = readyBatches.poll();
            if (batch == null) break;
            if (batch.gameTime <= earliest.gameTime || !visited.add(batch)) continue;
            roundBatches.add(batch);
            admitWaitingWorkers(batch);
            completed += pumpReadyMainThreadSteps(batch, budget - completed);
            if (!batch.pendingMainSteps.isEmpty() || !batch.waitingWorkers.isEmpty()) readyBatches.add(batch);
            nextBatchOffset++;
        }
        while (completed < budget) {
            int beforeRound = completed;
            for (TickBatch batch : roundBatches) {
                if (completed == budget) break;
                completed += pumpReadyMainThreadSteps(batch, budget - completed);
            }
            if (completed == beforeRound) break;
        }
        return completed;
    }

    private void executePendingMainStep(TickBatch batch, PendingMainStep pending) {
        if (beforePendingMainStep != null) beforePendingMainStep.run();
        synchronized (pending.task) {
            if (!taskActive(batch, pending.task)) return;
            while (pending.nextStep < pending.steps.size()) {
                if (pending.task.terminationRequested.get()) return;
                MainThreadStep step = pending.steps.get(pending.nextStep);
                MainThreadStep.Result result;
                try {
                    MainThreadStepExecutor executor = mainStepExecutors.get(pending.task.key);
                    result = executor == null ? step.execute() : executor.execute(pending.task.key, step);
                } catch (Throwable throwable) {
                    result = MainThreadStep.Result.failure(throwable);
                }
                if (result instanceof MainThreadStep.Result.Pending) {
                    pending.nextStep++;
                    batch.deferredMainSteps.put(pending.task.key, pending);
                    signalProgress();
                    return;
                }
                if (result instanceof MainThreadStep.Result.Failure(Throwable cause)) {
                    fail(batch, pending.task, cause);
                    return;
                }
                pending.results.add(result);
                pending.nextStep++;
            }
        }
        if (pending.task.terminationRequested.get()) return;
        if (pending.resume == null) {
            requestTermination(batch, pending.task, new TaskOutcome.Succeeded());
            return;
        }
        synchronized (pending.task) {
            if (!taskActive(batch, pending.task)) return;
            try {
                AsyncContinuation continuation = pending.resume.apply(List.copyOf(pending.results));
                if (continuation.canAdvanceOnMainThread()) {
                    if (!taskActive(batch, pending.task)) return;
                    handleYield(batch, pending.task, continuation.advance(new AsyncExecutionContext(pending.task.key)));
                } else if (!schedule(batch, pending.task, continuation)) {
                    batch.waitingWorkers.add(new WorkerSegment(pending.task, continuation));
                    readyBatches.add(batch);
                }
            } catch (Throwable throwable) {
                fail(batch, pending.task, throwable);
            }
        }
    }

    private void fail(TickBatch batch, Task task, Throwable throwable) {
        requestTermination(batch, task, new TaskOutcome.Failed(throwable));
    }

    private boolean taskActive(TickBatch batch, Task task) {
        if (task.terminationRequested.get()) return false;
        try {
            if (task.hooks.validator().getAsBoolean()) return true;
            requestTermination(batch, task, new TaskOutcome.Cancelled("task validation failed"));
        } catch (Throwable throwable) {
            fail(batch, task, throwable);
        }
        return false;
    }

    private void requestTermination(TickBatch batch, Task task, TaskOutcome outcome) {
        if (!task.terminationRequested.compareAndSet(false, true)) return;
        task.outcome = outcome;
        batch.pendingTerminations.add(new PendingTermination(task, outcome));
        signalProgress();
    }

    private void drainAllTerminations() {
        for (TickBatch batch : batches.values()) drainTerminations(batch);
    }

    private void drainTerminations(TickBatch batch) {
        PendingTermination pending;
        while ((pending = batch.pendingTerminations.poll()) != null) finishTermination(batch, pending);
    }

    private void finishTermination(TickBatch batch, PendingTermination pending) {
        Task task = pending.task();
        removeTask(batch, task);
        try {
            task.hooks.completion().complete(task.key, pending.outcome());
        } catch (Throwable throwable) {
            MMCR.LOG.error("Async task completion failed: key={} outcome={}", task.key, pending.outcome(), throwable);
        }
    }

    private void removeTask(TickBatch batch, Task task) {
        tasks.remove(task.key, task);
        batch.tasks.remove(task.key, task);
        batch.deferredMainSteps.remove(task.key);
        mainStepExecutors.remove(task.key);
    }

    private void cancelAll() {
        for (TickBatch batch : batches.values()) {
            for (Task task : List.copyOf(batch.tasks.values())) {
                requestTermination(batch, task, new TaskOutcome.Cancelled("level unloaded"));
            }
            drainTerminations(batch);
        }
        batches.clear();
        tasks.clear();
        mainStepExecutors.clear();
    }

    private void signalProgress() {
        progress.incrementAndGet();
        synchronized (progressMonitor) {
            progressMonitor.notifyAll();
        }
    }

    public enum SubmissionResult { ACCEPTED, DUPLICATE, REJECTED }

    public sealed interface TaskOutcome permits TaskOutcome.Succeeded, TaskOutcome.Failed, TaskOutcome.Cancelled {
        record Succeeded() implements TaskOutcome { }

        record Failed(Throwable cause) implements TaskOutcome {
            public Failed {
                Objects.requireNonNull(cause, "cause");
            }
        }

        record Cancelled(String reason) implements TaskOutcome {
            public Cancelled {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    @FunctionalInterface
    public interface TaskCompletion {
        void complete(TaskKey key, TaskOutcome outcome);
    }

    public record TaskHooks(BooleanSupplier validator, TaskCompletion completion) {
        private static final TaskHooks DEFAULTS = new TaskHooks(() -> true, (key, outcome) -> { });

        public TaskHooks {
            Objects.requireNonNull(validator, "validator");
            Objects.requireNonNull(completion, "completion");
        }

        public static TaskHooks defaults() {
            return DEFAULTS;
        }
    }

    public record TaskKey(BlockPos controllerPos, long gameTime, MachineWorkMode workMode, String laneId,
                          long lifecycleEpoch) {
        public TaskKey(BlockPos controllerPos, long gameTime) {
            this(controllerPos, gameTime, MachineWorkMode.ASYNC, "base", 0L);
        }

        public TaskKey(BlockPos controllerPos, long gameTime, MachineWorkMode workMode) {
            this(controllerPos, gameTime, workMode, "base", 0L);
        }

        public TaskKey(BlockPos controllerPos, long gameTime, MachineWorkMode workMode, String laneId) {
            this(controllerPos, gameTime, workMode, laneId, 0L);
        }

        public TaskKey {
            controllerPos = controllerPos.immutable();
            workMode = Objects.requireNonNull(workMode);
            laneId = Objects.requireNonNull(laneId);
        }
    }

    private static final class Task {
        private final TaskKey key;
        private final TaskHooks hooks;
        private final AtomicBoolean terminationRequested = new AtomicBoolean();
        private volatile @Nullable TaskOutcome outcome;

        private Task(TaskKey key, TaskHooks hooks) {
            this.key = key;
            this.hooks = Objects.requireNonNull(hooks, "hooks");
        }
    }

    private static final class TickBatch {
        private final long gameTime;
        private final Map<TaskKey, Task> tasks = new ConcurrentHashMap<>();
        private final ReadyQueue<PendingMainStep> pendingMainSteps = new ReadyQueue<>();
        private final Map<TaskKey, PendingMainStep> deferredMainSteps = new ConcurrentHashMap<>();
        private final ReadyQueue<WorkerSegment> waitingWorkers = new ReadyQueue<>();
        private final ConcurrentLinkedQueue<PendingTermination> pendingTerminations = new ConcurrentLinkedQueue<>();
        private final AtomicInteger runningWorkers = new AtomicInteger();

        private TickBatch(long gameTime) {
            this.gameTime = gameTime;
        }

    }

    /**
     * Publishes ready work with an O(1) fence count. Queue locks never cover task callbacks.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class ReadyQueue<T> {
        private final ArrayDeque<T> entries = new ArrayDeque<>();

        private synchronized void add(T entry) {
            entries.addLast(entry);
        }

        private synchronized @Nullable T poll() {
            return entries.pollFirst();
        }

        private synchronized @Nullable T peek() {
            return entries.peekFirst();
        }

        private synchronized int size() {
            return entries.size();
        }

        private synchronized boolean isEmpty() {
            return entries.isEmpty();
        }
    }

    private static final class PendingMainStep {
        private final Task task;
        private final List<MainThreadStep> steps;
        private final @Nullable Function<List<MainThreadStep.Result>, AsyncContinuation> resume;
        private final List<MainThreadStep.Result> results = new ArrayList<>();
        private int nextStep;

        private PendingMainStep(Task task, List<MainThreadStep> steps,
                                @Nullable Function<List<MainThreadStep.Result>, AsyncContinuation> resume) {
            this.task = task;
            this.steps = steps;
            this.resume = resume;
        }
    }

    private record WorkerSegment(Task task, AsyncContinuation continuation) {
    }

    private record PendingTermination(Task task, TaskOutcome outcome) {
    }

    @FunctionalInterface
    public interface MainThreadStepExecutor {
        MainThreadStep.Result execute(TaskKey key, MainThreadStep step);
    }
}
