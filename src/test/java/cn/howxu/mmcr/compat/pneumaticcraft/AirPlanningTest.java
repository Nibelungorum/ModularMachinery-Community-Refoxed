package cn.howxu.mmcr.compat.pneumaticcraft;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.OutputFit;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.compat.pneumaticcraft.AirState;
import cn.howxu.mmcr.api.compat.pneumaticcraft.PneumaticAirFacet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler.WakeupReason;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.internal.recipe.RequirementPlanner;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/** Tests the production planner using only mutable neutral handler snapshots.
 * @author howxu <dev@howxu.cn>
 */
class AirPlanningTest {
    private RequirementHandlerRegistry.TestScope scope;

    @BeforeEach
    void setup() {
        scope = RequirementHandlerRegistry.openTestScope();
        PneumaticRecipeTypes.register();
        PneumaticCraftBridgeBootstrap.installForTesting(() -> true);
    }

    @AfterEach
    void cleanup() {
        scope.close();
        PneumaticCraftBridgeBootstrap.resetForTesting();
    }

    @Test
    void sharedRequirementsAndDuplicateViewsReserveOnePhysicalBudget() {
        var port = new Port(IOType.INPUT, 120, 10, List.of("drive"));
        var alias = new Port(IOType.INPUT, port.store, List.of("drive"));
        var result = plan(List.of(AirRequirement.input(40, 0F), AirRequirement.input(30, 0F)),
                List.of(port, port, alias), 8, false);
        assertThat(result.plan().parallelism()).isEqualTo(1);
        assertThat(port.state().air()).isEqualTo(120);
        assertThat(port.validations).isZero();
        assertThat(port.applications).isZero();
        assertThat(result.plan().commit()).isTrue();
        assertThat(port.state().air()).isEqualTo(50);
        assertThat(port.applications).isEqualTo(2);
        assertThat(alias.applications).isZero();
    }

    @Test
    void differentTaggedFacetViewsShareBudgetAndVirtualPressure() {
        var first = new Port(IOType.INPUT, 120, 10, List.of("first"));
        var second = new Port(IOType.INPUT, first.store, List.of("second"));
        var result = plan(List.of(AirRequirement.input(40, 4F, List.of("first")),
                        AirRequirement.input(30, 4F, List.of("second"))), List.of(first, second), 8, false);
        assertThat(result.plan().parallelism()).isEqualTo(1);
        assertThat(result.plan().commit()).isTrue();
        assertThat(first.state().air()).isEqualTo(50);
        assertThat(first.applications).isEqualTo(1);
        assertThat(second.applications).isEqualTo(1);
        first.store.air = 50;
        var pressure = plan(List.of(AirRequirement.input(20, 4F, List.of("first")),
                        AirRequirement.input(0, 4F, List.of("second"))), List.of(first, second), 1, false);
        assertThat(pressure.failure().reason()).isSameAs(AirFailureReasons.INSUFFICIENT_PRESSURE);
        assertThat(first.state().air()).isEqualTo(50);
    }

    @Test
    void eachParticipatingPortMustMeetPressureIndividually() {
        var low = new Port(IOType.INPUT, 20, 10, List.of());
        var otherLow = new Port(IOType.INPUT, 20, 10, List.of());
        var denied = plan(List.of(AirRequirement.input(1, 4F)), List.of(low, otherLow), 1, false);
        assertThat(denied.failure().reason()).isSameAs(AirFailureReasons.INSUFFICIENT_PRESSURE);
        var high = new Port(IOType.INPUT, 50, 10, List.of());
        var allowed = plan(List.of(AirRequirement.input(50, 4F)), List.of(low, high), 1, false);
        assertThat(allowed.plan().commit()).isTrue();
        assertThat(high.state().air()).isZero();
        assertThat(low.state().air()).isEqualTo(20);
    }

    @Test
    void pressureOnlyCommitRevalidatesWithoutChangingAir() {
        var port = new Port(IOType.INPUT, 40, 10, List.of());
        var success = plan(List.of(AirRequirement.input(0, 4F)), List.of(port), 100, false);
        assertThat(success.plan().parallelism()).isEqualTo(100);
        assertThat(success.plan().hasOperations(0)).isTrue();
        assertThat(success.plan().commit()).isTrue();
        assertThat(port.state().air()).isEqualTo(40);
        assertThat(port.validations).isEqualTo(1);
        var stale = plan(List.of(AirRequirement.input(0, 4F)), List.of(port), 1, false);
        port.store.air = 39;
        assertThat(stale.plan().commit()).isFalse();
        assertThat(stale.plan().failure().reason()).isSameAs(AirFailureReasons.INSUFFICIENT_PRESSURE);
        assertThat(port.applications).isEqualTo(1);
    }

    @Test
    void nextRequirementChecksPressureAfterVirtualDrainAndCannotBorrowAnotherPortPressure() {
        var port = new Port(IOType.INPUT, 50, 10, List.of());
        var result = plan(List.of(AirRequirement.input(20, 4F), AirRequirement.input(0, 4F)),
                List.of(port), 8, false);
        assertThat(result.plan()).isNull();
        assertThat(result.failure().reason()).isSameAs(AirFailureReasons.INSUFFICIENT_PRESSURE);
        assertThat(port.state().air()).isEqualTo(50);
        var once = plan(List.of(AirRequirement.input(20, 4F)), List.of(port), 8, false);
        assertThat(once.plan().parallelism()).isEqualTo(2);
        assertThat(once.plan().commit()).isTrue();
        assertThat(port.state().air()).isEqualTo(10);
    }

