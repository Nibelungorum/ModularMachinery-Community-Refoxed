package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.internal.async.AsyncContinuation;
import cn.howxu.mmcr.internal.async.AsyncExecutionContext;
import cn.howxu.mmcr.internal.async.MainThreadStep;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps candidate traversal on workers while yielding narrowly identified compatibility proofs.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeSearchContinuation implements AsyncContinuation {
    private final RecipeSearchSnapshot snapshot;
    private @Nullable RecipeSearchPlanner planner;
    private final String laneId;
    private final long catalogVersion;
    private final long searchId;
    private volatile @Nullable RecipeSearchPlanner.Outcome outcome;

    public RecipeSearchContinuation(RecipeSearchSnapshot snapshot, String laneId, long catalogVersion, long searchId) {
        this.snapshot = snapshot;
        this.laneId = laneId;
        this.catalogVersion = catalogVersion;
        this.searchId = searchId;
    }

    public @Nullable RecipeSearchPlanner.Outcome outcome() { return outcome; }

    @Override
    public Yield advance(AsyncExecutionContext context) {
        if (planner == null) planner = new RecipeSearchPlanner(snapshot);
        RecipeSearchPlanner current = planner;
        RecipeSearchPlanner.Outcome result = current.advance();
        if (result instanceof RecipeSearchPlanner.NeedsProof needed) {
            return Yield.mainThread(new MainThreadStep.RecipeSearchProofStep(laneId, catalogVersion, searchId, needed.request()),
                    proof -> {
                        RecipeSearchProof.Result value = proof instanceof MainThreadStep.Result.Value received
                                && received.value() instanceof RecipeSearchProof.Result evidence ? evidence
                                : new RecipeSearchProof.Failure(needed.request().sequence(),
                                proof instanceof MainThreadStep.Result.Failure failure ? failure.cause()
                                        : new IllegalStateException("Recipe proof returned no evidence"));
                        current.acceptProof(value);
                        return this;
                    });
        }
        outcome = result;
        return Yield.mainThread(new MainThreadStep.FactorySearch(laneId, catalogVersion, searchId),
                ignored -> ignoredContext -> Yield.complete());
    }
}
