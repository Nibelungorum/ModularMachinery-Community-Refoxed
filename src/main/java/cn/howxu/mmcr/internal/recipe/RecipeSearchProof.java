package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.InputScope;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Explicit, candidate-bounded compatibility proofs; never a request to search a pool.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeSearchProof {
    private RecipeSearchProof() {}

    public enum Kind { CANDIDATE, OVERLAP }
    public enum Reason { OPAQUE_REQUIREMENT, LIVE_CAPABILITY, INCOMPLETE_ROUTING, UNSAFE_OVERLAP }

    public record Request(long sequence, Kind kind, Reason reason, int candidateIndex, int relatedCandidateIndex,
                          int requirementIndex, long maximumParallelism, List<InputScope> scopes) {
        public Request {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(reason, "reason");
            scopes = List.copyOf(scopes);
            if (sequence < 1L || candidateIndex < 0 || maximumParallelism < 1L
                    || kind == Kind.CANDIDATE && (scopes.isEmpty() || relatedCandidateIndex != -1
                    || reason == Reason.UNSAFE_OVERLAP)
                    || kind == Kind.OVERLAP && (!scopes.isEmpty() || relatedCandidateIndex < 0
                    || reason != Reason.UNSAFE_OVERLAP)) throw new IllegalArgumentException("Invalid search proof");
        }
    }

    public sealed interface Result permits CandidateResult, OverlapResult, Failure {
        long sequence();
    }

    public record CandidateResult(long sequence, int candidateIndex, boolean feasible, long parallelism,
                                  @Nullable InputScope scope,
                                  List<RecipeSearchPlanner.Failure> failures) implements Result {
        public CandidateResult {
            failures = List.copyOf(failures);
            if (candidateIndex < 0 || feasible && (parallelism < 1L || scope == null)) {
                throw new IllegalArgumentException("Invalid candidate proof result");
            }
        }
    }

    public record OverlapResult(long sequence, int candidateIndex, int relatedCandidateIndex,
                                boolean overlaps) implements Result {}
    public record Failure(long sequence, Throwable cause) implements Result {
        public Failure { Objects.requireNonNull(cause, "cause"); }
    }
}