    @Test
    void staleSplitPreflightsAllPortsBeforeAnyApply() {
        var first = new Port(IOType.INPUT, 50, 10, List.of());
        var second = new Port(IOType.INPUT, 50, 10, List.of());
        var result = plan(List.of(AirRequirement.input(80, 4F)), List.of(first, second), 1, false);
        second.store.air = 39;
        assertThat(result.plan().commit()).isFalse();
        assertThat(first.state().air()).isEqualTo(50);
        assertThat(second.state().air()).isEqualTo(39);
        assertThat(first.validations).isEqualTo(1);
        assertThat(second.validations).isEqualTo(1);
        assertThat(first.applications + second.applications).isZero();
    }

    @Test
    void startupPreflightUsesExactChosenParallelismAndSharedVirtualBudgetWithoutApply() {
        var port = new Port(IOType.INPUT, 120, 10, List.of());
        var tooLarge = AirRequirementHandler.preflightStart(AirRequirement.input(70, 4F), List.of(port),
                new PlanningContext(2, 3));
        assertThat(tooLarge.success()).isFalse();
        assertThat(tooLarge.status().reason()).isSameAs(AirFailureReasons.INSUFFICIENT_AIR);
        var reservations = new PlanningReservations();
        assertThat(AirRequirementHandler.preflightStart(AirRequirement.input(40, 4F), List.of(port),
                new PlanningContext(2, 3, false, reservations)).success()).isTrue();
        assertThat(AirRequirementHandler.preflightStart(AirRequirement.input(0, 4F), List.of(port),
                new PlanningContext(2, 4, false, reservations)).success()).isTrue();
        assertThat(AirRequirementHandler.preflightStart(AirRequirement.input(1, 4F), List.of(port),
                new PlanningContext(2, 5, false, reservations)).success()).isTrue();
        var lowPressure = AirRequirementHandler.preflightStart(AirRequirement.input(0, 4F), List.of(port),
                new PlanningContext(2, 6, false, reservations));
        assertThat(lowPressure.success()).isFalse();
        assertThat(lowPressure.status().reason()).isSameAs(AirFailureReasons.INSUFFICIENT_PRESSURE);
        assertThat(lowPressure.status().failure().trace().frames().getFirst().requirementIndex()).isEqualTo(6);
        assertThat(port.state().air()).isEqualTo(120);
        assertThat(port.validations).isEqualTo(3);
        assertThat(port.applications).isZero();
    }

    @Test
    void successfulSplitValidatesAllBeforeApplyingAnyPort() {
        var events = new ArrayList<String>();
        var first = new Port(IOType.INPUT, 50, 10, List.of());
        var second = new Port(IOType.INPUT, 50, 10, List.of());
        first.events = events;
        second.events = events;
        var result = plan(List.of(AirRequirement.input(80, 4F)), List.of(first, second), 1, false);
        assertThat(result.plan().commit()).isTrue();
        assertThat(events).containsExactly("validate", "validate", "apply", "apply");
        assertThat(first.state().air() + second.state().air()).isEqualTo(20);
    }

    @Test
    void outputsRespectFullPartialAndZeroCapacityPolicies() {
        var port = new Port(IOType.OUTPUT, 190, 10, List.of());
        var full = plan(List.of(AirRequirement.output(20)), List.of(port), 1, false);
        assertThat(full.failure().reason()).isSameAs(AirFailureReasons.OUTPUT_BLOCKED);
        assertThat(full.outputSimulations()).singleElement().satisfies(simulation -> {
            assertThat(simulation.requested()).isEqualTo(20);
            assertThat(simulation.accepted()).isEqualTo(10);
            assertThat(simulation.fit()).isEqualTo(OutputFit.PARTIAL);
        });
        var partial = plan(List.of(AirRequirement.output(20)), List.of(port), 4, true);
        assertThat(partial.plan().parallelism()).isEqualTo(4);
        assertThat(partial.plan().outputSimulations()).singleElement().satisfies(simulation -> {
            assertThat(simulation.requested()).isEqualTo(80);
            assertThat(simulation.accepted()).isEqualTo(10);
            assertThat(simulation.fit()).isEqualTo(OutputFit.PARTIAL);
        });
        assertThat(partial.plan().commit()).isTrue();
        assertThat(port.state().air()).isEqualTo(200);
        assertThat(plan(List.of(AirRequirement.output(1)), List.of(port), 1, true).failure().reason())
                .isSameAs(AirFailureReasons.OUTPUT_BLOCKED);
    }

    @Test
    void perRequirementOutputPolicyOverridesRecipeDefault() {
        var port = new Port(IOType.OUTPUT, 190, 10, List.of());
        var planner = new RequirementPlanner();
        var full = planner.plan(List.of(AirRequirement.output(20)), List.of(port),
                new PlanningContext(1, 0, true, new PlanningReservations(), Map.of(0, OutputPolicy.REQUIRE_FULL)));
        assertThat(full.failure().reason()).isSameAs(AirFailureReasons.OUTPUT_BLOCKED);
        var partial = planner.plan(List.of(AirRequirement.output(20)), List.of(port),
                new PlanningContext(1, 0, false, new PlanningReservations(), Map.of(0, OutputPolicy.ALLOW_PARTIAL)));
        assertThat(partial.plan().commit()).isTrue();
        assertThat(port.state().air()).isEqualTo(200);
    }

