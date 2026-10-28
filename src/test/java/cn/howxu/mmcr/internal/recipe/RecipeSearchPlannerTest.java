package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Candidate;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Port;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Overlap;
import java.util.Set;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static cn.howxu.mmcr.internal.recipe.RecipeSearchTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** @author howxu <dev@howxu.cn> */
class RecipeSearchPlannerTest {
    @Test
    void lower_parallelism_is_solved_without_requesting_a_proof() {
        assertThat(new RecipeSearchPlanner(singleResourceSnapshot(8L, 256L)).advance())
                .isInstanceOfSatisfying(RecipeSearchPlanner.Match.class,
                        match -> assertThat(match.parallelism()).isEqualTo(8L));
    }

    @Test
    void missing_inputs_are_a_definite_failure_not_a_proof_request() {
        assertThat(new RecipeSearchPlanner(singleResourceSnapshot(0L, 256L)).advance())
                .isInstanceOfSatisfying(RecipeSearchPlanner.NoMatch.class, result ->
                        assertThat(result.failures()).singleElement().satisfies(failure ->
                                assertThat(failure.reasonId()).isEqualTo(BuiltinFailureReasons.MISSING_INPUT.id())));
    }

    @Test
    void shared_requirements_and_alias_views_cannot_double_count_inventory() {
        assertThat(new RecipeSearchPlanner(sharedPoolSnapshot()).advance())
                .isInstanceOfSatisfying(RecipeSearchPlanner.Match.class,
                        match -> assertThat(match.parallelism()).isEqualTo(4L));
    }

    @Test
    void selects_the_first_feasible_isolated_scope_not_the_largest_parallelism() {
        var snapshot = twoScopeSnapshot(2L, 8L);
        assertThat(new RecipeSearchPlanner(snapshot).advance())
                .isInstanceOfSatisfying(RecipeSearchPlanner.Match.class, match -> {
                    assertThat(match.parallelism()).isEqualTo(2L);
                    assertThat(match.scope()).isEqualTo(snapshot.candidates().getFirst().scopes().getFirst().input());
                });
    }

    @Test
    void insufficient_isolated_sources_are_never_pooled() {
        var source = twoScopeSnapshot(1L, 1L);
        var candidate = source.candidates().getFirst();
        var rule = input(0, 2L, 1F, candidate.rules().getFirst().ports());
        assertThat(new RecipeSearchPlanner(snapshot(source.pools(),
                List.of(candidate("a", 1L, List.of(rule), candidate.scopes())))).advance())
                .isInstanceOf(RecipeSearchPlanner.NoMatch.class);
    }

    @Test
    void failed_candidate_reservations_do_not_pollute_the_next_candidate() {
        var port = new Port(0, 0, 0L, Long.MAX_VALUE);
        var first = candidate("a", 1L, List.of(input(0, 3L, 1F, List.of(port)),
                input(1, 3L, 1F, List.of(port))), List.of(LEGACY));
        var second = candidate("b", 1L, List.of(input(0, 4L, 1F, List.of(port))), List.of(LEGACY));
        assertThat(new RecipeSearchPlanner(snapshot(List.of(pool(4L)), List.of(first, second))).advance())
                .isInstanceOfSatisfying(RecipeSearchPlanner.Match.class,
                        match -> assertThat(match.candidateIndex()).isEqualTo(1));
    }

    @Test
    void partial_outputs_accept_space_that_full_outputs_reject() {
        Port output = new Port(0, 0, 3L, Long.MAX_VALUE);
        List<AsyncCapabilitySnapshot> pools = List.of(pool(0L));
        assertThat(new RecipeSearchPlanner(snapshot(pools, List.of(candidate("full", 4L,
                List.of(output(0, 5L, false, 1F, List.of(output))), List.of(LEGACY))))).advance())
                .isInstanceOf(RecipeSearchPlanner.NoMatch.class);
        assertThat(new RecipeSearchPlanner(snapshot(pools, List.of(candidate("partial", 4L,
                List.of(output(0, 5L, true, 1F, List.of(output))), List.of(LEGACY))))).advance())
                .isInstanceOfSatisfying(RecipeSearchPlanner.Match.class,
                        match -> assertThat(match.parallelism()).isEqualTo(4L));
    }

