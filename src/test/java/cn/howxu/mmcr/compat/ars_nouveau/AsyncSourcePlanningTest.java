package cn.howxu.mmcr.compat.ars_nouveau;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.compat.ars_nouveau.loaded.SourcePortCapability;
import cn.howxu.mmcr.compat.ars_nouveau.loaded.SourcePortStorage;
import cn.howxu.mmcr.internal.recipe.AsyncRequirementPlanner;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import com.mojang.authlib.GameProfile;
import net.minecraft.SystemReport;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.debugchart.SampleLogger;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.Proxy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies source snapshot identity, pure dependent planning, and native intent revalidation.
 *
 * @author howxu <dev@howxu.cn>
 */
class AsyncSourcePlanningTest {
    private MinecraftServer previousServer;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void installServerThreadIdentity() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        TestServer server = (TestServer) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(TestServer.class);
        server.serverThread = Thread.currentThread();
        Field currentServer = currentServerField();
        previousServer = (MinecraftServer) currentServer.get(null);
        currentServer.set(null, server);
    }

    @AfterEach
    void restoreServerIdentity() throws Exception {
        currentServerField().set(null, previousServer);
    }

    @Test
    void aliasesCaptureOneSnapshotAndCannotEachConsumeTheWholeStorage() throws Exception {
        SourcePortStorage storage = storage(1_000L);
        SourcePortCapability first = capability(storage, IOType.INPUT);
        SourcePortCapability second = capability(storage, IOType.INPUT);
        assertThat(facet(first).planningIdentity()).isSameAs(storage.identity());
        assertThat(facet(second).planningIdentity()).isSameAs(storage.identity());
        AsyncRequirementPlanner.PreparedPlan prepared;
        try (AsyncPlanningFacet.CaptureScope ignored = AsyncPlanningFacet.beginCaptureScope()) {
            prepared = context(first, second).planAsync(List.of(SourceRequirement.input(600L),
                    SourceRequirement.input(600L)), 1L);
        }

        assertThat(prepared.capabilities().get(0).snapshot()).isSameAs(prepared.capabilities().get(1).snapshot());
        AsyncRequirementPlanner.PlanResult result = CompletableFuture.supplyAsync(prepared::plan).get();

        assertThat(result.operations()).containsExactly(new AsyncRequirementPlanner.PlannedOperation(0, 0,
                new AsyncCapabilityOperation.Scalar(ArsSourceIds.SOURCE, 600L, false)));
        assertThat(result.mainThreadRequirements()).containsExactly(1);
        assertThat(storage.amount()).isEqualTo(1_000L);
    }

    @Test
    void aliasesCannotCombineDuplicateInventoryToSatisfyOneOversizedInput() {
        SourcePortStorage storage = storage(1_000L);
        AsyncRequirementPlanner.PreparedPlan prepared;
        try (AsyncPlanningFacet.CaptureScope ignored = AsyncPlanningFacet.beginCaptureScope()) {
            prepared = context(capability(storage, IOType.INPUT), capability(storage, IOType.INPUT))
                    .planAsync(List.of(SourceRequirement.input(1_200L)), 1L);
        }

        assertThat(prepared.plan().operations()).isEmpty();
        assertThat(prepared.plan().mainThreadRequirements()).containsExactly(0);
        assertThat(storage.amount()).isEqualTo(1_000L);
    }

    @Test
    void separatelyCapturedDependentContextsShareAvailabilityInOneScope() throws Exception {
        SourcePortStorage storage = storage(900L);
        List<AsyncRequirementPlanner.Capability> input;
        List<AsyncRequirementPlanner.Capability> output;
        try (AsyncPlanningFacet.CaptureScope ignored = AsyncPlanningFacet.beginCaptureScope()) {
            input = context(capability(storage, IOType.INPUT)).captureAsyncCapabilities();
            output = context(capability(storage, IOType.OUTPUT)).captureAsyncCapabilities();
            storage.move(100L, false, false);
            assertThat(input.getFirst().snapshot()).isSameAs(output.getFirst().snapshot());
        }
        assertThat(input.getFirst().snapshot()).isNotSameAs(facet(capability(storage, IOType.INPUT)).captureSnapshot());
        List<AsyncRequirementPlanner.Capability> capabilities = List.of(input.getFirst(), output.getFirst());
        AsyncRequirementPlanner.PreparedPlan prepared = CraftingContext.prepareAsyncPlan(
                List.of(SourceRequirement.input(300L), SourceRequirement.output(9_300L)), 1L, capabilities);

        AsyncRequirementPlanner.PlanResult result = CompletableFuture.supplyAsync(prepared::plan).get();

        assertThat(result.mainThreadRequirements()).isEmpty();
        assertThat(result.operations()).containsExactly(
                new AsyncRequirementPlanner.PlannedOperation(0, 0,
                        new AsyncCapabilityOperation.Scalar(ArsSourceIds.SOURCE, 300L, false)),
                new AsyncRequirementPlanner.PlannedOperation(1, 1,
                        new AsyncCapabilityOperation.Scalar(ArsSourceIds.SOURCE, 9_300L, true)));
        assertThat(storage.amount()).isEqualTo(800L);
    }

    @Test
    void outputCapabilityCannotSupplyInputAndInputCapabilityCannotReceiveOutput() {
        SourcePortStorage storage = storage(900L);
        var input = context(capability(storage, IOType.OUTPUT)).planAsync(List.of(SourceRequirement.input(300L)), 1L);
        var output = context(capability(storage, IOType.INPUT)).planAsync(List.of(SourceRequirement.output(100L)), 1L);

        assertThat(input.plan().operations()).isEmpty();
        assertThat(input.plan().mainThreadRequirements()).containsExactly(0);
        assertThat(output.plan().operations()).isEmpty();
        assertThat(output.plan().mainThreadRequirements()).containsExactly(0);
        assertThat(storage.amount()).isEqualTo(900L);
    }

    @Test
    void contextPreparesScaledInputAndOutputSourceRequests() {
        AsyncRequirementPlanner.PreparedPlan prepared = CraftingContext.prepareAsyncPlan(
                List.of(SourceRequirement.input(300L), SourceRequirement.output(100L)), 2L, List.of());

        assertThat(prepared.initialMainThreadRequirements()).isEmpty();
        assertThat(prepared.requirements()).containsExactly(
                new AsyncRequirementPlanner.Requirement(0, 600L, IOType.INPUT,
                        List.of(new AsyncCapabilityRequest.Scalar(ArsSourceIds.SOURCE, 2L, 600L, false))),
                new AsyncRequirementPlanner.Requirement(1, 200L, IOType.OUTPUT,
                        List.of(new AsyncCapabilityRequest.Scalar(ArsSourceIds.SOURCE, 2L, 200L, true))));
    }

    @Test
    void capturedParallelInputAndOutputCommitTheirAllocatedTotalsOnce() throws Exception {
        SourcePortStorage inputStorage = storage(900L);
        SourcePortStorage outputStorage = storage(0L);
        SourcePortCapability input = capability(inputStorage, IOType.INPUT);
        SourcePortCapability output = capability(outputStorage, IOType.OUTPUT);
        var prepared = context(input, output).planAsync(
                List.of(SourceRequirement.input(300L), SourceRequirement.output(100L)), 2L);

        AsyncRequirementPlanner.PlanResult result = CompletableFuture.supplyAsync(prepared::plan).get();

        assertThat(result.mainThreadRequirements()).isEmpty();
        assertThat(result.operations()).containsExactly(
                new AsyncRequirementPlanner.PlannedOperation(0, 0,
                        new AsyncCapabilityOperation.Scalar(ArsSourceIds.SOURCE, 600L, false)),
                new AsyncRequirementPlanner.PlannedOperation(1, 1,
                        new AsyncCapabilityOperation.Scalar(ArsSourceIds.SOURCE, 200L, true)));
        assertThat(inputStorage.amount()).isEqualTo(900L);
        assertThat(outputStorage.amount()).isZero();
        assertThat(facet(input).commit(result.operations().get(0).operation()).success()).isTrue();
        assertThat(inputStorage.amount()).isEqualTo(300L);
        assertThat(outputStorage.amount()).isZero();
        assertThat(facet(output).commit(result.operations().get(1).operation()).success()).isTrue();
        assertThat(outputStorage.amount()).isEqualTo(200L);
    }

    @Test
    void hugeSourceRequestSaturatesAndFallsBackWithoutOverflowOrMutation() {
        SourcePortStorage storage = storage(900L);
        var prepared = context(capability(storage, IOType.INPUT)).planAsync(List.of(SourceRequirement.input(Long.MAX_VALUE)), 2L);

        assertThat(prepared.requirements().getFirst().amount()).isEqualTo(Long.MAX_VALUE);
        assertThat(prepared.plan().operations()).isEmpty();
        assertThat(prepared.plan().mainThreadRequirements()).containsExactly(0);
        assertThat(storage.amount()).isEqualTo(900L);
    }

    @Test
    void inputStockChangeAfterCaptureProducesTypedCommitFailureWithoutConsumption() throws Exception {
        SourcePortStorage storage = storage(900L);
        AsyncPlanningFacet facet = facet(capability(storage, IOType.INPUT));
        AsyncCapabilitySnapshot snapshot = facet.captureSnapshot();
        var planner = facet.workerPlanner();
        storage.move(700L, false, false);

        AsyncCapabilityOperation operation = CompletableFuture.supplyAsync(() -> planner.plan(snapshot,
                new AsyncCapabilityRequest.Scalar(ArsSourceIds.SOURCE, 2L, 600L, false))).get().orElseThrow();
        assertThat(storage.amount()).isEqualTo(200L);
        CapabilityResult result = facet.commit(operation);

        assertThat(result.success()).isFalse();
        assertThat(result.status().reason()).isEqualTo(SourceFailureReasons.INPUT_MISSING);
        assertThat(result.status().details()).containsEntry("required", "600").containsEntry("available", "200");
        assertThat(storage.amount()).isEqualTo(200L);
    }

    @Test
    void outputSpaceChangeAfterCaptureProducesTypedCommitFailureWithoutInsertion() throws Exception {
        SourcePortStorage storage = storage(9_000L);
        AsyncPlanningFacet facet = facet(capability(storage, IOType.OUTPUT));
        AsyncCapabilitySnapshot snapshot = facet.captureSnapshot();
        var planner = facet.workerPlanner();
        storage.move(900L, true, false);

        AsyncCapabilityOperation operation = CompletableFuture.supplyAsync(() -> planner.plan(snapshot,
                new AsyncCapabilityRequest.Scalar(ArsSourceIds.SOURCE, 2L, 600L, true))).get().orElseThrow();
        CapabilityResult result = facet.commit(operation);

        assertThat(result.success()).isFalse();
        assertThat(result.status().reason()).isEqualTo(SourceFailureReasons.OUTPUT_BLOCKED);
        assertThat(result.status().details()).containsEntry("required", "600").containsEntry("available", "100");
        assertThat(storage.amount()).isEqualTo(9_900L);
    }

    @Test
    void validGroupsCommitOneAggregateNativeTransferForBothDirections() {
        AtomicInteger changes = new AtomicInteger();
        SourcePortStorage storage = new SourcePortStorage(changes::incrementAndGet);
        storage.setAmount(900L);
        changes.set(0);

        assertThat(facet(capability(storage, IOType.INPUT)).commit(group(false, 200L, 400L)).success()).isTrue();
        assertThat(storage.amount()).isEqualTo(300L);
        assertThat(changes.get()).isEqualTo(1);
        assertThat(facet(capability(storage, IOType.OUTPUT)).commit(group(true, 50L, 150L)).success()).isTrue();
        assertThat(storage.amount()).isEqualTo(500L);
        assertThat(changes.get()).isEqualTo(2);
    }

    @Test
    void invalidGroupChildrenAreRejectedBeforeAnyNativeTransfer() {
        AtomicInteger changes = new AtomicInteger();
        SourcePortStorage storage = new SourcePortStorage(changes::incrementAndGet);
        storage.setAmount(900L);
        changes.set(0);
        AsyncPlanningFacet facet = facet(capability(storage, IOType.INPUT));
        AsyncCapabilityOperation valid = new AsyncCapabilityOperation.Scalar(ArsSourceIds.SOURCE, 100L, false);
        List<AsyncCapabilityOperation> invalid = List.of(
                new AsyncCapabilityOperation.Group(List.of(valid,
                        new AsyncCapabilityOperation.Scalar(ArsSourceIds.SOURCE, 100L, true))),
                new AsyncCapabilityOperation.Group(List.of(valid,
                        new AsyncCapabilityOperation.Heat(ArsSourceIds.SOURCE, 100D, false, 100L))),
                new AsyncCapabilityOperation.Scalar(MMCR.id("energy"), 100L, false),
                new AsyncCapabilityOperation.Scalar(ArsSourceIds.SOURCE, 100L, true));

        for (AsyncCapabilityOperation operation : invalid) {
            CapabilityResult result = facet.commit(operation);
            assertThat(result.success()).isFalse();
            assertThat(result.status().reason()).isEqualTo(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        assertThat(storage.amount()).isEqualTo(900L);
        assertThat(changes.get()).isZero();
    }

    @Test
    void validButOversizedGroupFailsBeforeConsumingItsFirstChild() {
        SourcePortStorage storage = storage(500L);
        CapabilityResult result = facet(capability(storage, IOType.INPUT)).commit(group(false, 300L, 300L));

        assertThat(result.success()).isFalse();
        assertThat(result.status().reason()).isEqualTo(SourceFailureReasons.INPUT_MISSING);
        assertThat(storage.amount()).isEqualTo(500L);
    }

    @Test
    void overflowingGroupAmountCannotWrapIntoAnAllowedNativeTransfer() {
        SourcePortStorage storage = storage(500L);
        CapabilityResult result = facet(capability(storage, IOType.INPUT)).commit(group(false, Long.MAX_VALUE, 1L));

        assertThat(result.success()).isFalse();
        assertThat(result.status().reason()).isEqualTo(SourceFailureReasons.INPUT_MISSING);
        assertThat(result.status().details()).containsEntry("required", Long.toString(Long.MAX_VALUE));
        assertThat(storage.amount()).isEqualTo(500L);
    }

    @Test
    void workerCannotCaptureOrCommitLiveSourceStorage() throws Exception {
        SourcePortStorage storage = storage(500L);
        AsyncPlanningFacet facet = facet(capability(storage, IOType.INPUT));

        CompletableFuture.runAsync(() -> {
            assertThatThrownBy(facet::captureSnapshot).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> facet.commit(new AsyncCapabilityOperation.Scalar(ArsSourceIds.SOURCE, 100L, false)))
                    .isInstanceOf(IllegalStateException.class);
        }).get();

        assertThat(storage.amount()).isEqualTo(500L);
    }

    private static SourcePortStorage storage(long amount) {
        SourcePortStorage storage = new SourcePortStorage(() -> { });
        storage.setAmount(amount);
        return storage;
    }

    private static SourcePortCapability capability(SourcePortStorage storage, IOType direction) {
        return new SourcePortCapability(null, storage, direction);
    }

    private static AsyncPlanningFacet facet(SourcePortCapability capability) {
        return capability.facet(AsyncPlanningFacet.class).orElseThrow();
    }

    private static CraftingContext context(SourcePortCapability... capabilities) {
        return new CraftingContext(new CapabilitySnapshot(List.of(capabilities)));
    }

    private static AsyncCapabilityOperation.Group group(boolean insert, long first, long second) {
        return new AsyncCapabilityOperation.Group(List.of(
                new AsyncCapabilityOperation.Scalar(ArsSourceIds.SOURCE, first, insert),
                new AsyncCapabilityOperation.Scalar(ArsSourceIds.SOURCE, second, insert)));
    }

    private static Field currentServerField() throws Exception {
        Field field = ServerLifecycleHooks.class.getDeclaredField("currentServer");
        field.setAccessible(true);
        return field;
    }

    /**
     * Minimal server identity, matching the existing async planning test harness.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class TestServer extends MinecraftServer {
        private Thread serverThread;

        private TestServer() {
            super(null, null, null, null, Proxy.NO_PROXY, null, null, null);
        }

        @Override public Thread getRunningThread() { return serverThread; }
        @Override protected boolean initServer() { return false; }
        @Override public int getOperatorUserPermissionLevel() { return 4; }
        @Override public int getFunctionCompilationLevel() { return 4; }
        @Override public boolean shouldRconBroadcast() { return false; }
        @Override public boolean isDedicatedServer() { return false; }
        @Override public int getRateLimitPacketsPerSecond() { return 0; }
        @Override public boolean isEpollEnabled() { return false; }
        @Override public boolean isCommandBlockEnabled() { return false; }
        @Override public boolean isPublished() { return false; }
        @Override public boolean shouldInformAdmins() { return false; }
        @Override public boolean isSingleplayerOwner(GameProfile profile) { return false; }
        @Override protected SampleLogger getTickTimeLogger() { return null; }
        @Override public boolean isTickTimeLoggingEnabled() { return false; }
        @Override public int getMaxPlayers() { return 1; }
        @Override public SystemReport fillServerSystemReport(SystemReport report) { return report; }
    }
}
