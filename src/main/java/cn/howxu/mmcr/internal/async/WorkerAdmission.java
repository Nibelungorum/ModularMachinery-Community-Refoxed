package cn.howxu.mmcr.internal.async;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

/**
 * Nonblocking admission shared by every producer of a bounded worker pool.
 * Capacity includes both queued and running commands; keeping it no larger than
 * the executor queue capacity leaves room even while completed workers unwind.
 *
 * @author howxu <dev@howxu.cn>
 */
final class WorkerAdmission {
    private final Executor executor;
    private final Semaphore capacity;

    WorkerAdmission(Executor executor, int capacity) {
        this.executor = executor;
        this.capacity = new Semaphore(capacity);
    }

    boolean tryExecute(Runnable command) {
        if (!capacity.tryAcquire()) return false;
        AdmittedTask task = new AdmittedTask(command);
        try {
            executor.execute(task);
            return true;
        } catch (RuntimeException | Error failure) {
            // Inline test executors may propagate the command's own failure.
            if (task.started) throw failure;
            capacity.release();
            if (failure instanceof RejectedExecutionException) return false;
            throw failure;
        }
    }

    /** An admitted command owns its capacity until it actually returns.
     * @author howxu <dev@howxu.cn>
     */
    private final class AdmittedTask implements Runnable {
        private final Runnable command;
        private volatile boolean started;

        private AdmittedTask(Runnable command) {
            this.command = command;
        }

        @Override
        public void run() {
            started = true;
            try {
                command.run();
            } finally {
                capacity.release();
            }
        }
    }
}
