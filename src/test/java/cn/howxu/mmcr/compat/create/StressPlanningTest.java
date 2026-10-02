package cn.howxu.mmcr.compat.create;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.compat.create.CreateFailureReasons;
import cn.howxu.mmcr.api.compat.create.StressFacet;
import cn.howxu.mmcr.api.compat.create.StressState;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.internal.recipe.RequirementPlanner;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.util.IOType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Real planner/reservation/ledger behavior; fixtures supply only port and network state.
 * @author howxu <dev@howxu.cn>
 */
class StressPlanningTest {
    private RequirementHandlerRegistry.TestScope scope;

    @BeforeEach
    void setup() {
        scope = RequirementHandlerRegistry.openTestScope();
        CreateRecipeTypes.register();
        CreateBridgeBootstrap.installForTesting(new CreateBridge() {
            @Override public boolean available() { return true; }
        });
    }

    @AfterEach
    void cleanup() {
        CreateBridgeBootstrap.resetForTesting();
        scope.close();
    }

    @Test
    void recipe_modifiers_scale_only_base_stress_and_preserve_target_direction_and_chance_filters() {
        var input = StressRequirement.input(4.5, 16, List.of("drive"));
        var output = StressRequirement.output(4.5, -64, List.of("generator"));
        var modifiers = List.of(
                new RecipeModifier("create:stress", RecipeModifier.IOType.INPUT, 1, RecipeModifier.Operation.ADD, false),
                new RecipeModifier("create:stress", RecipeModifier.IOType.INPUT, 0.5F, RecipeModifier.Operation.SUBTRACT, false),
                new RecipeModifier("create:stress", RecipeModifier.IOType.INPUT, 1.5F, RecipeModifier.Operation.MULTIPLY, false),
                new RecipeModifier("create:stress", RecipeModifier.IOType.OUTPUT, 2, RecipeModifier.Operation.DIVIDE, false),
                new RecipeModifier("neoforge:energy", RecipeModifier.IOType.INPUT, 100, RecipeModifier.Operation.MULTIPLY, false),
                new RecipeModifier("create:stress", RecipeModifier.IOType.INPUT, 100, RecipeModifier.Operation.MULTIPLY, true),
                new RecipeModifier("create:stress", RecipeModifier.IOType.OUTPUT, 100, RecipeModifier.Operation.ADD, true));
        var modifiedInput = (StressRequirement) RequirementHandlerRegistry.applyModifiers(input, modifiers);
        var modifiedOutput = (StressRequirement) RequirementHandlerRegistry.applyModifiers(output, modifiers);
        assertThat(modifiedInput).isEqualTo(StressRequirement.input(7.5, 16, List.of("drive")));
        assertThat(modifiedOutput).isEqualTo(StressRequirement.output(2.25, -64, List.of("generator")));
        assertThat(input).isEqualTo(StressRequirement.input(4.5, 16, List.of("drive")));
        assertThat(output).isEqualTo(StressRequirement.output(4.5, -64, List.of("generator")));

        var inputPort = new Port(new Network(240), 16, IOType.INPUT);
        inputPort.tags = List.of("drive");
        var outputPort = new Port(new Network(0), 0, IOType.OUTPUT);
        outputPort.tags = List.of("generator");
        var lane = new StressSession();
        var planned = plan(lane, 4, List.of(modifiedInput, modifiedOutput), inputPort, outputPort);
        assertThat(planned.plan().parallelism()).isEqualTo(2);
        assertThat(planned.plan().commit()).isTrue();
        assertThat(inputPort.ledger.get(lane, 0)).isEqualTo(new StressContributions.Contribution(15, 0));
        assertThat(inputPort.network.load()).isEqualTo(240);
        assertThat(outputPort.ledger.get(lane, 1)).isEqualTo(new StressContributions.Contribution(4.5, -64));
    }

    @Test
    void level_modifiers_use_input_energy_and_output_multiplier_without_scaling_rotation() {
        var input = StressRequirement.input(2.5, 16, List.of("drive"));
        var output = StressRequirement.output(2.5, -64, List.of("generator"));
        var modifiedInput = (StressRequirement) RequirementHandlerRegistry.applyLevelModifiers(input, 0.5, 3);
        var modifiedOutput = (StressRequirement) RequirementHandlerRegistry.applyLevelModifiers(output, 0.5, 3);
        assertThat(modifiedInput).isEqualTo(StressRequirement.input(1.25, 16, List.of("drive")));
        assertThat(modifiedOutput).isEqualTo(StressRequirement.output(7.5, -64, List.of("generator")));

        var inputPort = new Port(new Network(80), 16, IOType.INPUT);
        inputPort.tags = List.of("drive");
        var outputPort = new Port(new Network(0), 0, IOType.OUTPUT);
        outputPort.tags = List.of("generator");
        var lane = new StressSession();
        var planned = plan(lane, 4, List.of(modifiedInput, modifiedOutput), inputPort, outputPort);
        assertThat(planned.plan().parallelism()).isEqualTo(4);
        assertThat(planned.plan().commit()).isTrue();
        assertThat(inputPort.ledger.get(lane, 0)).isEqualTo(new StressContributions.Contribution(5, 0));
        assertThat(outputPort.ledger.get(lane, 1)).isEqualTo(new StressContributions.Contribution(30, -64));
        inputPort.actualRpm = 8;
        assertThat(plan(lane, 1, List.of(modifiedInput), inputPort).failure().reason())
                .isEqualTo(CreateFailureReasons.INSUFFICIENT_RPM);
    }

