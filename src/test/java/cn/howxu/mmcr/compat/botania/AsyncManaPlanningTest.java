package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.internal.recipe.AsyncRequirementPlanner;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import com.mojang.authlib.GameProfile;
import net.minecraft.SystemReport;
import net.minecraft.resources.ResourceLocation;
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
 * Verifies immutable worker planning and atomic revalidation against real mana.
 *
 * @author howxu <dev@howxu.cn>
 */
class AsyncManaPlanningTest {
    private MinecraftServer previousServer;

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

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
    void aliasesWithoutACaptureScopeShareDependentRequirementAvailability() throws Exception {
        ManaStorage storage = storage(1000L);
        ManaPortCapability first = capability(storage, IOType.INPUT);
        ManaPortCapability alias = capability(storage, IOType.INPUT);
        assertThat(facet(first).planningIdentity()).isSameAs(storage.identity());
        var prepared = context(first, alias).planAsync(List.of(ManaRequirement.input(600L),
                ManaRequirement.input(600L)), 1L);
        assertThat(prepared.capabilities().get(0).snapshot()).isSameAs(prepared.capabilities().get(1).snapshot());
        var result = CompletableFuture.supplyAsync(prepared::plan).get();
        assertThat(result.operations()).containsExactly(new AsyncRequirementPlanner.PlannedOperation(0, 0,
                new AsyncCapabilityOperation.Scalar(BotaniaManaIds.MANA, 600L, false)));
        assertThat(result.mainThreadRequirements()).containsExactly(1);
        assertThat(storage.amount()).isEqualTo(1000);
    }

    @Test
    void aliasesWithoutACaptureScopeCannotDoubleInventoryAndWrongDirectionsFallBack() throws Exception {
        ManaStorage storage = storage(1000L);
        var prepared = context(capability(storage, IOType.INPUT), capability(storage, IOType.INPUT))
                .planAsync(List.of(ManaRequirement.input(1200L)), 1L);
        var result = CompletableFuture.supplyAsync(prepared::plan).get();
        assertThat(result.operations()).isEmpty();
        assertThat(result.mainThreadRequirements()).containsExactly(0);
        var wrongInput = context(capability(storage, IOType.OUTPUT)).planAsync(List.of(ManaRequirement.input(300L)), 1L);
        var wrongOutput = context(capability(storage, IOType.INPUT)).planAsync(List.of(ManaRequirement.output(300L)), 1L);
        assertThat(wrongInput.plan().operations()).isEmpty();
        assertThat(wrongOutput.plan().operations()).isEmpty();
        assertThat(storage.amount()).isEqualTo(1000);
    }

    @Test
    void consecutiveRequirementsCommitOnlyOnePhysicalBudgetWithoutACaptureScope() throws Exception {
        ManaStorage storage = storage(1000L);
        ManaPortCapability first = capability(storage, IOType.INPUT);
        ManaPortCapability alias = capability(storage, IOType.INPUT);
        var prepared = context(first, alias).planAsync(List.of(ManaRequirement.input(600L),
                ManaRequirement.input(400L)), 1L);
        var result = CompletableFuture.supplyAsync(prepared::plan).get();
        assertThat(result.mainThreadRequirements()).isEmpty();
        assertThat(result.operations()).containsExactly(
                new AsyncRequirementPlanner.PlannedOperation(0, 0,
                        new AsyncCapabilityOperation.Scalar(BotaniaManaIds.MANA, 600L, false)),
                new AsyncRequirementPlanner.PlannedOperation(1, 0,
                        new AsyncCapabilityOperation.Scalar(BotaniaManaIds.MANA, 400L, false)));
        assertThat(storage.amount()).isEqualTo(1000);
        for (var operation : result.operations()) {
            assertThat(facet(first).commit(operation.operation()).success()).isTrue();
        }
        assertThat(storage.amount()).isZero();
    }

