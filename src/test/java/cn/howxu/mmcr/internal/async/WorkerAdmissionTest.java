package cn.howxu.mmcr.internal.async;

import org.junit.jupiter.api.Test;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies nonblocking worker admission and exactly-once capacity release.
 * @author howxu <dev@howxu.cn>
 */
class WorkerAdmissionTest {
    @Test
    void saturation_does_not_submit_or_execute_on_the_calling_thread() {
        Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        AtomicInteger submissions = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();
        WorkerAdmission workers = new WorkerAdmission(command -> {
            submissions.incrementAndGet();
            tasks.add(command);
        }, 1);
        assertThat(workers.tryExecute(executions::incrementAndGet)).isTrue();
        for (int index = 0; index < 100; index++) {
            assertThat(workers.tryExecute(executions::incrementAndGet)).isFalse();
        }
        assertThat(submissions).hasValue(1);
        assertThat(executions).hasValue(0);
        tasks.remove().run();
        assertThat(executions).hasValue(1);
        assertThat(workers.tryExecute(executions::incrementAndGet)).isTrue();
        assertThat(workers.tryExecute(executions::incrementAndGet)).isFalse();
    }

    @Test
    void a_completed_command_can_release_capacity_before_its_pool_worker_is_ready() throws Exception {
        CountDownLatch unwinding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicInteger rejections = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), (command, pool) -> {
                    rejections.incrementAndGet();
                    throw new RejectedExecutionException("unexpected queue overflow");
                }) {
            @Override
            protected void afterExecute(Runnable command, Throwable failure) {
                unwinding.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("worker did not resume");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
            }
        };
        try {
            WorkerAdmission workers = new WorkerAdmission(executor, 1);
            assertThat(workers.tryExecute(() -> {})).isTrue();
            assertThat(unwinding.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(workers.tryExecute(completed::countDown)).isTrue();
            assertThat(workers.tryExecute(() -> {})).isFalse();
            assertThat(completed.getCount()).isEqualTo(1L);
            assertThat(rejections).hasValue(0);
        } finally {
            release.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(completed.getCount()).isZero();
        assertThat(rejections).hasValue(0);
    }

    @Test
    void concurrent_submitters_share_one_capacity_reservation() throws Exception {
        Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        WorkerAdmission workers = new WorkerAdmission(tasks::add, 1);
        CountDownLatch start = new CountDownLatch(1);
        FutureTask<Boolean> first = new FutureTask<>(() -> {
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return workers.tryExecute(() -> {});
        });
        FutureTask<Boolean> second = new FutureTask<>(() -> {
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return workers.tryExecute(() -> {});
        });
        new Thread(first).start();
        new Thread(second).start();
        start.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS) ^ second.get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(tasks).hasSize(1);
        tasks.remove().run();
        assertThat(workers.tryExecute(() -> {})).isTrue();
        assertThat(workers.tryExecute(() -> {})).isFalse();
    }

    @Test
    void executor_rejection_releases_capacity_for_a_later_attempt() {
        Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        AtomicReference<Executor> delegate = new AtomicReference<>(command -> {
            throw new RejectedExecutionException("closed executor");
        });
        WorkerAdmission workers = new WorkerAdmission(command -> delegate.get().execute(command), 1);
        assertThat(workers.tryExecute(() -> {})).isFalse();
        delegate.set(tasks::add);
        assertThat(workers.tryExecute(() -> {})).isTrue();
        assertThat(workers.tryExecute(() -> {})).isFalse();
    }

    @Test
    void executor_fault_releases_capacity_and_is_not_misreported_as_saturation() {
        Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        IllegalStateException failure = new IllegalStateException("broken executor");
        AtomicReference<Executor> delegate = new AtomicReference<>(command -> { throw failure; });
        WorkerAdmission workers = new WorkerAdmission(command -> delegate.get().execute(command), 1);
        assertThatThrownBy(() -> workers.tryExecute(() -> {})).isSameAs(failure);
        delegate.set(command -> { throw new AssertionError("executor error"); });
        assertThatThrownBy(() -> workers.tryExecute(() -> {})).isInstanceOf(AssertionError.class);
        delegate.set(tasks::add);
        assertThat(workers.tryExecute(() -> {})).isTrue();
        assertThat(workers.tryExecute(() -> {})).isFalse();
    }

    @Test
    void inline_task_failure_releases_capacity_only_once() {
        Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        AtomicReference<Executor> delegate = new AtomicReference<>(Runnable::run);
        WorkerAdmission workers = new WorkerAdmission(command -> delegate.get().execute(command), 1);
        RejectedExecutionException failure = new RejectedExecutionException("task failure, not submission rejection");
        assertThatThrownBy(() -> workers.tryExecute(() -> { throw failure; })).isSameAs(failure);
        delegate.set(tasks::add);
        assertThat(workers.tryExecute(() -> {})).isTrue();
        assertThat(workers.tryExecute(() -> {})).isFalse();
    }

    @Test
    void asynchronously_failing_task_releases_capacity() {
        Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        WorkerAdmission workers = new WorkerAdmission(tasks::add, 1);
        assertThat(workers.tryExecute(() -> { throw new AssertionError("worker failed"); })).isTrue();
        assertThatThrownBy(() -> tasks.remove().run()).isInstanceOf(AssertionError.class);
        assertThat(workers.tryExecute(() -> {})).isTrue();
        assertThat(workers.tryExecute(() -> {})).isFalse();
    }
}