    @Test
    void same_network_ports_do_not_multiply_free_budget() {
        var network = new Network(100);
        var first = new Port(network, 10, IOType.INPUT);
        var second = new Port(network, 10, IOType.INPUT);
        var result = plan(new StressSession(), 1, List.of(StressRequirement.input(15, 1)), first, second);
        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isEqualTo(CreateFailureReasons.INSUFFICIENT_STRESS);
        assertThat(network.load()).isZero();
    }

    @Test
    void different_requirements_share_budget_and_binary_search_selects_runnable_parallelism() {
        var network = new Network(100);
        var port = new Port(network, 10, IOType.INPUT);
        var lane = new StressSession();
        var requirements = List.of(StressRequirement.input(2, 1), StressRequirement.input(3, 1));
        var result = plan(lane, 8, requirements, port);
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(2);
        assertThat(network.load()).isZero();
        assertThat(result.plan().commit()).isTrue();
        assertThat(network.load()).isEqualTo(100);
        assertThat(port.ledger.get(lane, 0)).isEqualTo(new StressContributions.Contribution(4, 0));
        assertThat(port.ledger.get(lane, 1)).isEqualTo(new StressContributions.Contribution(6, 0));
        assertThat(plan(lane, 2, requirements, port).plan().commit()).isTrue();
        assertThat(network.load()).isEqualTo(100);
        assertThat(plan(new StressSession(), 1, List.of(StressRequirement.input(1, 0)), port).successful()).isFalse();
    }

    @Test
    void local_rpm_converts_each_allocation_to_actual_network_stress() {
        var slowNetwork = new Network(64);
        var fastNetwork = new Network(128);
        var slow = new Port(slowNetwork, -16, IOType.INPUT);
        var fast = new Port(fastNetwork, 32, IOType.INPUT);
        var lane = new StressSession();
        var result = plan(lane, 1, List.of(StressRequirement.input(8, 16)), slow, fast);
        assertThat(result.plan().commit()).isTrue();
        assertThat(slow.ledger.get(lane, 0).baseStress()).isEqualTo(4);
        assertThat(fast.ledger.get(lane, 0).baseStress()).isEqualTo(4);
        assertThat(slowNetwork.load()).isEqualTo(64);
        assertThat(fastNetwork.load()).isEqualTo(128);
    }

    @Test
    void lanes_compete_repeated_ticks_replace_and_release_makes_room() {
        var network = new Network(100);
        var port = new Port(network, 10, IOType.INPUT);
        var laneA = new StressSession();
        var laneB = new StressSession();
        var requestA = List.of(StressRequirement.input(8, 1));
        assertThat(plan(laneA, 1, requestA, port).plan().commit()).isTrue();
        for (int attempt = 0; attempt < 3; attempt++) {
            var replan = plan(laneA, 1, requestA, port);
            assertThat(network.load()).isEqualTo(80);
            assertThat(replan.plan().commit()).isTrue();
            assertThat(network.load()).isEqualTo(80);
        }
        assertThat(plan(laneB, 1, List.of(StressRequirement.input(4, 1)), port).successful()).isFalse();
        laneA.releaseAll();
        assertThat(plan(laneB, 1, List.of(StressRequirement.input(4, 1)), port).plan().commit()).isTrue();
        assertThat(network.load()).isEqualTo(40);
    }