    @Test
    void taggedInputsAndOutputsFallBackWithOriginalIndexesWhileUntaggedManaStillPlans() throws Exception {
        ManaStorage input = storage(900L);
        ManaStorage output = storage(0L);
        var prepared = context(capability(input, IOType.INPUT), capability(output, IOType.OUTPUT))
                .planAsync(List.of(
                        new ManaRequirement(RecipeModifier.IOType.INPUT, 300L, List.of("selected")),
                        ManaRequirement.input(200L),
                        new ManaRequirement(RecipeModifier.IOType.OUTPUT, 100L, List.of("selected"))),
                        1L, List.of(4, 7, 9));
        var result = CompletableFuture.supplyAsync(prepared::plan).get();
        assertThat(result.mainThreadRequirements()).containsExactly(4, 9);
        assertThat(result.operations()).containsExactly(new AsyncRequirementPlanner.PlannedOperation(7, 0,
                new AsyncCapabilityOperation.Scalar(BotaniaManaIds.MANA, 200L, false)));
        assertThat(input.amount()).isEqualTo(900);
        assertThat(output.amount()).isZero();
    }

    @Test
    void aliasOperationsKeepTheirOwnDirectionAndCommitRouteWithoutACaptureScope() throws Exception {
        ManaStorage storage = storage(900L);
        ManaPortCapability input = capability(storage, IOType.INPUT);
        ManaPortCapability output = capability(storage, IOType.OUTPUT);
        var prepared = context(input, output).planAsync(List.of(ManaRequirement.input(300L),
                ManaRequirement.output(BotaniaManaIds.CAPACITY - 600L)), 1L);
        var result = CompletableFuture.supplyAsync(prepared::plan).get();
        assertThat(result.mainThreadRequirements()).isEmpty();
        assertThat(result.operations()).containsExactly(
                new AsyncRequirementPlanner.PlannedOperation(0, 0,
                        new AsyncCapabilityOperation.Scalar(BotaniaManaIds.MANA, 300L, false)),
                new AsyncRequirementPlanner.PlannedOperation(1, 1,
                        new AsyncCapabilityOperation.Scalar(BotaniaManaIds.MANA,
                                BotaniaManaIds.CAPACITY - 600L, true)));
        assertThat(storage.amount()).isEqualTo(900);
        List<ManaPortCapability> routes = List.of(input, output);
        for (var operation : result.operations()) {
            assertThat(facet(routes.get(operation.capabilityIndex())).commit(operation.operation()).success()).isTrue();
        }
        assertThat(storage.amount()).isEqualTo(BotaniaManaIds.CAPACITY);
    }

    @Test
    void capturesOutsideAnOuterScopeRefreshThePhysicalBudget() {
        ManaStorage storage = storage(900L);
        CraftingContext context = context(capability(storage, IOType.INPUT), capability(storage, IOType.INPUT));
        var before = context.planAsync(List.of(ManaRequirement.input(600L)), 1L);
        storage.move(700L, false, false);
        var after = context.planAsync(List.of(ManaRequirement.input(600L)), 1L);
        assertThat(before.plan().mainThreadRequirements()).isEmpty();
        assertThat(after.plan().operations()).isEmpty();
        assertThat(after.plan().mainThreadRequirements()).containsExactly(0);
        assertThat(storage.amount()).isEqualTo(200);
    }

