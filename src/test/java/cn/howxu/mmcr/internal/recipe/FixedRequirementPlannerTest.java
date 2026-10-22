package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.EnergyOutputAdmissionFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.OutputFit;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import com.mojang.serialization.MapCodec;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.compat.create.StressSession;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies fixed planning against the existing planner and native partial-commit protocol.
 *
 * @author howxu <dev@howxu.cn>
 */
class FixedRequirementPlannerTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        TestBootstrap.bootstrapCapabilities();
    }

    @Test
    void energy_output_uses_capacity_even_with_zero_transfer_limit_in_both_planners() {
        for (long limit : new long[] { 512_000L, 0L }) {
            LongEnergyStorage storage = storage(2_000_000L, limit, 100_000L);
            List<MachineRequirement> requirements = List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 800_000L));
            var maximum = new RequirementPlanner().plan(requirements, List.of(output(storage)), new PlanningContext(3L, 0));
            assertThat(maximum.successful()).isTrue();
            assertThat(maximum.plan().parallelism()).isEqualTo(2L);
            var fixed = fixed(requirements, List.of(output(storage)), 2L, List.of(0), Map.of());
            assertThat(fixed.successful()).isTrue();
            assertThat(storage.getAmountAsLong()).isEqualTo(100_000L);
            assertThat(fixed.plan().commit()).isTrue();
            assertThat(storage.getAmountAsLong()).isEqualTo(1_700_000L);
            storage.setAmount(100_000L);
            assertThat(maximum.plan().commit()).isTrue();
            assertThat(storage.getAmountAsLong()).isEqualTo(1_700_000L);
        }
    }

    @Test
    void energy_output_capacity_is_shared_by_aliases_and_partial_output_never_overfills() {
        LongEnergyStorage storage = storage(1_000_000L, 0L, 100_000L);
        List<MachineCapability> capabilities = List.of(output(storage), output(storage));
        List<MachineRequirement> requirements = List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 800_000L));
        assertThat(fixed(requirements, capabilities, 2L, List.of(0), Map.of()).successful()).isFalse();
        assertThat(storage.getAmountAsLong()).isEqualTo(100_000L);
        var partial = fixed(requirements, capabilities, 2L, List.of(0), Map.of(0, OutputPolicy.ALLOW_PARTIAL));
        assertThat(partial.successful()).isTrue();
        assertThat(partial.outputSimulations()).singleElement().satisfies(simulation -> {
            assertThat(simulation.requested()).isEqualTo(1_600_000L);
            assertThat(simulation.accepted()).isEqualTo(900_000L);
        });
        assertThat(partial.plan().commit()).isTrue();
        assertThat(storage.getAmountAsLong()).isEqualTo(1_000_000L);
        assertThat(fixed(requirements, capabilities, 1L, List.of(0), Map.of()).successful()).isFalse();
    }

    @Test
    void energy_output_revalidates_capacity_without_partially_filling_a_stale_destination() {
        LongEnergyStorage storage = storage(1_000_000L, 10L, 0L);
        var result = fixed(List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 800_000L)),
                List.of(output(storage)), 1L, List.of(0), Map.of());
        assertThat(result.successful()).isTrue();
        storage.setAmount(300_000L);
        assertThat(result.plan().commit()).isFalse();
        assertThat(storage.getAmountAsLong()).isEqualTo(300_000L);
    }

    @Test
    void recipe_output_does_not_remove_input_or_external_energy_transfer_limits() {
        LongEnergyStorage storage = storage(2_000_000L, 512_000L, 0L);
        var result = fixed(List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 800_000L)),
                List.of(output(storage)), 1L, List.of(0), Map.of());
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().commit()).isTrue();
        assertThat(fixed(List.of(new EnergyRequirement(800_000L)), List.of(input(storage)),
                1L, List.of(0), Map.of()).successful()).isFalse();
        assertThat(storage.extractEnergy(800_000, true)).isEqualTo(512_000);
        assertThat(storage.receiveEnergy(800_000, true)).isEqualTo(512_000);
        assertThat(storage.getAmountAsLong()).isEqualTo(800_000L);
    }

    @Test
    void item_and_fluid_recipe_outputs_ignore_external_transfer_limits_but_respect_capacity() {
        LongItemStorage items = new LongItemStorage(2, 64L, null);
        LongFluidStorage fluids = new LongFluidStorage(4_000_000_000L, null);
        List<MachineCapability> capabilities = List.of(new ItemBusCapability(items, IOType.OUTPUT),
                new FluidHatchCapability(fluids, IOType.OUTPUT));
        List<MachineRequirement> requirements = List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(Items.IRON_INGOT, 32)),
                new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new FluidStack(Fluids.WATER, 1_000_000_000)));
        var result = fixed(requirements, capabilities, 3L, List.of(0, 1), Map.of());
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().commit()).isTrue();
        assertThat(items.amount(0)).isEqualTo(64L);
        assertThat(items.amount(1)).isEqualTo(32L);
        assertThat(fluids.amount(0)).isEqualTo(3_000_000_000L);
        assertThat(fixed(requirements, capabilities, 2L, List.of(0, 1), Map.of()).successful()).isFalse();
        assertThat(items.amount(1)).isEqualTo(32L);
        assertThat(fluids.amount(0)).isEqualTo(3_000_000_000L);
    }

    @Test
    void second_port_completes_fixed_input_with_the_same_transfer_as_maximum_planning() {
        LongEnergyStorage oldFirst = storage(40L, 40L, 7L);
        LongEnergyStorage oldSecond = storage(40L, 40L, 20L);
        LongEnergyStorage fixedFirst = storage(40L, 40L, 7L);
        LongEnergyStorage fixedSecond = storage(40L, 40L, 20L);
        List<MachineRequirement> requirements = List.of(new EnergyRequirement(8L));
        var old = new RequirementPlanner().plan(requirements,
                List.of(input(oldFirst), input(oldSecond)), new PlanningContext(3L, 0));
        var fixed = fixed(requirements, List.of(input(fixedFirst), input(fixedSecond)), 3L, List.of(9), Map.of());

        assertThat(old.successful()).isTrue();
        assertThat(fixed.successful()).isTrue();
        assertThat(fixed.plan().parallelism()).isEqualTo(old.plan().parallelism());
        assertThat(fixed.plan().requirements()).singleElement()
                .satisfies(plan -> assertThat(plan.requirementIndex()).isEqualTo(9));
        assertThat(old.plan().commit()).isTrue();
        assertThat(fixed.plan().commit()).isTrue();
        assertThat(fixedFirst.getAmountAsLong()).isEqualTo(oldFirst.getAmountAsLong());
        assertThat(fixedSecond.getAmountAsLong()).isEqualTo(oldSecond.getAmountAsLong());
        assertThat(27L - fixedFirst.getAmountAsLong() - fixedSecond.getAmountAsLong()).isEqualTo(24L);
    }

    @Test
    void insufficient_fixed_input_fails_without_lowering_parallelism_or_mutating_storage() {
        LongEnergyStorage storage = storage(40L, 40L, 17L);
        List<MachineRequirement> requirements = List.of(new EnergyRequirement(8L));
        var old = new RequirementPlanner().plan(requirements, List.of(input(storage)), new PlanningContext(3L, 0));
        var fixed = fixed(requirements, List.of(input(storage)), 3L, List.of(11), Map.of());

        assertThat(old.plan().parallelism()).isEqualTo(2L);
        assertThat(fixed.successful()).isFalse();
        assertThat(fixed.failureRequirementIndex()).isEqualTo(11);
        assertThat(fixed.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_ENERGY);
        assertThat(storage.getAmountAsLong()).isEqualTo(17L);
        var unavailableOld = new RequirementPlanner().plan(requirements, List.of(), new PlanningContext(3L, 0));
        assertThat(fixed.failure().reason()).isEqualTo(unavailableOld.failure().reason());
    }

    @Test
    void aliases_cannot_supply_the_same_energy_twice_across_atomic_requirements() {
        LongEnergyStorage storage = storage(20L, 20L, 10L);
        PlanningReservations base = new PlanningReservations();
        var result = new RequirementPlanner().planFixed(List.of(new EnergyRequirement(6L), new EnergyRequirement(6L)),
                List.of(input(storage), input(storage)), new PlanningContext(1L, 0, false, base), List.of(2, 7));

        assertThat(result.successful()).isFalse();
        assertThat(result.failureRequirementIndex()).isEqualTo(7);
        assertThat(result.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_ENERGY);
        assertThat(base.valueAvailable(storage, false)).isEqualTo(10L);
        assertThat(storage.getAmountAsLong()).isEqualTo(10L);
    }

    @Test
    void output_policy_at_original_index_preserves_partial_fit_and_transfer() {
        LongEnergyStorage oldStorage = storage(20L, 20L, 15L);
        LongEnergyStorage fixedStorage = storage(20L, 20L, 15L);
        List<MachineRequirement> requirements = List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 4L));
        Map<Integer, OutputPolicy> policies = Map.of(13, OutputPolicy.ALLOW_PARTIAL);
        var old = new RequirementPlanner().plan(requirements, List.of(output(oldStorage)),
                new PlanningContext(3L, 0, policies), List.of(13));
        var fixed = fixed(requirements, List.of(output(fixedStorage)), 3L, List.of(13), policies);

        assertThat(fixed.successful()).isTrue();
        assertThat(fixed.outputSimulations()).isEqualTo(old.outputSimulations());
        assertThat(fixed.outputSimulations()).singleElement().satisfies(simulation -> {
            assertThat(simulation.requested()).isEqualTo(12L);
            assertThat(simulation.accepted()).isEqualTo(5L);
            assertThat(simulation.fit()).isEqualTo(OutputFit.PARTIAL);
        });
        assertThat(old.plan().commit()).isTrue();
        assertThat(fixed.plan().commit()).isTrue();
        assertThat(fixedStorage.getAmountAsLong()).isEqualTo(oldStorage.getAmountAsLong());
        var full = fixed(requirements, List.of(output(fixedStorage)), 3L, List.of(13), Map.of());
        assertThat(full.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT);
        assertThat(full.outputSimulations()).singleElement()
                .satisfies(simulation -> assertThat(simulation.fit()).isEqualTo(OutputFit.NONE));
    }

    @Test
    void tags_and_direction_filter_the_same_candidate_set() {
        LongEnergyStorage matching = storage(20L, 20L, 8L);
        LongEnergyStorage wrongTag = storage(20L, 20L, 20L);
        LongEnergyStorage wrongDirection = storage(20L, 20L, 20L);
        var result = fixed(List.of(new EnergyRequirement(8L, List.of("selected"))), List.of(
                new TaggedCapability(input(wrongTag), List.of("other"), 0),
                new TaggedCapability(output(wrongDirection), List.of("selected"), 0),
                new TaggedCapability(input(matching), List.of("selected"), 0)), 1L, List.of(4), Map.of());

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().commitInputs()).isTrue();
        assertThat(matching.getAmountAsLong()).isZero();
        assertThat(wrongTag.getAmountAsLong()).isEqualTo(20L);
        assertThat(wrongDirection.getAmountAsLong()).isEqualTo(20L);
    }

    @Test
    void admission_is_queried_once_without_outer_or_inner_maximum_search() {
        AdmissionCapability capability = new AdmissionCapability(1_000_000L);
        var result = fixed(List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 1L)),
                List.of(capability), 100_000L, List.of(5), Map.of());

        assertThat(result.successful()).isTrue();
        assertThat(capability.capacityCalls).isEqualTo(1);
        assertThat(capability.planCalls).isEqualTo(1);
        assertThat(capability.materialized).isEqualTo(1);
        assertThat(result.plan().commit()).isTrue();
        assertThat(capability.destination.getAmountAsLong()).isEqualTo(100_000L);
    }

    @Test
    void output_priority_and_full_remaining_admission_rule_are_preserved() {
        LongEnergyStorage ordinary = storage(5L, 5L, 0L);
        AdmissionCapability admission = new AdmissionCapability(3L);
        var result = fixed(List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 8L)), List.of(
                admission, new TaggedCapability(output(ordinary), List.of(), 1)), 1L, List.of(6), Map.of());

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().commit()).isTrue();
        assertThat(ordinary.getAmountAsLong()).isEqualTo(5L);
        assertThat(admission.destination.getAmountAsLong()).isEqualTo(3L);
        AdmissionCapability tooSmall = new AdmissionCapability(3L);
        var partial = fixed(List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 8L)),
                List.of(tooSmall), 1L, List.of(6), Map.of(6, OutputPolicy.ALLOW_PARTIAL));
        assertThat(partial.successful()).isFalse();
        assertThat(tooSmall.planCalls).isZero();
        assertThat(partial.outputSimulations()).singleElement()
                .satisfies(simulation -> assertThat(simulation.accepted()).isZero());
    }

    @Test
    void changed_storage_retains_native_inputs_and_fresh_context_binds_replacement() {
        LongEnergyStorage first = storage(20L, 20L, 8L);
        LongEnergyStorage second = storage(20L, 4L, 8L);
        List<MachineRequirement> requirements = List.of(new EnergyRequirement(4L), new EnergyRequirement(4L));
        CraftingContext context = new CraftingContext(new CapabilitySnapshot(List.of(input(first), input(second))));
        var result = context.planFixedRequirements(requirements, 2L, Map.of(), List.of(0, 1));
        assertThat(result.successful()).isTrue();
        second.setAmount(4L);
        assertThat(result.plan().commit()).isFalse();
        assertThat(first.getAmountAsLong()).isZero();
        assertThat(second.getAmountAsLong()).isEqualTo(4L);

        LongEnergyStorage replacement = storage(30L, 30L, 24L);
        CraftingContext replaced = new CraftingContext(new CapabilitySnapshot(List.of(input(replacement))));
        var fresh = replaced.planFixedRequirements(requirements, 2L, Map.of(), List.of(0, 1));
        assertThat(fresh.successful()).isTrue();
        assertThat(fresh.plan().commit()).isTrue();
        assertThat(replacement.getAmountAsLong()).isEqualTo(8L);
        assertThat(first.getAmountAsLong()).isZero();
    }

    @Test
    void zero_probability_compatibility_output_remains_a_no_op() {
        var result = fixed(List.of(new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                FluidStack.EMPTY, 0F, List.of())), List.of(), 8L, List.of(12), Map.of());
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(8L);
        assertThat(result.plan().hasOperations(12)).isFalse();
        assertThat(result.plan().commit()).isTrue();
    }

    @Test
    void transfer_limit_and_native_single_operation_protocol_match_existing_planning() {
        LongEnergyStorage oldStorage = storage(30L, 4L, 24L);
        LongEnergyStorage fixedStorage = storage(30L, 4L, 24L);
        List<MachineRequirement> requirements = List.of(new EnergyRequirement(4L));
        var old = new RequirementPlanner().plan(requirements, List.of(input(oldStorage)), new PlanningContext(3L, 0));
        var fixed = fixed(requirements, List.of(input(fixedStorage)), 3L, List.of(0), Map.of());

        assertThat(fixed.successful()).isEqualTo(old.successful()).isTrue();
        assertThat(fixed.plan().parallelism()).isEqualTo(old.plan().parallelism());
        // Native hatches split the total into legal per-parallel transfer-limit batches.
        assertThat(fixed.plan().commit()).isEqualTo(old.plan().commit()).isTrue();
        assertThat(fixedStorage.getAmountAsLong()).isEqualTo(oldStorage.getAmountAsLong()).isEqualTo(12L);
        var overLimit = fixed(List.of(new EnergyRequirement(5L)), List.of(input(fixedStorage)),
                3L, List.of(0), Map.of());
        assertThat(overLimit.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_ENERGY);
    }

    @Test
    void compatibility_reservation_failure_is_checked_once_without_materializing_or_reducing() {
        AtomicInteger reserveCalls = new AtomicInteger();
        AtomicInteger materializeCalls = new AtomicInteger();
        RequirementHandler<CompatibilityRequirement> handler = (requirement, capabilities, context) ->
                new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null,
                        (parallelism, reservations) -> {
                            materializeCalls.incrementAndGet();
                            return new RequirementPlan.OperationPlan(List.of(), null);
                        }, (parallelism, reservations) -> {
                            reserveCalls.incrementAndGet();
                            return RequirementHandlerSupport.blocked(requirement, context,
                                    BuiltinFailureReasons.MISSING_INPUT);
                        });
        RequirementType<CompatibilityRequirement> type = compatibilityType("fixed_compatibility", handler);
        try (var scope = RequirementHandlerRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(type);
            var result = fixed(List.of(new CompatibilityRequirement(type)), List.of(), 100L, List.of(17), Map.of());
            assertThat(result.successful()).isFalse();
            assertThat(result.failureRequirementIndex()).isEqualTo(17);
            assertThat(result.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_INPUT);
            assertThat(reserveCalls).hasValue(1);
            assertThat(materializeCalls).hasValue(0);
        }
    }

    @Test
    void fixed_compatibility_keeps_lane_owner_and_original_indices_through_real_native_payment() {
        LongEnergyStorage storage = storage(24L, 24L, 24L);
        MachineCapability capability = input(storage);
        List<PlanningContext> observed = new ArrayList<>();
        RequirementHandler<CompatibilityRequirement> handler = (requirement, candidates, context) -> {
            observed.add(context);
            return new EnergyRequirementHandler().plan(new EnergyRequirement(6L), List.of(capability), context);
        };
        RequirementType<CompatibilityRequirement> type = compatibilityType("fixed_lane_owner", handler);
        StressSession firstOwner = new StressSession();
        StressSession secondOwner = new StressSession();
        try (var scope = RequirementHandlerRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(type);
            var requirement = new CompatibilityRequirement(type);
            for (StressSession owner : List.of(firstOwner, secondOwner)) {
                var result = new CraftingContext(new CapabilitySnapshot(List.of(capability)))
                        .withReservationOwner(owner)
                        .planFixedRequirements(List.of(requirement, requirement), 1L, Map.of(), List.of(3, 17));
                assertThat(result.successful()).isTrue();
                assertThat(result.plan().commit()).isTrue();
            }
        }
        assertThat(observed).extracting(PlanningContext::reservationOwner)
                .containsExactly(firstOwner, firstOwner, secondOwner, secondOwner);
        assertThat(observed).extracting(PlanningContext::requirementIndex).containsExactly(3, 17, 3, 17);
        assertThat(storage.getAmountAsLong()).isZero();
    }

    @Test
    void compatibility_factories_materialize_once_each_using_shared_reservations() {
        LongEnergyStorage storage = storage(20L, 20L, 12L);
        MachineCapability capability = input(storage);
        AtomicInteger reserveCalls = new AtomicInteger();
        AtomicInteger materializeCalls = new AtomicInteger();
        RequirementHandler<CompatibilityRequirement> handler = (requirement, candidates, context) ->
                new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null,
                        (parallelism, reservations) -> {
                            materializeCalls.incrementAndGet();
                            if (!reservations.reserveValueTotal(storage, 6L, false)) {
                                return new RequirementPlan.OperationPlan(List.of(), RequirementHandlerSupport.blocked(
                                        requirement, context, BuiltinFailureReasons.MISSING_INPUT));
                            }
                            return new RequirementPlan.OperationPlan(List.of(capability.prepare(
                                    new CapabilityRequests.ValueRequest(capability.type(), IOType.INPUT,
                                            parallelism, 6L, false))), null);
                        }, (parallelism, reservations) -> {
                            reserveCalls.incrementAndGet();
                            return reservations.reserveValueTotal(storage, 6L, false) ? null
                                    : RequirementHandlerSupport.blocked(requirement, context,
                                            BuiltinFailureReasons.MISSING_INPUT);
                        });
        RequirementType<CompatibilityRequirement> type = compatibilityType("fixed_factory", handler);
        try (var scope = RequirementHandlerRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(type);
            var requirement = new CompatibilityRequirement(type);
            var result = fixed(List.of(requirement, requirement), List.of(), 1L, List.of(3, 8), Map.of());
            assertThat(result.successful()).isTrue();
            assertThat(reserveCalls).hasValue(2);
            assertThat(materializeCalls).hasValue(2);
            assertThat(result.plan().commit()).isTrue();
            assertThat(storage.getAmountAsLong()).isZero();
        }
    }

    @Test
    void reservation_only_deferred_conflicts_match_old_planner_in_both_mixed_energy_orders() {
        for (int order = 0; order < 3; order++) verifyReservationOnlyDeferred(order, false);
    }

    @Test
    void reservation_only_deferred_success_matches_old_transaction_in_both_mixed_energy_orders() {
        for (int order = 0; order < 3; order++) verifyReservationOnlyDeferred(order, true);
    }

    @Test
    void operation_only_legacy_factories_still_share_the_budget_when_no_reservation_callback_exists() {
        LongEnergyStorage storage = storage(20L, 20L, 10L);
        PlanningReservations base = new PlanningReservations();
        AtomicInteger materializeCalls = new AtomicInteger();
        RequirementHandler<CompatibilityRequirement> handler = (requirement, candidates, context) ->
                new RequirementPlan(context.requirementIndex(), 1L, List.of(), null,
                        (parallelism, reservations) -> {
                            materializeCalls.incrementAndGet();
                            return new RequirementPlan.OperationPlan(List.of(),
                                    reservations.reserveValueTotal(storage, 6L, false) ? null
                                            : RequirementHandlerSupport.blocked(requirement, context,
                                                    BuiltinFailureReasons.MISSING_INPUT));
                        });
        RequirementType<CompatibilityRequirement> type = compatibilityType("fixed_operation_only", handler);
        try (var scope = RequirementHandlerRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(type);
            var requirement = new CompatibilityRequirement(type);
            var result = new RequirementPlanner().planFixed(List.of(requirement, requirement), List.of(),
                    new PlanningContext(1L, 0, false, base), List.of(3, 17));
            assertThat(result.successful()).isFalse();
            assertThat(result.failureRequirementIndex()).isEqualTo(17);
            assertThat(result.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_INPUT);
            assertThat(materializeCalls).hasValue(2);
            assertThat(base.valueAvailable(storage, false)).isEqualTo(10L);
            assertThat(storage.getAmountAsLong()).isEqualTo(10L);
        }
    }

    private static void verifyReservationOnlyDeferred(int order, boolean sufficient) {
        long initial = sufficient ? 14L : 12L;
        LongEnergyStorage storage = storage(20L, 20L, initial);
        MachineCapability capability = input(storage);
        AtomicInteger planCalls = new AtomicInteger();
        AtomicInteger reserveCalls = new AtomicInteger();
        AtomicInteger materializeCalls = new AtomicInteger();
        RequirementHandler<CompatibilityRequirement> handler = (requirement, candidates, context) -> {
            planCalls.incrementAndGet();
            return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null,
                    (parallelism, reservations) -> {
                        materializeCalls.incrementAndGet();
                        // Legal deferred contract: only the reservation callback records the resource budget.
                        return new RequirementPlan.OperationPlan(List.of(capability.prepare(
                                new CapabilityRequests.ValueRequest(capability.type(), IOType.INPUT,
                                        parallelism, 6L, false))), null);
                    }, (parallelism, reservations) -> {
                        reserveCalls.incrementAndGet();
                        return reservations.reserveValueTotal(storage, 6L, false) ? null
                                : RequirementHandlerSupport.blocked(requirement, context,
                                        BuiltinFailureReasons.MISSING_INPUT);
                    });
        };
        RequirementType<CompatibilityRequirement> type = compatibilityType("fixed_reservation_only", handler);
        try (var scope = RequirementHandlerRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(type);
            MachineRequirement compatibility = new CompatibilityRequirement(type);
            MachineRequirement energy = new EnergyRequirement(6L);
            List<MachineRequirement> requirements = switch (order) {
                case 0 -> List.of(compatibility, compatibility);
                case 1 -> List.of(compatibility, energy);
                default -> List.of(energy, compatibility);
            };
            PlanningReservations base = new PlanningReservations();
            assertThat(base.reserveValueTotal(storage, 2L, false)).isTrue();
            PlanningContext context = new PlanningContext(1L, 0, false, base);
            List<Integer> indices = List.of(3, 17);
            var old = new RequirementPlanner().plan(requirements, List.of(capability), context, indices);
            planCalls.set(0);
            reserveCalls.set(0);
            materializeCalls.set(0);
            var fixed = new RequirementPlanner().planFixed(requirements, List.of(capability), context, indices);

            assertThat(fixed.successful()).isEqualTo(old.successful()).isEqualTo(sufficient);
            assertThat(planCalls).hasValue(order == 0 ? 2 : 1);
            assertThat(reserveCalls).hasValue(order == 0 ? 2 : 1);
            assertThat(materializeCalls).hasValue(sufficient ? (order == 0 ? 2 : 1) : 0);
            assertThat(base.valueAvailable(storage, false)).isEqualTo(initial - 2L);
            assertThat(storage.getAmountAsLong()).isEqualTo(initial);
            if (!sufficient) {
                assertThat(fixed.failureRequirementIndex()).isEqualTo(old.failureRequirementIndex()).isEqualTo(17);
                assertThat(fixed.failure().reason()).isEqualTo(old.failure().reason());
                return;
            }
            assertThat(fixed.plan().parallelism()).isEqualTo(old.plan().parallelism()).isEqualTo(1L);
            assertThat(old.plan().commit()).isTrue();
            long oldBalance = storage.getAmountAsLong();
            storage.setAmount(initial);
            assertThat(fixed.plan().commit()).isTrue();
            assertThat(storage.getAmountAsLong()).isEqualTo(oldBalance).isEqualTo(initial - 12L);
            assertThat(base.valueAvailable(storage, false)).isEqualTo(initial - 14L);
        }
    }

    private static RequirementType<CompatibilityRequirement> compatibilityType(String path,
            RequirementHandler<CompatibilityRequirement> handler) {
        return new RequirementType<>() {
            public ResourceLocation id() { return ResourceLocation.fromNamespaceAndPath("mmcr_test", path); }
            public MapCodec<CompatibilityRequirement> codec() {
                return MapCodec.unit(() -> new CompatibilityRequirement(this));
            }
            public RequirementHandler<CompatibilityRequirement> handler() { return handler; }
        };
    }

    private record CompatibilityRequirement(RequirementType<CompatibilityRequirement> type)
            implements MachineRequirement {
        public RecipeModifier.IOType io() { return RecipeModifier.IOType.INPUT; }
        public List<String> tags() { return List.of(); }
    }

    private static PlanningResult fixed(List<MachineRequirement> requirements, List<MachineCapability> capabilities,
                                         long parallelism, List<Integer> indices, Map<Integer, OutputPolicy> policies) {
        return new RequirementPlanner().planFixed(requirements, capabilities,
                new PlanningContext(parallelism, 0, policies), indices);
    }

    private static LongEnergyStorage storage(long capacity, long limit, long amount) {
        LongEnergyStorage storage = new LongEnergyStorage(capacity, limit, null);
        storage.setAmount(amount);
        return storage;
    }

    private static MachineCapability input(LongEnergyStorage storage) {
        return new EnergyHatchCapability(storage, IOType.INPUT);
    }

    private static MachineCapability output(LongEnergyStorage storage) {
        return new EnergyHatchCapability(storage, IOType.OUTPUT);
    }

    private record TaggedCapability(MachineCapability delegate, List<String> tags, int outputPriority)
            implements MachineCapability {
        public CapabilityType type() { return delegate.type(); }
        public CapabilityDirections directions() { return delegate.directions(); }
        public CapabilityOperation prepare(CapabilityRequest request) { return delegate.prepare(request); }
        public <F extends CapabilityFacet> Optional<F> facet(Class<F> type) { return delegate.facet(type); }
        public CapabilityView view() {
            return new CapabilityView() {
                public CapabilityType type() { return delegate.type(); }
                public CapabilityDirections directions() { return delegate.directions(); }
                public List<String> tags() { return tags; }
            };
        }
    }

    private static final class AdmissionCapability implements MachineCapability, EnergyOutputAdmissionFacet {
        private final LongEnergyStorage destination;
        private final MachineCapability delegate;
        private int capacityCalls;
        private int planCalls;
        private int materialized;

        private AdmissionCapability(long capacity) {
            destination = storage(capacity, capacity, 0L);
            delegate = output(destination);
        }

        public CapabilityType type() { return delegate.type(); }
        public CapabilityDirections directions() { return delegate.directions(); }
        public CapabilityView view() { return delegate.view(); }
        public CapabilityOperation prepare(CapabilityRequest request) { return delegate.prepare(request); }
        public <F extends CapabilityFacet> Optional<F> facet(Class<F> type) {
            return type == EnergyOutputAdmissionFacet.class ? Optional.of(type.cast(this)) : Optional.empty();
        }

        public long outputCapacity(PlanningReservations reservations) {
            capacityCalls++;
            return reservations.valueAvailable(destination, true);
        }

        public OutputPlan planOutput(long amount, PlanningReservations reservations, boolean materialize) {
            planCalls++;
            if (!reservations.reserveValueTotal(destination, amount, true)) return new OutputPlan(0L, null);
            if (materialize) materialized++;
            return new OutputPlan(amount, materialize ? delegate.prepare(new CapabilityRequests.ValueRequest(
                    type(), IOType.OUTPUT, 1L, amount, true)) : null);
        }
    }
}
