package cn.howxu.mmcr.internal.async;

import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.runtime.AsyncCraftingExecution;
import cn.howxu.mmcr.internal.runtime.RecipeExecutionState;
import cn.howxu.mmcr.internal.runtime.RecipeExecutionState.Acknowledgement;
import cn.howxu.mmcr.internal.runtime.RecipeExecutionState.Receipt;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram.ExecutionIdentity;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MachineAsyncCoordinatorTest {

    @Test
    void receipt_drain_coalesces_one_execution_and_retains_both_terminal_identities() {
        Queue<Runnable> workers = new ArrayDeque<>();
        var coordinator = MachineAsyncCoordinator.forTesting(workers::add);
        var domain = new MachineAsyncCoordinator.ExecutionDomain(1L, 2L);
        var first = new ExecutionIdentity(1L, 1L, "base", 1L);
        var second = new ExecutionIdentity(1L, 1L, "base", 2L);
        coordinator.publishReceipt(domain, new Receipt(first, 1L, 0L, 0, 0L, false, null));
        coordinator.pumpReceiptDrains();
        coordinator.publishReceipt(domain, new Receipt(first, 1L, 4L, 3, 3L, true, null));
        coordinator.publishReceipt(domain, new Receipt(second, 2L, 0L, 0, 0L, true, null));
        coordinator.pumpReceiptDrains();
        assertThat(workers).hasSize(1);
        workers.remove().run();
        var acknowledgements = List.of(coordinator.pollReceiptAcknowledgement(),
                coordinator.pollReceiptAcknowledgement());
        assertThat(acknowledgements).extracting(ack -> ack.identity()).containsExactlyInAnyOrder(first, second);
        assertThat(acknowledgements).filteredOn(ack -> ack.identity().equals(first))
                .extracting(ack -> ack.sequence()).containsExactly(4L);
        assertThat(coordinator.pollReceiptAcknowledgement()).isNull();
    }

    @Test
    void saturated_receipt_worker_retries_without_computing_on_the_pump_thread() {
        Queue<Runnable> workers = new ArrayDeque<>();
        AtomicBoolean saturated = new AtomicBoolean(true);
        var coordinator = MachineAsyncCoordinator.forTesting(command -> {
            if (saturated.get()) throw new RejectedExecutionException();
            workers.add(command);
        });
        var identity = new ExecutionIdentity(1L, 1L, "base", 1L);
        coordinator.publishReceipt(new MachineAsyncCoordinator.ExecutionDomain(1L, 1L),
                new Receipt(identity, 1L, 0L, 0, 0L, true, null));
        coordinator.pumpReceiptDrains();
        assertThat(coordinator.pollReceiptAcknowledgement()).isNull();
        saturated.set(false);
        coordinator.pumpReceiptDrains();
        assertThat(coordinator.pollReceiptAcknowledgement()).isNull();
        workers.remove().run();
        assertThat(coordinator.pollReceiptAcknowledgement().identity()).isEqualTo(identity);
    }

    @Test
    void ordinary_workers_and_receipts_share_admission_across_coordinators_without_rejection() {
        ManualExecutor executor = new ManualExecutor();
        AtomicInteger submissions = new AtomicInteger();
        WorkerAdmission workers = new WorkerAdmission(command -> {
            submissions.incrementAndGet();
            executor.execute(command);
        }, 1);
        var ordinary = MachineAsyncCoordinator.forTesting(workers);
        var receipts = MachineAsyncCoordinator.forTesting(workers);
        AtomicBoolean advanced = new AtomicBoolean();
        assertThat(ordinary.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 1L), ignored -> {
            advanced.set(true);
            return AsyncContinuation.Yield.complete();
        })).isTrue();
        var first = new ExecutionIdentity(1L, 1L, "base", 1L);
        var second = new ExecutionIdentity(2L, 1L, "base", 1L);
        receipts.publishReceipt(new MachineAsyncCoordinator.ExecutionDomain(1L, 1L),
                new Receipt(first, 1L, 1L, 0, 0L, true, null));
        receipts.publishReceipt(new MachineAsyncCoordinator.ExecutionDomain(2L, 1L),
                new Receipt(second, 1L, 1L, 0, 0L, true, null));
        for (int index = 0; index < 100; index++) receipts.pumpReceiptDrains();
        assertThat(submissions).hasValue(1);
        assertThat(advanced).isFalse();
        assertThat(receipts.pollReceiptAcknowledgement()).isNull();

        executor.runNext();
        assertThat(advanced).isTrue();
        receipts.pumpReceiptDrains();
        executor.runNext();
        assertThat(receipts.pollReceiptAcknowledgement()).isEqualTo(new Acknowledgement(first, 1L, 1L));
        assertThat(receipts.pollReceiptAcknowledgement()).isNull();
        receipts.pumpReceiptDrains();
        executor.runNext();
        assertThat(receipts.pollReceiptAcknowledgement()).isEqualTo(new Acknowledgement(second, 1L, 1L));
        assertThat(submissions).hasValue(3);
        assertThat(executor.pendingTaskCount()).isZero();
    }

    @Test
    void a_receipt_worker_holds_the_same_admission_as_an_ordinary_continuation() {
        ManualExecutor executor = new ManualExecutor();
        WorkerAdmission workers = new WorkerAdmission(executor, 1);
        var receipts = MachineAsyncCoordinator.forTesting(workers);
        var ordinary = MachineAsyncCoordinator.forTesting(workers);
        var identity = new ExecutionIdentity(1L, 1L, "base", 1L);
        receipts.publishReceipt(new MachineAsyncCoordinator.ExecutionDomain(1L, 1L),
                new Receipt(identity, 1L, 1L, 0, 0L, true, null));
        receipts.pumpReceiptDrains();
        AtomicBoolean advanced = new AtomicBoolean();
        assertThat(ordinary.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 1L), ignored -> {
            advanced.set(true);
            return AsyncContinuation.Yield.complete();
        })).isTrue();
        ordinary.completeTick();
        assertThat(executor.pendingTaskCount()).isEqualTo(1);
        assertThat(advanced).isFalse();
        executor.runNext();
        ordinary.completeTick();
        executor.runNext();
        ordinary.completeTick();
        assertThat(advanced).isTrue();
        assertThat(receipts.pollReceiptAcknowledgement()).isEqualTo(new Acknowledgement(identity, 1L, 1L));
    }

    @Test
    void a_busy_receipt_domain_rejoins_behind_its_peer_after_a_bounded_workset() throws Exception {
        ManualExecutor executor = new ManualExecutor();
        var coordinator = MachineAsyncCoordinator.forTesting(new WorkerAdmission(executor, 1));
        var acknowledgements = new ReceiptAcknowledgements(false);
        acknowledgements.release.countDown();
        replaceReceiptField(coordinator, "receiptAcknowledgements", acknowledgements);
        var hotDomain = new MachineAsyncCoordinator.ExecutionDomain(1L, 1L);
        var coldDomain = new MachineAsyncCoordinator.ExecutionDomain(2L, 1L);
        int budget = ServerConfig.asyncSharedIoWorksetEntries();
        for (int index = 0; index <= budget; index++) {
            coordinator.publishReceipt(hotDomain, new Receipt(new ExecutionIdentity(1L, 1L, "base", index + 1L),
                    1L, 1L, 0, 0L, true, null));
        }
        var cold = new ExecutionIdentity(2L, 1L, "base", 1L);
        for (int index = 0; index < 100; index++) {
            coordinator.publishReceipt(coldDomain, new Receipt(cold, 1L, 1L, 0, 0L, true, null));
        }
        coordinator.pumpReceiptDrains();
        executor.runNext();
        assertThat(acknowledgements.merged).hasSize(budget);
        coordinator.pumpReceiptDrains();
        executor.runNext();
        assertThat(acknowledgements.merged.getLast().identity()).isEqualTo(cold);
        coordinator.pumpReceiptDrains();
        executor.runNext();
        assertThat(acknowledgements.merged).hasSize(budget + 2);
        assertThat(acknowledgements.merged.getLast().identity()).isEqualTo(
                new ExecutionIdentity(1L, 1L, "base", budget + 1L));
        assertThat(executor.pendingTaskCount()).isZero();
        assertThat(receiptDrains(coordinator)).isEmpty();
    }

    @Test
    void concurrent_publication_and_pumps_under_backpressure_preserve_the_later_terminal() throws Exception {
        ManualExecutor executor = new ManualExecutor();
        var coordinator = MachineAsyncCoordinator.forTesting(new WorkerAdmission(executor, 1));
        var acknowledgements = new ReceiptAcknowledgements(false);
        replaceReceiptField(coordinator, "receiptAcknowledgements", acknowledgements);
        var domain = new MachineAsyncCoordinator.ExecutionDomain(1L, 1L);
        var identity = new ExecutionIdentity(1L, 1L, "base", 1L);
        coordinator.publishReceipt(domain, new Receipt(identity, 1L, 1L, 0, 0L, true, null));
        coordinator.pumpReceiptDrains();
        var worker = new FutureTask<Void>(() -> { executor.runNext(); return null; });
        new Thread(worker).start();
        try {
            awaitReceiptLatch(acknowledgements.entered);
            var publisher = new FutureTask<Void>(() -> {
                for (long sequence = 2L; sequence <= 100L; sequence++) {
                    coordinator.publishReceipt(domain, new Receipt(identity, 2L, sequence, 0, 0L, true, null));
                    coordinator.pumpReceiptDrains();
                }
                return null;
            });
            new Thread(publisher).start();
            for (int index = 0; index < 100; index++) coordinator.pumpReceiptDrains();
            publisher.get(5, TimeUnit.SECONDS);
            assertThat(executor.pendingTaskCount()).isZero();
        } finally {
            acknowledgements.release.countDown();
        }
        worker.get(5, TimeUnit.SECONDS);
        assertThat(coordinator.pollReceiptAcknowledgement()).isEqualTo(new Acknowledgement(identity, 1L, 1L));
        coordinator.pumpReceiptDrains();
        assertThat(executor.pendingTaskCount()).isEqualTo(1);
        executor.runNext();
        assertThat(coordinator.pollReceiptAcknowledgement()).isEqualTo(new Acknowledgement(identity, 2L, 100L));
        assertThat(acknowledgements.merged).hasSize(2);
        assertThat(executor.pendingTaskCount()).isZero();
        assertThat(receiptDrains(coordinator)).isEmpty();
    }

    @Test
    void failed_terminal_ack_releases_admission_and_retries_on_the_next_pump() throws Exception {
        ManualExecutor executor = new ManualExecutor();
        var coordinator = MachineAsyncCoordinator.forTesting(new WorkerAdmission(executor, 1));
        var acknowledgements = new ReceiptAcknowledgements(true);
        acknowledgements.release.countDown();
        replaceReceiptField(coordinator, "receiptAcknowledgements", acknowledgements);
        var identity = new ExecutionIdentity(1L, 1L, "base", 1L);
        coordinator.publishReceipt(new MachineAsyncCoordinator.ExecutionDomain(1L, 1L),
                new Receipt(identity, 1L, 1L, 0, 0L, true, null));
        coordinator.pumpReceiptDrains();
        executor.runNext();
        assertThat(coordinator.pollReceiptAcknowledgement()).isNull();
        assertThat(executor.pendingTaskCount()).isZero();
        coordinator.pumpReceiptDrains();
        executor.runNext();
        assertThat(coordinator.pollReceiptAcknowledgement()).isEqualTo(new Acknowledgement(identity, 1L, 1L));
        assertThat(receiptDrains(coordinator)).isEmpty();
    }

    @Test
    void an_inline_ack_failure_waits_for_another_pump_instead_of_retrying_the_same_round() throws Exception {
        var coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        var acknowledgements = new ReceiptAcknowledgements(true);
        acknowledgements.release.countDown();
        replaceReceiptField(coordinator, "receiptAcknowledgements", acknowledgements);
        var identity = new ExecutionIdentity(1L, 1L, "base", 1L);
        coordinator.publishReceipt(new MachineAsyncCoordinator.ExecutionDomain(1L, 1L),
                new Receipt(identity, 1L, 1L, 0, 0L, true, null));
        coordinator.pumpReceiptDrains();
        assertThat(coordinator.pollReceiptAcknowledgement()).isNull();
        assertThat(acknowledgements.merged).isEmpty();
        coordinator.pumpReceiptDrains();
        assertThat(coordinator.pollReceiptAcknowledgement()).isEqualTo(new Acknowledgement(identity, 1L, 1L));
        assertThat(acknowledgements.merged).hasSize(1);
        assertThat(receiptDrains(coordinator)).isEmpty();
    }

    @Test
    void a_failed_ordinary_continuation_releases_capacity_and_keeps_its_failure_outcome() {
        ManualExecutor executor = new ManualExecutor();
        WorkerAdmission workers = new WorkerAdmission(executor, 1);
        var ordinary = MachineAsyncCoordinator.forTesting(workers);
        var receipts = MachineAsyncCoordinator.forTesting(workers);
        List<MachineAsyncCoordinator.TaskOutcome> outcomes = new ArrayList<>();
        IllegalStateException failure = new IllegalStateException("continuation failure");
        ordinary.submitDetailed(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 1L), ignored -> {
            throw failure;
        }, null, new MachineAsyncCoordinator.TaskHooks(() -> true, (key, outcome) -> outcomes.add(outcome)));
        var identity = new ExecutionIdentity(1L, 1L, "base", 1L);
        receipts.publishReceipt(new MachineAsyncCoordinator.ExecutionDomain(1L, 1L),
                new Receipt(identity, 1L, 1L, 0, 0L, true, null));
        receipts.pumpReceiptDrains();
        executor.runNext();
        ordinary.completeTick();
        assertThat(outcomes).containsExactly(new MachineAsyncCoordinator.TaskOutcome.Failed(failure));
        receipts.pumpReceiptDrains();
        executor.runNext();
        assertThat(receipts.pollReceiptAcknowledgement()).isEqualTo(new Acknowledgement(identity, 1L, 1L));
        assertThat(executor.pendingTaskCount()).isZero();
    }

    @Test
    void closing_a_queued_receipt_drain_releases_capacity_only_when_the_executor_consumes_it() throws Exception {
        ManualExecutor executor = new ManualExecutor();
        WorkerAdmission workers = new WorkerAdmission(executor, 1);
        var closed = MachineAsyncCoordinator.forTesting(workers);
        var live = MachineAsyncCoordinator.forTesting(workers);
        var domain = new MachineAsyncCoordinator.ExecutionDomain(1L, 1L);
        var identity = new ExecutionIdentity(1L, 1L, "base", 1L);
        closed.publishReceipt(domain, new Receipt(identity, 1L, 1L, 0, 0L, true, null));
        closed.pumpReceiptDrains();
        closeReceiptCoordinator(closed);
        assertThat(workers.tryExecute(() -> {})).isFalse();
        live.publishReceipt(domain, new Receipt(identity, 1L, 2L, 0, 0L, true, null));
        live.pumpReceiptDrains();
        executor.runNext();
        live.pumpReceiptDrains();
        executor.runNext();
        assertThat(live.pollReceiptAcknowledgement()).isEqualTo(new Acknowledgement(identity, 1L, 2L));
        assertThat(closed.pollReceiptAcknowledgement()).isNull();
        assertThat(receiptDrains(closed)).isEmpty();
        assertThat(executor.pendingTaskCount()).isZero();
    }

    @Test
    void cancellation_of_a_queued_ordinary_worker_does_not_leak_admission() throws Exception {
        ManualExecutor executor = new ManualExecutor();
        WorkerAdmission workers = new WorkerAdmission(executor, 1);
        var closed = MachineAsyncCoordinator.forTesting(workers);
        var live = MachineAsyncCoordinator.forTesting(workers);
        closed.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 1L), ignored -> {
            throw new AssertionError("cancelled continuation must not execute");
        });
        closeReceiptCoordinator(closed);
        var identity = new ExecutionIdentity(1L, 1L, "base", 1L);
        live.publishReceipt(new MachineAsyncCoordinator.ExecutionDomain(1L, 1L),
                new Receipt(identity, 1L, 1L, 0, 0L, true, null));
        live.pumpReceiptDrains();
        assertThat(executor.pendingTaskCount()).isEqualTo(1);
        executor.runNext();
        live.pumpReceiptDrains();
        executor.runNext();
        assertThat(live.pollReceiptAcknowledgement()).isEqualTo(new Acknowledgement(identity, 1L, 1L));
        assertThat(executor.pendingTaskCount()).isZero();
    }

    @Test
    void running_receipts_reuse_the_lifecycle_slot_and_confirm_only_the_newest_valid_fact() throws Exception {
        ManualExecutor workers = new ManualExecutor();
        var coordinator = MachineAsyncCoordinator.forTesting(workers);
        var domain = new MachineAsyncCoordinator.ExecutionDomain(1L, 1L);
        var identity = new ExecutionIdentity(1L, 1L, "base", 1L);
        coordinator.publishReceipt(domain, new Receipt(identity, 5L, 0L, 8, 8L, false, null));
        Object drain = receiptDrains(coordinator).get(domain);
        Object mailbox = receiptMailbox(drain, identity);
        coordinator.pumpReceiptDrains();
        workers.runNext();
        RecipeExecutionState state = (RecipeExecutionState) receiptField(mailbox, "state");
        assertThat(state.confirmedTick()).isEqualTo(8);
        coordinator.pumpReceiptDrains();
        assertThat(receiptDrains(coordinator).get(domain)).isSameAs(drain);

        coordinator.publishReceipt(domain, new Receipt(identity, 5L, 3L, 4, 9L, false, null));
        coordinator.publishReceipt(domain, new Receipt(identity, 5L, 2L, 15, 8L, false, null));
        assertThat(receiptMailbox(drain, identity)).isSameAs(mailbox);
        coordinator.pumpReceiptDrains();
        coordinator.pumpReceiptDrains();
        assertThat(workers.pendingTaskCount()).isEqualTo(1);
        workers.runNext();
        assertThat(receiptField(mailbox, "state")).isSameAs(state);
        assertThat(state.consumedSequence()).isEqualTo(3L);
        assertThat(state.confirmedTick()).isEqualTo(4);
        assertThat(state.confirmedPaidAttempts()).isEqualTo(9L);

        coordinator.publishReceipt(domain, new Receipt(identity, 4L, 4L, 0, 9L, true, null));
        coordinator.pumpReceiptDrains();
        workers.runNext();
        assertThat(coordinator.pollReceiptAcknowledgement()).isNull();
        assertThat(state.terminal()).isFalse();
        assertThat(receiptMailbox(drain, identity)).isSameAs(mailbox);

        coordinator.publishReceipt(domain, new Receipt(identity, 5L, 5L, 4, 9L, true, null));
        coordinator.pumpReceiptDrains();
        workers.runNext();
        assertThat(coordinator.pollReceiptAcknowledgement()).isEqualTo(new Acknowledgement(identity, 5L, 5L));
        assertThat(receiptMailbox(drain, identity)).isNull();
        coordinator.pumpReceiptDrains();
        assertThat(receiptDrains(coordinator)).isEmpty();
    }

    @Test
    void a_terminal_published_after_worker_take_keeps_its_slot_until_the_later_fact_is_acknowledged() throws Exception {
        ManualExecutor workers = new ManualExecutor();
        var coordinator = MachineAsyncCoordinator.forTesting(workers);
        var acknowledgements = new ReceiptAcknowledgements(false);
        replaceReceiptField(coordinator, "receiptAcknowledgements", acknowledgements);
        var domain = new MachineAsyncCoordinator.ExecutionDomain(1L, 1L);
        var identity = new ExecutionIdentity(1L, 1L, "base", 1L);
        coordinator.publishReceipt(domain, new Receipt(identity, 2L, 1L, 1, 1L, true, null));
        Object drain = receiptDrains(coordinator).get(domain);
        Object mailbox = receiptMailbox(drain, identity);
        coordinator.pumpReceiptDrains();
        var worker = new FutureTask<Void>(() -> { workers.runNext(); return null; });
        new Thread(worker).start();
        try {
            awaitReceiptLatch(acknowledgements.entered);
            coordinator.publishReceipt(domain, new Receipt(identity, 3L, 5L, 2, 2L, true, null));
            coordinator.publishReceipt(domain, new Receipt(identity, 3L, 4L, 1, 1L, true, null));
            coordinator.pumpReceiptDrains();
            assertThat(workers.pendingTaskCount()).isZero();
            assertThat(receiptMailbox(drain, identity)).isSameAs(mailbox);
        } finally {
            acknowledgements.release.countDown();
        }
        worker.get(5, TimeUnit.SECONDS);
        assertThat(coordinator.pollReceiptAcknowledgement()).isEqualTo(new Acknowledgement(identity, 2L, 1L));
        assertThat(receiptMailbox(drain, identity)).isSameAs(mailbox);
        assertThat(workers.pendingTaskCount()).isEqualTo(1);
        workers.runNext();
        assertThat(coordinator.pollReceiptAcknowledgement()).isEqualTo(new Acknowledgement(identity, 3L, 5L));
        assertThat(receiptMailbox(drain, identity)).isNull();
    }

    @Test
    void failed_receipt_replay_cannot_overwrite_a_newer_pending_terminal() throws Exception {
        ManualExecutor workers = new ManualExecutor();
        var coordinator = MachineAsyncCoordinator.forTesting(workers);
        var acknowledgements = new ReceiptAcknowledgements(true);
        replaceReceiptField(coordinator, "receiptAcknowledgements", acknowledgements);
        var domain = new MachineAsyncCoordinator.ExecutionDomain(1L, 1L);
        var identity = new ExecutionIdentity(1L, 1L, "base", 1L);
        coordinator.publishReceipt(domain, new Receipt(identity, 2L, 4L, 1, 1L, true, null));
        Object drain = receiptDrains(coordinator).get(domain);
        Object mailbox = receiptMailbox(drain, identity);
        coordinator.pumpReceiptDrains();
        var worker = new FutureTask<Void>(() -> { workers.runNext(); return null; });
        new Thread(worker).start();
        try {
            awaitReceiptLatch(acknowledgements.entered);
            coordinator.publishReceipt(domain, new Receipt(identity, 3L, 6L, 2, 2L, true, null));
            coordinator.publishReceipt(domain, new Receipt(identity, 3L, 5L, 1, 1L, true, null));
        } finally {
            acknowledgements.release.countDown();
        }
        worker.get(5, TimeUnit.SECONDS);
        assertThat(coordinator.pollReceiptAcknowledgement()).isNull();
        assertThat(workers.pendingTaskCount()).isZero();
        coordinator.pumpReceiptDrains();
        workers.runNext();
        assertThat(coordinator.pollReceiptAcknowledgement()).isEqualTo(new Acknowledgement(identity, 3L, 6L));
        assertThat(((RecipeExecutionState) receiptField(mailbox, "state")).consumedSequence()).isEqualTo(6L);
        assertThat(receiptMailbox(drain, identity)).isNull();
    }

    @Test
    void an_ack_failure_retries_the_terminal_already_accepted_by_the_worker() throws Exception {
        ManualExecutor workers = new ManualExecutor();
        var coordinator = MachineAsyncCoordinator.forTesting(workers);
        var acknowledgements = new ReceiptAcknowledgements(true);
        replaceReceiptField(coordinator, "receiptAcknowledgements", acknowledgements);
        var identity = new ExecutionIdentity(1L, 1L, "base", 1L);
        coordinator.publishReceipt(new MachineAsyncCoordinator.ExecutionDomain(1L, 1L),
                new Receipt(identity, 2L, 4L, 1, 1L, true, null));
        coordinator.pumpReceiptDrains();
        acknowledgements.release.countDown();
        workers.runNext();
        assertThat(coordinator.pollReceiptAcknowledgement()).isNull();
        coordinator.pumpReceiptDrains();
        workers.runNext();
        assertThat(coordinator.pollReceiptAcknowledgement()).isEqualTo(new Acknowledgement(identity, 2L, 4L));
        coordinator.pumpReceiptDrains();
        assertThat(receiptDrains(coordinator)).isEmpty();
    }

    @Test
    void a_publisher_that_read_an_idle_drain_retries_after_that_drain_is_detached() throws Exception {
        ManualExecutor workers = new ManualExecutor();
        var coordinator = MachineAsyncCoordinator.forTesting(workers);
        var domain = new MachineAsyncCoordinator.ExecutionDomain(1L, 1L);
        var first = new ExecutionIdentity(1L, 1L, "base", 1L);
        var second = new ExecutionIdentity(1L, 1L, "base", 2L);
        coordinator.publishReceipt(domain, new Receipt(first, 1L, 1L, 0, 0L, true, null));
        coordinator.pumpReceiptDrains();
        var drains = new PausingReceiptDrains(receiptDrains(coordinator));
        replaceReceiptField(coordinator, "receiptDrains", drains);
        drains.pauseNextRead.set(true);
        var publisher = new FutureTask<Void>(() -> {
            coordinator.publishReceipt(domain, new Receipt(second, 2L, 1L, 0, 0L, true, null));
            return null;
        });
        new Thread(publisher).start();
        try {
            awaitReceiptLatch(drains.entered);
            workers.runNext();
            assertThat(coordinator.pollReceiptAcknowledgement().identity()).isEqualTo(first);
            coordinator.pumpReceiptDrains();
            assertThat(drains).isEmpty();
        } finally {
            drains.release.countDown();
        }
        publisher.get(5, TimeUnit.SECONDS);
        coordinator.pumpReceiptDrains();
        workers.runNext();
        assertThat(coordinator.pollReceiptAcknowledgement()).isEqualTo(new Acknowledgement(second, 2L, 1L));
        coordinator.pumpReceiptDrains();
        assertThat(drains).isEmpty();
    }

    @Test
    void close_prevents_a_cold_publisher_from_attaching_after_its_initial_lookup() throws Exception {
        ManualExecutor workers = new ManualExecutor();
        var coordinator = MachineAsyncCoordinator.forTesting(workers);
        var drains = new PausingReceiptDrains(receiptDrains(coordinator));
        replaceReceiptField(coordinator, "receiptDrains", drains);
        drains.pauseNextRead.set(true);
        var publisher = new FutureTask<Void>(() -> {
            coordinator.publishReceipt(new MachineAsyncCoordinator.ExecutionDomain(1L, 1L),
                    new Receipt(new ExecutionIdentity(1L, 1L, "base", 1L), 1L, 1L, 0, 0L, true, null));
            return null;
        });
        new Thread(publisher).start();
        try {
            awaitReceiptLatch(drains.entered);
            closeReceiptCoordinator(coordinator);
        } finally {
            drains.release.countDown();
        }
        publisher.get(5, TimeUnit.SECONDS);
        coordinator.pumpReceiptDrains();
        assertThat(drains).isEmpty();
        assertThat(workers.pendingTaskCount()).isZero();
        assertThat(coordinator.pollReceiptAcknowledgement()).isNull();
    }

    @Test
    void close_discards_a_terminal_taken_by_an_inflight_worker_before_ack_publication() throws Exception {
        ManualExecutor workers = new ManualExecutor();
        var coordinator = MachineAsyncCoordinator.forTesting(workers);
        var domain = new MachineAsyncCoordinator.ExecutionDomain(1L, 1L);
        var identity = new ExecutionIdentity(1L, 1L, "base", 1L);
        coordinator.publishReceipt(domain, new Receipt(identity, 1L, 1L, 0, 0L, true, null));
        coordinator.pumpReceiptDrains();
        CountDownLatch started = new CountDownLatch(1);
        var worker = new FutureTask<Void>(() -> { started.countDown(); workers.runNext(); return null; });
        Thread thread = new Thread(worker);
        Object lifecycle = receiptField(coordinator, "receiptLifecycle");
        synchronized (lifecycle) {
            thread.start();
            awaitReceiptLatch(started);
            awaitReceiptMonitor(thread, lifecycle);
            closeReceiptCoordinator(coordinator);
        }
        worker.get(5, TimeUnit.SECONDS);
        coordinator.publishReceipt(domain, new Receipt(identity, 1L, 2L, 0, 0L, true, null));
        coordinator.pumpReceiptDrains();
        assertThat(receiptDrains(coordinator)).isEmpty();
        assertThat(workers.pendingTaskCount()).isZero();
        assertThat(coordinator.pollReceiptAcknowledgement()).isNull();
    }

    @Test
    void receipt_worksets_are_budget_bounded_and_hot_republication_goes_behind_captured_peers() throws Exception {
        ManualExecutor workers = new ManualExecutor();
        var coordinator = MachineAsyncCoordinator.forTesting(workers);
        var acknowledgements = new ReceiptAcknowledgements(false);
        replaceReceiptField(coordinator, "receiptAcknowledgements", acknowledgements);
        var domain = new MachineAsyncCoordinator.ExecutionDomain(1L, 1L);
        var hot = new ExecutionIdentity(1L, 1L, "base", 1L);
        int budget = ServerConfig.asyncSharedIoWorksetEntries();
        List<ExecutionIdentity> order = new ArrayList<>();
        order.add(hot);
        coordinator.publishReceipt(domain, new Receipt(hot, 1L, 1L, 0, 0L, true, null));
        for (int i = 0; i <= budget; i++) {
            var peer = new ExecutionIdentity(1L, 1L, "base", i + 2L);
            order.add(peer);
            coordinator.publishReceipt(domain, new Receipt(peer, 1L, 1L, 0, 0L, true, null));
        }
        coordinator.pumpReceiptDrains();
        var worker = new FutureTask<Void>(() -> { workers.runNext(); return null; });
        new Thread(worker).start();
        try {
            awaitReceiptLatch(acknowledgements.entered);
            coordinator.publishReceipt(domain, new Receipt(hot, 1L, 2L, 0, 0L, true, null));
            coordinator.pumpReceiptDrains();
            assertThat(workers.pendingTaskCount()).isZero();
        } finally {
            acknowledgements.release.countDown();
        }
        worker.get(5, TimeUnit.SECONDS);
        assertThat(acknowledgements.merged).extracting(Acknowledgement::identity)
                .containsExactlyElementsOf(order.subList(0, budget));
        assertThat(workers.pendingTaskCount()).isEqualTo(1);
        int remaining = order.size() + 1 - budget;
        for (int i = 0; i < (remaining + budget - 1) / budget; i++) workers.runNext();
        order.add(hot);
        assertThat(acknowledgements.merged).extracting(Acknowledgement::identity).containsExactlyElementsOf(order);
        assertThat(acknowledgements.merged.getLast()).isEqualTo(new Acknowledgement(hot, 1L, 2L));
        assertThat(workers.pendingTaskCount()).isZero();
        coordinator.pumpReceiptDrains();
        assertThat(receiptDrains(coordinator)).isEmpty();
    }

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
    void complete_tick_consumes_an_immediately_ready_worker_yield_with_remaining_budget() {
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
        assertThat(phases).containsExactly("first-main", "second-main", "complete");

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
    void complete_tick_uses_remaining_budget_for_a_later_ready_tick() {
        List<String> phases = new CopyOnWriteArrayList<>();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);

        coordinator.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 40L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> phases.add("current")),
                        result -> context -> AsyncContinuation.Yield.complete()));
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(new BlockPos(1, 0, 0), 41L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> phases.add("later")),
                        result -> context -> AsyncContinuation.Yield.complete()));

        coordinator.completeTick();

        assertThat(phases).containsExactly("current", "later");
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

        Thread pump = new Thread(coordinator::completeTick);
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

    @Test
    void worker_publication_during_a_fence_can_use_remaining_budget() throws InterruptedException {
        ManualExecutor executor = new ManualExecutor();
        CountDownLatch dequeued = new CountDownLatch(1);
        CountDownLatch published = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(executor, () -> {
            if (first.getAndSet(false)) {
                dequeued.countDown();
                await(published);
            }
        });
        List<String> phases = new ArrayList<>();
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> phases.add("first")),
                        result -> context -> AsyncContinuation.Yield.complete()));
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(new BlockPos(1, 0, 0), 7L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> phases.add("published")),
                        result -> context -> AsyncContinuation.Yield.complete()));
        executor.runNext();
        Thread worker = new Thread(() -> {
            try {
                await(dequeued);
                executor.runNext();
            } finally {
                published.countDown();
            }
        });
        worker.start();
        try {
            coordinator.completeTick();
            assertThat(phases).containsExactly("first", "published");
            assertThat(coordinator.hasPendingMainStepForTesting()).isFalse();
            coordinator.completeTick();
            assertThat(phases).containsExactly("first", "published");
            assertThat(coordinator.hasPendingMainStepForTesting()).isFalse();
        } finally {
            dequeued.countDown();
            worker.join(1_000L);
        }
        assertThat(worker.isAlive()).isFalse();
    }

    @Test
    void newer_batch_created_by_a_callback_is_outside_the_ready_batch_boundary() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        List<String> phases = new ArrayList<>();
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.Deferred(
                        MainThreadStep.Kind.BEFORE_START, () -> { }), result -> context ->
                        AsyncContinuation.Yield.complete()));
        coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(new BlockPos(1, 0, 0), 8L),
                new MainThreadStep.TestStep(() -> {
                    phases.add("existing");
                    coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(new BlockPos(2, 0, 0), 9L),
                            new MainThreadStep.TestStep(() -> phases.add("created")), null,
                            MachineAsyncCoordinator.TaskHooks.defaults());
                }), null, MachineAsyncCoordinator.TaskHooks.defaults());

        coordinator.completeTick();
        assertThat(phases).containsExactly("existing");
        coordinator.completeTick();
        assertThat(phases).containsExactly("existing", "created");
    }

    @Test
    void cross_batch_callback_keeps_the_existing_per_batch_capture_point() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        List<String> phases = new ArrayList<>();
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.Deferred(
                        MainThreadStep.Kind.BEFORE_START, () -> { }), result -> context ->
                        AsyncContinuation.Yield.complete()));
        coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(new BlockPos(1, 0, 0), 8L),
                new MainThreadStep.TestStep(() -> {
                    phases.add("callback");
                    coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(new BlockPos(3, 0, 0), 9L),
                            new MainThreadStep.TestStep(() -> phases.add("appended")), null,
                            MachineAsyncCoordinator.TaskHooks.defaults());
                }), null, MachineAsyncCoordinator.TaskHooks.defaults());
        coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(new BlockPos(2, 0, 0), 9L),
                new MainThreadStep.TestStep(() -> phases.add("already-ready")), null,
                MachineAsyncCoordinator.TaskHooks.defaults());

        coordinator.completeTick();

        assertThat(phases).containsExactly("callback", "already-ready", "appended");
    }

    @Test
    void newer_batch_rotation_preserves_budget_and_defers_its_yielded_continuation() {
        ManualExecutor executor = new ManualExecutor();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(executor, 1);
        List<String> phases = new ArrayList<>();
        coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L),
                new MainThreadStep.Deferred(MainThreadStep.Kind.BEFORE_START, () -> { }), null,
                MachineAsyncCoordinator.TaskHooks.defaults());
        coordinator.completeTick();
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(new BlockPos(1, 0, 0), 8L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> phases.add("a-first")),
                        result -> context -> AsyncContinuation.Yield.mainThread(
                                new MainThreadStep.TestStep(() -> phases.add("a-second")),
                                next -> last -> AsyncContinuation.Yield.complete())));
        executor.runNext();
        coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(new BlockPos(2, 0, 0), 9L),
                new MainThreadStep.TestStep(() -> phases.add("b")), null,
                MachineAsyncCoordinator.TaskHooks.defaults());

        coordinator.completeTick();
        assertThat(phases).containsExactly("a-first");
        executor.runNext();
        coordinator.completeTick();
        assertThat(phases).containsExactly("a-first", "b");
        coordinator.completeTick();
        assertThat(phases).containsExactly("a-first", "b", "a-second");
    }

    @Test
    void pending_batch_resume_during_arbitration_keeps_the_earliest_batch_fence() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run, 1);
        var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L);
        List<String> phases = new ArrayList<>();
        coordinator.submit(key, ignored -> AsyncContinuation.Yield.mainThreadBatch(List.of(
                        new MainThreadStep.Deferred(MainThreadStep.Kind.BEFORE_START, () -> phases.add("pending")),
                        new MainThreadStep.TestStep(() -> phases.add("after-resume"))),
                results -> context -> {
                    assertThat(results).hasSize(2);
                    phases.add("complete");
                    return AsyncContinuation.Yield.complete();
                }));

        coordinator.completeTick(() -> {
            coordinator.resume(key);
            return 1;
        });
        assertThat(phases).containsExactly("pending");
        coordinator.completeTick();
        assertThat(phases).containsExactly("pending", "after-resume", "complete");
        coordinator.completeTick();
        assertThat(coordinator.hasPendingMainStepForTesting()).isFalse();
    }

    @Test
    void cancelled_queue_entries_consume_the_captured_budget_without_stale_ready_work() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run, 1);
        List<String> phases = new ArrayList<>();
        List<MachineAsyncCoordinator.TaskOutcome> outcomes = new ArrayList<>();
        var cancelled = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L);
        var active = new MachineAsyncCoordinator.TaskKey(new BlockPos(1, 0, 0), 7L);
        coordinator.submitMainThread(cancelled, new MainThreadStep.TestStep(() -> phases.add("cancelled")),
                null, new MachineAsyncCoordinator.TaskHooks(() -> true, (key, outcome) -> outcomes.add(outcome)));
        coordinator.submitMainThread(active, new MainThreadStep.TestStep(() -> phases.add("active")),
                null, MachineAsyncCoordinator.TaskHooks.defaults());
        coordinator.cancel(cancelled);

        coordinator.completeTick();
        assertThat(phases).isEmpty();
        assertThat(outcomes).singleElement().isInstanceOf(MachineAsyncCoordinator.TaskOutcome.Cancelled.class);
        coordinator.completeTick();
        assertThat(phases).containsExactly("active");
        coordinator.completeTick();
        assertThat(coordinator.hasPendingMainStepForTesting()).isFalse();
        assertThat(outcomes).hasSize(1);
        coordinator.submitMainThread(active, new MainThreadStep.TestStep(() -> phases.add("resubmitted")),
                null, MachineAsyncCoordinator.TaskHooks.defaults());
        coordinator.completeTick();
        assertThat(phases).containsExactly("active", "resubmitted");
    }

    @Test
    void termination_callback_can_publish_a_new_task_after_the_captured_main_steps() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        List<String> phases = new ArrayList<>();
        var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L);
        coordinator.submitMainThread(key, new MainThreadStep.TestStep(() -> phases.add("terminated")),
                null, new MachineAsyncCoordinator.TaskHooks(() -> true, (finished, outcome) -> {
                    coordinator.submitMainThread(key, new MainThreadStep.TestStep(() -> phases.add("replacement")),
                            null, MachineAsyncCoordinator.TaskHooks.defaults());
                }));

        coordinator.completeTick();
        assertThat(phases).containsExactly("terminated");
        coordinator.completeTick();
        assertThat(phases).containsExactly("terminated", "replacement");
        coordinator.completeTick();
        assertThat(coordinator.hasPendingMainStepForTesting()).isFalse();
    }

    @Test
    void unbounded_pump_drain_leaves_no_phantom_count_for_the_next_fence() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run, 1);
        List<String> phases = new ArrayList<>();
        coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L),
                new MainThreadStep.TestStep(() -> phases.add("drained")), null,
                MachineAsyncCoordinator.TaskHooks.defaults());
        coordinator.pumpMainThreadSteps();
        assertThat(coordinator.hasPendingMainStepForTesting()).isFalse();
        coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(new BlockPos(1, 0, 0), 8L),
                new MainThreadStep.TestStep(() -> phases.add("newer")), null,
                MachineAsyncCoordinator.TaskHooks.defaults());

        coordinator.completeTick();

        assertThat(phases).containsExactly("drained", "newer");
        assertThat(coordinator.hasPendingMainStepForTesting()).isFalse();
    }

    @Test
    void safe_start_and_finish_stages_share_a_fence_but_each_consume_budget() {
        for (MainThreadStep.Kind kind : List.of(MainThreadStep.Kind.BEFORE_START, MainThreadStep.Kind.BEFORE_FINISH)) {
            ManualExecutor executor = new ManualExecutor();
            MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(executor, 3, false);
            List<Class<?>> stages = new ArrayList<>();
            var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L);
            coordinator.submit(key, kind == MainThreadStep.Kind.BEFORE_START
                    ? AsyncCraftingExecution.start("base", 1L) : AsyncCraftingExecution.finish("base", 1L),
                    (taskKey, step) -> {
                        stages.add(step.getClass());
                        return step.execute();
                    });
            executor.runNext();
            coordinator.beginLevelTick(7L);

            coordinator.completeTick();

            assertThat(stages).containsExactly(MainThreadStep.Lifecycle.class, MainThreadStep.ScreenTextFlush.class,
                    MainThreadStep.SharedIoRequest.class);
            assertThat(executor.pendingTaskCount()).isZero();
            AtomicBoolean peer = new AtomicBoolean();
            coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(new BlockPos(1, 0, 0), 7L),
                    new MainThreadStep.TestStep(() -> peer.set(true)), null, MachineAsyncCoordinator.TaskHooks.defaults());
            coordinator.beginLevelTick(7L);
            coordinator.completeTick();
            assertThat(peer).isFalse();
            coordinator.beginLevelTick(8L);
            coordinator.completeTick();
            assertThat(peer).isTrue();
        }
    }

    @Test
    void safe_stages_wait_when_budget_is_exhausted_without_an_extra_worker_hop() {
        ManualExecutor executor = new ManualExecutor();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(executor, 2, false);
        List<Class<?>> stages = new ArrayList<>();
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L),
                AsyncCraftingExecution.finish("base", 1L), (key, step) -> {
                    stages.add(step.getClass());
                    return step.execute();
                });
        executor.runNext();
        coordinator.beginLevelTick(7L);
        coordinator.completeTick();
        coordinator.completeTick();
        assertThat(stages).containsExactly(MainThreadStep.Lifecycle.class, MainThreadStep.ScreenTextFlush.class);
        assertThat(executor.pendingTaskCount()).isZero();
        coordinator.beginLevelTick(8L);
        coordinator.completeTick();
        assertThat(stages).containsExactly(MainThreadStep.Lifecycle.class, MainThreadStep.ScreenTextFlush.class,
                MainThreadStep.SharedIoRequest.class);
    }

    @Test
    void default_continuation_stays_on_the_worker_after_a_main_result() {
        ManualExecutor executor = new ManualExecutor();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(executor, 4, false);
        AtomicBoolean advanced = new AtomicBoolean();
        AsyncContinuation continuation = ignored -> {
            advanced.set(true);
            return AsyncContinuation.Yield.complete();
        };
        assertThat(continuation.canAdvanceOnMainThread()).isFalse();
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L), ignored ->
                AsyncContinuation.Yield.mainThread(MainThreadStep.Result::success, result -> continuation));
        executor.runNext();
        coordinator.beginLevelTick(7L);
        coordinator.completeTick();
        assertThat(advanced).isFalse();
        assertThat(executor.pendingTaskCount()).isEqualTo(1);
        executor.runNext();
        coordinator.completeTick();
        assertThat(advanced).isTrue();
    }

    @Test
    void old_worker_tail_does_not_block_newer_ready_work_after_using_one_step() {
        ManualExecutor executor = new ManualExecutor();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(executor, 2, false);
        List<String> phases = new ArrayList<>();
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> phases.add("old")),
                        result -> context -> AsyncContinuation.Yield.complete()));
        executor.runNext();
        coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(new BlockPos(1, 0, 0), 8L),
                new MainThreadStep.TestStep(() -> phases.add("new")), null, MachineAsyncCoordinator.TaskHooks.defaults());
        coordinator.beginLevelTick(8L);
        coordinator.completeTick();
        assertThat(phases).containsExactly("old", "new");
        assertThat(executor.pendingTaskCount()).isEqualTo(1);

        coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(new BlockPos(2, 0, 0), 8L),
                new MainThreadStep.TestStep(() -> phases.add("exhausted")), null, MachineAsyncCoordinator.TaskHooks.defaults());
        coordinator.completeTick();
        assertThat(phases).containsExactly("old", "new");
        coordinator.beginLevelTick(9L);
        coordinator.completeTick();
        assertThat(phases).containsExactly("old", "new", "exhausted");
    }

    @Test
    void visited_newer_waiting_worker_is_readmitted_with_remaining_budget() {
        AtomicBoolean saturated = new AtomicBoolean(true);
        Queue<Runnable> workers = new ArrayDeque<>();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(command -> {
            if (saturated.get()) throw new RejectedExecutionException("saturated");
            workers.add(command);
        }, 1, false);
        coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L),
                new MainThreadStep.Deferred(MainThreadStep.Kind.BEFORE_START, () -> { }), null,
                MachineAsyncCoordinator.TaskHooks.defaults());
        coordinator.beginLevelTick(7L);
        coordinator.completeTick();
        AtomicBoolean committed = new AtomicBoolean();
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(new BlockPos(1, 0, 0), 8L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> committed.set(true)),
                        result -> context -> AsyncContinuation.Yield.complete()));
        saturated.set(false);
        coordinator.completeTick();
        assertThat(workers).isEmpty(); // No ready-batch scan at zero remaining budget.
        coordinator.beginLevelTick(8L);
        coordinator.completeTick();
        assertThat(workers).hasSize(1);
        workers.remove().run();
        coordinator.completeTick();
        assertThat(committed).isTrue();
    }

    @Test
    void infinite_safe_yields_are_budget_bounded_and_append_behind_ready_peers() {
        ManualExecutor executor = new ManualExecutor();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(executor, 4, false);
        List<String> phases = new ArrayList<>();
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L), safeLoop(phases));
        executor.runNext();
        coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(new BlockPos(1, 0, 0), 7L),
                new MainThreadStep.TestStep(() -> phases.add("peer")), null, MachineAsyncCoordinator.TaskHooks.defaults());
        coordinator.beginLevelTick(7L);
        coordinator.completeTick();
        coordinator.completeTick();
        assertThat(phases).containsExactly("loop", "peer", "loop", "loop");
        assertThat(executor.pendingTaskCount()).isZero();
        coordinator.beginLevelTick(8L);
        coordinator.completeTick();
        assertThat(phases).hasSize(8);
        coordinator.cancel(BlockPos.ZERO);
        coordinator.beginLevelTick(9L);
        coordinator.completeTick();
        assertThat(phases).hasSize(8);
    }

    @Test
    void deferred_safe_resume_is_charged_and_cannot_repeat_the_pending_business_step() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run, 2, false);
        List<String> phases = new ArrayList<>();
        var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L);
        coordinator.submit(key, ignored -> AsyncContinuation.Yield.mainThread(
                new MainThreadStep.Deferred(MainThreadStep.Kind.BEFORE_START, () -> phases.add("pending")),
                result -> safeLoop(phases)));
        coordinator.beginLevelTick(7L);
        coordinator.completeTick(() -> {
            coordinator.resume(key);
            coordinator.resume(key);
            return 1;
        });
        coordinator.completeTick();
        assertThat(phases).containsExactly("pending");
        coordinator.beginLevelTick(8L);
        coordinator.completeTick();
        assertThat(phases).containsExactly("pending", "loop", "loop");
        coordinator.cancel(key);
        coordinator.resume(key);
        coordinator.beginLevelTick(9L);
        coordinator.completeTick();
        assertThat(phases).containsExactly("pending", "loop", "loop");
    }

    @Test
    void live_task_validation_stays_on_the_server_thread_across_worker_resumption() throws Exception {
        Thread serverThread = Thread.currentThread();
        Queue<Runnable> workers = new ArrayDeque<>();
        var coordinator = MachineAsyncCoordinator.forTesting(workers::add);
        List<Thread> validations = new CopyOnWriteArrayList<>();
        List<Thread> computations = new CopyOnWriteArrayList<>();
        List<MachineAsyncCoordinator.TaskOutcome> outcomes = new ArrayList<>();
        coordinator.submitDetailed(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L), ignored -> {
            computations.add(Thread.currentThread());
            return AsyncContinuation.Yield.mainThread(MainThreadStep.Result::success, result -> context -> {
                computations.add(Thread.currentThread());
                return AsyncContinuation.Yield.complete();
            });
        }, null, new MachineAsyncCoordinator.TaskHooks(() -> {
            validations.add(Thread.currentThread());
            return true;
        }, (key, outcome) -> outcomes.add(outcome)));

        var first = new FutureTask<Void>(workers.remove(), null);
        Thread.ofPlatform().start(first);
        first.get(5, TimeUnit.SECONDS);
        coordinator.completeTick();
        var resumed = new FutureTask<Void>(workers.remove(), null);
        Thread.ofPlatform().start(resumed);
        resumed.get(5, TimeUnit.SECONDS);
        coordinator.completeTick();

        assertThat(computations).hasSize(2).doesNotContain(serverThread);
        assertThat(validations).isNotEmpty().containsOnly(serverThread);
        assertThat(outcomes).singleElement().isInstanceOf(MachineAsyncCoordinator.TaskOutcome.Succeeded.class);
    }

    @Test
    void stale_task_is_cancelled_before_dispatching_worker_computation() {
        Queue<Runnable> workers = new ArrayDeque<>();
        var coordinator = MachineAsyncCoordinator.forTesting(workers::add);
        List<MachineAsyncCoordinator.TaskOutcome> outcomes = new ArrayList<>();
        coordinator.submitDetailed(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L),
                ignored -> AsyncContinuation.Yield.complete(), null,
                new MachineAsyncCoordinator.TaskHooks(() -> false, (key, outcome) -> outcomes.add(outcome)));
        coordinator.completeTick();

        assertThat(workers).isEmpty();
        assertThat(outcomes).singleElement().isInstanceOf(MachineAsyncCoordinator.TaskOutcome.Cancelled.class);
    }

    @Test
    void validation_after_a_callback_prevents_safe_forwarding_of_a_stale_epoch() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run, 4);
        AtomicBoolean current = new AtomicBoolean(true);
        List<String> phases = new ArrayList<>();
        List<MachineAsyncCoordinator.TaskOutcome> outcomes = new ArrayList<>();
        coordinator.submitDetailed(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> current.set(false)),
                        result -> safeLoop(phases)), null, new MachineAsyncCoordinator.TaskHooks(current::get,
                (key, outcome) -> outcomes.add(outcome)));
        coordinator.completeTick();
        assertThat(phases).isEmpty();
        assertThat(outcomes).singleElement().isInstanceOf(MachineAsyncCoordinator.TaskOutcome.Cancelled.class);
    }

    @Test
    void cancellation_by_the_resume_factory_prevents_even_pure_forwarded_advancement() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run, 4);
        var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L, MachineWorkMode.SEMI_SYNC);
        AtomicInteger advances = new AtomicInteger();
        List<MachineAsyncCoordinator.TaskOutcome> outcomes = new ArrayList<>();
        AsyncContinuation safe = new AsyncContinuation() {
            @Override public boolean canAdvanceOnMainThread() { return true; }
            @Override public Yield advance(AsyncExecutionContext context) {
                assertThat(context.workMode()).isEqualTo(MachineWorkMode.SEMI_SYNC);
                advances.incrementAndGet();
                return Yield.complete();
            }
        };
        coordinator.submitDetailed(key, ignored -> AsyncContinuation.Yield.mainThread(MainThreadStep.Result::success,
                result -> {
                    coordinator.cancel(key);
                    return safe;
                }), null, new MachineAsyncCoordinator.TaskHooks(() -> true, (taskKey, outcome) -> outcomes.add(outcome)));
        coordinator.completeTick();
        assertThat(advances).hasValue(0);
        assertThat(outcomes).singleElement().isInstanceOf(MachineAsyncCoordinator.TaskOutcome.Cancelled.class);
    }

    @Test
    void a_safe_forwarding_exception_terminates_once_without_escaping_the_fence() {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run, 4);
        List<MachineAsyncCoordinator.TaskOutcome> outcomes = new ArrayList<>();
        AsyncContinuation failing = new AsyncContinuation() {
            @Override public boolean canAdvanceOnMainThread() { return true; }
            @Override public Yield advance(AsyncExecutionContext context) { throw new IllegalStateException("forward failure"); }
        };
        coordinator.submitDetailed(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L), ignored ->
                AsyncContinuation.Yield.mainThread(MainThreadStep.Result::success, result -> failing), null,
                new MachineAsyncCoordinator.TaskHooks(() -> true, (key, outcome) -> outcomes.add(outcome)));
        assertThatCode(coordinator::completeTick).doesNotThrowAnyException();
        coordinator.completeTick();
        assertThat(outcomes).singleElement().isInstanceOf(MachineAsyncCoordinator.TaskOutcome.Failed.class);
    }

    @Test
    void old_safe_loop_and_deferred_work_share_bounded_rounds_with_newer_ready_peers() {
        ManualExecutor executor = new ManualExecutor();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(executor, 4, false);
        List<String> phases = new ArrayList<>();
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L), safeLoop(phases));
        executor.runNext();
        coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(new BlockPos(1, 0, 0), 7L),
                new MainThreadStep.Deferred(MainThreadStep.Kind.BEFORE_START, () -> phases.add("deferred")),
                null, MachineAsyncCoordinator.TaskHooks.defaults());
        for (long gameTime = 8L; gameTime <= 10L; gameTime++) {
            String peer = "peer-" + gameTime;
            coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(new BlockPos(2, 0, 0), gameTime),
                    new MainThreadStep.TestStep(() -> phases.add(peer)), null, MachineAsyncCoordinator.TaskHooks.defaults());
            coordinator.beginLevelTick(gameTime);
            int before = phases.size();
            coordinator.completeTick();
            assertThat(phases.subList(before, phases.size())).hasSize(4).contains(peer);
            coordinator.completeTick();
            assertThat(phases).hasSize(before + 4);
        }
        assertThat(phases.subList(0, 4)).containsExactly("loop", "deferred", "peer-8", "loop");
        assertThat(executor.pendingTaskCount()).isZero();
    }

    @Test
    void worker_admission_captures_its_entry_tail_once_and_keeps_a_ready_peer_runnable() {
        AtomicBoolean rejecting = new AtomicBoolean(true);
        AtomicBoolean appendTail = new AtomicBoolean(true);
        AtomicReference<MachineAsyncCoordinator> owner = new AtomicReference<>();
        Queue<Runnable> workers = new ArrayDeque<>();
        List<String> phases = new ArrayList<>();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(command -> {
            if (rejecting.get()) throw new RejectedExecutionException("saturated");
            workers.add(command);
            if (appendTail.getAndSet(false)) {
                rejecting.set(true);
                owner.get().submit(new MachineAsyncCoordinator.TaskKey(new BlockPos(3, 0, 0), 8L), ignored -> {
                    phases.add("tail-worker");
                    return AsyncContinuation.Yield.complete();
                });
                rejecting.set(false);
            }
        }, 2, false);
        owner.set(coordinator);
        coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L),
                new MainThreadStep.Deferred(MainThreadStep.Kind.BEFORE_START, () -> { }), null,
                MachineAsyncCoordinator.TaskHooks.defaults());
        coordinator.beginLevelTick(7L);
        coordinator.completeTick();
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(new BlockPos(1, 0, 0), 8L), ignored -> {
            phases.add("head-worker");
            return AsyncContinuation.Yield.complete();
        });
        coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(new BlockPos(2, 0, 0), 8L),
                new MainThreadStep.TestStep(() -> phases.add("ready-peer")), null, MachineAsyncCoordinator.TaskHooks.defaults());
        rejecting.set(false);
        coordinator.beginLevelTick(8L);
        coordinator.completeTick();
        assertThat(workers).hasSize(1);
        assertThat(phases).containsExactly("ready-peer");
        workers.remove().run();
        coordinator.beginLevelTick(9L);
        coordinator.completeTick();
        assertThat(workers).hasSize(1);
        workers.remove().run();
        assertThat(phases).containsExactly("ready-peer", "head-worker", "tail-worker");
    }

    @Test
    void cancelled_and_stale_worker_polls_count_toward_the_entry_capture() {
        AtomicReference<MachineAsyncCoordinator> owner = new AtomicReference<>();
        AtomicInteger tailValidations = new AtomicInteger();
        AtomicBoolean stale = new AtomicBoolean();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(command -> {
            throw new RejectedExecutionException("saturated");
        }, 2, false);
        owner.set(coordinator);
        coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L),
                new MainThreadStep.Deferred(MainThreadStep.Kind.BEFORE_START, () -> { }), null,
                MachineAsyncCoordinator.TaskHooks.defaults());
        coordinator.beginLevelTick(7L);
        coordinator.completeTick();
        var cancelled = new MachineAsyncCoordinator.TaskKey(new BlockPos(1, 0, 0), 8L);
        coordinator.submit(cancelled, ignored -> AsyncContinuation.Yield.complete());
        coordinator.cancel(cancelled);
        coordinator.submitDetailed(new MachineAsyncCoordinator.TaskKey(new BlockPos(2, 0, 0), 8L),
                ignored -> AsyncContinuation.Yield.complete(), null, new MachineAsyncCoordinator.TaskHooks(() -> {
                    if (!stale.get()) return true;
                    AtomicBoolean tailWaiting = new AtomicBoolean();
                    owner.get().submitDetailed(new MachineAsyncCoordinator.TaskKey(new BlockPos(3, 0, 0), 8L),
                            ignored -> AsyncContinuation.Yield.complete(), null,
                            new MachineAsyncCoordinator.TaskHooks(() -> {
                                if (!tailWaiting.get()) return true;
                                tailValidations.incrementAndGet();
                                return false;
                            }, (key, outcome) -> { }));
                    tailWaiting.set(true);
                    return false;
                }, (key, outcome) -> { }));
        stale.set(true);
        coordinator.beginLevelTick(8L);
        coordinator.completeTick();
        assertThat(tailValidations).hasValue(0);
        coordinator.beginLevelTick(9L);
        coordinator.completeTick();
        assertThat(tailValidations).hasValue(1);
    }

    @Test
    void zero_main_budget_preserves_bounded_oldest_worker_admission_without_business_execution() {
        AtomicBoolean rejecting = new AtomicBoolean(true);
        Queue<Runnable> workers = new ArrayDeque<>();
        List<String> phases = new ArrayList<>();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(command -> {
            if (rejecting.get()) throw new RejectedExecutionException("saturated");
            workers.add(command);
        }, 1, false);
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 7L), ignored -> {
            phases.add("old-worker");
            return AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> phases.add("old-main")),
                    result -> context -> AsyncContinuation.Yield.complete());
        });
        coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(new BlockPos(1, 0, 0), 7L),
                new MainThreadStep.TestStep(() -> phases.add("charged")), null, MachineAsyncCoordinator.TaskHooks.defaults());
        coordinator.beginLevelTick(7L);
        coordinator.completeTick();
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(new BlockPos(2, 0, 0), 8L), ignored -> {
            phases.add("new-worker");
            return AsyncContinuation.Yield.complete();
        });
        coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(new BlockPos(3, 0, 0), 8L),
                new MainThreadStep.TestStep(() -> phases.add("new-main")), null, MachineAsyncCoordinator.TaskHooks.defaults());
        rejecting.set(false);
        coordinator.beginLevelTick(7L);
        coordinator.completeTick();
        assertThat(workers).hasSize(1);
        workers.remove().run();
        coordinator.completeTick();
        assertThat(workers).isEmpty();
        assertThat(phases).containsExactly("charged", "old-worker");
        coordinator.beginLevelTick(8L);
        coordinator.completeTick();
        assertThat(phases).containsExactly("charged", "old-worker", "old-main");
    }

    @Test
    void fully_used_budgets_retire_ready_registrations_each_game_time() throws Exception {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run, 1, false);
        AtomicInteger commits = new AtomicInteger();
        for (long gameTime = 0L; gameTime < 128L; gameTime++) {
            coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, gameTime),
                    new MainThreadStep.TestStep(commits::incrementAndGet), null,
                    MachineAsyncCoordinator.TaskHooks.defaults());
            coordinator.beginLevelTick(gameTime);
            coordinator.completeTick();

            assertThat(commits).hasValue((int) gameTime + 1);
            assertThat(readyBatchCount(coordinator)).isZero();
        }
    }

    @Test
    void repeated_publications_register_one_batch_and_retire_newer_batches_behind_an_old_worker() throws Exception {
        ManualExecutor workers = new ManualExecutor();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(workers, 2, false);
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, -1L),
                ignored -> AsyncContinuation.Yield.complete());
        AtomicInteger commits = new AtomicInteger();
        for (long gameTime = 0L; gameTime < 128L; gameTime++) {
            for (int lane = 0; lane < 2; lane++) {
                coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(new BlockPos(lane + 1, 0, 0), gameTime),
                        new MainThreadStep.TestStep(commits::incrementAndGet), null,
                        MachineAsyncCoordinator.TaskHooks.defaults());
            }
            assertThat(readyBatchCount(coordinator)).isEqualTo(1);
            coordinator.beginLevelTick(gameTime);
            coordinator.completeTick();
            assertThat(commits).hasValue(((int) gameTime + 1) * 2);
            assertThat(readyBatchCount(coordinator)).isZero();
        }
        workers.runNext();
        coordinator.completeTick();
        assertThat(workers.pendingTaskCount()).isZero();
    }

    @Test
    void retiring_a_captured_peer_does_not_admit_a_callback_created_batch_into_the_round() {
        ManualExecutor workers = new ManualExecutor();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(workers, 4, false);
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 0L),
                ignored -> AsyncContinuation.Yield.complete());
        List<String> phases = new ArrayList<>();
        var cancelled = new MachineAsyncCoordinator.TaskKey(new BlockPos(2, 0, 0), 2L);
        coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(new BlockPos(1, 0, 0), 1L),
                new MainThreadStep.TestStep(() -> {
                    phases.add("first");
                    coordinator.cancel(cancelled);
                    coordinator.pumpMainThreadSteps();
                    coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(new BlockPos(3, 0, 0), 3L),
                            new MainThreadStep.TestStep(() -> phases.add("callback")), null,
                            MachineAsyncCoordinator.TaskHooks.defaults());
                }), null, MachineAsyncCoordinator.TaskHooks.defaults());
        coordinator.submitMainThread(cancelled, new MainThreadStep.TestStep(() -> phases.add("cancelled")), null,
                MachineAsyncCoordinator.TaskHooks.defaults());
        coordinator.beginLevelTick(3L);
        coordinator.completeTick();

        assertThat(phases).containsExactly("first");
        coordinator.completeTick();
        assertThat(phases).containsExactly("first", "callback");
    }

    @Test
    void a_worker_yield_after_its_batch_retires_cannot_republish_a_ready_registration() throws Exception {
        ManualExecutor workers = new ManualExecutor();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(workers, 1, false);
        var key = new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 1L);
        coordinator.submit(key, ignored -> {
            coordinator.cancel(key);
            coordinator.completeTick();
            return AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> { }),
                    result -> context -> AsyncContinuation.Yield.complete());
        });
        coordinator.beginLevelTick(1L);
        workers.runNext();
        assertThat(readyBatchCount(coordinator)).isZero();
        assertThat(coordinator.hasPendingMainStepForTesting()).isFalse();
    }

    private static Object receiptField(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void replaceReceiptField(MachineAsyncCoordinator coordinator, String name, Object value) throws Exception {
        var field = MachineAsyncCoordinator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(coordinator, value);
    }

    @SuppressWarnings("unchecked")
    private static Map<MachineAsyncCoordinator.ExecutionDomain, Object> receiptDrains(MachineAsyncCoordinator coordinator)
            throws Exception {
        return (Map<MachineAsyncCoordinator.ExecutionDomain, Object>) receiptField(coordinator, "receiptDrains");
    }

    private static Object receiptMailbox(Object drain, ExecutionIdentity identity) throws Exception {
        synchronized (drain) {
            return ((Map<?, ?>) receiptField(drain, "mailboxes")).get(identity);
        }
    }

    private static void closeReceiptCoordinator(MachineAsyncCoordinator coordinator) throws Exception {
        var close = MachineAsyncCoordinator.class.getDeclaredMethod("cancelAll");
        close.setAccessible(true);
        close.invoke(coordinator);
    }

    private static void awaitReceiptLatch(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for receipt interleaving");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for receipt interleaving", exception);
        }
    }

    private static void awaitReceiptMonitor(Thread thread, Object monitor) {
        var threads = ManagementFactory.getThreadMXBean();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            var info = threads.getThreadInfo(thread.threadId());
            if (info != null && info.getThreadState() == Thread.State.BLOCKED && info.getLockInfo() != null
                    && info.getLockInfo().getIdentityHashCode() == System.identityHashCode(monitor)) return;
            if (!thread.isAlive() || System.nanoTime() >= deadline) {
                throw new AssertionError("Receipt worker did not reach the held lifecycle monitor");
            }
            Thread.onSpinWait();
        }
    }

    /** A real ACK map with a one-shot barrier/failure at the publication boundary. @author howxu <dev@howxu.cn> */
    private static final class ReceiptAcknowledgements extends ConcurrentHashMap<ExecutionIdentity, Acknowledgement> {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicBoolean pauseNextMerge = new AtomicBoolean(true);
        private final List<Acknowledgement> merged = new CopyOnWriteArrayList<>();
        private final boolean failFirst;

        private ReceiptAcknowledgements(boolean failFirst) {
            this.failFirst = failFirst;
        }

        @Override
        public Acknowledgement merge(ExecutionIdentity identity, Acknowledgement acknowledgement,
                                     BiFunction<? super Acknowledgement, ? super Acknowledgement,
                                             ? extends Acknowledgement> remapping) {
            if (pauseNextMerge.getAndSet(false)) {
                entered.countDown();
                awaitReceiptLatch(release);
                if (failFirst) throw new IllegalStateException("Injected ACK publication failure");
            }
            Acknowledgement result = super.merge(identity, acknowledgement, remapping);
            merged.add(result);
            return result;
        }
    }

    /** Pauses a completed lock-free lookup, never a CHM bin lock. @author howxu <dev@howxu.cn> */
    private static final class PausingReceiptDrains extends ConcurrentHashMap<MachineAsyncCoordinator.ExecutionDomain, Object> {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicBoolean pauseNextRead = new AtomicBoolean();

        private PausingReceiptDrains(Map<MachineAsyncCoordinator.ExecutionDomain, Object> drains) {
            super(drains);
        }

        @Override
        public Object get(Object domain) {
            Object drain = super.get(domain);
            if (pauseNextRead.getAndSet(false)) {
                entered.countDown();
                awaitReceiptLatch(release);
            }
            return drain;
        }
    }

    private static int readyBatchCount(MachineAsyncCoordinator coordinator) throws Exception {
        var field = MachineAsyncCoordinator.class.getDeclaredField("readyBatches");
        field.setAccessible(true);
        Object queue = field.get(coordinator);
        var size = queue.getClass().getDeclaredMethod("size");
        size.setAccessible(true);
        return (int) size.invoke(queue);
    }

    private static AsyncContinuation safeLoop(List<String> phases) {
        return new AsyncContinuation() {
            @Override public boolean canAdvanceOnMainThread() { return true; }
            @Override public Yield advance(AsyncExecutionContext context) {
                return Yield.mainThread(new MainThreadStep.TestStep(() -> phases.add("loop")), result -> this);
            }
        };
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
