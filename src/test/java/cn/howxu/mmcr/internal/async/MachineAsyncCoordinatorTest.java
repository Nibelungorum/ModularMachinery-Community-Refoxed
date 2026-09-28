package cn.howxu.mmcr.internal.async;

import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MachineAsyncCoordinatorTest {

    @Test
    void continuation_receives_the_work_mode_from_its_task_key() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 40L, MachineWorkMode.SEMI_SYNC);

        assertThat(coordinator.submit(key, context -> {
            assertThat(context.workMode()).isEqualTo(MachineWorkMode.SEMI_SYNC);
            return AsyncContinuation.Yield.complete();
        })).isTrue();
    }

    @Test
    void yielded_main_step_runs_on_the_pump_thread_and_resumes_the_worker_continuation() {
        List<String> phases = new CopyOnWriteArrayList<>();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 40L);

        coordinator.submit(key, context -> {
            phases.add("worker-before");
            return AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> phases.add("main")),
                    ignored -> resumeContext -> {
                        phases.add("worker-after");
                        return AsyncContinuation.Yield.complete();
                    });
        });

        coordinator.pumpMainThreadSteps();
        coordinator.completeTick();

        assertThat(phases).containsExactly("worker-before", "main", "worker-after");
    }

    @Test
    void ready_main_step_does_not_dispatch_a_worker() {
        AtomicBoolean committed = new AtomicBoolean();
        Executor rejectingWorker = command -> {
            throw new AssertionError("worker must not run");
        };
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(rejectingWorker);
        var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 40L);

        assertThat(coordinator.submitMainThread(key,
                new MainThreadStep.TestStep(() -> committed.set(true)), null,
                MachineAsyncCoordinator.TaskHooks.defaults()))
                .isEqualTo(MachineAsyncCoordinator.SubmissionResult.ACCEPTED);
        coordinator.completeTick();

        assertThat(committed).isTrue();
    }

    @Test
    void deferred_main_step_resumes_only_after_the_shared_io_grant() {
        List<String> phases = new CopyOnWriteArrayList<>();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 40L);

        coordinator.submit(key, context -> {
            phases.add("worker-before");
            return AsyncContinuation.Yield.mainThread(new MainThreadStep.Deferred(
                    MainThreadStep.Kind.BEFORE_START, () -> phases.add("main")), result -> resumeContext -> {
                phases.add("worker-after");
                return AsyncContinuation.Yield.complete();
            });
        });

        coordinator.pumpMainThreadSteps();

        assertThat(phases).containsExactly("worker-before", "main");
        coordinator.resume(key);
        coordinator.completeTick();

        assertThat(phases).containsExactly("worker-before", "main", "worker-after");
    }

    @Test
    void resumed_continuation_receives_the_work_mode_from_its_task_key() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 40L, MachineWorkMode.SEMI_SYNC);

        coordinator.submit(key, ignored -> AsyncContinuation.Yield.mainThread(MainThreadStep.Result::success,
                result -> context -> {
                    assertThat(context.workMode()).isEqualTo(MachineWorkMode.SEMI_SYNC);
                    return AsyncContinuation.Yield.complete();
                }));

        coordinator.pumpMainThreadSteps();
        coordinator.completeTick();
    }

    @Test
    void only_one_pending_chain_is_allowed_for_a_controller_in_one_tick() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 40L);

        assertThat(coordinator.submit(key, ignored -> AsyncContinuation.Yield.complete())).isTrue();
        assertThat(coordinator.submit(key, ignored -> AsyncContinuation.Yield.complete())).isFalse();
    }

    @Test
    void worker_saturation_queues_a_tick_task_without_rejecting_it() {
        SaturatingExecutor executor = new SaturatingExecutor(2);
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(executor, 1);
        var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L);
        List<MachineAsyncCoordinator.TaskOutcome> outcomes = new ArrayList<>();

        assertThat(coordinator.submitDetailed(key, ignored -> AsyncContinuation.Yield.complete(), null,
                new MachineAsyncCoordinator.TaskHooks(() -> true, (ignored, outcome) -> outcomes.add(outcome))))
                .isEqualTo(MachineAsyncCoordinator.SubmissionResult.ACCEPTED);
        coordinator.completeTick();
        assertThat(outcomes).isEmpty();

        coordinator.completeTick();
        assertThat(outcomes).isEmpty();
        executor.runNext();
        coordinator.completeTick();

        assertThat(outcomes).singleElement().isInstanceOf(MachineAsyncCoordinator.TaskOutcome.Succeeded.class);
    }

    @Test
    void main_step_budget_defers_excess_work_until_the_next_fence() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run, 2);
        List<Integer> completed = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            int value = index;
            coordinator.submit(new MachineAsyncCoordinator.TaskKey(new BlockPos(index, 0, 0), 7L), ignored ->
                    AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> completed.add(value)),
                            result -> context -> AsyncContinuation.Yield.complete()));
        }

        coordinator.completeTick();
        assertThat(completed).containsExactly(0, 1);

        coordinator.completeTick();
        assertThat(completed).containsExactly(0, 1, 2);
    }

    @Test
    void main_thread_batch_consumes_one_budget_unit_and_runs_atomically() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run, 1);
        List<String> completed = new ArrayList<>();
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L), ignored ->
                AsyncContinuation.Yield.mainThreadBatch(List.of(
                                new MainThreadStep.TestStep(() -> completed.add("batch-1")),
                                new MainThreadStep.TestStep(() -> completed.add("batch-2")),
                                new MainThreadStep.TestStep(() -> completed.add("batch-3"))),
                        results -> context -> AsyncContinuation.Yield.complete()));
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(new BlockPos(1, 0, 0), 7L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> completed.add("single")),
                        result -> context -> AsyncContinuation.Yield.complete()));

        coordinator.completeTick();
        assertThat(completed).containsExactly("batch-1", "batch-2", "batch-3");

        coordinator.completeTick();
        assertThat(completed).containsExactly("batch-1", "batch-2", "batch-3", "single");
    }

    @Test
    void separate_lanes_of_one_controller_can_wait_for_their_own_shared_io_grants() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        var base = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 40L, MachineWorkMode.ASYNC, "base");
        var factory = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 40L, MachineWorkMode.ASYNC, "factory-0");

        assertThat(coordinator.submit(base, ignored -> AsyncContinuation.Yield.complete())).isTrue();
        assertThat(coordinator.submit(factory, ignored -> AsyncContinuation.Yield.complete())).isTrue();
    }

    @Test
    void complete_tick_defers_a_worker_continuation_yielded_by_its_main_step_to_the_next_fence() {
        List<String> phases = new CopyOnWriteArrayList<>();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);

        coordinator.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 40L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> phases.add("first-main")), result -> context ->
                        AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> phases.add("second-main")),
                                ignoredAgain -> contextAgain -> {
                                    phases.add("complete");
                                    return AsyncContinuation.Yield.complete();
                                })));

        coordinator.completeTick();
        assertThat(phases).containsExactly("first-main");

        coordinator.completeTick();

        assertThat(phases).containsExactly("first-main", "second-main", "complete");
    }

    @Test
    void complete_until_idle_is_not_limited_to_sixty_four_continuation_fences() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        AtomicInteger completedSteps = new AtomicInteger();
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 40L),
                continuationChain(65, completedSteps));

        coordinator.completeUntilIdleForTesting(() -> 0);

        assertThat(completedSteps).hasValue(65);
    }

    @Test
    void complete_tick_does_not_run_a_later_tick() {
        List<String> phases = new CopyOnWriteArrayList<>();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);

        coordinator.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 40L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> phases.add("current")),
                        result -> context -> AsyncContinuation.Yield.complete()));
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(new BlockPos(1, 0, 0), 41L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> phases.add("later")),
                        result -> context -> AsyncContinuation.Yield.complete()));

        coordinator.completeTick();

        assertThat(phases).containsExactly("current");
    }

    private static AsyncContinuation continuationChain(int remaining, AtomicInteger completedSteps) {
        return ignored -> {
            if (remaining == 0) return AsyncContinuation.Yield.complete();
            return AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(completedSteps::incrementAndGet),
                    result -> continuationChain(remaining - 1, completedSteps));
        };
    }

    @Test
    void cancelling_a_controller_discards_its_uncommitted_main_step() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        AtomicBoolean committed = new AtomicBoolean();
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> committed.set(true)),
                        result -> context -> AsyncContinuation.Yield.complete()));

        coordinator.cancel(BlockPos.ZERO);
        coordinator.pumpMainThreadSteps();

        assertThat(committed).isFalse();
    }

    @Test
    void cancelling_one_lane_keeps_other_lanes_of_the_same_controller_running() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        var cancelledKey = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L, MachineWorkMode.ASYNC, "base");
        var activeKey = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L, MachineWorkMode.ASYNC, "factory-0");
        List<String> committedLanes = new CopyOnWriteArrayList<>();
        coordinator.submit(cancelledKey, ignored -> AsyncContinuation.Yield.mainThread(
                new MainThreadStep.TestStep(() -> committedLanes.add("base")),
                result -> context -> AsyncContinuation.Yield.complete()));
        coordinator.submit(activeKey, ignored -> AsyncContinuation.Yield.mainThread(
                new MainThreadStep.TestStep(() -> committedLanes.add("factory-0")),
                result -> context -> AsyncContinuation.Yield.complete()));

        coordinator.cancel(cancelledKey);
        coordinator.completeTick();

        assertThat(committedLanes).containsExactly("factory-0");
    }

    @Test
    void structure_scan_result_is_delivered_to_its_main_thread_owner() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L, MachineWorkMode.ASYNC, "structure-scan");
        Object pattern = new Object();
        Object candidate = new Object();
        var identity = new StructureMatcher.ScanIdentity(4L, Direction.SOUTH, Direction.NORTH, 2,
                pattern, 9L, 3);
        var result = new StructureMatcher.ScanResult(StructureMatcher.ScanStatus.IN_PROGRESS, 2, null, null);
        AtomicBoolean delivered = new AtomicBoolean();

        coordinator.submit(key, ignored -> AsyncContinuation.Yield.mainThread(
                new MainThreadStep.StructureScan(identity, candidate, result), resume -> context ->
                        AsyncContinuation.Yield.complete()), (taskKey, step) -> {
            assertThat(taskKey).isEqualTo(key);
            assertThat(step).isInstanceOf(MainThreadStep.StructureScan.class);
            MainThreadStep.StructureScan scan = (MainThreadStep.StructureScan) step;
            assertThat(scan.identity()).isSameAs(identity);
            assertThat(scan.candidateIdentity()).isSameAs(candidate);
            assertThat(scan.result()).isSameAs(result);
            delivered.set(true);
            return MainThreadStep.Result.success();
        });

        coordinator.completeTick();

        assertThat(delivered).isTrue();
    }

    @Test
    void cancellation_wins_when_it_interleaves_after_a_main_step_is_dequeued() throws InterruptedException {
        CountDownLatch dequeued = new CountDownLatch(1);
        CountDownLatch allowPump = new CountDownLatch(1);
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run, () -> {
            dequeued.countDown();
            await(allowPump);
        });
        AtomicBoolean committed = new AtomicBoolean();
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> committed.set(true)),
                        result -> context -> AsyncContinuation.Yield.complete()));

        Thread pump = new Thread(coordinator::pumpMainThreadSteps);
        pump.start();
        assertThat(dequeued.await(1, TimeUnit.SECONDS)).isTrue();

        coordinator.cancel(BlockPos.ZERO);
        allowPump.countDown();
        pump.join(1_000L);

        assertThat(pump.isAlive()).isFalse();
        assertThat(committed).isFalse();
    }

    @Test
    void complete_tick_does_not_wait_for_a_worker_resumed_after_a_main_step() throws InterruptedException {
        ManualExecutor executor = new ManualExecutor();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(executor);
        AtomicBoolean resumed = new AtomicBoolean();
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L), ignored ->
                AsyncContinuation.Yield.mainThread(MainThreadStep.Result::success, result -> context -> {
                    resumed.set(true);
                    return AsyncContinuation.Yield.complete();
                }));
        executor.runNext();

        coordinator.completeTick();

        assertThat(executor.awaitTask()).isTrue();
        assertThat(resumed).isFalse();
        executor.runNext();
        coordinator.completeTick();
        assertThat(resumed).isTrue();
    }

    @Test
    void tick_fence_keeps_an_unfinished_worker_for_a_later_fence() {
        ManualExecutor executor = new ManualExecutor();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(executor);
        var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L);
        coordinator.submit(key, ignored -> AsyncContinuation.Yield.complete());

        assertThatCode(coordinator::completeTick).doesNotThrowAnyException();
        executor.runNext();
        coordinator.completeTick();
    }

    @Test
    void completion_requested_after_the_terminal_drain_is_finished_by_the_next_fence() {
        ManualExecutor executor = new ManualExecutor();
        List<MachineAsyncCoordinator.TaskOutcome> outcomes = new ArrayList<>();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(executor, null, () -> {
            if (executor.pendingTaskCount() > 0) executor.runNext();
        });
        var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L);

        coordinator.submitDetailed(key, ignored -> AsyncContinuation.Yield.complete(), null,
                new MachineAsyncCoordinator.TaskHooks(() -> true, (ignored, outcome) -> outcomes.add(outcome)));

        coordinator.completeTick();
        coordinator.completeTick();

        assertThat(outcomes).singleElement().isInstanceOf(MachineAsyncCoordinator.TaskOutcome.Succeeded.class);
    }

    @Test
    void tick_fence_keeps_a_deferred_shared_io_request_for_the_next_level_tick() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L);
        AtomicInteger resolutions = new AtomicInteger();
        coordinator.submit(key, ignored -> AsyncContinuation.Yield.mainThread(
                new MainThreadStep.SharedIoRequest(MainThreadStep.Kind.INTENT_COMMIT, "base", 1L),
                result -> context -> AsyncContinuation.Yield.complete()));

        coordinator.completeTick(() -> {
            resolutions.incrementAndGet();
            return 0;
        });

        assertThat(resolutions.get()).isPositive();
    }

    @Test
    void deferred_shared_io_from_an_older_tick_does_not_block_newer_runnable_main_steps() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        AtomicBoolean newerStepRan = new AtomicBoolean();
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.SharedIoRequest(
                        MainThreadStep.Kind.INTENT_COMMIT, "base", 1L), result -> context ->
                        AsyncContinuation.Yield.complete()));
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(new BlockPos(1, 0, 0), 8L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> newerStepRan.set(true)),
                        result -> context -> AsyncContinuation.Yield.complete()));

        coordinator.completeTick(() -> 0);

        assertThat(newerStepRan).isTrue();
    }

    @Test
    void an_unready_worker_from_an_older_tick_does_not_block_a_newer_ready_main_step() {
        AtomicInteger submissions = new AtomicInteger();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(command -> {
            if (submissions.getAndIncrement() > 0) command.run();
        });
        AtomicBoolean newerStepRan = new AtomicBoolean();
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L), ignored ->
                AsyncContinuation.Yield.complete());
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(new BlockPos(1, 0, 0), 8L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> newerStepRan.set(true)),
                        result -> context -> AsyncContinuation.Yield.complete()));

        coordinator.completeTick();

        assertThat(newerStepRan).isTrue();
    }

    @Test
    void worker_exceptions_are_captured_without_escaping_the_pump() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 40L);
        List<MachineAsyncCoordinator.TaskOutcome> outcomes = new ArrayList<>();
        coordinator.submitDetailed(key, ignored -> {
            throw new IllegalStateException("worker failure");
        }, null, new MachineAsyncCoordinator.TaskHooks(() -> true,
                (ignored, outcome) -> outcomes.add(outcome)));

        assertThatCode(coordinator::completeTick).doesNotThrowAnyException();
        assertThat(outcomes).singleElement().isInstanceOf(MachineAsyncCoordinator.TaskOutcome.Failed.class);
        MachineAsyncCoordinator.TaskOutcome.Failed failure =
                (MachineAsyncCoordinator.TaskOutcome.Failed) outcomes.getFirst();
        assertThat(failure.cause()).isInstanceOf(IllegalStateException.class).hasMessage("worker failure");
    }

    @Test
    void failed_main_step_does_not_resume_its_worker_continuation() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 40L);
        AtomicBoolean resumed = new AtomicBoolean();
        List<MachineAsyncCoordinator.TaskOutcome> outcomes = new ArrayList<>();

        coordinator.submitDetailed(key, ignored -> AsyncContinuation.Yield.mainThread(MainThreadStep.Result::success,
                result -> context -> {
                    resumed.set(true);
                    return AsyncContinuation.Yield.complete();
                }), (taskKey, step) -> MainThreadStep.Result.failure(new IllegalStateException("stale runtime")),
                new MachineAsyncCoordinator.TaskHooks(() -> true,
                        (ignored, outcome) -> outcomes.add(outcome)));

        coordinator.completeTick();

        assertThat(resumed).isFalse();
        assertThat(outcomes).singleElement().isInstanceOf(MachineAsyncCoordinator.TaskOutcome.Failed.class);
    }

    @Test
    void worker_failure_terminates_once_on_the_pump_thread() {
        ManualExecutor executor = new ManualExecutor();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(executor);
        var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 40L);
        List<MachineAsyncCoordinator.TaskOutcome> outcomes = new ArrayList<>();
        Thread pumpThread = Thread.currentThread();
        List<Thread> completionThreads = new ArrayList<>();

        coordinator.submitDetailed(key, ignored -> {
            throw new IllegalStateException("worker failure");
        }, null, new MachineAsyncCoordinator.TaskHooks(() -> true, (ignored, outcome) -> {
            outcomes.add(outcome);
            completionThreads.add(Thread.currentThread());
        }));

        executor.runNext();
        assertThat(outcomes).isEmpty();
        coordinator.completeTick();
        coordinator.completeTick();

        assertThat(outcomes).singleElement().isInstanceOf(MachineAsyncCoordinator.TaskOutcome.Failed.class);
        assertThat(completionThreads).containsExactly(pumpThread);
    }

    @Test
    void cancellation_and_late_worker_completion_share_one_terminal_outcome() {
        ManualExecutor executor = new ManualExecutor();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(executor);
        var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 40L);
        List<MachineAsyncCoordinator.TaskOutcome> outcomes = new ArrayList<>();

        coordinator.submitDetailed(key, ignored -> AsyncContinuation.Yield.complete(), null,
                new MachineAsyncCoordinator.TaskHooks(() -> true,
                        (ignored, outcome) -> outcomes.add(outcome)));
        coordinator.cancel(key);
        executor.runNext();
        coordinator.completeTick();

        assertThat(outcomes).singleElement().isInstanceOf(MachineAsyncCoordinator.TaskOutcome.Cancelled.class);
    }

    @Test
    void tick_fence_resolves_intents_yielded_after_an_earlier_main_step() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        SharedIoCoordinator sharedIo = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = new StructureClaimRegistry.ResourceDomain(1L, 1L, Set.of(BlockPos.ZERO));
        var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L);
        AtomicInteger committed = new AtomicInteger();

        coordinator.submit(key, ignored -> AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> { }),
                result -> context -> AsyncContinuation.Yield.mainThread(new MainThreadStep.IntentCommit("base", 1L,
                                new cn.howxu.mmcr.internal.recipe.AsyncRequirementPlanner.PlanResult(List.of(), List.of())),
                        ignoredAgain -> finalContext -> AsyncContinuation.Yield.complete())), (taskKey, step) -> {
            if (step instanceof MainThreadStep.IntentCommit) {
                sharedIo.enqueue(new SharedIoCoordinator.TickRequest(domain,
                        new SharedIoCoordinator.LaneKey(BlockPos.ZERO, "base"), 1L, 0L,
                        () -> {
                            committed.incrementAndGet();
                            coordinator.resume(taskKey);
                            return true;
                        }, () -> true, () -> 1L, () -> 0L));
                return MainThreadStep.Result.pending();
            }
            return step.execute();
        });

        coordinator.completeTick(() -> sharedIo.resolve(domain));
        coordinator.completeTick(() -> sharedIo.resolve(domain));
        coordinator.completeTick(() -> sharedIo.resolve(domain));

        assertThat(committed).hasValue(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for test interleaving");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for test interleaving", exception);
        }
    }

    private static final class ManualExecutor implements java.util.concurrent.Executor {
        private final java.util.concurrent.BlockingQueue<Runnable> tasks = new java.util.concurrent.LinkedBlockingQueue<>();
        private final java.util.concurrent.Semaphore available = new java.util.concurrent.Semaphore(0);

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
            available.release();
        }

        boolean awaitTask() throws InterruptedException {
            if (!available.tryAcquire(1, TimeUnit.SECONDS)) {
                return false;
            }
            available.release();
            return true;
        }

        void runNext() {
            if (!available.tryAcquire()) {
                throw new AssertionError("Expected a queued worker task");
            }
            Runnable task = tasks.poll();
            if (task == null) {
                throw new AssertionError("Expected a queued worker task");
            }
            task.run();
        }

        int pendingTaskCount() {
            return tasks.size();
        }
    }

    private static final class SaturatingExecutor implements Executor {
        private final Queue<Runnable> accepted = new ArrayDeque<>();
        private int rejections;

        private SaturatingExecutor(int rejections) {
            this.rejections = rejections;
        }

        @Override
        public void execute(Runnable command) {
            if (rejections-- > 0) throw new RejectedExecutionException("saturated");
            accepted.add(command);
        }

        private void runNext() {
            Runnable command = accepted.poll();
            if (command == null) throw new AssertionError("Expected an accepted worker task");
            command.run();
        }
    }
}
