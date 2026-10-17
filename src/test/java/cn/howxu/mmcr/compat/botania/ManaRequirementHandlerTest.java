package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.internal.recipe.InputValidationOperation;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.OutputFit;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.OutputSimulation;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies deferred allocation, physical reservations and full/partial outputs.
 *
 * @author howxu <dev@howxu.cn>
 */
class ManaRequirementHandlerTest {
    private RequirementHandlerRegistry.TestScope requirementScope;
    private OutputRegistry.TestScope outputScope;

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @BeforeEach
    void installHandler() {
        requirementScope = RequirementHandlerRegistry.openTestScope();
        outputScope = OutputRegistry.openTestScope();
        BotaniaRecipeTypes.register();
        ManaRequirement.installHandler(new ManaRequirementHandler());
    }

    @AfterEach
    void resetHandler() {
        ManaRequirement.installUnavailableHandler();
        outputScope.close();
        requirementScope.close();
    }

    @Test
    void zeroConsumptionStillRequiresFullParallelInputAndDoesNotReserveIt() {
        ManaPortCapability input = capability(800L, IOType.INPUT);
        var missing = context(List.of(input)).planRequirements(List.of(ManaRequirement.input(801L, 0F)), 1L, Map.of());
        assertThat(missing.successful()).isFalse();
        var free = context(List.of(input)).planRequirements(
                List.of(ManaRequirement.input(400L, 0F), ManaRequirement.input(400L)), 3L, Map.of());
        assertThat(free.successful()).isTrue();
        assertThat(free.plan().parallelism()).isEqualTo(2L);
        assertThat(free.plan().hasOperations(0)).isFalse();
        assertThat(free.plan().commit()).isTrue();
        assertThat(input.amount()).isZero();
        var depleted = context(List.of(capability(800L, IOType.INPUT))).planRequirements(
                List.of(ManaRequirement.input(500L), ManaRequirement.input(400L, 0F)), 1L, Map.of());
        assertThat(depleted.successful()).isFalse();
        assertThat(depleted.failureRequirementIndex()).isEqualTo(1);
    }

    @Test
    void commitRechecksFullParallelInputAndDeduplicatesLiveAliasesBeforeAnyDebit() {
        ManaStorage firstStorage = storage(600L);
        ManaStorage secondStorage = storage(500L);
        var first = new ManaPortCapability(null, firstStorage, IOType.INPUT);
        var alias = new ManaPortCapability(null, firstStorage, IOType.INPUT);
        var second = new ManaPortCapability(null, secondStorage, IOType.INPUT);
        var result = context(List.of(first, alias, second)).planRequirements(
                List.of(ManaRequirement.input(400L, 0F)), 2L, Map.of());
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().hasOperations(0)).isFalse();
        secondStorage.setAmount(100L);

