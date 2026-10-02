package cn.howxu.mmcr.compat.ars_nouveau;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.CraftingPlan;
import cn.howxu.mmcr.api.capability.plan.OutputFit;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.OutputSimulation;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.compat.ars_nouveau.loaded.SourcePortCapability;
import cn.howxu.mmcr.compat.ars_nouveau.loaded.SourcePortStorage;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies source planning, physical reservations and native ordered commit semantics.
 *
 * @author howxu <dev@howxu.cn>
 */
class SourceRequirementHandlerTest {
    private RequirementHandlerRegistry.TestScope requirementScope;
    private OutputRegistry.TestScope outputScope;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void installSourceHandler() {
        requirementScope = RequirementHandlerRegistry.openTestScope();
        outputScope = OutputRegistry.openTestScope();
        ArsNouveauRecipeTypes.register();
        SourceRequirement.installHandler(new SourceRequirementHandler());
    }

    @AfterEach
    void resetSourceHandler() {
        SourceRequirement.installUnavailableHandler();
        outputScope.close();
        requirementScope.close();
    }

    @Test
    void sourceInputIsAllocatedAcrossPortsOnlyAfterFinalParallelismIsKnown() {
        SourcePortCapability first = capability(600L, IOType.INPUT);
        SourcePortCapability second = capability(500L, IOType.INPUT);
        List<CapabilityRequests.ValueRequest> prepared = new ArrayList<>();
        PlanningResult result = context(List.of(observed(first, 0, prepared), observed(second, 0, prepared)))
                .planRequirements(List.of(SourceRequirement.input(400L)), 3L, Map.of());

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(2L);
        assertThat(first.amount() + second.amount()).isEqualTo(1_100L);
        assertThat(prepared).containsExactly(
                new CapabilityRequests.ValueRequest(ArsSourceIds.TYPE, IOType.INPUT, 2L, 600L, false),
                new CapabilityRequests.ValueRequest(ArsSourceIds.TYPE, IOType.INPUT, 2L, 200L, false));
        assertThat(result.plan().commit()).isTrue();
        assertThat(first.amount() + second.amount()).isEqualTo(300L);
    }

    @Test
    void samePhysicalStorageCannotBeCountedTwice() {
        SourcePortStorage storage = new SourcePortStorage(() -> {});
        storage.setAmount(600L);
        SourcePortCapability first = new SourcePortCapability(null, storage, IOType.INPUT);
        SourcePortCapability alias = new SourcePortCapability(null, storage, IOType.INPUT);
        CraftingContext context = context(List.of(first, alias));

        PlanningResult single = context.planRequirements(List.of(SourceRequirement.input(400L)), 2L, Map.of());
        assertThat(single.successful()).isTrue();
        assertThat(single.plan().parallelism()).isEqualTo(1L);
        PlanningResult combined = context.planRequirements(List.of(
                SourceRequirement.input(400L), SourceRequirement.input(300L)), 1L, Map.of());
        assertThat(combined.successful()).isFalse();
        assertThat(combined.failure().reason()).isEqualTo(SourceFailureReasons.INPUT_MISSING);
        assertThat(combined.failureRequirementIndex()).isEqualTo(1);
        assertThat(combined.failure().details()).containsAllEntriesOf(Map.of(
                "required", "300", "available", "200", "shortfall", "100"));
        assertThat(storage.amount()).isEqualTo(600);
    }

    @Test
    void sharedRequirementsReduceParallelismBeforeAnyOperationIsPrepared() {
        SourcePortCapability input = capability(1_100L, IOType.INPUT);
        List<CapabilityRequests.ValueRequest> prepared = new ArrayList<>();
        PlanningResult result = context(List.of(observed(input, 0, prepared))).planRequirements(
                List.of(SourceRequirement.input(300L), SourceRequirement.input(200L)), 3L, Map.of());

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(2L);
        assertThat(prepared).containsExactly(
                new CapabilityRequests.ValueRequest(ArsSourceIds.TYPE, IOType.INPUT, 2L, 600L, false),
                new CapabilityRequests.ValueRequest(ArsSourceIds.TYPE, IOType.INPUT, 2L, 400L, false));
        assertThat(input.amount()).isEqualTo(1_100L);
        assertThat(result.plan().commit()).isTrue();
        assertThat(input.amount()).isEqualTo(100L);
    }

