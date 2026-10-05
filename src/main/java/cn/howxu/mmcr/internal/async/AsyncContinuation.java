package cn.howxu.mmcr.internal.async;

import java.util.List;
import java.util.function.Function;

/**
 * A worker continuation which can yield a step for the server thread.
 *
 * @author howxu <dev@howxu.cn>
 */
@FunctionalInterface
public interface AsyncContinuation {
    Yield advance(AsyncExecutionContext context);

    /** Opts in only when advancing yields pure state transitions without worker-only computation. */
    default boolean canAdvanceOnMainThread() {
        return false;
    }

    sealed interface Yield permits Yield.Complete, Yield.MainThread, Yield.MainThreadBatch {
        record Complete() implements Yield {
        }

        record MainThread(MainThreadStep step, Function<MainThreadStep.Result, AsyncContinuation> resume) implements Yield {
        }

        record MainThreadBatch(List<MainThreadStep> steps,
                               Function<List<MainThreadStep.Result>, AsyncContinuation> resume) implements Yield {
            public MainThreadBatch {
                steps = List.copyOf(steps);
            }
        }

        static Complete complete() {
            return new Complete();
        }

        static MainThread mainThread(MainThreadStep step, Function<MainThreadStep.Result, AsyncContinuation> resume) {
            return new MainThread(step, resume);
        }

        static MainThreadBatch mainThreadBatch(List<MainThreadStep> steps,
                                               Function<List<MainThreadStep.Result>, AsyncContinuation> resume) {
            return new MainThreadBatch(steps, resume);
        }
    }
}
