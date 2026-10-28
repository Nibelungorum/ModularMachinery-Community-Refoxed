package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.internal.async.AsyncContinuation;
import cn.howxu.mmcr.internal.async.AsyncExecutionContext;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.async.MainThreadStep;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Candidate;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static cn.howxu.mmcr.internal.recipe.RecipeSearchTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/** @author howxu <dev@howxu.cn> */
class RecipeSearchContinuationTest {
    private static final AsyncExecutionContext CONTEXT = new AsyncExecutionContext(
            new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 0L, MachineWorkMode.ASYNC, "search", 1L));

    @Test
    void native_search_publishes_a_result_without_yielding_compatibility_work() {
        var continuation = new RecipeSearchContinuation(singleResourceSnapshot(2L, 8L),
                "search", 3L, 7L);
        assertThat(continuation.canAdvanceOnMainThread()).isFalse();
        var yielded = (AsyncContinuation.Yield.MainThread) continuation.advance(CONTEXT);
        assertThat(yielded.step()).isEqualTo(new MainThreadStep.FactorySearch("search", 3L, 7L));
        assertThat(continuation.outcome()).isInstanceOf(RecipeSearchPlanner.Match.class);
    }

    @Test
    void proof_resume_installs_evidence_without_advancing_the_search_on_the_server_thread() {
        var nativeCandidate = singleResourceSnapshot(4L, 4L).candidates().getFirst();
        var opaque = new Candidate(nativeCandidate.id(), 0, 1, 4L, null, nativeCandidate.rules(),
                nativeCandidate.scopes(), NO_OVERLAP, RecipeSearchProof.Reason.OPAQUE_REQUIREMENT, 0);
        var continuation = new RecipeSearchContinuation(snapshot(List.of(pool(4L)),
                List.of(opaque)), "search", 3L, 7L);
        var yielded = (AsyncContinuation.Yield.MainThread) continuation.advance(CONTEXT);
        var proof = (MainThreadStep.RecipeSearchProofStep) yielded.step();
        assertThat(continuation.outcome()).isNull();
        AsyncContinuation resumed = yielded.resume().apply(MainThreadStep.Result.value(
                new RecipeSearchProof.CandidateResult(proof.request().sequence(), 0, true, 3L, LEGACY.input(), List.of())));
        assertThat(resumed).isSameAs(continuation);
        assertThat(continuation.outcome()).isNull();
        assertThat(resumed.canAdvanceOnMainThread()).isFalse();
        resumed.advance(CONTEXT);
        assertThat(continuation.outcome()).isInstanceOfSatisfying(RecipeSearchPlanner.Match.class,
                match -> assertThat(match.parallelism()).isEqualTo(3L));
    }

    @Test
    void proof_failure_is_terminal_instead_of_reentering_synchronous_search() {
        var nativeCandidate = singleResourceSnapshot(4L, 4L).candidates().getFirst();
        var opaque = new Candidate(nativeCandidate.id(), 0, 1, 4L, null, nativeCandidate.rules(),
                nativeCandidate.scopes(), NO_OVERLAP, RecipeSearchProof.Reason.LIVE_CAPABILITY, 0);
        var continuation = new RecipeSearchContinuation(snapshot(List.of(pool(4L)),
                List.of(opaque)), "search", 3L, 7L);
        var yielded = (AsyncContinuation.Yield.MainThread) continuation.advance(CONTEXT);
        IllegalStateException failure = new IllegalStateException("proof failed");
        yielded.resume().apply(MainThreadStep.Result.failure(failure)).advance(CONTEXT);
        assertThat(continuation.outcome()).isInstanceOfSatisfying(RecipeSearchPlanner.Failed.class,
                result -> assertThat(result.cause()).isSameAs(failure));
    }
}