    @Test
    void reservationCopyDoesNotConsumeOriginalOrPrepareOperations() {
        SourcePortCapability input = capability(800L, IOType.INPUT);
        List<CapabilityRequests.ValueRequest> prepared = new ArrayList<>();
        PlanningReservations original = new PlanningReservations();
        assertThat(original.reserveNativeExtract(input.queryIdentity(), 0, ArsSourceIds.SOURCE,
                ArsSourceIds.SOURCE, input.amount(), 100L)).isTrue();
        RequirementPlan plan = new SourceRequirementHandler().plan(SourceRequirement.input(200L),
                List.of(observed(input, 0, prepared)), new PlanningContext(3L, 0, false, original));
        PlanningReservations copy = original.copy();

        assertThat(plan.operations()).isEmpty();
        assertThat(plan.reserve(2L, copy)).isNull();
        assertThat(copy.nativeAmount(input.queryIdentity(), 0, input.amount())).isEqualTo(300L);
        assertThat(original.nativeAmount(input.queryIdentity(), 0, input.amount())).isEqualTo(700L);
        assertThat(input.amount()).isEqualTo(800L);
        assertThat(prepared).isEmpty();
    }

    @Test
    void fullOutputWaitsAndPartialOutputReportsAcceptedAmount() {
        SourcePortCapability output = capability(9_700L, IOType.OUTPUT);
        CraftingContext context = context(List.of(output));
        PlanningResult full = context.planRequirements(List.of(SourceRequirement.output(400L)), 2L, Map.of());

        assertThat(full.successful()).isFalse();
        assertThat(full.failure().reason()).isEqualTo(SourceFailureReasons.OUTPUT_BLOCKED);
        assertThat(full.failure().failure().trace().frames().getFirst().phase()).isEqualTo(FailurePhase.REQUIREMENT_PLAN);
        assertThat(full.failure().details()).containsAllEntriesOf(Map.of(
                "required", "800", "available", "300", "shortfall", "500"));
        assertThat(full.outputSimulations()).containsExactly(new OutputSimulation(800L, 0L, OutputFit.NONE));
        PlanningResult partial = context.planRequirements(List.of(SourceRequirement.output(400L)), 2L,
                Map.of(0, OutputPolicy.ALLOW_PARTIAL));
        assertThat(partial.successful()).isTrue();
        assertThat(partial.plan().parallelism()).isEqualTo(2L);
        assertThat(partial.outputSimulations()).containsExactly(new OutputSimulation(800L, 300L, OutputFit.PARTIAL));
        assertThat(output.amount()).isEqualTo(9_700L);
        assertThat(partial.plan().commit()).isTrue();
        assertThat(output.amount()).isEqualTo(output.capacity());
    }

    @Test
    void completionRequiresTheCommittedParallelismWhileStartupCanStillLowerIt() {
        SourcePortStorage storage = new SourcePortStorage(() -> {});
        storage.setAmount(9_900L);
        SourcePortCapability output = new SourcePortCapability(null, storage, IOType.OUTPUT);
        CraftingContext context = context(List.of(output));
        List<MachineRequirement> requirements =
                List.of(SourceRequirement.input(300L), SourceRequirement.output(100L));
        PlanningResult candidate = context.planStartRequirements(List.of(SourceRequirement.output(100L)), 2L, false);
        assertThat(candidate.successful()).isTrue();
        assertThat(candidate.plan().parallelism()).isEqualTo(1L);

        PlanningResult finish = context.planOutputRequirements(requirements, List.of(new SourceOutput(100L)), 2L, false);
        assertThat(finish.successful()).isFalse();
        assertThat(finish.plan()).isNull();
        assertThat(finish.failure().reason()).isEqualTo(SourceFailureReasons.OUTPUT_BLOCKED);
        assertThat(finish.failureRequirementIndex()).isEqualTo(1);
        assertThat(finish.failure().details()).containsAllEntriesOf(Map.of(
                "required", "200", "available", "100", "shortfall", "100"));
        assertThat(output.amount()).isEqualTo(9_900L);
        assertThat(context.planOutputRequirements(requirements, 2L, false).successful()).isFalse();

        storage.move(100L, false, false);
        PlanningResult retry = context.planOutputRequirements(requirements, List.of(new SourceOutput(100L)), 2L, false);
        assertThat(retry.successful()).isTrue();
        assertThat(retry.plan().parallelism()).isEqualTo(2L);
        assertThat(retry.plan().commit()).isTrue();
        assertThat(output.amount()).isEqualTo(output.capacity());
    }