    @Test
    void outputPriorityAndSignedVacuumBudgetArePreservedAcrossRequirements() {
        var low = new Port(IOType.OUTPUT, 0, 10, List.of());
        var high = new Port(IOType.OUTPUT, -100, 10, List.of());
        high.priority = 10;
        var result = plan(List.of(AirRequirement.output(150), AirRequirement.output(150)),
                List.of(low, high, high), 1, false);
        assertThat(result.plan().commit()).isTrue();
        assertThat(high.state().air()).isEqualTo(200);
        assertThat(low.state().air()).isZero();
        assertThat(high.applications).isEqualTo(2);
    }

    @Test
    void deepVacuumSingleOutputAcceptsOnlyOneExecutableIntDelta() {
        var port = new Port(IOType.OUTPUT, -Integer.MAX_VALUE, 10_000, List.of());
        long requested = (long) Integer.MAX_VALUE + 1L;
        var full = plan(List.of(AirRequirement.output(requested)), List.of(port), 1, false);
        assertThat(full.plan()).isNull();
        assertThat(full.failure().reason()).isSameAs(AirFailureReasons.OUTPUT_BLOCKED);
        assertThat(full.outputSimulations()).singleElement().satisfies(simulation -> {
            assertThat(simulation.accepted()).isEqualTo(Integer.MAX_VALUE);
            assertThat(simulation.fit()).isEqualTo(OutputFit.PARTIAL);
        });
        var partial = plan(List.of(AirRequirement.output(requested)), List.of(port), 1, true);
        assertThat(partial.plan().outputSimulations()).singleElement().satisfies(simulation -> {
            assertThat(simulation.requested()).isEqualTo(requested);
            assertThat(simulation.accepted()).isEqualTo(Integer.MAX_VALUE);
            assertThat(simulation.fit()).isEqualTo(OutputFit.PARTIAL);
        });
        assertThat(port.state().air()).isEqualTo(-Integer.MAX_VALUE);
        assertThat(port.validations + port.applications).isZero();
        assertThat(partial.plan().commit()).isTrue();
        assertThat(port.state().air()).isZero();
        assertThat(port.applications).isEqualTo(1);
    }

    @Test
    void deepVacuumOutputSplitsIntMaxPlusOneAcrossTwoNativeDeltas() {
        var first = new Port(IOType.OUTPUT, -Integer.MAX_VALUE, 10_000, List.of());
        var second = new Port(IOType.OUTPUT, 0, 10_000, List.of());
        var result = plan(List.of(AirRequirement.output((long) Integer.MAX_VALUE + 1L)),
                List.of(first, first, second), 1, false);
        assertThat(result.plan().outputSimulations()).singleElement().satisfies(simulation -> {
            assertThat(simulation.accepted()).isEqualTo((long) Integer.MAX_VALUE + 1L);
            assertThat(simulation.fit()).isEqualTo(OutputFit.FULL);
        });
        assertThat(result.plan().commit()).isTrue();
        assertThat(first.state().air()).isZero();
        assertThat(second.state().air()).isEqualTo(1);
        assertThat(first.applications).isEqualTo(1);
        assertThat(second.applications).isEqualTo(1);
    }

    @Test
    void inputIntBoundaryStillSplitsWithoutOverflowOrVacuumExtraction() {
        var first = new Port(IOType.INPUT, Integer.MAX_VALUE, 10_000, List.of());
        var second = new Port(IOType.INPUT, 1, 10_000, List.of());
        var requirement = AirRequirement.input((long) Integer.MAX_VALUE + 1L, 0F);
        var blocked = plan(List.of(requirement), List.of(first), 1, false);
        assertThat(blocked.failure().reason()).isSameAs(AirFailureReasons.INSUFFICIENT_AIR);
        assertThat(first.state().air()).isEqualTo(Integer.MAX_VALUE);
        var split = plan(List.of(requirement), List.of(first, second), 1, false);
        assertThat(split.plan().commit()).isTrue();
        assertThat(first.state().air()).isZero();
        assertThat(second.state().air()).isZero();
        assertThat(first.applications + second.applications).isEqualTo(2);
    }

    @Test
    void virtualOutputsShareIntStorageBoundAndLeaveBaseReservationsUntouched() {
        var first = new Port(IOType.OUTPUT, Integer.MAX_VALUE - 3, Integer.MAX_VALUE, List.of("first"));
        var alias = new Port(IOType.OUTPUT, first.store, List.of("second"));
        var reservations = new PlanningReservations();
        var requirements = List.<MachineRequirement>of(AirRequirement.output(2, List.of("first")),
                AirRequirement.output(2, List.of("second")));
        var planner = new RequirementPlanner();
        var full = planner.plan(requirements, List.of(first, alias),
                new PlanningContext(1, 0, false, reservations));
        assertThat(full.plan()).isNull();
        assertThat(full.failure().reason()).isSameAs(AirFailureReasons.OUTPUT_BLOCKED);
        assertThat(first.state().air()).isEqualTo(Integer.MAX_VALUE - 3);
        assertThat(reservations.valueAvailable(first.store, Integer.MAX_VALUE, first.store.air, true)).isEqualTo(3);
        var partial = planner.plan(requirements, List.of(first, alias),
                new PlanningContext(1, 0, true, reservations.copy()));
        assertThat(partial.plan().outputSimulations()).extracting(simulation -> simulation.accepted())
                .containsExactly(2L, 1L);
        assertThat(reservations.valueAvailable(first.store, Integer.MAX_VALUE, first.store.air, true)).isEqualTo(3);
        assertThat(partial.plan().commit()).isTrue();
        assertThat(first.state().air()).isEqualTo(Integer.MAX_VALUE);
        assertThat(first.applications).isEqualTo(1);
        assertThat(alias.applications).isEqualTo(1);
        assertThat(plan(List.of(AirRequirement.output(1)), List.of(first), 1, true).failure().reason())
                .isSameAs(AirFailureReasons.OUTPUT_BLOCKED);
    }