    @Test
    void catalysts_require_presence_but_do_not_consume_shared_inputs() {
        Port port = new Port(0, 0, 0L, Long.MAX_VALUE);
        var rules = List.of(input(0, 1L, 0F, List.of(port)), input(1, 1L, 1F, List.of(port)));
        assertThat(new RecipeSearchPlanner(snapshot(List.of(pool(8L)),
                List.of(candidate("a", 16L, rules, List.of(LEGACY))))).advance())
                .isInstanceOfSatisfying(RecipeSearchPlanner.Match.class,
                        match -> assertThat(match.parallelism()).isEqualTo(8L));
        assertThat(new RecipeSearchPlanner(snapshot(List.of(pool(0L)),
                List.of(candidate("a", 16L, rules, List.of(LEGACY))))).advance())
                .isInstanceOf(RecipeSearchPlanner.NoMatch.class);
    }

    @Test
    void probability_samples_are_reused_across_scopes_and_parallelism_trials() {
        var source = twoScopeSnapshot(0L, 8L);
        var candidate = source.candidates().getFirst();
        AtomicInteger draws = new AtomicInteger();
        var rule = input(0, 1L, 0.5F, candidate.rules().getFirst().ports());
        var planner = new RecipeSearchPlanner(snapshot(source.pools(),
                List.of(candidate("a", 10L, List.of(rule), candidate.scopes()))), () -> {
            draws.incrementAndGet();
            return 0.25D;
        });
        assertThat(planner.advance()).isInstanceOfSatisfying(RecipeSearchPlanner.Match.class,
                match -> assertThat(match.parallelism()).isEqualTo(8L));
        assertThat(draws).hasValue(10);
        planner.advance();
        assertThat(draws).hasValue(10);
    }

    @Test
    void overlap_preserves_the_direction_of_unsupported_item_enumeration() {
        var ports = List.of(new Port(0, 0, 0L, Long.MAX_VALUE));
        var firstRules = List.of(input(0, 2L, 1F, ports), input(1, 1L, 1F, ports));
        var emptyDeclaration = new Overlap(Set.of(), false, true, false);
        var unsupportedDeclaration = new Overlap(Set.of(), true, true, false);
        var first = new Candidate(candidate("earlier", 1L, firstRules, List.of(LEGACY)).id(), 0, 2, 1L, null,
                firstRules, List.of(LEGACY), emptyDeclaration, null, -1);
        var second = new Candidate(candidate("later", 1L, List.of(), List.of(LEGACY)).id(), 0, 1, 1L, null,
                List.of(input(0, 1L, 1F, ports)), List.of(LEGACY), unsupportedDeclaration, null, -1);
        assertThat(new RecipeSearchPlanner(snapshot(List.of(pool(1L)), List.of(first, second))).advance())
                .isInstanceOfSatisfying(RecipeSearchPlanner.Match.class, match -> assertThat(match.conflictProne()).isFalse());
        var reversed = new Candidate(first.id(), 0, 2, 1L, null, firstRules, List.of(LEGACY), unsupportedDeclaration, null, -1);
        var emptySecond = new Candidate(second.id(), 0, 1, 1L, null, second.rules(), List.of(LEGACY), emptyDeclaration, null, -1);
        assertThat(new RecipeSearchPlanner(snapshot(List.of(pool(1L)), List.of(reversed, emptySecond))).advance())
                .isInstanceOfSatisfying(RecipeSearchPlanner.Match.class, match -> assertThat(match.conflictProne()).isTrue());
    }

