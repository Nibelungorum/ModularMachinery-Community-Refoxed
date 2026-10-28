package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.compat.mekanism.MekanismPortFamilies;
import cn.howxu.mmcr.api.recipe.InputScope;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Candidate;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Kind;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Port;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Rule;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Scope;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.util.SaturatingLong;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * Resumable search over captured values. Advancing is worker-only in the production continuation.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeSearchPlanner {
    private final RecipeSearchSnapshot snapshot;
    private final DoubleSupplier random;
    private final State initial;
    private final State working;
    private final Map<Integer, Samples> samples = new HashMap<>();
    private final List<Failure> failures = new ArrayList<>();
    private final Map<Integer, List<Failure>> provedFailures = new HashMap<>();
    private final Map<Long, Boolean> overlaps = new HashMap<>();
    private int cursor;
    private long sequence;
    private @Nullable RecipeSearchProof.Request waiting;
    private @Nullable Match selected;
    private @Nullable Outcome complete;

    public RecipeSearchPlanner(RecipeSearchSnapshot snapshot) {
        this(snapshot, Math::random);
    }

    RecipeSearchPlanner(RecipeSearchSnapshot snapshot, DoubleSupplier random) {
        this.snapshot = snapshot;
        this.random = random;
        initial = new State(snapshot.pools());
        working = new State(snapshot.pools());
    }

    public sealed interface Outcome permits Match, NoMatch, NeedsProof, Failed {}
    public record Match(int candidateIndex, long parallelism, InputScope scope, boolean conflictProne) implements Outcome {}
    public record NoMatch(List<Failure> failures) implements Outcome {
        public NoMatch { failures = List.copyOf(failures); }
    }
    public record NeedsProof(RecipeSearchProof.Request request) implements Outcome {}
    public record Failed(Throwable cause) implements Outcome {}
    public record Failure(int candidateIndex, int requirementIndex, Identifier reasonId, int completedRequirements,
                          boolean inputInsufficientWithFeasibleOutputs, InputScope scope) {}

    public Outcome advance() {
        if (complete != null) return complete;
        if (waiting != null) return new NeedsProof(waiting);
        try {
            while (selected == null && cursor < snapshot.candidates().size()) {
                Candidate candidate = snapshot.candidates().get(cursor);
                if (candidate.staticFailure() != null
                        && !candidate.staticFailure().equals(BuiltinFailureReasons.LEVEL_INSUFFICIENT.id())) {
                    failures.add(new Failure(cursor, -1, candidate.staticFailure(), 0, false,
                            candidate.scopes().getFirst().input()));
                    cursor++;
                    continue;
                }
                // An opaque rule owns the entire attempt, including its probability samples and ordered scopes.
                if (candidate.proofReason() != null) {
                    waiting = new RecipeSearchProof.Request(++sequence, RecipeSearchProof.Kind.CANDIDATE,
                            candidate.proofReason(), cursor, -1, candidate.proofRequirementIndex(),
                            candidate.maximumParallelism(), candidate.scopes().stream().map(Scope::input).toList());
                    return new NeedsProof(waiting);
                }
                Failure best = null;
                for (Scope scope : candidate.scopes()) {
                    Attempt result = solve(cursor, scope, null, false);
                    if (result.parallelism() > 0L) {
                        if (candidate.staticFailure() == null) selected = new Match(cursor, result.parallelism(), scope.input(), false);
                        else best = new Failure(cursor, -1, candidate.staticFailure(), 0, false, scope.input());
                        break;
                    }
                    Failure failure = failure(cursor, scope, result, inputWaiting(cursor, scope));
                    if (best == null || failure.completedRequirements() > best.completedRequirements()) best = failure;
                }
                if (selected == null) {
                    if (best != null) failures.add(best);
                    cursor++;
                }
            }
            if (selected == null) return complete = new NoMatch(failures);
            return resolveConflict();
        } catch (RuntimeException exception) {
            return complete = new Failed(exception);
        }
    }

    public void acceptProof(RecipeSearchProof.Result result) {
        RecipeSearchProof.Request request = waiting;
        if (request == null || request.sequence() != result.sequence()) {
            throw new IllegalStateException("Unexpected search proof sequence");
        }
        if (result instanceof RecipeSearchProof.Failure failed) {
            waiting = null;
            complete = new Failed(failed.cause());
            return;
        }
        if (request.kind() == RecipeSearchProof.Kind.CANDIDATE
                && result instanceof RecipeSearchProof.CandidateResult candidate
                && candidate.candidateIndex() == request.candidateIndex()) {
            if (candidate.feasible()) {
                if (!request.scopes().contains(candidate.scope()) || candidate.parallelism() <= 0L
                        || candidate.parallelism() > request.maximumParallelism()) {
                    throw new IllegalStateException("Candidate proof exceeded its scope");
                }
                selected = new Match(cursor, candidate.parallelism(), candidate.scope(), false);
            } else {
                if (candidate.failures().isEmpty() || candidate.failures().stream().anyMatch(failure ->
                        failure.candidateIndex() != request.candidateIndex() || !request.scopes().contains(failure.scope()))) {
                    throw new IllegalStateException("Candidate proof omitted its failure evidence");
                }
                provedFailures.put(cursor, candidate.failures());
                Failure best = candidate.failures().getFirst();
                for (Failure failure : candidate.failures()) {
                    if (failure.completedRequirements() > best.completedRequirements()) best = failure;
                }
                failures.add(best);
                cursor++;
            }
        } else if (request.kind() == RecipeSearchProof.Kind.OVERLAP
                && result instanceof RecipeSearchProof.OverlapResult overlap
                && overlap.candidateIndex() == request.candidateIndex()
                && overlap.relatedCandidateIndex() == request.relatedCandidateIndex()) {
            overlaps.put(pair(overlap.candidateIndex(), overlap.relatedCandidateIndex()), overlap.overlaps());
        } else throw new IllegalStateException("Search proof does not match the suspended operation");
        waiting = null;
    }

    private Outcome resolveConflict() {
        Candidate chosen = snapshot.candidates().get(selected.candidateIndex());
        for (int index = 0; index < selected.candidateIndex(); index++) {
            Candidate earlier = snapshot.candidates().get(index);
            // RecipeSearchTask checks levels after start feasibility. Missing inputs of a level-blocked
            // specific recipe can therefore still delay a fallback; stage/module-ineligible recipes cannot.
            if (earlier.staticFailure() != null && !earlier.staticFailure().equals(BuiltinFailureReasons.LEVEL_INSUFFICIENT.id())
                    || earlier.priority() != chosen.priority() || earlier.inputCount() <= chosen.inputCount()) continue;
            boolean overlapping;
            if (earlier.overlap().customHandler()) {
                Boolean proof = overlaps.get(pair(selected.candidateIndex(), index));
                if (proof == null) {
                    waiting = new RecipeSearchProof.Request(++sequence, RecipeSearchProof.Kind.OVERLAP,
                            RecipeSearchProof.Reason.UNSAFE_OVERLAP, selected.candidateIndex(), index, -1,
                            chosen.maximumParallelism(), List.of());
                    return new NeedsProof(waiting);
                }
                overlapping = proof;
            } else {
                var left = earlier.overlap();
                var right = chosen.overlap();
                overlapping = left.hasItem() && right.hasItem() && (left.anyItem()
                        || !left.items().isEmpty() && right.anyItem()
                        || left.items().stream().anyMatch(right.items()::contains));
            }
            if (!overlapping) continue;
            boolean inputWaiting;
            if (earlier.proofReason() != null) {
                inputWaiting = provedFailures.getOrDefault(index, List.of()).stream()
                        .anyMatch(failure -> failure.scope().equals(selected.scope())
                                && failure.inputInsufficientWithFeasibleOutputs());
            } else {
                Scope scope = earlier.scopes().stream().filter(value -> value.input().equals(selected.scope()))
                        .findFirst().orElse(null);
                inputWaiting = scope != null && (selected.scope().kind() == InputScope.Kind.LEGACY
                        || solve(index, scope, IOType.INPUT, true).parallelism() > 0L) && inputWaiting(index, scope);
            }
            if (inputWaiting) {
                return complete = new Match(selected.candidateIndex(), selected.parallelism(), selected.scope(), true);
            }
        }
        return complete = selected;
    }

    private boolean inputWaiting(int index, Scope scope) {
        return solve(index, scope, IOType.INPUT, false).parallelism() == 0L
                && solve(index, scope, IOType.OUTPUT, false).parallelism() > 0L;
    }

    private Failure failure(int index, Scope scope, Attempt attempt, boolean inputWaiting) {
        Candidate candidate = snapshot.candidates().get(index);
        Rule rule = attempt.failedRule() < 0 ? null : candidate.rules().get(attempt.failedRule());
        Identifier reason = rule == null ? BuiltinFailureReasons.RECIPE_SEARCH.id() : rule.failureReason();
        if (rule != null && rule.type().equals(MekanismPortFamilies.CHEMICAL) && rule.direction() == IOType.INPUT
                && rule.ports().stream().noneMatch(port -> scope.allows(port.sourceIndex()))) reason = BuiltinFailureReasons.MISSING_INPUT.id();
        return new Failure(index, rule == null ? -1 : rule.index(), reason,
                Math.max(0, attempt.failedRule()), inputWaiting, scope.input());
    }

    private Attempt solve(int index, Scope scope, @Nullable IOType direction, boolean retainedOnly) {
        Candidate candidate = snapshot.candidates().get(index);
        Samples sampling = samples.computeIfAbsent(index, ignored -> new Samples());
        long upper = retainedOnly ? 1L : candidate.maximumParallelism();
        for (int ruleIndex = 0; ruleIndex < candidate.rules().size(); ruleIndex++) {
            Rule rule = candidate.rules().get(ruleIndex);
            if (!included(rule, direction, retainedOnly) || !sampling.produces(rule)) continue;
            if (!rule.valid()) return new Attempt(0L, ruleIndex);
            upper = Math.min(upper, independentMaximum(rule, scope, upper));
            if (upper == 0L) return new Attempt(0L, ruleIndex);
            if (rule.kind() == Kind.RESOURCE && rule.direction() == IOType.INPUT && rule.consumeChance() > 0F) {
                sampling.consumed(rule, 0L, candidate.maximumParallelism());
            }
        }
        long lower = 0L;
        int failed = -1;
        while (lower < upper) {
            long distance = upper - lower;
            long middle = lower + (distance >>> 1) + (distance & 1L);
            State state = working;
            state.reset(initial);
            int failure = -1;
            for (int ruleIndex = 0; ruleIndex < candidate.rules().size(); ruleIndex++) {
                Rule rule = candidate.rules().get(ruleIndex);
                if (included(rule, direction, retainedOnly) && sampling.produces(rule)
                        && !reserve(rule, scope, middle, state, sampling, candidate.maximumParallelism())) {
                    failure = ruleIndex;
                    break;
                }
            }
            if (failure < 0) lower = middle;
            else {
                failed = failure;
                upper = middle - 1L;
            }
        }
        return new Attempt(lower, failed);
    }

    private static boolean included(Rule rule, @Nullable IOType direction, boolean retainedOnly) {
        return (direction == null || rule.direction() == direction)
                && (!retainedOnly || rule.kind() == Kind.RESOURCE && rule.consumeChance() <= 0F);
    }

    private long independentMaximum(Rule rule, Scope scope, long requested) {
        if (rule.kind() == Kind.NOOP) return requested;
        if (rule.kind() == Kind.TEMPERATURE) {
            return rule.ports().stream().anyMatch(port ->
                    ((AsyncCapabilitySnapshot.Heat) snapshot.pools().get(port.pool())).temperature() >= rule.heat())
                    ? requested : 0L;
        }
        if (rule.kind() == Kind.HEAT) return rule.ports().isEmpty() ? 0L : requested;
        if (rule.amount() <= 0L) return requested;
        if (rule.kind() == Kind.SCALAR) {
            long lower = 0L;
            long upper = requested;
            while (lower < upper) {
                long distance = upper - lower;
                long middle = lower + (distance >>> 1) + (distance & 1L);
                working.reset(initial);
                if (reserveScalar(rule, middle, working)) lower = middle;
                else upper = middle - 1L;
            }
            return lower;
        }
        long available = 0L;
        for (Port port : rule.ports()) {
            if (!allowed(rule, scope, port)) continue;
            available = SaturatingLong.add(available, resourceAvailable(rule, port, initial));
        }
        if (rule.direction() == IOType.OUTPUT && rule.partial()) return available > 0L ? requested : 0L;
        long maximum = Math.min(requested, available / rule.amount());
        return rule.direction() == IOType.OUTPUT && maximum == 0L && available > 0L ? 1L : maximum;
    }

    private boolean reserve(Rule rule, Scope scope, long parallelism, State state, Samples sampling, long requested) {
        if (rule.kind() == Kind.NOOP || rule.kind() == Kind.TEMPERATURE || rule.kind() == Kind.HEAT) return true;
        if (rule.kind() == Kind.SCALAR) return reserveScalar(rule, parallelism, state);
        if (rule.direction() == IOType.INPUT && rule.consumeChance() <= 0F) return true;
        long batches = rule.direction() == IOType.INPUT ? sampling.consumed(rule, parallelism, requested) : parallelism;
        long amount = SaturatingLong.multiply(rule.amount(), batches);
        if (amount == 0L) return true;
        long remaining = amount;
        for (Port port : rule.ports()) {
            if (!allowed(rule, scope, port)) continue;
            long moved = Math.min(remaining, resourceAvailable(rule, port, state));
            if (moved <= 0L) continue;
            if (rule.direction() == IOType.INPUT) state.amounts[port.pool()] -= moved;
            else {
                state.resources[port.pool()] = rule.output();
                state.amounts[port.pool()] += moved;
            }
            remaining -= moved;
            if (remaining == 0L) return true;
        }
        return rule.direction() == IOType.OUTPUT && rule.partial() && remaining < amount;
    }

    private static boolean reserveScalar(Rule rule, long parallelism, State state) {
        long amount = SaturatingLong.multiply(rule.amount(), parallelism);
        if (amount == 0L) return true;
        long remaining = amount;
        for (Port port : rule.ports()) {
            boolean output = rule.direction() == IOType.OUTPUT;
            long available = output ? Math.max(0L, port.capacity() - state.amounts[port.pool()])
                    : Math.min(state.amounts[port.pool()], SaturatingLong.multiply(port.transferLimit(), parallelism));
            long moved = Math.min(remaining, available);
            state.amounts[port.pool()] += output ? moved : -moved;
            remaining -= moved;
            if (remaining == 0L) return true;
        }
        return rule.direction() == IOType.OUTPUT && rule.partial() && remaining < amount;
    }

    private static long resourceAvailable(Rule rule, Port port, State state) {
        AsyncResourceValue current = state.resources[port.pool()];
        if (rule.direction() == IOType.INPUT) {
            return current != null && rule.resources().contains(current)
                    ? Math.min(state.amounts[port.pool()], port.transferLimit()) : 0L;
        }
        return current == null || current.equals(rule.output())
                ? Math.max(0L, port.capacity() - state.amounts[port.pool()]) : 0L;
    }

    private static boolean allowed(Rule rule, Scope scope, Port port) {
        return !RequirementPlanner.isMaterialInput(rule.direction(), rule.type()) || scope.allows(port.sourceIndex());
    }

    private static long pair(int candidate, int earlier) { return (long) candidate << 32 | earlier & 0xffffffffL; }
    private record Attempt(long parallelism, int failedRule) {}

    private final class Samples {
        private final Map<Integer, Boolean> outputs = new HashMap<>();
        private final Map<Integer, int[]> prefixes = new LinkedHashMap<>();

        boolean produces(Rule rule) {
            return rule.direction() != IOType.OUTPUT || outputs.computeIfAbsent(rule.index(), ignored ->
                    rule.chance() >= 1F || rule.chance() > 0F && random.getAsDouble() < rule.chance());
        }

        long consumed(Rule rule, long parallelism, long requested) {
            if (rule.consumeChance() >= 1F) return parallelism;
            if (requested > 1_024L) return Math.round(parallelism * (double) rule.consumeChance());
            int[] prefix = prefixes.computeIfAbsent(rule.index(), ignored -> {
                int[] result = new int[(int) requested + 1];
                for (int index = 1; index < result.length; index++) {
                    result[index] = result[index - 1] + (random.getAsDouble() < rule.consumeChance() ? 1 : 0);
                }
                return result;
            });
            return prefix[(int) parallelism];
        }
    }

    private static final class State {
        private final long[] amounts;
        private final AsyncResourceValue[] resources;

        void reset(State initial) {
            System.arraycopy(initial.amounts, 0, amounts, 0, amounts.length);
            System.arraycopy(initial.resources, 0, resources, 0, resources.length);
        }

        State(List<AsyncCapabilitySnapshot> pools) {
            amounts = new long[pools.size()];
            resources = new AsyncResourceValue[pools.size()];
            for (int index = 0; index < pools.size(); index++) {
                switch (pools.get(index)) {
                    case AsyncCapabilitySnapshot.Resource resource -> {
                        var slot = resource.slots().getFirst();
                        amounts[index] = slot.amount();
                        resources[index] = slot.resource().orElse(null);
                    }
                    case AsyncCapabilitySnapshot.Scalar scalar -> amounts[index] = scalar.amount();
                    case AsyncCapabilitySnapshot.Heat ignored -> { }
                }
            }
        }
    }
}