    @Test
    void belowNativeVacuumFloorSkipsNonExecutableInsertionBeforeReportingFull() {
        var deep = new Port(IOType.OUTPUT, -10_100, 10_000, List.of());
        for (boolean partial : List.of(false, true)) {
            var blocked = plan(List.of(AirRequirement.output(99)), List.of(deep), 1, partial);
            assertThat(blocked.plan()).isNull();
            assertThat(blocked.failure().reason()).isSameAs(AirFailureReasons.OUTPUT_BLOCKED);
            assertThat(blocked.outputSimulations()).singleElement().satisfies(simulation -> {
                assertThat(simulation.accepted()).isZero();
                assertThat(simulation.fit()).isEqualTo(OutputFit.NONE);
            });
        }
        assertThat(deep.state().air()).isEqualTo(-10_100);
        assertThat(deep.validations + deep.applications).isZero();
        var other = new Port(IOType.OUTPUT, 0, 10_000, List.of());
        var routed = plan(List.of(AirRequirement.output(1)), List.of(deep, other), 1, false);
        assertThat(routed.plan().commit()).isTrue();
        assertThat(deep.state().air()).isEqualTo(-10_100);
        assertThat(deep.applications).isZero();
        assertThat(other.state().air()).isEqualTo(1);
        assertThat(plan(List.of(AirRequirement.output(100)), List.of(deep), 1, false).plan().commit()).isTrue();
        assertThat(deep.state().air()).isEqualTo(-10_000);
    }

    @Test
    void populatedReservationCopyKeepsOneSignedBudgetForInputAndRepresentableOutput() {
        var output = new Port(IOType.OUTPUT, Integer.MAX_VALUE - 4, Integer.MAX_VALUE, List.of());
        var input = new Port(IOType.INPUT, output.store, List.of());
        long safetyCapacity = (long) Math.floor((double) output.state().dangerPressure() * output.store.volume);
        var reservations = new PlanningReservations();
        assertThat(reservations.reserveValueTotal(output.store, safetyCapacity, output.store.air, 2, true)).isTrue();
        var result = new RequirementPlanner().plan(List.of(AirRequirement.output(3), AirRequirement.input(3, 0F)),
                List.of(output, input), new PlanningContext(1, 0, true, reservations.copy()));
        assertThat(result.plan().outputSimulations()).singleElement().satisfies(simulation -> {
            assertThat(simulation.accepted()).isEqualTo(2);
            assertThat(simulation.fit()).isEqualTo(OutputFit.PARTIAL);
        });
        assertThat(reservations.valueAvailable(output.store, Integer.MAX_VALUE, output.store.air, true)).isEqualTo(2);
        assertThat(output.state().air()).isEqualTo(Integer.MAX_VALUE - 4);
        // Settle the operation represented by the caller's existing reservation before this plan commits.
        assertThat(output.validate(2, true, 0F).success()).isTrue();
        assertThat(output.apply(2, true, 0F).success()).isTrue();
        assertThat(result.plan().commit()).isTrue();
        assertThat(output.state().air()).isEqualTo(Integer.MAX_VALUE - 3);
        assertThat(input.applications).isEqualTo(1);
    }