    @Test
    void level_blocked_opaque_candidate_keeps_conflict_evidence_and_level_failure_precedence() {
        var ports = List.of(new Port(0, 0, 0L, Long.MAX_VALUE));
        var overlap = new Overlap(Set.of(IRON.resourceId()), false, true, false);
        var earlier = new Candidate(candidate("earlier", 1L, List.of(), List.of(LEGACY)).id(), 0, 2, 1L,
                BuiltinFailureReasons.LEVEL_INSUFFICIENT.id(), List.of(), List.of(LEGACY), overlap,
                RecipeSearchProof.Reason.OPAQUE_REQUIREMENT, 0);
        var later = new Candidate(candidate("later", 1L, List.of(), List.of(LEGACY)).id(), 0, 1, 1L, null,
                List.of(input(0, 1L, 1F, ports)), List.of(LEGACY), overlap, null, -1);
        for (boolean fallback : List.of(false, true)) {
            var planner = new RecipeSearchPlanner(snapshot(List.of(pool(1L)),
                    fallback ? List.of(earlier, later) : List.of(earlier)));
            var request = ((RecipeSearchPlanner.NeedsProof) planner.advance()).request();
            planner.acceptProof(new RecipeSearchProof.CandidateResult(request.sequence(), 0, false, 0L, null,
                    List.of(new RecipeSearchPlanner.Failure(0, 0, BuiltinFailureReasons.MISSING_INPUT.id(),
                            0, true, LEGACY.input()))));
            if (fallback) {
                assertThat(planner.advance()).isInstanceOfSatisfying(RecipeSearchPlanner.Match.class, match -> {
                    assertThat(match.candidateIndex()).isEqualTo(1);
                    assertThat(match.conflictProne()).isTrue();
                });
            } else {
                assertThat(planner.advance()).isInstanceOfSatisfying(RecipeSearchPlanner.NoMatch.class, failure ->
                        assertThat(failure.failures()).singleElement().satisfies(evidence ->
                                assertThat(evidence.reasonId()).isEqualTo(BuiltinFailureReasons.LEVEL_INSUFFICIENT.id())));
            }
        }
    }

    @Test
    void maximum_long_parallelism_terminates_without_overflow_or_per_batch_allocation() {
        assertThat(new RecipeSearchPlanner(singleResourceSnapshot(Long.MAX_VALUE, Long.MAX_VALUE)).advance())
                .isInstanceOfSatisfying(RecipeSearchPlanner.Match.class,
                        match -> assertThat(match.parallelism()).isEqualTo(Long.MAX_VALUE));
    }

    @Test
    void opaque_candidate_is_proved_before_sampling_and_worker_then_continues_in_order() {
        Candidate nativeCandidate = singleResourceSnapshot(4L, 4L).candidates().getFirst();
        Candidate opaque = new Candidate(nativeCandidate.id(), 0, 1, 4L, null, nativeCandidate.rules(),
                nativeCandidate.scopes(), NO_OVERLAP, RecipeSearchProof.Reason.OPAQUE_REQUIREMENT, 0);
        AtomicInteger draws = new AtomicInteger();
        var planner = new RecipeSearchPlanner(snapshot(List.of(pool(4L)), List.of(opaque,
                candidate("b", 4L, nativeCandidate.rules(), nativeCandidate.scopes()))), () -> {
            draws.incrementAndGet();
            return 0.5D;
        });
        var request = ((RecipeSearchPlanner.NeedsProof) planner.advance()).request();
        assertThat(draws).hasValue(0);
        planner.acceptProof(new RecipeSearchProof.CandidateResult(request.sequence(), 0, false, 0L, null, List.of(
                new RecipeSearchPlanner.Failure(0, 0, BuiltinFailureReasons.MISSING_INPUT.id(), 0, false, LEGACY.input()))));
        assertThat(planner.advance()).isInstanceOfSatisfying(RecipeSearchPlanner.Match.class,
                match -> assertThat(match.candidateIndex()).isEqualTo(1));
        assertThatThrownBy(() -> planner.acceptProof(new RecipeSearchProof.OverlapResult(request.sequence(), 0, 1, true)))
                .isInstanceOf(IllegalStateException.class);
    }
}
