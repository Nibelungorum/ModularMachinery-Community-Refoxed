package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.compat.mekanism.MekanismPortFamilies;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Rule;
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
    void one_recipe_quantity_of_catalyst_allows_large_parallelism_for_every_resource_family() {
        long parallelism = (long) Integer.MAX_VALUE + 1L;
        for (var family : List.of(ITEM, BuiltinCapabilityDefinitions.FLUID_TYPE.id(), MekanismPortFamilies.CHEMICAL)) {
            long amount = family.equals(ITEM) ? 2L : 1_000L;
            var template = input(0, amount, 0F, List.of(new Port(0, 0, 0L, Long.MAX_VALUE)));
            Rule catalyst = new Rule(template.index(), family, template.kind(), template.direction(),
                    template.amount(), template.heat(), template.chance(), template.consumeChance(),
                    template.partial(), template.valid(), template.resources(), template.output(), template.ports(),
                    template.failureReason());
            var rules = List.of(catalyst, input(1, 1L, 1F, List.of(new Port(1, 1, 0L, Long.MAX_VALUE))),
                    output(2, 1L, false, 1F, List.of(new Port(2, 2, parallelism, Long.MAX_VALUE))));
            var candidate = candidate("catalyst", parallelism + 1L, rules, List.of(LEGACY));
            for (long available : new long[]{0L, amount - 1L, amount}) {
                var catalystPool = new AsyncCapabilitySnapshot.Resource(family,
                        ((AsyncCapabilitySnapshot.Resource) pool(available)).slots());
                var result = new RecipeSearchPlanner(snapshot(List.of(catalystPool, pool(parallelism + 1L), pool(0L)),
                        List.of(candidate))).advance();
                if (available < amount) {
                    assertThat(result).isInstanceOf(RecipeSearchPlanner.NoMatch.class);
                } else {
                    assertThat(result).isInstanceOfSatisfying(RecipeSearchPlanner.Match.class,
                            match -> assertThat(match.parallelism()).isEqualTo(parallelism));
                }
            }
        }
    }

    @Test
    void isolated_catalyst_quantities_are_not_pooled_to_satisfy_one_recipe() {
        var source = twoScopeSnapshot(1L, 1L);
        var original = source.candidates().getFirst();
        var catalyst = input(0, 2L, 0F, original.rules().getFirst().ports());
        assertThat(new RecipeSearchPlanner(snapshot(source.pools(),
                List.of(candidate("catalyst", (long) Integer.MAX_VALUE + 1L,
                        List.of(catalyst), original.scopes())))).advance())
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
    void expected_outputs_use_reduced_capacity_and_reuse_both_samples_for_every_resource_family() {
        for (var family : List.of(ITEM, BuiltinCapabilityDefinitions.FLUID_TYPE.id(), MekanismPortFamilies.CHEMICAL,
                LightningTier.HIGH_VOLTAGE.requirementId())) {
            var template = output(0, 2L, false, 0.7F, List.of(new Port(0, 0, 50L, Long.MAX_VALUE)));
            Rule rule = new Rule(template.index(), family, template.kind(), template.direction(),
                    template.amount(), template.heat(), template.chance(), template.consumeChance(),
                    template.partial(), template.valid(), template.resources(), template.output(), template.ports(),
                    template.failureReason());
            AtomicInteger draws = new AtomicInteger();
            var planner = new RecipeSearchPlanner(snapshot(List.of(pool(0L)),
                    List.of(candidate("expected", 100L, List.of(rule), List.of(LEGACY)))), () -> {
                draws.incrementAndGet();
                return 0.5D;
            });
            assertThat(planner.advance()).isInstanceOfSatisfying(RecipeSearchPlanner.Match.class,
                    match -> assertThat(match.parallelism()).isEqualTo(36L));
            assertThat(draws).hasValue(2);
            planner.advance();
            assertThat(draws).hasValue(2);
        }
    }

    @Test
    void fractional_output_samples_are_reused_across_failed_isolated_scopes() {
        var source = twoScopeSnapshot(0L, 80L);
        var original = source.candidates().getFirst();
        var rules = List.of(input(0, 1L, 1F, original.rules().getFirst().ports()),
                output(1, 1L, false, 0.7F, List.of(new Port(2, 2, 1_000L, Long.MAX_VALUE))));
        AtomicInteger draws = new AtomicInteger();
        var planner = new RecipeSearchPlanner(snapshot(List.of(source.pools().get(0), source.pools().get(1), pool(0L)),
                List.of(candidate("expected_scopes", 100L, rules, original.scopes()))), () -> {
            draws.incrementAndGet();
            return 0.5D;
        });
        assertThat(planner.advance()).isInstanceOfSatisfying(RecipeSearchPlanner.Match.class, match -> {
            assertThat(match.parallelism()).isEqualTo(80L);
            assertThat(match.scope()).isEqualTo(original.scopes().get(1).input());
        });
        assertThat(draws).hasValue(2);
    }

    @Test
    void expected_outputs_reserve_shared_capacity_in_order_instead_of_each_using_the_full_space() {
        var ports = List.of(new Port(0, 0, 50L, Long.MAX_VALUE));
        var rules = List.of(output(0, 2L, false, 0.7F, ports), output(1, 2L, false, 0.7F, ports));
        AtomicInteger draws = new AtomicInteger();
        var result = new RecipeSearchPlanner(snapshot(List.of(pool(0L)),
                List.of(candidate("shared_expected", 100L, rules, List.of(LEGACY)))), () -> {
            draws.incrementAndGet();
            return 0.5D;
        }).advance();
        assertThat(result).isInstanceOfSatisfying(RecipeSearchPlanner.Match.class,
                match -> assertThat(match.parallelism()).isEqualTo(18L));
        assertThat(draws).hasValue(4);
    }

    @Test
    void candidate_order_uses_perturbation_then_fraction_samples_for_each_output() {
        var probabilistic = output(0, 1L, false, 0.7F, List.of(new Port(0, 0, 0L, Long.MAX_VALUE)));
        var certain = output(0, 5L, false, 1F, probabilistic.ports());
        var request = snapshot(List.of(pool(0L)), List.of(
                candidate("clear", 1L, List.of(probabilistic), List.of(LEGACY)),
                candidate("rain", 1L, List.of(probabilistic), List.of(LEGACY)),
                candidate("thunder", 1L, List.of(certain), List.of(LEGACY))));
        var samples = List.of(List.of(0.5D, 0.9D), List.of(0.5D, 0.1D, 0.5D, 0.9D),
                List.of(0.5D, 0.1D, 0.5D, 0.1D));
        for (int index = 0; index < samples.size(); index++) {
            var draws = samples.get(index).iterator();
            var outcome = new RecipeSearchPlanner(request, draws::next).advance();
            if (index < 2) {
                int expected = index;
                assertThat(outcome).isInstanceOfSatisfying(RecipeSearchPlanner.Match.class,
                        match -> assertThat(match.candidateIndex()).isEqualTo(expected));
            } else assertThat(outcome).isInstanceOf(RecipeSearchPlanner.NoMatch.class);
            assertThat(draws.hasNext()).isFalse();
        }
    }

    @Test
    void output_fraction_rounding_allows_low_probability_single_outputs_without_a_port() {
        var rule = output(0, 1L, false, 0.2F, List.of());
        assertThat(new RecipeSearchPlanner(snapshot(List.of(),
                List.of(candidate("zero_tail", 1L, List.of(rule), List.of(LEGACY)))), () -> 0.5D).advance())
                .isInstanceOfSatisfying(RecipeSearchPlanner.Match.class,
                        match -> assertThat(match.parallelism()).isEqualTo(1L));
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