    @Test
    void completionReservesAllOutputsAtExactParallelismBeforeAnyCommit() {
        SourcePortCapability output = capability(9_700L, IOType.OUTPUT);
        List<CapabilityRequests.ValueRequest> prepared = new ArrayList<>();
        PlanningResult result = context(List.of(observed(output, 0, prepared))).planOutputRequirements(
                List.of(SourceRequirement.output(100L), SourceRequirement.output(100L)), 2L, false);

        assertThat(result.successful()).isFalse();
        assertThat(result.plan()).isNull();
        assertThat(result.failureRequirementIndex()).isEqualTo(1);
        assertThat(result.failure().reason()).isEqualTo(SourceFailureReasons.OUTPUT_BLOCKED);
        assertThat(result.failure().details()).containsEntry("required", "200").containsEntry("available", "100");
        assertThat(output.amount()).isEqualTo(9_700L);
        assertThat(prepared).isEmpty();
    }

    @Test
    void completionPartialOutputKeepsExactParallelismAndAcceptsOnlyRealSpace() {
        SourcePortCapability output = capability(9_900L, IOType.OUTPUT);
        CraftingContext context = context(List.of(output));
        PlanningResult partial = context.planOutputRequirements(List.of(SourceRequirement.output(100L)),
                List.of(new SourceOutput(100L)), 2L, true);

        assertThat(partial.successful()).isTrue();
        assertThat(partial.plan().parallelism()).isEqualTo(2L);
        assertThat(partial.outputSimulations()).containsExactly(new OutputSimulation(200L, 100L, OutputFit.PARTIAL));
        assertThat(partial.plan().commit()).isTrue();
        assertThat(output.amount()).isEqualTo(output.capacity());
        PlanningResult fullStore = context.planOutputRequirements(List.of(SourceRequirement.output(100L)), 2L, true);
        assertThat(fullStore.successful()).isTrue();
        assertThat(fullStore.plan().parallelism()).isEqualTo(2L);
        assertThat(fullStore.outputSimulations()).containsExactly(new OutputSimulation(200L, 0L, OutputFit.NONE));
        assertThat(fullStore.plan().commit()).isTrue();
        assertThat(output.amount()).isEqualTo(output.capacity());
    }

    @Test
    void zeroAcceptedPartialOutputKeepsRequestedParallelismAndCommitsNoOperations() {
        SourcePortCapability output = capability(10_000L, IOType.OUTPUT);
        PlanningResult result = context(List.of(output)).planRequirements(
                List.of(SourceRequirement.output(400L)), 3L, Map.of(0, OutputPolicy.ALLOW_PARTIAL));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(3L);
        assertThat(result.outputSimulations()).containsExactly(new OutputSimulation(1_200L, 0L, OutputFit.NONE));
        assertThat(result.plan().hasOperations(0)).isFalse();
        assertThat(result.plan().commit()).isTrue();
        assertThat(output.amount()).isEqualTo(output.capacity());
    }