        assertThat(result.plan().commitInputs()).isFalse();
        assertThat(result.plan().failure().reason()).isEqualTo(ManaFailureReasons.INPUT_MISSING);
        assertThat(result.plan().failure().details()).containsAllEntriesOf(Map.of(
                "required", "800", "available", "700", "shortfall", "100"));
        assertThat(result.plan().failure().failure().trace().frames().getFirst().phase())
                .isEqualTo(FailurePhase.CAPABILITY_COMMIT);
        assertThat(first.amount()).isEqualTo(600L);
        assertThat(second.amount()).isEqualTo(100L);
    }

    @Test
    void partiallyConsumedParallelInputStillRechecksItsFullLiveDemand() {
        ManaStorage storage = storage(3_000L);
        var input = new ManaPortCapability(null, storage, IOType.INPUT);
        List<CapabilityRequests.ValueRequest> prepared = new ArrayList<>();
        // The existing large-profile path deterministically consumes half the batches without random draws.
        var result = context(List.of(observed(input, 0, prepared))).planRequirements(
                List.of(ManaRequirement.input(1L, 0.5F)), 2_048L, Map.of());
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(2_048L);
        assertThat(prepared).singleElement().satisfies(request -> assertThat(request.amount()).isEqualTo(1_024L));
        storage.setAmount(1_500L);

        assertThat(result.plan().commitInputs()).isFalse();
        assertThat(result.plan().failure().details()).containsEntry("required", "2048").containsEntry("available", "1500");
        assertThat(input.amount()).isEqualTo(1_500L);
    }

    @Test
    void readOnlyInputValidationNeverBeginsNativeExecutionOrMarksConsumption() {
        for (boolean stale : new boolean[]{false, true}) {
            ManaStorage storage = storage(800L);
            var input = new ManaPortCapability(null, storage, IOType.INPUT);
            var result = context(List.of(input)).planRequirements(List.of(ManaRequirement.input(400L, 0F)), 3L, Map.of());
            assertThat(result.successful()).isTrue();
            assertThat(result.plan().parallelism()).isEqualTo(2L);
            assertThat(result.plan().hasOperations(0)).isFalse();
            assertThat(result.plan().requirements().getFirst().operations()).singleElement()
                    .satisfies(operation -> assertThat(operation).isInstanceOf(InputValidationOperation.class));
            if (stale) storage.setAmount(799L);
            AtomicInteger beforeNative = new AtomicInteger();

            assertThat(result.plan().commitInputsRuntime(Set.of(), beforeNative::incrementAndGet, () -> true)).isEqualTo(!stale);
            assertThat(beforeNative).hasValue(0);
            assertThat(result.plan().wasNativeExecuted(0)).isFalse();
            assertThat(result.plan().hasOperations(0)).isFalse();
            assertThat(input.amount()).isEqualTo(stale ? 799L : 800L);
        }
    }

    @Test
    void skippedOutputNeedsNeitherPortsNorCapacity() {
        var absent = context(List.of()).planRequirements(List.of(ManaRequirement.output(400L, 0F)), 3L, Map.of());
        assertThat(absent.successful()).isTrue();
        assertThat(absent.plan().parallelism()).isEqualTo(3L);
        assertThat(absent.plan().hasOperations(0)).isFalse();
        assertThat(absent.plan().commit()).isTrue();
        ManaPortCapability full = capability(BotaniaManaIds.CAPACITY, IOType.OUTPUT);
        var skipped = context(List.of(full)).planOutputRequirements(List.of(ManaRequirement.output(400L, 0F)),
                List.of(new ManaOutput(400L, 0F)), 3L, false);
        assertThat(skipped.successful()).isTrue();
        assertThat(skipped.plan().commit()).isTrue();
        assertThat(full.amount()).isEqualTo(BotaniaManaIds.CAPACITY);
    }

    @Test
    void sharedSamplingKeepsReservationMaterializationAndAlternativeScopesIdentical() {
        ManaPortCapability input = capability(800L, IOType.INPUT);
        List<CapabilityRequests.ValueRequest> prepared = new ArrayList<>();
        var context = new PlanningContext(4L, 0, false, new PlanningReservations(), Map.of(), null, null,
                new RequirementHandlerSupport.Sampling());
        var handler = new ManaRequirementHandler();
        var requirement = ManaRequirement.input(100L, 0.5F);
        var plan = handler.plan(requirement, List.of(observed(input, 0, prepared)), context);
        assertThat(plan.successful()).isTrue();
        var reserved = new PlanningReservations();
        assertThat(plan.reserve(3L, reserved)).isNull();
        long expected = reserved.nativeAmount(input.queryIdentity(), 0, input.amount());
        var materialized = new PlanningReservations();
        var operations = plan.materialize(3L, materialized, null);
        assertThat(materialized.nativeAmount(input.queryIdentity(), 0, input.amount())).isEqualTo(expected);
        var alternative = handler.plan(requirement, List.of(input), context.forScope(null, context.sampling()));
        var other = new PlanningReservations();
        assertThat(alternative.reserve(3L, other)).isNull();
        assertThat(other.nativeAmount(input.queryIdentity(), 0, input.amount())).isEqualTo(expected);
        assertThat(prepared.stream().mapToLong(CapabilityRequests.ValueRequest::amount).sum()).isEqualTo(800L - expected);
        assertThat(operations.operations()).allSatisfy(operation -> assertThat(operation.commit().success()).isTrue());
        assertThat(input.amount()).isEqualTo(expected);
    }

    @Test
    void outputSamplingIsSharedAcrossScopesAndDeferredAllocation() {
        var output = capability(0L, IOType.OUTPUT);
        var context = new PlanningContext(4L, 0, false, new PlanningReservations(), Map.of(), null, null,
                new RequirementHandlerSupport.Sampling());
        var handler = new ManaRequirementHandler();
        var requirement = ManaRequirement.output(100L, 0.5F);
        var plan = handler.plan(requirement, List.of(output), context);
        assertThat(plan.successful()).isTrue();
        var reserved = new PlanningReservations();
        assertThat(plan.reserve(3L, reserved)).isNull();
        long expected = reserved.nativeAmount(output.queryIdentity(), 0, output.amount());
        var materialized = new PlanningReservations();
        var operations = plan.materialize(3L, materialized, null);
        assertThat(materialized.nativeAmount(output.queryIdentity(), 0, output.amount())).isEqualTo(expected);
        var alternative = handler.plan(requirement, List.of(output), context.forScope(null, context.sampling()));
        var other = new PlanningReservations();
        assertThat(alternative.reserve(3L, other)).isNull();
        assertThat(other.nativeAmount(output.queryIdentity(), 0, output.amount())).isEqualTo(expected);
        assertThat(operations.operations()).allSatisfy(operation -> assertThat(operation.commit().success()).isTrue());
        assertThat(output.amount()).isEqualTo(expected);
    }

    @Test
    void distributesInputsOnlyAfterFinalParallelismIsKnown() {
        ManaPortCapability first = capability(600L, IOType.INPUT);
        ManaPortCapability second = capability(500L, IOType.INPUT);
        List<CapabilityRequests.ValueRequest> prepared = new ArrayList<>();
        var result = context(List.of(observed(first, 0, prepared), observed(second, 0, prepared)))
                .planRequirements(List.of(ManaRequirement.input(400L)), 3L, Map.of());
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(2L);
        assertThat(first.amount() + second.amount()).isEqualTo(1100L);
        assertThat(prepared).containsExactly(
                new CapabilityRequests.ValueRequest(BotaniaManaIds.TYPE, IOType.INPUT, 2L, 600L, false),
                new CapabilityRequests.ValueRequest(BotaniaManaIds.TYPE, IOType.INPUT, 2L, 200L, false));
        assertThat(result.plan().commit()).isTrue();
        assertThat(first.amount() + second.amount()).isEqualTo(300L);
    }

    @Test
    void aliasesAndConsecutiveRequirementsShareOnePhysicalReservation() {
        ManaStorage storage = storage(600L);
        ManaPortCapability first = new ManaPortCapability(null, storage, IOType.INPUT);
        ManaPortCapability alias = new ManaPortCapability(null, storage, IOType.INPUT);
        CraftingContext context = context(List.of(first, alias));
        var oversized = context.planRequirements(List.of(ManaRequirement.input(800L)), 1L, Map.of());
        assertThat(oversized.successful()).isFalse();
        assertThat(oversized.failure().reason()).isEqualTo(ManaFailureReasons.INPUT_MISSING);
        var consecutive = context.planRequirements(List.of(
                ManaRequirement.input(400L), ManaRequirement.input(300L)), 1L, Map.of());
        assertThat(consecutive.successful()).isFalse();
        assertThat(consecutive.failureRequirementIndex()).isEqualTo(1);
        assertThat(consecutive.failure().details()).containsEntry("available", "200");
        assertThat(storage.amount()).isEqualTo(600);
        var single = context.planRequirements(List.of(ManaRequirement.input(400L)), 2L, Map.of());
        assertThat(single.successful()).isTrue();
        assertThat(single.plan().parallelism()).isEqualTo(1L);
    }

    @Test
    void sharedRequirementsReduceParallelismBeforePreparingOperations() {
        ManaPortCapability input = capability(1100L, IOType.INPUT);
        List<CapabilityRequests.ValueRequest> prepared = new ArrayList<>();
        var result = context(List.of(observed(input, 0, prepared))).planRequirements(
                List.of(ManaRequirement.input(300L), ManaRequirement.input(200L)), 3L, Map.of());
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(2L);
        assertThat(prepared).containsExactly(
                new CapabilityRequests.ValueRequest(BotaniaManaIds.TYPE, IOType.INPUT, 2L, 600L, false),
                new CapabilityRequests.ValueRequest(BotaniaManaIds.TYPE, IOType.INPUT, 2L, 400L, false));
        assertThat(input.amount()).isEqualTo(1100L);
        assertThat(result.plan().commit()).isTrue();
        assertThat(input.amount()).isEqualTo(100L);
    }

    @Test
    void reservationCopyAndVirtualInsertExtractNeverTouchLiveStorage() {
        ManaStorage storage = storage(0L);
        ManaPortCapability output = new ManaPortCapability(null, storage, IOType.OUTPUT);
        ManaPortCapability input = new ManaPortCapability(null, storage, IOType.INPUT);
        ManaRequirementHandler handler = new ManaRequirementHandler();
        PlanningReservations original = new PlanningReservations();
        var insertion = handler.plan(ManaRequirement.output(500L), List.of(output),
                new PlanningContext(1L, 0, false, original));
        assertThat(insertion.reserve(1L, original)).isNull();
        PlanningReservations copy = original.copy();
        var extraction = handler.plan(ManaRequirement.input(300L), List.of(input),
                new PlanningContext(1L, 1, false, copy));
        assertThat(extraction.successful()).isTrue();
        assertThat(extraction.reserve(1L, copy)).isNull();
        assertThat(copy.nativeAmount(storage.identity(), 0, storage.amount())).isEqualTo(200L);
        assertThat(original.nativeAmount(storage.identity(), 0, storage.amount())).isEqualTo(500L);
        assertThat(storage.amount()).isZero();
    }

    @Test
    void fullOutputBlocksAndPartialOutputReportsOnlyRealSpace() {
        ManaPortCapability output = capability(BotaniaManaIds.CAPACITY - 300L, IOType.OUTPUT);
        CraftingContext context = context(List.of(output));
        var full = context.planRequirements(List.of(ManaRequirement.output(400L)), 2L, Map.of());
        assertThat(full.successful()).isFalse();
        assertThat(full.failure().reason()).isEqualTo(ManaFailureReasons.OUTPUT_BLOCKED);
        assertThat(full.outputSimulations()).containsExactly(new OutputSimulation(800L, 0L, OutputFit.NONE));
        var partial = context.planRequirements(List.of(ManaRequirement.output(400L)), 2L,
                Map.of(0, OutputPolicy.ALLOW_PARTIAL));
        assertThat(partial.successful()).isTrue();
        assertThat(partial.plan().parallelism()).isEqualTo(2L);
        assertThat(partial.outputSimulations()).containsExactly(new OutputSimulation(800L, 300L, OutputFit.PARTIAL));
        assertThat(output.amount()).isEqualTo(output.capacity() - 300L);
        assertThat(partial.plan().commit()).isTrue();
        var noSpace = context.planRequirements(List.of(ManaRequirement.output(400L)), 2L,
                Map.of(0, OutputPolicy.ALLOW_PARTIAL));
        assertThat(noSpace.successful()).isTrue();
        assertThat(noSpace.outputSimulations()).containsExactly(new OutputSimulation(800L, 0L, OutputFit.NONE));
        assertThat(noSpace.plan().hasOperations(0)).isFalse();
        assertThat(noSpace.plan().commit()).isTrue();
    }

    @Test
    void outputPriorityAndAliasDeduplicationSelectTheRealFreeSpace() {
        ManaStorage shared = storage(BotaniaManaIds.CAPACITY - 300L);
        ManaPortCapability high = new ManaPortCapability(null, shared, IOType.OUTPUT);
        ManaPortCapability alias = new ManaPortCapability(null, shared, IOType.OUTPUT);
        ManaPortCapability low = capability(BotaniaManaIds.CAPACITY - 500L, IOType.OUTPUT);
        List<CapabilityRequests.ValueRequest> prepared = new ArrayList<>();
        var result = context(List.of(observed(low, 0, prepared), observed(high, 10, prepared),
                observed(alias, 5, prepared))).planRequirements(List.of(ManaRequirement.output(600L)), 2L,
                Map.of(0, OutputPolicy.ALLOW_PARTIAL));
        assertThat(result.successful()).isTrue();
        assertThat(result.outputSimulations()).containsExactly(new OutputSimulation(1200L, 800L, OutputFit.PARTIAL));
        assertThat(prepared).containsExactly(
                new CapabilityRequests.ValueRequest(BotaniaManaIds.TYPE, IOType.OUTPUT, 2L, 300L, true),
                new CapabilityRequests.ValueRequest(BotaniaManaIds.TYPE, IOType.OUTPUT, 2L, 500L, true));
        assertThat(result.plan().commit()).isTrue();
        assertThat(high.amount()).isEqualTo(high.capacity());
        assertThat(low.amount()).isEqualTo(low.capacity());
    }

    @Test
    void completionKeepsCommittedParallelismAndRetriesWhenCapacityIsReleased() {
        ManaStorage storage = storage(BotaniaManaIds.CAPACITY - 100L);
        ManaPortCapability output = new ManaPortCapability(null, storage, IOType.OUTPUT);
        CraftingContext context = context(List.of(output));
        var startup = context.planStartRequirements(List.of(ManaRequirement.output(100L)), 2L, false);
        assertThat(startup.successful()).isTrue();
        assertThat(startup.plan().parallelism()).isEqualTo(1L);
        var completion = context.planOutputRequirements(List.of(ManaRequirement.output(100L)),
                List.of(new ManaOutput(100L)), 2L, false);
        assertThat(completion.successful()).isFalse();
        assertThat(completion.failure().reason()).isEqualTo(ManaFailureReasons.OUTPUT_BLOCKED);
        assertThat(storage.amount()).isEqualTo(storage.capacity() - 100);
        storage.move(100L, false, false);
        var retry = context.planOutputRequirements(List.of(ManaRequirement.output(100L)),
                List.of(new ManaOutput(100L)), 2L, false);
        assertThat(retry.successful()).isTrue();
        assertThat(retry.plan().parallelism()).isEqualTo(2L);
        assertThat(retry.plan().commit()).isTrue();
        assertThat(storage.amount()).isEqualTo(storage.capacity());
    }

    @Test
    void directionFilteringAndWakeupsKeepManaSeparateFromEnergy() {
        ManaPortCapability wrong = capability(800L, IOType.OUTPUT);
        var result = context(List.of(wrong)).planRequirements(List.of(ManaRequirement.input(400L)), 2L, Map.of());
        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isEqualTo(ManaFailureReasons.INPUT_MISSING);
        assertThat(result.failure().details()).containsEntry("available", "0");
        assertThat(wrong.amount()).isEqualTo(800L);
        var input = ManaRequirement.TYPE.handler().resourceWakeups(ManaRequirement.input(100L)).getFirst();
        var output = ManaRequirement.TYPE.handler().resourceWakeups(ManaRequirement.output(100L)).getFirst();
        assertThat(input.matches(ManaFailureReasons.INPUT_MISSING)).isTrue();
        assertThat(input.matches(ManaFailureReasons.OUTPUT_BLOCKED)).isFalse();
        assertThat(input.reason()).isEqualTo(RequirementHandler.WakeupReason.INPUT_AVAILABLE);
        assertThat(output.reason()).isEqualTo(RequirementHandler.WakeupReason.OUTPUT_CAPACITY);
        assertThat(output.matches(ManaFailureReasons.OUTPUT_BLOCKED)).isTrue();
        assertThat(input.matcher().test(BotaniaManaIds.MANA)).isTrue();
        assertThat(input.matcher().test(BuiltinCapabilityDefinitions.ENERGY_TYPE)).isFalse();
    }

    @Test
    void sequentialMultiPoolCommitRetainsEarlierTransfersWhenALaterPoolChanges() {
        ManaStorage firstStorage = storage(300L);
        ManaStorage secondStorage = storage(400L);
        ManaPortCapability first = new ManaPortCapability(null, firstStorage, IOType.INPUT);
        ManaPortCapability second = new ManaPortCapability(null, secondStorage, IOType.INPUT);
        var result = context(List.of(first, second)).planRequirements(
                List.of(ManaRequirement.input(600L)), 1L, Map.of());
        assertThat(result.successful()).isTrue();
        secondStorage.move(200L, false, false);
        assertThat(result.plan().commit()).isFalse();
        assertThat(result.plan().failure().reason()).isEqualTo(ManaFailureReasons.INPUT_MISSING);
        assertThat(firstStorage.amount()).isZero();
        assertThat(secondStorage.amount()).isEqualTo(200);
    }

    private static ManaStorage storage(long amount) {
        ManaStorage storage = new ManaStorage(() -> {});
        storage.setAmount(amount);
        return storage;
    }

    private static ManaPortCapability capability(long amount, IOType io) {
        return new ManaPortCapability(null, storage(amount), io);
    }

    private static CraftingContext context(List<MachineCapability> capabilities) {
        return new CraftingContext(new CapabilitySnapshot(capabilities));
    }

    private static MachineCapability observed(ManaPortCapability capability, int priority,
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
