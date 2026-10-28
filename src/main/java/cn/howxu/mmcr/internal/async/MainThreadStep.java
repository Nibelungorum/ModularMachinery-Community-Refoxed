package cn.howxu.mmcr.internal.async;

import cn.howxu.mmcr.internal.recipe.AsyncRequirementPlanner;
import cn.howxu.mmcr.internal.recipe.RecipeSearchProof;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickPhase;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import java.util.List;

/**
 * A state mutation that must execute on the server thread.
 *
 * @author howxu <dev@howxu.cn>
 */
@FunctionalInterface
public interface MainThreadStep {
    Result execute();

    default Kind kind() {
        return Kind.GENERIC;
    }

    enum Kind {
        GENERIC,
        BEFORE_START,
        BEFORE_FINISH,
        CAPABILITY_TICK,
        UNSUPPORTED_REQUIREMENT,
        INTENT_COMMIT,
        SCREEN_TEXT_FLUSH,
        FACTORY_SEARCH,
        TICK_WORKSET,
        STRUCTURE_SCAN
    }

    sealed interface Result permits Result.Success, Result.Failure, Result.Pending, Result.Value {
        record Success() implements Result {
        }

        record Failure(Throwable cause) implements Result {
        }

        /** The step registered work that will resume its continuation later. */
        record Pending() implements Result {
        }

        record Value(Object value) implements Result {
        }

        static Success success() {
            return new Success();
        }

        static Failure failure(Throwable cause) {
            return new Failure(cause);
        }

        static Pending pending() {
            return new Pending();
        }

        static Value value(Object value) {
            return new Value(value);
        }
    }

    record TestStep(Runnable action) implements MainThreadStep {
        @Override
        public Result execute() {
            action.run();
            return Result.success();
        }
    }

    record Named(Kind kind, Runnable action) implements MainThreadStep {
        @Override
        public Result execute() {
            action.run();
            return Result.success();
        }
    }

    record Deferred(Kind kind, Runnable action) implements MainThreadStep {
        @Override
        public Result execute() {
            action.run();
            return Result.pending();
        }
    }

    /** Pure lifecycle boundary whose owner performs the matching server-thread callback. */
    record Lifecycle(Kind kind, String laneId, long catalogVersion) implements MainThreadStep {
        @Override
        public Result execute() {
            return Result.success();
        }
    }

    /** Identifies one capability tick phase that its owner must execute on the server thread. */
    record CapabilityTick(CapabilityTickPhase phase, String laneId, long catalogVersion) implements MainThreadStep {
        @Override
        public Kind kind() {
            return Kind.CAPABILITY_TICK;
        }

        @Override
        public Result execute() {
            return Result.success();
        }
    }

    /** Flushes recipe callback screen-text replacements on the server thread. */
    record ScreenTextFlush(Kind source, String laneId, long catalogVersion) implements MainThreadStep {
        @Override
        public Kind kind() {
            return Kind.SCREEN_TEXT_FLUSH;
        }

        @Override
        public Result execute() {
            return Result.success();
        }
    }

    /** Identifies the unsupported requirement that the owning runtime must handle on the server thread. */
    record UnsupportedRequirement(int requirementIndex, long catalogVersion,
                                  AsyncRequirementPlanner.PlanResult intent) implements MainThreadStep {
        @Override
        public Kind kind() {
            return Kind.UNSUPPORTED_REQUIREMENT;
        }

        @Override
        public Result execute() {
            return Result.success();
        }
    }

    /** Pure worker result that a server-thread owner routes to shared-IO arbitration. */
    record IntentCommit(String laneId, long catalogVersion, AsyncRequirementPlanner.PlanResult intent) implements MainThreadStep {
        @Override
        public Kind kind() {
            return Kind.INTENT_COMMIT;
        }

        @Override
        public Result execute() {
            return Result.success();
        }
    }

    /** Commits a worker-planned recipe tick and all post-input phases in one server-thread grant. */
    record TickTransitionCommit(String laneId, long catalogVersion,
                                AsyncRequirementPlanner.PlanResult intent) implements MainThreadStep {
        @Override
        public Kind kind() {
            return Kind.INTENT_COMMIT;
        }

        @Override
        public Result execute() {
            return Result.success();
        }
    }

    /** Pure ordered results produced for one SharedIO resource-domain workset. */
    record TickWorkset(long worksetId, List<AsyncRequirementPlanner.PlanResult> intents) implements MainThreadStep {
        public TickWorkset {
            intents = List.copyOf(intents);
        }

        @Override
        public Kind kind() {
            return Kind.TICK_WORKSET;
        }

        @Override
        public Result execute() {
            return Result.success();
        }
    }

    /** Marks that a worker finished pure factory planning for one lane. */
    record RecipeSearchProofStep(String laneId, long catalogVersion, long searchId,
                                 RecipeSearchProof.Request request) implements MainThreadStep {
        @Override
        public Kind kind() { return Kind.UNSUPPORTED_REQUIREMENT; }

        @Override
        public Result execute() { return Result.success(); }
    }

    record FactorySearch(String laneId, long catalogVersion, long searchId) implements MainThreadStep {
        @Override
        public Kind kind() {
            return Kind.FACTORY_SEARCH;
        }

        @Override
        public Result execute() {
            return Result.success();
        }
    }

    /** Pure structure-match result whose controller validates and applies it on the server thread. */
    record StructureScan(StructureMatcher.ScanIdentity identity, Object candidateIdentity,
                         StructureMatcher.ScanResult result) implements MainThreadStep {
        @Override
        public Kind kind() {
            return Kind.STRUCTURE_SCAN;
        }

        @Override
        public Result execute() {
            return Result.success();
        }
    }

    /** Pure lifecycle request whose owning main-thread coordinator performs the live operation. */
    record SharedIoRequest(Kind kind, String laneId, long catalogVersion) implements MainThreadStep {
        @Override
        public Result execute() {
            return Result.pending();
        }
    }
}