    @Test
    void outputAllocationHonorsPriorityAndDeduplicatesPhysicalCapacity() {
        SourcePortStorage storage = new SourcePortStorage(() -> {});
        storage.setAmount(9_700L);
        SourcePortCapability low = capability(9_500L, IOType.OUTPUT);
        SourcePortCapability high = new SourcePortCapability(null, storage, IOType.OUTPUT);
        SourcePortCapability alias = new SourcePortCapability(null, storage, IOType.OUTPUT);
        List<CapabilityRequests.ValueRequest> prepared = new ArrayList<>();
        PlanningResult result = context(List.of(observed(low, 0, prepared), observed(high, 10, prepared),
                observed(alias, 5, prepared))).planRequirements(List.of(SourceRequirement.output(600L)), 2L,
                Map.of(0, OutputPolicy.ALLOW_PARTIAL));

        assertThat(result.successful()).isTrue();
        assertThat(result.outputSimulations()).containsExactly(new OutputSimulation(1_200L, 800L, OutputFit.PARTIAL));
        assertThat(prepared).containsExactly(
                new CapabilityRequests.ValueRequest(ArsSourceIds.TYPE, IOType.OUTPUT, 2L, 300L, true),
                new CapabilityRequests.ValueRequest(ArsSourceIds.TYPE, IOType.OUTPUT, 2L, 500L, true));
        assertThat(result.plan().commit()).isTrue();
        assertThat(high.amount()).isEqualTo(high.capacity());
        assertThat(low.amount()).isEqualTo(low.capacity());
    }

    @Test
    void completeOutputsReduceParallelismAndShareReservationsAcrossRequirements() {
        SourcePortCapability first = capability(9_400L, IOType.OUTPUT);
        SourcePortCapability second = capability(9_500L, IOType.OUTPUT);
        PlanningResult result = context(List.of(first, second)).planRequirements(
                List.of(SourceRequirement.output(300L), SourceRequirement.output(200L)), 3L, Map.of());

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(2L);
        assertThat(result.outputSimulations()).containsExactly(
                new OutputSimulation(600L, 600L, OutputFit.FULL), new OutputSimulation(400L, 400L, OutputFit.FULL));
        assertThat(first.amount() + second.amount()).isEqualTo(18_900L);
        assertThat(result.plan().commit()).isTrue();
        assertThat(first.amount() + second.amount()).isEqualTo(19_900L);
    }

    @Test
    void reservationsCanFillAnEmptyStoreAndExtractItsVirtualSource() {
        SourcePortStorage storage = new SourcePortStorage(() -> {});
        SourcePortCapability output = new SourcePortCapability(null, storage, IOType.OUTPUT);
        SourcePortCapability input = new SourcePortCapability(null, storage, IOType.INPUT);
        SourceRequirementHandler handler = new SourceRequirementHandler();
        PlanningReservations reservations = new PlanningReservations();
        RequirementPlan insertion = handler.plan(SourceRequirement.output(500L), List.of(output),
                new PlanningContext(1L, 0, false, reservations));

        assertThat(insertion.reserve(1L, reservations)).isNull();
        RequirementPlan extraction = handler.plan(SourceRequirement.input(300L), List.of(input),
                new PlanningContext(1L, 1, false, reservations));
        assertThat(extraction.successful()).isTrue();
        assertThat(extraction.reserve(1L, reservations)).isNull();
        assertThat(reservations.nativeAmount(storage.identity(), 0, storage.amount())).isEqualTo(200L);
        assertThat(storage.amount()).isZero();
    }

    @Test
    void directionFilteringAndMissingInputKeepSourceSpecificFailureDetails() {
        SourcePortCapability wrongDirection = capability(800L, IOType.OUTPUT);
        PlanningResult result = context(List.of(wrongDirection)).planRequirements(
                List.of(SourceRequirement.input(400L)), 2L, Map.of());

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isEqualTo(SourceFailureReasons.INPUT_MISSING);
        assertThat(result.failure().details()).containsAllEntriesOf(Map.of(
                "required", "800", "available", "0", "shortfall", "800"));
        assertThat(wrongDirection.amount()).isEqualTo(800L);
    }

    @Test
    void changedStorageReportsTypedCommitFailureAfterSuccessfulPlanning() {
        SourcePortStorage storage = new SourcePortStorage(() -> {});
        storage.setAmount(700L);
        SourcePortCapability input = new SourcePortCapability(null, storage, IOType.INPUT);
        PlanningResult result = context(List.of(input)).planRequirements(
                List.of(SourceRequirement.input(600L)), 1L, Map.of());
        assertThat(result.successful()).isTrue();
        storage.move(300L, false, false);

        assertThat(result.plan().commit()).isFalse();
        assertThat(result.plan().failure().reason()).isEqualTo(SourceFailureReasons.INPUT_MISSING);
        assertThat(result.plan().failure().failure().trace().frames().getFirst().phase()).isEqualTo(FailurePhase.CAPABILITY_COMMIT);
        assertThat(result.plan().failure().details()).containsAllEntriesOf(Map.of(
                "required", "600", "available", "400", "shortfall", "200"));
        assertThat(input.amount()).isEqualTo(400L);
    }