    @Test
    void scaledDescriptorsAndCapturedTransfersCommitTotalsExactlyOnce() throws Exception {
        ManaStorage inputStorage = storage(900L);
        ManaStorage outputStorage = storage(0L);
        ManaPortCapability input = capability(inputStorage, IOType.INPUT);
        ManaPortCapability output = capability(outputStorage, IOType.OUTPUT);
        var prepared = context(input, output).planAsync(List.of(ManaRequirement.input(300L),
                ManaRequirement.output(100L)), 2L);
        assertThat(prepared.initialMainThreadRequirements()).isEmpty();
        assertThat(prepared.requirements()).containsExactly(
                new AsyncRequirementPlanner.Requirement(0, 600L, IOType.INPUT,
                        List.of(new AsyncCapabilityRequest.Scalar(BotaniaManaIds.MANA, 2L, 600L, false))),
                new AsyncRequirementPlanner.Requirement(1, 200L, IOType.OUTPUT,
                        List.of(new AsyncCapabilityRequest.Scalar(BotaniaManaIds.MANA, 2L, 200L, true))));
        var result = CompletableFuture.supplyAsync(prepared::plan).get();
        assertThat(result.mainThreadRequirements()).isEmpty();
        assertThat(inputStorage.amount()).isEqualTo(900);
        assertThat(outputStorage.amount()).isZero();
        assertThat(facet(input).commit(result.operations().get(0).operation()).success()).isTrue();
        assertThat(inputStorage.amount()).isEqualTo(300);
        assertThat(facet(output).commit(result.operations().get(1).operation()).success()).isTrue();
        assertThat(outputStorage.amount()).isEqualTo(200);
    }

    @Test
    void separateContextsInOneCaptureScopeSharePhysicalSnapshot() throws Exception {
        ManaStorage storage = storage(900L);
        List<AsyncRequirementPlanner.Capability> input;
        List<AsyncRequirementPlanner.Capability> output;
        try (AsyncPlanningFacet.CaptureScope ignored = AsyncPlanningFacet.beginCaptureScope()) {
            input = context(capability(storage, IOType.INPUT)).captureAsyncCapabilities();
            storage.move(100L, false, false);
            output = context(capability(storage, IOType.OUTPUT)).captureAsyncCapabilities();
            assertThat(input.getFirst().snapshot()).isSameAs(output.getFirst().snapshot());
        }
        var prepared = CraftingContext.prepareAsyncPlan(List.of(ManaRequirement.input(300L),
                        ManaRequirement.output(BotaniaManaIds.CAPACITY - 600L)), 1L,
                List.of(input.getFirst(), output.getFirst()));
        var result = CompletableFuture.supplyAsync(prepared::plan).get();
        assertThat(result.mainThreadRequirements()).isEmpty();
        assertThat(result.operations()).hasSize(2);
        assertThat(storage.amount()).isEqualTo(800);
    }

    @Test
    void hugeRequirementSaturatesWithoutOverflowOrMutation() {
        ManaStorage storage = storage(900L);
        var prepared = context(capability(storage, IOType.INPUT))
                .planAsync(List.of(ManaRequirement.input(Long.MAX_VALUE)), 2L);
        assertThat(prepared.requirements().getFirst().amount()).isEqualTo(Long.MAX_VALUE);
        assertThat(prepared.plan().operations()).isEmpty();
        assertThat(prepared.plan().mainThreadRequirements()).containsExactly(0);
        assertThat(storage.amount()).isEqualTo(900);
    }

    @Test
    void externalInputAndOutputChangesRejectStaleIntentsWithoutPartialMovement() throws Exception {
        for (IOType direction : IOType.values()) {
            boolean insert = direction == IOType.OUTPUT;
            ManaStorage storage = storage(insert ? BotaniaManaIds.CAPACITY - 900L : 900L);
            AsyncPlanningFacet facet = facet(capability(storage, direction));
            var snapshot = facet.captureSnapshot();
            var planner = facet.workerPlanner();
            storage.move(700L, insert, false);
            var operation = CompletableFuture.supplyAsync(() -> planner.plan(snapshot,
                    new AsyncCapabilityRequest.Scalar(BotaniaManaIds.MANA, 2L, 600L, insert))).get().orElseThrow();
            int before = storage.amount();
            var result = facet.commit(operation);
            assertThat(result.success()).isFalse();
            assertThat(result.status().reason()).isEqualTo(insert
                    ? ManaFailureReasons.OUTPUT_BLOCKED : ManaFailureReasons.INPUT_MISSING);
            assertThat(result.status().details()).containsEntry("required", "600").containsEntry("available", "200");
            assertThat(storage.amount()).isEqualTo(before);
        }
    }