    @Test
    void previous_requirement_credit_is_counted_once_even_when_multiple_ports_are_candidates() {
        var network = new Network(100);
        var first = new Port(network, 10, IOType.INPUT);
        var second = new Port(network, 10, IOType.INPUT);
        var lane = new StressSession();
        first.ledger.replace(lane, 0, new StressContributions.Contribution(3, 0));
        second.ledger.replace(lane, 0, new StressContributions.Contribution(3, 0));
        lane.track(first, 0, RecipeModifier.IOType.INPUT);
        lane.track(second, 0, RecipeModifier.IOType.INPUT);
        assertThat(plan(lane, 1, List.of(StressRequirement.input(12, 0)), first, second).successful()).isFalse();
        assertThat(network.load()).isEqualTo(60);
        var replace = plan(lane, 1, List.of(StressRequirement.input(10, 0)), first, second);
        assertThat(replace.plan().commit()).isTrue();
        assertThat(first.observedBatch).isTrue();
        assertThat(lane.committing(0)).isFalse();
        assertThat(first.ledger.get(lane, 0).baseStress()).isEqualTo(10);
        assertThat(second.ledger.get(lane, 0)).isNull();
        assertThat(network.load()).isEqualTo(100);
    }

    @Test
    void successful_reallocation_releases_old_location_and_preserves_other_indexes() {
        var oldNetwork = new Network(200);
        var newNetwork = new Network(100);
        var oldPort = new Port(oldNetwork, 10, IOType.INPUT);
        var newPort = new Port(newNetwork, 10, IOType.INPUT);
        var lane = new StressSession();
        assertThat(plan(lane, 1, List.of(StressRequirement.input(8, 0), StressRequirement.input(2, 0)), oldPort)
                .plan().commit()).isTrue();
        assertThat(plan(lane, 1, List.of(StressRequirement.input(8, 0)), newPort).plan().commit()).isTrue();
        assertThat(oldPort.ledger.get(lane, 0)).isNull();
        assertThat(oldPort.ledger.get(lane, 1).baseStress()).isEqualTo(2);
        assertThat(oldNetwork.load()).isEqualTo(20);
        assertThat(newNetwork.load()).isEqualTo(80);
    }