    @Test
    void neutralFixtureRejectsNativeAmountStorageAndVacuumClampViolations() {
        var port = new Port(IOType.OUTPUT, -Integer.MAX_VALUE, 10_000, List.of());
        assertThat(port.validate((long) Integer.MAX_VALUE + 1L, true, 0F).status().reason())
                .isSameAs(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        assertThat(port.validate(1L, true, 0F).status().reason()).isSameAs(AirFailureReasons.OUTPUT_BLOCKED);
        assertThat(port.validate(Integer.MAX_VALUE, true, 0F).success()).isTrue();
        port.store.air = Integer.MAX_VALUE;
        var largeVolume = new Port(IOType.OUTPUT, port.store.air, Integer.MAX_VALUE, List.of());
        assertThat(largeVolume.validate(1L, true, 0F).status().reason()).isSameAs(AirFailureReasons.OUTPUT_BLOCKED);
        assertThat(largeVolume.state().air()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void vacuumCannotSupplyPositiveAirAndExternalOverpressureBlocksOutputOnly() {
        var vacuum = new Port(IOType.INPUT, -20, 10, List.of());
        assertThat(plan(List.of(AirRequirement.input(0, 0F)), List.of(vacuum), 1, false).failure().reason())
                .isSameAs(AirFailureReasons.INSUFFICIENT_PRESSURE);
        var overfull = new Port(IOType.OUTPUT, 250, 10, List.of());
        assertThat(plan(List.of(AirRequirement.output(1)), List.of(overfull), 1, true).failure().reason())
                .isSameAs(AirFailureReasons.OUTPUT_BLOCKED);
        var input = new Port(IOType.INPUT, overfull.store, List.of());
        var drain = plan(List.of(AirRequirement.input(10, 24F)), List.of(input), 1, false);
        assertThat(drain.plan().commit()).isTrue();
        assertThat(input.state().air()).isEqualTo(240);
    }

    @Test
    void staleOutputSplitRejectsWithoutPartialInsertion() {
        var first = new Port(IOType.OUTPUT, 190, 10, List.of());
        var second = new Port(IOType.OUTPUT, 190, 10, List.of());
        var result = plan(List.of(AirRequirement.output(20)), List.of(first, second), 1, false);
        second.store.air = 200;
        assertThat(result.plan().commit()).isFalse();
        assertThat(first.state().air()).isEqualTo(190);
        assertThat(first.applications + second.applications).isZero();
    }

    @Test
    void zeroOutputStillRequiresModAndMatchingDirectionAndTags() {
        var wrong = new Port(IOType.INPUT, 200, 10, List.of("other"));
        assertThat(plan(List.of(AirRequirement.output(0)), List.of(wrong), 1, false).failure().reason())
                .isSameAs(AirFailureReasons.MISSING_INTERFACE);
        var output = new Port(IOType.OUTPUT, 200, 10, List.of("other"));
        assertThat(plan(List.of(AirRequirement.output(0, List.of("drive"))), List.of(output), 1, false).failure().reason())
                .isSameAs(AirFailureReasons.MISSING_INTERFACE);
        var zero = plan(List.of(AirRequirement.output(0)), List.of(output), 1, false);
        assertThat(zero.plan().commit()).isTrue();
        assertThat(output.state().air()).isEqualTo(200);
        PneumaticCraftBridgeBootstrap.installForTesting(() -> false);
        assertThat(plan(List.of(AirRequirement.output(0)), List.of(output), 1, false).failure().reason())
                .isSameAs(AirFailureReasons.UNAVAILABLE);
    }

    @Test
    void commitChecksAvailabilityBeforeAnyFacetOperation() {
        var port = new Port(IOType.INPUT, 100, 10, List.of());
        var result = plan(List.of(AirRequirement.input(40, 4F)), List.of(port), 1, false);
        PneumaticCraftBridgeBootstrap.installForTesting(() -> false);
        assertThat(result.plan().commit()).isFalse();
        assertThat(result.plan().failure().reason()).isSameAs(AirFailureReasons.UNAVAILABLE);
        assertThat(port.validations + port.applications).isZero();
        assertThat(port.state().air()).isEqualTo(100);
    }

    @Test
    void insufficientAirIsDistinctFromPressureFailure() {
        var port = new Port(IOType.INPUT, 40, 10, List.of());
        var result = plan(List.of(AirRequirement.input(50, 4F)), List.of(port), 1, false);
        assertThat(result.failure().reason()).isSameAs(AirFailureReasons.INSUFFICIENT_AIR);
        assertThat(port.state().air()).isEqualTo(40);
    }

    @Test
    void snapshotCopiesMutablePositionAndKeepsVacuumState() {
        var position = new BlockPos.MutableBlockPos(1, 2, 3);
        var state = new AirState(position, -100, 10, 20F, 25F);
        position.set(4, 5, 6);
        assertThat(state.position()).isEqualTo(new BlockPos(1, 2, 3));
        assertThat(state.pressure()).isNegative();
        var filled = new AirState(state.position(), Math.toIntExact(state.air() + state.outputCapacity()),
                state.volume(), state.dangerPressure(), state.criticalPressure());
        assertThat(filled.pressure()).isEqualTo(state.dangerPressure());
        assertThat(state.air()).isEqualTo(-100);
    }

    @Test
    void modifiersChangeRateOnlyAndHonorDirectionTargetAndChanceFilters() {
        var input = AirRequirement.input(10, 4F);
        var output = AirRequirement.output(10);
        var modifiers = List.of(new RecipeModifier("pneumaticcraft:air", IOType.INPUT, 2F,
                        RecipeModifier.Operation.MULTIPLY, false),
                new RecipeModifier("pneumaticcraft:air", IOType.OUTPUT, 3F, RecipeModifier.Operation.MULTIPLY, false),
                new RecipeModifier("pneumaticcraft:air", IOType.INPUT, 99F, RecipeModifier.Operation.ADD, true),
                new RecipeModifier("other:air", IOType.INPUT, 99F, RecipeModifier.Operation.ADD, false));
        assertThat(AirRequirement.TYPE.applyModifiers(input, modifiers)).isEqualTo(AirRequirement.input(20, 4F));
        assertThat(AirRequirement.TYPE.applyModifiers(output, modifiers)).isEqualTo(AirRequirement.output(30));
        assertThat(AirRequirement.TYPE.applyLevelModifiers(input, 2D, 3D)).isEqualTo(AirRequirement.input(20, 4F));
        assertThat(AirRequirement.TYPE.applyLevelModifiers(output, 2D, 3D)).isEqualTo(AirRequirement.output(30));
    }

    @Test
    void ordinaryDivisionAndDecimalLevelBoundariesRemainExecutableInBothDirections() {
        for (IOType direction : IOType.values()) {
            for (long original : new long[]{3, 6}) {
                var requirement = direction == IOType.INPUT ? AirRequirement.input(original, 4F)
                        : AirRequirement.output(original);
                var modifiers = List.of(new RecipeModifier("pneumaticcraft:air", direction, 3F,
                        RecipeModifier.Operation.DIVIDE, false));
                assertThat(RecipeModifier.applyModifiers(modifiers, "pneumaticcraft:air", direction,
                        (double) original, false)).isEqualTo(original / 3D);
                var adjusted = AirRequirement.TYPE.applyModifiers(requirement, modifiers);
                assertThat(adjusted.airPerTick()).isEqualTo(original / 3);
                var port = new Port(direction, 50, 10, List.of());
                assertThat(plan(List.of(adjusted), List.of(port), 1, false).plan().commit()).isTrue();
                assertThat(port.state().air()).isEqualTo(Math.toIntExact(
                        50 + (direction == IOType.INPUT ? -original / 3 : original / 3)));
            }
            var requirement = direction == IOType.INPUT ? AirRequirement.input(10, 4F) : AirRequirement.output(10);
            var adjusted = AirRequirement.TYPE.applyLevelModifiers(requirement,
                    direction == IOType.INPUT ? 0.3D : 99D, direction == IOType.OUTPUT ? 0.3D : 99D);
            assertThat(adjusted.airPerTick()).isEqualTo(3);
            var port = new Port(direction, 50, 10, List.of());
            assertThat(plan(List.of(adjusted), List.of(port), 1, false).plan().commit()).isTrue();
            assertThat(port.state().air()).isEqualTo(direction == IOType.INPUT ? 47 : 53);
        }
    }

    @Test
    void finiteModifiersKeepHelperGroupingWithExactAddMultiplyAndFinalDivision() {
        var modifiers = List.of(rateModifier(2F, RecipeModifier.Operation.MULTIPLY),
                rateModifier(3F, RecipeModifier.Operation.ADD),
                rateModifier(4F, RecipeModifier.Operation.DIVIDE),
                rateModifier(1F, RecipeModifier.Operation.SUBTRACT),
                rateModifier(3F, RecipeModifier.Operation.MULTIPLY));
        assertThat(RecipeModifier.applyModifiers(modifiers, "pneumaticcraft:air", IOType.INPUT, 10D, false))
                .isEqualTo(18D);
        assertThat(AirRequirement.TYPE.applyModifiers(AirRequirement.input(10, 4F), modifiers))
                .isEqualTo(AirRequirement.input(18, 4F));
        var large = AirRequirement.input(9_007_199_254_740_995L, 4F);
        assertThat(AirRequirement.TYPE.applyModifiers(large, List.of(
                rateModifier(3F, RecipeModifier.Operation.DIVIDE),
                rateModifier(0x1.0p53F, RecipeModifier.Operation.SUBTRACT))))
                .isEqualTo(AirRequirement.input(1, 4F));
        assertThat(AirRequirement.TYPE.applyModifiers(large, List.of(
                rateModifier(3F, RecipeModifier.Operation.MULTIPLY),
                rateModifier(3F, RecipeModifier.Operation.DIVIDE)))).isSameAs(large);
        assertThat(AirRequirement.TYPE.applyModifiers(AirRequirement.input(3, 4F), List.of(
                rateModifier(0x1.0p53F, RecipeModifier.Operation.ADD),
                rateModifier(1F, RecipeModifier.Operation.ADD),
                rateModifier(0x1.0p53F, RecipeModifier.Operation.SUBTRACT))))
                .isEqualTo(AirRequirement.input(4, 4F));
        assertThat(AirRequirement.TYPE.applyModifiers(large, List.of(
                rateModifier(Float.MAX_VALUE, RecipeModifier.Operation.MULTIPLY),
                rateModifier(Float.MAX_VALUE, RecipeModifier.Operation.MULTIPLY),
                rateModifier(Float.MAX_VALUE, RecipeModifier.Operation.DIVIDE),
                rateModifier(Float.MAX_VALUE, RecipeModifier.Operation.DIVIDE)))).isSameAs(large);
    }

    @Test
    void filteredAndIdentityModifiersPreserveEveryLongBit() {
        var input = AirRequirement.input(9_007_199_254_740_993L, 4F, List.of("drive"));
        for (RecipeModifier modifier : List.of(
                new RecipeModifier("other:air", IOType.INPUT, 99F, RecipeModifier.Operation.ADD, false),
                new RecipeModifier("pneumaticcraft:air", IOType.OUTPUT, 99F, RecipeModifier.Operation.ADD, false),
                new RecipeModifier("pneumaticcraft:air", IOType.INPUT, 99F, RecipeModifier.Operation.ADD, true),
                rateModifier(1F, RecipeModifier.Operation.MULTIPLY),
                rateModifier(1F, RecipeModifier.Operation.DIVIDE),
                rateModifier(0F, RecipeModifier.Operation.DIVIDE),
                rateModifier(0F, RecipeModifier.Operation.ADD),
                rateModifier(0F, RecipeModifier.Operation.SUBTRACT))) {
            assertThat(AirRequirement.TYPE.applyModifiers(input, List.of(modifier))).isSameAs(input);
        }
        assertThat(AirRequirement.TYPE.applyModifiers(input, List.of(
                rateModifier(8F, RecipeModifier.Operation.ADD),
                rateModifier(8F, RecipeModifier.Operation.SUBTRACT),
                rateModifier(2F, RecipeModifier.Operation.MULTIPLY),
                rateModifier(2F, RecipeModifier.Operation.DIVIDE)))).isSameAs(input);
        assertThat(AirRequirement.TYPE.applyLevelModifiers(input, 1D, 9D)).isSameAs(input);
        var output = AirRequirement.output(input.airPerTick());
        assertThat(AirRequirement.TYPE.applyModifiers(output,
                List.of(rateModifier(99F, RecipeModifier.Operation.ADD)))).isSameAs(output);
        assertThat(AirRequirement.TYPE.applyLevelModifiers(output, 9D, 1D)).isSameAs(output);
    }

    @Test
    void largeCancellationProducesTheExactSmallConsumableRateWithOriginalGrouping() {
        var input = AirRequirement.input(9_007_199_254_740_995L, 4F, List.of("drive"));
        var reduced = AirRequirement.TYPE.applyModifiers(input,
                List.of(rateModifier(0x1.0p53F, RecipeModifier.Operation.SUBTRACT)));
        assertThat(reduced).isEqualTo(AirRequirement.input(3, 4F, List.of("drive")));
        var port = new Port(IOType.INPUT, 50, 10, List.of("drive"));
        assertThat(plan(List.of(reduced), List.of(port), 1, false).plan().commit()).isTrue();
        assertThat(port.state().air()).isEqualTo(47);
        assertThat(AirRequirement.TYPE.applyModifiers(input, List.of(
                rateModifier(0x1.0p53F, RecipeModifier.Operation.SUBTRACT),
                rateModifier(2F, RecipeModifier.Operation.MULTIPLY),
                new RecipeModifier("", IOType.INPUT, 1F, RecipeModifier.Operation.ADD, false))))
                .isEqualTo(AirRequirement.input(8, 4F, List.of("drive")));
        assertThat(AirRequirement.TYPE.applyLevelModifiers(input, 0.5D, 1D))
                .isEqualTo(AirRequirement.input(4_503_599_627_370_497L, 4F, List.of("drive")));
        assertThat(AirRequirement.TYPE.applyLevelModifiers(AirRequirement.output(input.airPerTick()), 1D, 0.5D))
                .isEqualTo(AirRequirement.output(4_503_599_627_370_497L));
    }

    @Test
    void exactRateAdjustmentKeepsFloorSaturationAndNonfiniteConventions() {
        var input = AirRequirement.input(3, 4F);
        assertThat(AirRequirement.TYPE.applyModifiers(input,
                List.of(rateModifier(0.5F, RecipeModifier.Operation.MULTIPLY))))
                .isEqualTo(AirRequirement.input(1, 4F));
        assertThat(AirRequirement.TYPE.applyModifiers(AirRequirement.input(Long.MAX_VALUE, 4F),
                List.of(rateModifier(-1F, RecipeModifier.Operation.ADD))))
                .isEqualTo(AirRequirement.input(Long.MAX_VALUE - 1, 4F));
        assertThat(AirRequirement.TYPE.applyLevelModifiers(AirRequirement.input(Long.MAX_VALUE, 4F), 2D, 1D))
                .isEqualTo(AirRequirement.input(Long.MAX_VALUE, 4F));
        for (float multiplier : new float[]{Float.NaN, Float.NEGATIVE_INFINITY, -1F}) {
            assertThat(AirRequirement.TYPE.applyModifiers(input,
                    List.of(rateModifier(multiplier, RecipeModifier.Operation.MULTIPLY))))
                    .isEqualTo(AirRequirement.input(0, 4F));
        }
        assertThat(AirRequirement.TYPE.applyModifiers(input,
                List.of(rateModifier(Float.POSITIVE_INFINITY, RecipeModifier.Operation.MULTIPLY))))
                .isEqualTo(AirRequirement.input(Long.MAX_VALUE, 4F));
        assertThat(AirRequirement.TYPE.applyLevelModifiers(AirRequirement.input(0, 4F),
                Double.POSITIVE_INFINITY, 1D)).isEqualTo(AirRequirement.input(0, 4F));
        assertThat(AirRequirement.TYPE.applyModifiers(input, List.of(
                rateModifier(Float.POSITIVE_INFINITY, RecipeModifier.Operation.ADD),
                rateModifier(0F, RecipeModifier.Operation.MULTIPLY)))).isEqualTo(AirRequirement.input(0, 4F));
    }

    private static RecipeModifier rateModifier(float value, RecipeModifier.Operation operation) {
        return new RecipeModifier("pneumaticcraft:air", IOType.INPUT, value, operation, false);
    }

    @Test
    void saturatedRateDoesNotOverflowOrCreateChunkLoops() {
        var port = new Port(IOType.OUTPUT, -100, 10, List.of());
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            var result = plan(List.of(AirRequirement.output(Long.MAX_VALUE)), List.of(port), Long.MAX_VALUE, true);
            assertThat(result.plan().parallelism()).isEqualTo(Long.MAX_VALUE);
            assertThat(result.plan().outputSimulations()).singleElement().satisfies(simulation -> {
                assertThat(simulation.requested()).isEqualTo(Long.MAX_VALUE);
                assertThat(simulation.accepted()).isEqualTo(300);
            });
            assertThat(result.plan().commit()).isTrue();
        });
        assertThat(port.state().air()).isEqualTo(200);
    }

    @Test
    void signedReservationsCopyAndShareInsertionAndExtractionBudget() {
        var identity = new Object();
        var reservations = new PlanningReservations();
        assertThat(reservations.reserveValueTotal(identity, 200, -100, 150, true)).isTrue();
        var copy = reservations.copy();
        assertThat(copy.reserveValueTotal(identity, 200, -100, 50, false)).isTrue();
        assertThat(copy.valueAvailable(identity, 200, -100, false)).isZero();
        assertThat(reservations.valueAvailable(identity, 200, -100, false)).isEqualTo(50);
        assertThat(copy.reserveValueTotal(identity, 200, -100, 200, true)).isTrue();
        assertThat(copy.reserveValueTotal(identity, 200, -100, 1, true)).isFalse();
    }

    @Test
    void wakeupsMatchActualAirNotificationReasonsAndResourceIds() {
        var input = AirRequirement.TYPE.handler().resourceWakeups(AirRequirement.input(0, 4F)).getFirst();
        assertThat(input.matches(AirFailureReasons.INSUFFICIENT_PRESSURE)).isTrue();
        assertThat(input.matches(AirFailureReasons.INSUFFICIENT_AIR)).isTrue();
        assertThat(input.reason()).isEqualTo(WakeupReason.INPUT_AVAILABLE);
        assertThat(input.matcher().test(PneumaticIds.AIR)).isTrue();
        assertThat(input.matcher().test(new CapabilityType(PneumaticIds.AIR))).isFalse();
        assertThat(input.matcher().test(ResourceLocation.parse("other:air"))).isFalse();
        assertThat(input.matches(AirFailureReasons.OUTPUT_BLOCKED)).isFalse();
        var output = AirRequirement.TYPE.handler().resourceWakeups(AirRequirement.output(1)).getFirst();
        assertThat(output.matches(AirFailureReasons.OUTPUT_BLOCKED)).isTrue();
        assertThat(output.reason()).isEqualTo(WakeupReason.OUTPUT_CAPACITY);
        assertThat(output.matcher().test(PneumaticIds.AIR)).isTrue();
        assertThat(output.matcher().test(new CapabilityType(PneumaticIds.AIR))).isFalse();
    }

    private static PlanningResult plan(List<MachineRequirement> requirements, List<MachineCapability> ports,
                                       long parallelism, boolean partial) {
        return new RequirementPlanner().plan(requirements, ports,
                new PlanningContext(parallelism, 0, partial, new PlanningReservations()));
    }

    /** Shared mutable physical storage, deliberately without planning behavior.
     * @author howxu <dev@howxu.cn>
     */
    private static final class Store {
        private int air;
        private final int volume;

        private Store(int air, int volume) {
            this.air = air;
            this.volume = volume;
        }
    }

    /** Facet views can share one Store identity; validation observes actual live state only.
     * @author howxu <dev@howxu.cn>
     */
    private static final class Port implements MachineCapability, PneumaticAirFacet, CapabilityView {
        private final IOType direction;
        private final Store store;
        private final List<String> tags;
        private int validations;
        private int applications;
        private int priority;
        private List<String> events = new ArrayList<>();

        private Port(IOType direction, int air, int volume, List<String> tags) {
            this(direction, new Store(air, volume), tags);
        }

        private Port(IOType direction, Store store, List<String> tags) {
            this.direction = direction;
            this.store = store;
            this.tags = List.copyOf(tags);
        }

        @Override
        public CapabilityType type() {
            return new CapabilityType(PneumaticIds.AIR);
        }

        @Override
        public CapabilityDirections directions() {
            return CapabilityDirections.of(cn.howxu.mmcr.util.IOType.valueOf(direction.name()));
        }

        @Override
        @SuppressWarnings("removal")
        public cn.howxu.mmcr.util.IOType ioType() {
            return cn.howxu.mmcr.util.IOType.valueOf(direction.name());
        }

        @Override
        public CapabilityView view() {
            return this;
        }

        @Override
        public Set<Class<? extends CapabilityFacet>> facets() {
            return Set.of(PneumaticAirFacet.class);
        }

        @Override
        public List<String> tags() {
            return tags;
        }

        @Override
        public int outputPriority() {
            return priority;
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            throw new AssertionError("Air planning must use its neutral facet");
        }

        @Override
        public Object queryIdentity() {
            return store;
        }

        @Override
        public AirState state() {
            return new AirState(BlockPos.ZERO, store.air, store.volume, 20F, 25F);
        }

        @Override
        public CapabilityResult validate(long amount, boolean insert, float minPressure) {
            validations++;
            events.add("validate");
            if (insert != (direction == IOType.OUTPUT)) return failure(AirFailureReasons.MISSING_INTERFACE);
            if (amount < 0L || amount > Integer.MAX_VALUE || !Float.isFinite(minPressure) || minPressure < 0F
                    || insert && minPressure != 0F) return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
            if (!insert && state().pressure() < minPressure) return failure(AirFailureReasons.INSUFFICIENT_PRESSURE);
            if (amount > (insert ? state().outputCapacity() : Math.max(0, store.air))) {
                return failure(insert ? AirFailureReasons.OUTPUT_BLOCKED : AirFailureReasons.INSUFFICIENT_AIR);
            }
            long after = store.air + (insert ? amount : -amount);
            if (insert && (after > Integer.MAX_VALUE || amount > 0L && after < -(long) store.volume)) {
                return failure(AirFailureReasons.OUTPUT_BLOCKED);
            }
            return CapabilityResult.successful();
        }

        @Override
        public CapabilityResult apply(long amount, boolean insert, float minPressure) {
            applications++;
            events.add("apply");
            if (amount != 0L) store.air = Math.max(-store.volume,
                    Math.toIntExact(store.air + (insert ? amount : -amount)));
            return CapabilityResult.successful();
        }

        private CapabilityResult failure(FailureReason reason) {
            return CapabilityResult.failure(ExecutionStatus.blocked(PneumaticIds.AIR, PneumaticIds.AIR,
                    FailureOccurrence.at(reason, PneumaticIds.AIR, FailurePhase.CAPABILITY_COMMIT,
                            null, null, Map.of())));
        }
    }
}