    @Test
    void validGroupsCommitOneAggregateTransferInEachDirection() {
        AtomicInteger changes = new AtomicInteger();
        ManaStorage storage = new ManaStorage(changes::incrementAndGet);
        storage.setAmount(900L);
        changes.set(0);
        assertThat(facet(capability(storage, IOType.INPUT)).commit(group(false, 200L, 400L)).success()).isTrue();
        assertThat(storage.amount()).isEqualTo(300);
        assertThat(changes).hasValue(1);
        assertThat(facet(capability(storage, IOType.OUTPUT)).commit(group(true, 50L, 150L)).success()).isTrue();
        assertThat(storage.amount()).isEqualTo(500);
        assertThat(changes).hasValue(2);
    }

    @Test
    void invalidChildAndOversizedOrOverflowingGroupsNeverPartiallyCommit() {
        ManaStorage storage = storage(500L);
        AsyncPlanningFacet facet = facet(capability(storage, IOType.INPUT));
        var valid = new AsyncCapabilityOperation.Scalar(BotaniaManaIds.MANA, 100L, false);
        for (var invalid : List.of(
                new AsyncCapabilityOperation.Group(List.of(valid,
                        new AsyncCapabilityOperation.Scalar(BotaniaManaIds.MANA, 100L, true))),
                new AsyncCapabilityOperation.Group(List.of(valid,
                        new AsyncCapabilityOperation.Heat(BotaniaManaIds.MANA, 100D, false, 100L))),
                new AsyncCapabilityOperation.Scalar(ResourceLocation.fromNamespaceAndPath("mmcr", "energy"), 100L, false))) {
            var result = facet.commit(invalid);
            assertThat(result.success()).isFalse();
            assertThat(result.status().reason()).isEqualTo(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
            assertThat(storage.amount()).isEqualTo(500);
        }
        for (var oversized : List.of(group(false, 300L, 300L), group(false, Long.MAX_VALUE, 1L))) {
            var result = facet.commit(oversized);
            assertThat(result.success()).isFalse();
            assertThat(result.status().reason()).isEqualTo(ManaFailureReasons.INPUT_MISSING);
            assertThat(storage.amount()).isEqualTo(500);
        }
    }

    @Test
    void workerCannotCaptureOrCommitLiveStorage() throws Exception {
        ManaStorage storage = storage(500L);
        AsyncPlanningFacet facet = facet(capability(storage, IOType.INPUT));
        CompletableFuture.runAsync(() -> {
            assertThatThrownBy(facet::captureSnapshot).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> facet.commit(new AsyncCapabilityOperation.Scalar(BotaniaManaIds.MANA, 100L, false)))
                    .isInstanceOf(IllegalStateException.class);
        }).get();
        assertThat(storage.amount()).isEqualTo(500);
    }

    private static ManaStorage storage(long amount) {
        ManaStorage storage = new ManaStorage(() -> {});
        storage.setAmount(amount);
        return storage;
    }

    private static ManaPortCapability capability(ManaStorage storage, IOType direction) {
        return new ManaPortCapability(null, storage, direction);
    }

    private static AsyncPlanningFacet facet(ManaPortCapability capability) {
        return capability.facet(AsyncPlanningFacet.class).orElseThrow();
    }

    private static CraftingContext context(ManaPortCapability... capabilities) {
        return new CraftingContext(new CapabilitySnapshot(List.of(capabilities)));
    }

    private static AsyncCapabilityOperation.Group group(boolean insert, long first, long second) {
        return new AsyncCapabilityOperation.Group(List.of(
                new AsyncCapabilityOperation.Scalar(BotaniaManaIds.MANA, first, insert),
                new AsyncCapabilityOperation.Scalar(BotaniaManaIds.MANA, second, insert)));
    }

    private static Field currentServerField() throws Exception {
        Field field = ServerLifecycleHooks.class.getDeclaredField("currentServer");
        field.setAccessible(true);
        return field;
    }

    /**
     * Minimal server identity matching the existing async planning harness.
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