    @Test
    void nativeSequentialCommitRetainsSourceMovedBeforeFailureAndSkipsLaterOperations() {
        SourcePortCapability input = capability(700L, IOType.INPUT);
        CapabilityOperation first = input.prepare(new CapabilityRequests.ValueRequest(
                ArsSourceIds.TYPE, IOType.INPUT, 1L, 300L, false));
        ExecutionStatus failure = ExecutionStatus.blocked(ArsSourceIds.SOURCE, ArsSourceIds.SOURCE,
                FailureOccurrence.at(BuiltinFailureReasons.UNKNOWN, ArsSourceIds.SOURCE,
                        FailurePhase.CAPABILITY_COMMIT, null, null, Map.of()));
        CapabilityOperation failing = () -> CapabilityResult.failure(failure);
        AtomicInteger later = new AtomicInteger();
        CapabilityOperation notReached = () -> {
            later.incrementAndGet();
            return CapabilityResult.successful();
        };
        CraftingPlan plan = new CraftingPlan(List.of(new RequirementPlan(0, 1L,
                List.of(first, failing, notReached), null)), 1L);

        assertThat(plan.commit()).isFalse();
        assertThat(plan.failure()).isSameAs(failure);
        assertThat(input.amount()).isEqualTo(400L);
        assertThat(later).hasValue(0);
    }

    @Test
    void canonicalHandlerUsesSourceSpecificWakeupsWithoutEnergyOverlap() {
        RequirementHandler<SourceRequirement> handler = SourceRequirement.TYPE.handler();
        RequirementHandler.ResourceWakeup input = handler.resourceWakeups(SourceRequirement.input(100L)).getFirst();
        RequirementHandler.ResourceWakeup output = handler.resourceWakeups(SourceRequirement.output(100L)).getFirst();

        assertThat(input.matches(SourceFailureReasons.INPUT_MISSING)).isTrue();
        assertThat(input.matches(SourceFailureReasons.OUTPUT_BLOCKED)).isFalse();
        assertThat(input.reason()).isEqualTo(RequirementHandler.WakeupReason.INPUT_AVAILABLE);
        assertThat(output.matches(SourceFailureReasons.OUTPUT_BLOCKED)).isTrue();
        assertThat(output.reason()).isEqualTo(RequirementHandler.WakeupReason.OUTPUT_CAPACITY);
        assertThat(input.matcher().test(ArsSourceIds.SOURCE)).isTrue();
        assertThat(output.matcher().test(ArsSourceIds.SOURCE)).isTrue();
        assertThat(input.matcher().test(BuiltinCapabilityDefinitions.ENERGY_TYPE)).isFalse();
    }

    private static SourcePortCapability capability(long amount, IOType io) {
        SourcePortStorage storage = new SourcePortStorage(() -> {});
        storage.setAmount(amount);
        return new SourcePortCapability(null, storage, io);
    }

    private static CraftingContext context(List<MachineCapability> capabilities) {
        return new CraftingContext(new CapabilitySnapshot(capabilities), List.of());
    }

    private static MachineCapability observed(SourcePortCapability capability, int priority,
                                              List<CapabilityRequests.ValueRequest> prepared) {
        return new MachineCapability() {
            @Override public CapabilityType type() { return capability.type(); }
            @Override public CapabilityView view() { return capability.view(); }
            @Override public CapabilityDirections directions() { return capability.directions(); }
            @Override public int outputPriority() { return priority; }
            @Override public <F extends CapabilityFacet> Optional<F> facet(Class<F> facetType) {
                return capability.facet(facetType);
            }
            @Override public CapabilityOperation prepare(CapabilityRequest request) {
                prepared.add((CapabilityRequests.ValueRequest) request);
                return capability.prepare(request);
            }
        };
    }
}