    @Test
    void partial_apply_failure_cleans_the_requirement_without_erasing_other_indexes() {
        var first = new Port(new Network(60), 10, IOType.INPUT);
        var second = new Port(new Network(40), 10, IOType.INPUT);
        var lane = new StressSession();
        first.ledger.replace(lane, 7, new StressContributions.Contribution(2, 0));
        lane.track(first, 7, RecipeModifier.IOType.INPUT);
        second.rejectApply = true;
        var result = plan(lane, 1, List.of(StressRequirement.input(8, 0)), first, second);
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().commit()).isFalse();
        assertThat(first.ledger.get(lane, 0)).isNull();
        assertThat(second.ledger.get(lane, 0)).isNull();
        assertThat(first.ledger.get(lane, 7).baseStress()).isEqualTo(2);
        assertThat(lane.facets(0)).isEmpty();
    }

    @Test
    void unscoped_simulation_is_read_only_and_cannot_commit_persistent_load() {
        var port = new Port(new Network(100), 10, IOType.INPUT);
        var result = plan(null, 1, List.of(StressRequirement.input(8, 0)), port);
        assertThat(result.successful()).isTrue();
        assertThat(port.ledger.baseStress()).isZero();
        assertThat(result.plan().commit()).isFalse();
        assertThat(result.plan().failure().reason()).isEqualTo(CreateFailureReasons.MISSING_RECIPE_SCOPE);
        assertThat(port.ledger.baseStress()).isZero();
    }

    @Test
    void commit_rechecks_network_identity_capacity_and_rpm_without_writing_a_stale_plan() {
        var network = new Network(100);
        var port = new Port(network, 10, IOType.INPUT);
        var lane = new StressSession();
        var requirements = List.of(StressRequirement.input(8, 5));
        var capacityPlan = plan(lane, 1, requirements, port).plan();
        network.capacity = 60;
        assertThat(capacityPlan.commit()).isFalse();
        assertThat(port.ledger.baseStress()).isZero();
        network.capacity = 100;
        var identityPlan = plan(lane, 1, requirements, port).plan();
        port.moveTo(new Network(100));
        assertThat(identityPlan.commit()).isFalse();
        assertThat(port.ledger.baseStress()).isZero();
        var rpmPlan = plan(lane, 1, requirements, port).plan();
        port.actualRpm = 4;
        assertThat(rpmPlan.commit()).isFalse();
        assertThat(rpmPlan.failure().reason()).isEqualTo(CreateFailureReasons.INSUFFICIENT_RPM);
    }

    @Test
    void disabled_stress_ignores_budget_but_rotation_and_overstress_still_block() {
        var port = new Port(new Network(1), 32, IOType.INPUT);
        var lane = new StressSession();
        port.enabled = false;
        assertThat(plan(lane, 3, List.of(StressRequirement.input(8, 32)), port).plan().commit()).isTrue();
        port.actualRpm = 0;
        assertThat(plan(lane, 1, List.of(StressRequirement.input(8, 0)), port).failure().reason())
                .isEqualTo(CreateFailureReasons.MISSING_ROTATION);
        port.actualRpm = 32;
        port.overstressed = true;
        assertThat(plan(lane, 1, List.of(StressRequirement.input(8, 32)), port).failure().reason())
                .isEqualTo(CreateFailureReasons.INSUFFICIENT_STRESS);
    }

    @Test
    void direction_and_tags_filter_ports_before_allocation() {
        var wrongDirection = new Port(new Network(100), 10, IOType.OUTPUT);
        var wrongTag = new Port(new Network(100), 10, IOType.INPUT);
        wrongTag.tags = List.of("other");
        var right = new Port(new Network(100), 10, IOType.INPUT);
        right.tags = List.of("drive");
        var lane = new StressSession();
        var requirement = StressRequirement.input(8, 0, List.of("drive"));
        assertThat(plan(lane, 1, List.of(requirement), wrongDirection, wrongTag).successful()).isFalse();
        assertThat(plan(lane, 1, List.of(requirement), wrongDirection, wrongTag, right).plan().commit()).isTrue();
        assertThat(right.ledger.baseStress()).isEqualTo(8);
        assertThat(wrongDirection.ledger.baseStress()).isZero();
        assertThat(wrongTag.ledger.baseStress()).isZero();
    }

    @Test
    void output_rpm_conflicts_are_reserved_per_pass_and_exclude_only_the_same_owner_index() {
        var output = new Port(new Network(0), 0, IOType.OUTPUT);
        var laneA = new StressSession();
        var laneB = new StressSession();
        var conflict = plan(laneA, 1, List.of(StressRequirement.output(8, 64), StressRequirement.output(4, -64)), output);
        assertThat(conflict.successful()).isFalse();
        assertThat(output.ledger.baseStress()).isZero();
        assertThat(plan(laneA, 1, List.of(StressRequirement.output(8, 64)), output).plan().commit()).isTrue();
        assertThat(plan(laneB, 1, List.of(StressRequirement.output(8, -64)), output).successful()).isFalse();
        assertThat(plan(laneA, 1, List.of(StressRequirement.output(8, -64)), output).plan().commit()).isTrue();
        assertThat(output.ledger.generatedRpm()).isEqualTo(-64);
        assertThat(plan(laneA, 1, List.of(StressRequirement.output(8, 257)), output).failure().reason())
                .isEqualTo(CreateFailureReasons.INVALID_OUTPUT_RPM);
    }

    @Test
    void output_capacity_scales_once_and_lifecycle_releases_output_without_losing_input() {
        var input = new Port(new Network(1000), 10, IOType.INPUT);
        var firstOutput = new Port(new Network(0), 0, IOType.OUTPUT);
        var secondOutput = new Port(new Network(0), 0, IOType.OUTPUT);
        var lane = new StressSession();
        var result = plan(lane, 3, List.of(StressRequirement.input(2, 0), StressRequirement.output(8, -64)),
                input, firstOutput, secondOutput);
        assertThat(result.plan().commitInputs()).isTrue();
        assertThat(firstOutput.ledger.baseStress()).isZero();
        assertThat(result.plan().commitOutputs()).isTrue();
        assertThat(firstOutput.ledger.baseStress() + secondOutput.ledger.baseStress()).isEqualTo(24);
        assertThat(firstOutput.ledger.generatedRpm()).isEqualTo(-64);
        lane.releaseOutputs();
        assertThat(input.ledger.baseStress()).isEqualTo(6);
        assertThat(firstOutput.ledger.baseStress()).isZero();
        lane.releaseAll();
        assertThat(input.ledger.baseStress()).isZero();
    }

    @Test
    void original_requirement_indexes_propagate_to_contributions_and_failures() {
        var port = new Port(new Network(100), 10, IOType.INPUT);
        var lane = new StressSession();
        var result = new RequirementPlanner().plan(List.of(StressRequirement.input(8, 0)), List.of(new PortCapability(port)),
                context(lane, 1), List.of(17));
        assertThat(result.plan().commit()).isTrue();
        assertThat(port.ledger.get(lane, 0)).isNull();
        assertThat(port.ledger.get(lane, 17).baseStress()).isEqualTo(8);
        var failure = new RequirementPlanner().plan(List.of(StressRequirement.input(8, 0)), List.of(),
                context(lane, 1), List.of(17));
        assertThat(failure.failureRequirementIndex()).isEqualTo(17);
    }

    @Test
    void nonfinite_networks_and_float_overflow_do_not_become_usable_capacity() {
        var network = new Network(Double.POSITIVE_INFINITY);
        var port = new Port(network, 10, IOType.INPUT);
        var lane = new StressSession();
        assertThat(plan(lane, 1, List.of(StressRequirement.input(8, 0)), port).successful()).isFalse();
        network.capacity = 100;
        network.externalStress = Double.NaN;
        assertThat(plan(lane, 1, List.of(StressRequirement.input(8, 0)), port).successful()).isFalse();
        var output = new Port(new Network(0), 0, IOType.OUTPUT);
        assertThat(plan(lane, 1, List.of(StressRequirement.output(Double.MAX_VALUE, 64)), output).successful()).isFalse();
        assertThat(output.ledger.baseStress()).isZero();
        network.externalStress = 0;
        port.enabled = false;
        port.actualRpm = Double.MIN_VALUE;
        assertThat(plan(lane, 1, List.of(StressRequirement.input(8, 0)), port).failure().reason())
                .isEqualTo(CreateFailureReasons.MISSING_ROTATION);
        port.actualRpm = 10;
        assertThat(plan(lane, 1, List.of(StressRequirement.input(Double.MIN_VALUE, 0)), port).successful()).isFalse();
    }

    @Test
    void output_capacity_can_split_without_duplicating_request_and_agreed_requirements_share_a_port() {
        var first = new Port(new Network(0), 0, IOType.OUTPUT);
        var second = new Port(new Network(0), 0, IOType.OUTPUT);
        var lane = new StressSession();
        var result = plan(lane, 1, List.of(StressRequirement.output((double) Float.MAX_VALUE, 2)), first, second);
        assertThat(result.plan().commit()).isTrue();
        assertThat(first.ledger.baseStress() + second.ledger.baseStress()).isEqualTo((double) Float.MAX_VALUE);
        lane.releaseAll();
        assertThat(plan(lane, 1, List.of(StressRequirement.output(8, 64), StressRequirement.output(4, 64)), first)
                .plan().commit()).isTrue();
        assertThat(first.ledger.baseStress()).isEqualTo(12);
    }

    @Test
    void throwing_partial_commit_cleans_its_mutated_contributions_and_ends_batch_scope() {
        var first = new Port(new Network(40), 10, IOType.INPUT);
        var second = new Port(new Network(40), 10, IOType.INPUT);
        var lane = new StressSession();
        second.throwAfterApply = true;
        var result = plan(lane, 1, List.of(StressRequirement.input(8, 0)), first, second);
        assertThatThrownBy(() -> result.plan().commit())
                .isInstanceOf(IllegalStateException.class);
        assertThat(first.ledger.baseStress()).isZero();
        assertThat(second.ledger.baseStress()).isZero();
        assertThat(lane.committing(0)).isFalse();
    }

    @Test
    void output_headroom_preserves_another_lane_and_splits_into_an_independent_port() {
        var first = new Port(new Network(0), 0, IOType.OUTPUT);
        var second = new Port(new Network(0), 0, IOType.OUTPUT);
        var existingLane = new StressSession();
        var newLane = new StressSession();
        var existing = StressRequirement.output(4E36, 64);
        var request = StressRequirement.output(2E36, 64);
        assertThat(plan(existingLane, 1, List.of(existing), first).plan().commit()).isTrue();
        var previous = first.ledger.get(existingLane, 0);
        var result = plan(newLane, 1, List.of(request), first, second);
        assertThat(result.successful()).isTrue();
        assertThat(first.ledger.get(newLane, 0)).isNull();
        assertThat(second.ledger.get(newLane, 0)).isNull();
        assertThat(result.plan().commit()).isTrue();
        assertThat(first.ledger.get(existingLane, 0)).isEqualTo(previous);
        assertThat(first.ledger.get(newLane, 0)).isNotNull();
        assertThat(second.ledger.get(newLane, 0)).isNotNull();
        assertThat(first.ledger.get(newLane, 0).baseStress() + second.ledger.get(newLane, 0).baseStress())
                .isCloseTo(request.stress(), within(request.stress() * 1E-14));
        assertThat(Float.isFinite((float) first.network.capacity())).isTrue();
        assertThat(Float.isFinite((float) second.network.capacity())).isTrue();
    }

    @Test
    void output_replacement_credits_only_the_same_index_and_recomputes_headroom_for_new_rpm() {
        var port = new Port(new Network(0), 0, IOType.OUTPUT);
        var lane = new StressSession();
        var request = StressRequirement.output(4E36, 64);
        for (int tick = 0; tick < 3; tick++) {
            var result = plan(lane, 1, List.of(request), port);
            assertThat(result.successful()).isTrue();
            assertThat(result.plan().commit()).isTrue();
            assertThat(port.ledger.get(lane, 0)).isEqualTo(new StressContributions.Contribution(4E36, 64));
        }
        var otherIndex = new RequirementPlanner().plan(List.of(StressRequirement.output(2E36, 64)),
                List.of(new PortCapability(port)), context(lane, 1), List.of(1));
        assertThat(otherIndex.successful()).isFalse();
        assertThat(port.ledger.get(lane, 1)).isNull();
        var newRpm = plan(lane, 1, List.of(StressRequirement.output(2E37, -16)), port);
        assertThat(newRpm.successful()).isTrue();
        assertThat(newRpm.plan().commit()).isTrue();
        assertThat(port.ledger.get(lane, 0)).isEqualTo(new StressContributions.Contribution(2E37, -16));
    }

    @Test
    void base_reservations_include_other_lanes_and_all_requirements_for_output_and_disabled_input() {
        for (IOType io : IOType.values()) {
            var first = new Port(new Network(0), 64, io);
            var second = new Port(new Network(0), 64, io);
            first.enabled = second.enabled = false;
            var existingLane = new StressSession();
            var newLane = new StressSession();
            var existing = io == IOType.INPUT ? StressRequirement.input(4E36, 1) : StressRequirement.output(4E36, 64);
            var small = io == IOType.INPUT ? StressRequirement.input(1E36, 1) : StressRequirement.output(1E36, 64);
            assertThat(plan(existingLane, 1, List.of(existing), first).plan().commit()).isTrue();
            var previous = first.ledger.get(existingLane, 0);
            var requirements = List.of(small, small);
            assertThat(plan(newLane, 1, requirements, first).successful()).isFalse();
            assertThat(first.ledger.get(existingLane, 0)).isEqualTo(previous);
            var result = plan(newLane, 1, requirements, first, second);
            assertThat(result.successful()).isTrue();
            assertThat(result.plan().commit()).isTrue();
            assertThat(first.ledger.get(existingLane, 0)).isEqualTo(previous);
            assertThat(first.ledger.get(newLane, 0)).isNotNull();
            assertThat(first.ledger.get(newLane, 1)).isNotNull();
            assertThat(second.ledger.get(newLane, 1)).isNotNull();
            double total = first.ledger.get(newLane, 0).baseStress() + first.ledger.get(newLane, 1).baseStress()
                    + second.ledger.get(newLane, 1).baseStress();
            assertThat(total).isCloseTo(2 * small.stress(), within(small.stress() * 1E-14));
        }
    }

    @Test
    void full_capacity_multi_index_replacements_credit_each_old_base_once_in_both_directions() {
        for (IOType io : IOType.values()) {
            var port = new Port(new Network(0), 64, io);
            port.enabled = false;
            var lane = new StressSession();
            var large = io == IOType.INPUT ? StressRequirement.input(2E36, 1) : StressRequirement.output(2E36, 64);
            var small = io == IOType.INPUT ? StressRequirement.input(1E36, 1) : StressRequirement.output(1E36, 64);
            assertThat(plan(lane, 1, List.of(large, large), port).plan().commit()).isTrue();
            for (int tick = 0; tick < 3; tick++) {
                var replacement = plan(lane, 1, List.of(large, large, small), port);
                assertThat(replacement.successful()).isTrue();
                assertThat(replacement.plan().commit()).isTrue();
                assertThat(port.ledger.get(lane, 0).baseStress()).isEqualTo(large.stress());
                assertThat(port.ledger.get(lane, 1).baseStress()).isEqualTo(large.stress());
                assertThat(port.ledger.get(lane, 2).baseStress()).isEqualTo(small.stress());
            }
        }
    }

    @Test
    void disabled_input_uses_local_rpm_aggregate_headroom_and_preserves_the_existing_lane() {
        var first = new Port(new Network(0), 128, IOType.INPUT);
        var second = new Port(new Network(0), 128, IOType.INPUT);
        first.enabled = second.enabled = false;
        var existingLane = new StressSession();
        var newLane = new StressSession();
        var existing = StressRequirement.input(1.8E36, 1);
        var request = StressRequirement.input(1E36, 1);
        assertThat(plan(existingLane, 1, List.of(existing), first).plan().commit()).isTrue();
        var previous = first.ledger.get(existingLane, 0);
        assertThat(plan(newLane, 1, List.of(request), first).successful()).isFalse();
        var result = plan(newLane, 1, List.of(request), first, second);
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().commit()).isTrue();
        assertThat(first.ledger.get(existingLane, 0)).isEqualTo(previous);
        assertThat(first.ledger.get(newLane, 0).baseStress() + second.ledger.get(newLane, 0).baseStress())
                .isCloseTo(request.stress(), within(request.stress() * 1E-14));
        assertThat(Float.isFinite((float) first.network.load())).isTrue();
        assertThat(Float.isFinite((float) second.network.load())).isTrue();
    }

    @Test
    void live_headroom_changes_fail_preflight_before_any_native_apply_in_both_directions() {
        for (IOType io : IOType.values()) {
            var port = new Port(new Network(0), 64, io);
            port.enabled = false;
            var existingLane = new StressSession();
            var newLane = new StressSession();
            var existing = io == IOType.INPUT ? StressRequirement.input(4E36, 1) : StressRequirement.output(4E36, 64);
            var request = io == IOType.INPUT ? StressRequirement.input(0.5E36, 1) : StressRequirement.output(0.5E36, 64);
            var grown = io == IOType.INPUT ? StressRequirement.input(5E36, 1) : StressRequirement.output(5E36, 64);
            assertThat(plan(existingLane, 1, List.of(existing), port).plan().commit()).isTrue();
            var stale = plan(newLane, 1, List.of(request), port);
            assertThat(stale.successful()).isTrue();
            assertThat(plan(existingLane, 1, List.of(grown), port).plan().commit()).isTrue();
            int applies = port.applyCount;
            assertThat(stale.plan().commit()).isFalse();
            assertThat(stale.plan().failure().reason()).isEqualTo(CreateFailureReasons.INSUFFICIENT_STRESS);
            assertThat(port.applyCount).isEqualTo(applies);
            assertThat(port.ledger.get(newLane, 0)).isNull();
            assertThat(port.ledger.get(existingLane, 0).baseStress()).isEqualTo(grown.stress());
        }
    }

    @Test
    void non_power_of_two_output_speed_splits_at_a_native_float_safe_product_boundary() {
        var first = new Port(new Network(0), 0, IOType.OUTPUT);
        var second = new Port(new Network(0), 0, IOType.OUTPUT);
        var lane = new StressSession();
        var request = StressRequirement.output((double) Float.MAX_VALUE / 25, 25);
        assertThat(Float.isFinite((float) request.stress() * 25F)).isFalse();
        var result = plan(lane, 1, List.of(request), first, second);
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().commit()).isTrue();
        assertThat(first.ledger.baseStress() + second.ledger.baseStress())
                .isCloseTo(request.stress(), within(request.stress() * 1E-14));
        assertThat(second.ledger.get(lane, 0)).isNotNull();
        assertThat(Float.isFinite((float) first.ledger.baseStress() * 25F)).isTrue();
        assertThat(Float.isFinite((float) second.ledger.baseStress() * 25F)).isTrue();
    }

    private static PlanningResult plan(StressSession session, long parallelism, List<StressRequirement> requirements,
                                       Port... ports) {
        List<MachineCapability> capabilities = new ArrayList<>();
        for (Port port : ports) capabilities.add(new PortCapability(port));
        return new RequirementPlanner().plan(new ArrayList<>(requirements), capabilities, context(session, parallelism));
    }

    private static PlanningContext context(StressSession session, long parallelism) {
        return new PlanningContext(parallelism, 0, false, new PlanningReservations(), Map.of(), session);
    }

    /** Network fixture exposes physical sums, never a planner or allocation algorithm.
     * @author howxu <dev@howxu.cn>
     */
    private static final class Network {
        double capacity;
        double externalStress;
        final List<Port> ports = new ArrayList<>();
        Network(double capacity) { this.capacity = capacity; }
        double capacity() {
            float sum = (float) capacity;
            for (Port port : ports) if (port.io == IOType.OUTPUT) {
                sum += (float) port.ledger.baseStress() * Math.abs((float) port.ledger.generatedRpm());
            }
            return sum;
        }
        double load() {
            float sum = (float) externalStress;
            for (Port port : ports) if (port.io == IOType.INPUT) {
                sum += (float) port.ledger.baseStress() * Math.abs((float) port.theoreticalRpm);
            }
            return sum;
        }
    }

    /** Minimal native facet, backed by the production contribution ledger.
     * @author howxu <dev@howxu.cn>
     */
    private static final class Port implements StressFacet {
        Network network;
        final IOType io;
        final StressContributions ledger = new StressContributions();
        double actualRpm;
        double theoreticalRpm;
        boolean enabled = true;
        boolean overstressed;
        boolean rejectApply;
        boolean throwAfterApply;
        boolean observedBatch;
        int applyCount;
        List<String> tags = List.of();
        Port(Network network, double rpm, IOType io) {
            this.io = io;
            actualRpm = theoreticalRpm = rpm;
            moveTo(network);
        }
        void moveTo(Network next) {
            if (network != null) network.ports.remove(this);
            network = next;
            next.ports.add(this);
        }
        @Override public Object networkIdentity() { return network; }
        @Override public boolean stressEnabled() { return enabled; }
        @Override public StressState state() {
            double generated = ledger.generatedRpm();
            return new StressState(BlockPos.ZERO, actualRpm, theoreticalRpm, generated, ledger.baseStress(),
                    (float) ledger.baseStress() * Math.abs((float) (io == IOType.INPUT ? theoreticalRpm : generated)),
                    network.capacity(), network.load(), true, overstressed);
        }
        @Override public boolean acceptsGeneratedRpm(StressSession session, int index, double rpm) {
            return ledger.accepts(session, index, rpm);
        }
        @Override public double ownedActualStress(StressSession session, int index) {
            var owned = ledger.get(session, index);
            return owned == null || io != IOType.INPUT ? 0
                    : ((float) ledger.baseStress() - (float) (ledger.baseStress() - owned.baseStress()))
                    * (double) Math.abs((float) theoreticalRpm);
        }
        @Override public double ownedBaseStress(StressSession session, int index) {
            var owned = ledger.get(session, index);
            return owned == null ? 0D : owned.baseStress();
        }
        @Override public CapabilityResult apply(StressSession session, int index, double base, double rpm) {
            applyCount++;
            if (rejectApply) return CapabilityResult.failure(ExecutionStatus.blocked(CreateRecipeTypes.STRESS,
                    CreateRecipeTypes.STRESS, FailureOccurrence.at(CreateFailureReasons.INSUFFICIENT_STRESS,
                    CreateRecipeTypes.STRESS, FailurePhase.CAPABILITY_COMMIT, null, index, Map.of())));
            observedBatch = session.committing(index);
            var previous = ledger.get(session, index);
            double nextBase = ledger.baseStress() - (previous == null ? 0 : previous.baseStress()) + base;
            float speed = Math.abs((float) (io == IOType.INPUT ? theoreticalRpm : rpm));
            // Same aggregate checks as production StressPortCapability.apply, never bypassed by the batch marker.
            if (!StressContributions.positiveFloat(base) || !StressContributions.positiveFloat(nextBase)
                    || !StressContributions.positiveFloat((float) nextBase * speed)) return floatFailure(index);
            double delta = ((float) nextBase - (float) ledger.baseStress()) * (double) speed;
            if (io == IOType.INPUT && enabled && !session.committing(index) && delta > network.capacity() - network.load()) {
                return floatFailure(index);
            }
            if (io == IOType.OUTPUT) {
                double oldCapacity = (float) ledger.baseStress() * Math.abs((float) ledger.generatedRpm());
                double nextCapacity = (float) nextBase * speed;
                double aggregate = network.capacity() - oldCapacity + nextCapacity;
                if (!Double.isFinite(aggregate) || !Float.isFinite((float) aggregate)) return floatFailure(index);
            }
            ledger.replace(session, index, new StressContributions.Contribution(base, rpm));
            if (!Double.isFinite(network.capacity()) || !Double.isFinite(network.load())) {
                ledger.release(session, index);
                return floatFailure(index);
            }
            if (throwAfterApply) throw new IllegalStateException("fixture contribution callback failed");
            return CapabilityResult.successful();
        }
        private static CapabilityResult floatFailure(int index) {
            return CapabilityResult.failure(ExecutionStatus.blocked(CreateRecipeTypes.STRESS,
                    CreateRecipeTypes.STRESS, FailureOccurrence.at(CreateFailureReasons.INSUFFICIENT_STRESS,
                    CreateRecipeTypes.STRESS, FailurePhase.CAPABILITY_COMMIT, null, index, Map.of())));
        }
        @Override public void release(StressSession session) { ledger.release(session); }
        @Override public void release(StressSession session, int index) { ledger.release(session, index); }
    }

    /** Capability protocol adapter; the facet intentionally implements only StressFacet.
     * @author howxu <dev@howxu.cn>
     */
    private record PortCapability(Port port) implements MachineCapability {
        @Override public CapabilityType type() { return new CapabilityType(CreateRecipeTypes.STRESS); }
        @Override public CapabilityDirections directions() { return CapabilityDirections.of(port.io); }
        @Override public CapabilityView view() {
            return new CapabilityView() {
                @Override public CapabilityType type() { return PortCapability.this.type(); }
                @Override public CapabilityDirections directions() { return PortCapability.this.directions(); }
                @Override public List<String> tags() { return port.tags; }
                @Override public Set<Class<? extends CapabilityFacet>> facets() { return Set.of(StressFacet.class); }
            };
        }
        @Override public <F extends CapabilityFacet> Optional<F> facet(Class<F> type) {
            return type.isInstance(port) ? Optional.of(type.cast(port)) : Optional.empty();
        }
        @Override public CapabilityOperation prepare(CapabilityRequest request) {
            throw new UnsupportedOperationException("Stress planning operates on the declared facet");
        }
    }
}
