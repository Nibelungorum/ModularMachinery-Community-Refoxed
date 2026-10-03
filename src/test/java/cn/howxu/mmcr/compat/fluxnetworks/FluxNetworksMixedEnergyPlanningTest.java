package cn.howxu.mmcr.compat.fluxnetworks;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.plan.OutputFit;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.appliedflux.loaded.capability.FluxEnergyOutputCapability;
import cn.howxu.mmcr.compat.appliedflux.loaded.storage.FluxEnergyBuffer;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkInputCapability;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkOutputCapability;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkPlugHandler;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkPointHandler;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.util.IOType;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.net.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SystemReport;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.debugchart.SampleLogger;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises mixed native/Flux planning, shared reservations, and worker fallback.
 *
 * @author howxu <dev@howxu.cn>
 */
class FluxNetworksMixedEnergyPlanningTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        FluxNetworksTestBootstrap.bootstrap();
    }

    @Test
    void nativeAndFluxInputsBothParticipateInOneRequirement() {
        EnergyStorage nativeStorage = new EnergyStorage(10);
        nativeStorage.receiveEnergy(10, false);
        FluxNetworkPointHandler point = chargedPoint(20L);
        CraftingContext context = context(new EnergyHatchCapability(nativeStorage, IOType.INPUT),
                new FluxNetworkInputCapability(point, () -> "mixed-input"));

        var result = context.planRequirements(List.of(new EnergyRequirement(25L)), 1L, Map.of());

        assertThat(result.successful()).isTrue();
        assertThat(nativeStorage.getEnergyStored()).isEqualTo(10);
        assertThat(point.getBuffer()).isEqualTo(20L);
        assertThat(result.plan().commit()).isTrue();
        assertThat(nativeStorage.getEnergyStored()).isZero();
        assertThat(point.getBuffer()).isEqualTo(5L);
        assertThat(nativeStorage.getEnergyStored() + point.getBuffer()).isEqualTo(5L);
    }

    @Test
    void sharedInputReservationsRejectTwoRequirementsThatOverdrawCombinedInventory() {
        EnergyStorage nativeStorage = new EnergyStorage(10);
        nativeStorage.receiveEnergy(10, false);
        FluxNetworkPointHandler point = chargedPoint(20L);
        CraftingContext context = context(new EnergyHatchCapability(nativeStorage, IOType.INPUT),
                new FluxNetworkInputCapability(point, () -> "shared-input"));

        var result = context.planRequirements(List.of(new EnergyRequirement(20L), new EnergyRequirement(20L)),
                1L, Map.of());

        assertThat(result.successful()).isFalse();
        assertThat(result.failureRequirementIndex()).isEqualTo(1);
        assertThat(result.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_ENERGY);
        assertThat(nativeStorage.getEnergyStored()).isEqualTo(10);
        assertThat(point.getBuffer()).isEqualTo(20L);
    }

    @Test
    void sharedInputReservationsCommitMultipleRequirementsWithoutLosingFluxInventory() {
        EnergyStorage nativeStorage = new EnergyStorage(10);
        nativeStorage.receiveEnergy(10, false);
        FluxNetworkPointHandler point = chargedPoint(20L);
        CraftingContext context = context(new EnergyHatchCapability(nativeStorage, IOType.INPUT),
                new FluxNetworkInputCapability(point, () -> "shared-successful-input"));

        var result = context.planRequirements(List.of(new EnergyRequirement(15L), new EnergyRequirement(15L)),
                1L, Map.of());

        assertThat(result.successful()).isTrue();
        assertThat(nativeStorage.getEnergyStored()).isEqualTo(10);
        assertThat(point.getBuffer()).isEqualTo(20L);
        assertThat(result.plan().commit()).isTrue();
        assertThat(nativeStorage.getEnergyStored()).isZero();
        assertThat(point.getBuffer()).isZero();
    }

    @Test
    void nativeAndFluxOutputsTogetherAcceptMoreThanEitherEndpointAlone() {
        EnergyStorage nativeStorage = new EnergyStorage(10);
        FluxNetworkPlugHandler plug = plug(20L);
        CraftingContext context = context(new EnergyHatchCapability(nativeStorage, IOType.OUTPUT),
                new FluxNetworkOutputCapability(plug));

        var result = context.planRequirements(List.of(output(25L)), 1L, Map.of());

        assertThat(result.successful()).isTrue();
        assertThat(result.outputSimulations()).singleElement().satisfies(simulation -> {
            assertThat(simulation.requested()).isEqualTo(25L);
            assertThat(simulation.accepted()).isEqualTo(25L);
            assertThat(simulation.fit()).isEqualTo(OutputFit.FULL);
        });
        assertThat(nativeStorage.getEnergyStored()).isZero();
        assertThat(plug.getBuffer()).isZero();
        assertThat(result.plan().commit()).isTrue();
        assertThat(nativeStorage.getEnergyStored()).isEqualTo(5);
        assertThat(plug.getBuffer()).isEqualTo(20L);
        assertThat(nativeStorage.getEnergyStored() + plug.getBuffer()).isEqualTo(25L);
    }

    @Test
    void fullFluxBufferDoesNotAdvertiseItsValueMirrorAsOutputSpace() {
        EnergyStorage nativeStorage = new EnergyStorage(10);
        FluxNetworkPlugHandler plug = plug(20L);
        assertThat(plug.acceptRecipeEnergy(20L)).isTrue();
        CraftingContext context = context(new EnergyHatchCapability(nativeStorage, IOType.OUTPUT),
                new FluxNetworkOutputCapability(plug));

        var blocked = context.planRequirements(List.of(output(11L)), 1L, Map.of());
        assertThat(blocked.successful()).isFalse();
        assertThat(blocked.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT);
        assertThat(nativeStorage.getEnergyStored()).isZero();
        assertThat(plug.getBuffer()).isEqualTo(20L);

        var nativeOnly = context.planRequirements(List.of(output(10L)), 1L, Map.of());
        assertThat(nativeOnly.successful()).isTrue();
        assertThat(nativeOnly.plan().commit()).isTrue();
        assertThat(nativeStorage.getEnergyStored()).isEqualTo(10);
        assertThat(plug.getBuffer()).isEqualTo(20L);
    }

    @Test
    void sequentialOutputsReserveNonLinearAdmissionWithoutProducingDuringSimulation() {
        EnergyStorage fullNative = new EnergyStorage(1);
        fullNative.receiveEnergy(1, false);
        AtomicInteger changes = new AtomicInteger();
        FluxNetworkPlugHandler plug = new FluxNetworkPlugHandler(() -> true, () -> 100L, changes::incrementAndGet);
        plug.setLimit(100L);
        CraftingContext context = context(new EnergyHatchCapability(fullNative, IOType.OUTPUT),
                new FluxNetworkOutputCapability(plug));

        var result = context.planRequirements(List.of(output(40L), output(40L)), 1L, Map.of());

        assertThat(result.successful()).isFalse();
        assertThat(result.plan()).isNull();
        assertThat(result.failureRequirementIndex()).isEqualTo(1);
        assertThat(result.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT);
        assertThat(result.outputSimulations()).hasSize(2);
        assertThat(result.outputSimulations().get(0).accepted()).isEqualTo(40L);
        assertThat(result.outputSimulations().get(1).accepted()).isEqualTo(20L);
        assertThat(plug.getBuffer()).isZero();
        assertThat(changes.get()).isZero();

        var partial = context.planRequirements(List.of(output(40L), output(40L)), 1L,
                Map.of(1, OutputPolicy.ALLOW_PARTIAL));
        assertThat(partial.successful()).isTrue();
        assertThat(plug.getBuffer()).isZero();
        assertThat(partial.plan().commit()).isTrue();
        assertThat(plug.getBuffer()).isEqualTo(60L);
        assertThat(fullNative.getEnergyStored()).isEqualTo(1);
        assertThat(changes.get()).isEqualTo(2);
    }

    @Test
    void valueOnlySequentialOutputsShareNonLinearReservationsIncludingHandlerAliases() {
        for (boolean alias : List.of(false, true)) {
            AtomicInteger changes = new AtomicInteger();
            FluxNetworkPlugHandler plug = new FluxNetworkPlugHandler(() -> true, () -> 100L, changes::incrementAndGet);
            plug.setLimit(100L);
            FluxNetworkOutputCapability capability = new FluxNetworkOutputCapability(plug);
            CraftingContext context = alias ? context(capability, new FluxNetworkOutputCapability(plug))
                    : context(capability);

            var full = context.planRequirements(List.of(output(40L), output(40L)), 1L, Map.of());
            assertThat(full.successful()).isFalse();
            assertThat(full.plan()).isNull();
            assertThat(full.failureRequirementIndex()).isEqualTo(1);
            assertThat(full.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT);
            assertThat(full.outputSimulations()).hasSize(2);
            assertThat(full.outputSimulations().get(0).accepted()).isEqualTo(40L);
            assertThat(full.outputSimulations().get(1).accepted()).isEqualTo(20L);
            assertThat(plug.getBuffer()).isZero();
            assertThat(changes.get()).isZero();

            var fitting = context.planRequirements(List.of(output(40L), output(20L)), 1L, Map.of());
            assertThat(fitting.successful()).isTrue();
            assertThat(fitting.outputSimulations()).allSatisfy(simulation ->
                    assertThat(simulation.fit()).isEqualTo(OutputFit.FULL));
            var partial = context.planRequirements(List.of(output(40L), output(40L)), 1L,
                    Map.of(1, OutputPolicy.ALLOW_PARTIAL));
            assertThat(partial.successful()).isTrue();
            assertThat(partial.outputSimulations()).hasSize(2);
            assertThat(partial.outputSimulations().get(1).accepted()).isEqualTo(20L);
            assertThat(partial.outputSimulations().get(1).fit()).isEqualTo(OutputFit.PARTIAL);
            assertThat(plug.getBuffer()).isZero();
            assertThat(changes.get()).isZero();
            assertThat(partial.plan().commit()).isTrue();
            assertThat(plug.getBuffer()).isEqualTo(60L);
            assertThat(plug.storage().amount()).isEqualTo(60L);
            assertThat(changes.get()).isEqualTo(2);
        }
    }

    @Test
    void realAppFluxAdmissionRejectsInsufficientRemainingEvenInPartialMode() {
        for (boolean mixed : List.of(false, true)) {
            FluxEnergyBuffer pending = new FluxEnergyBuffer();
            FluxEnergyOutputCapability appFlux = new FluxEnergyOutputCapability(pending);
            appFlux.refreshAdmissionBudget(3L);
            EnergyStorage fullNative = new EnergyStorage(1);
            fullNative.receiveEnergy(1, false);
            CraftingContext context = mixed ? context(appFlux, new EnergyHatchCapability(fullNative, IOType.OUTPUT))
                    : context(appFlux);

            var result = context.planRequirements(List.of(output(5L)), 1L, Map.of(0, OutputPolicy.ALLOW_PARTIAL));

            assertThat(result.successful()).isFalse();
            assertThat(result.plan()).isNull();
            assertThat(result.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT);
            assertThat(result.outputSimulations()).singleElement().satisfies(simulation -> {
                assertThat(simulation.requested()).isEqualTo(5L);
                assertThat(simulation.accepted()).isZero();
                assertThat(simulation.fit()).isEqualTo(OutputFit.NONE);
            });
            assertThat(pending.amount()).isZero();
            assertThat(appFlux.admissionBudget()).isEqualTo(3L);
            assertThat(fullNative.getEnergyStored()).isEqualTo(1);
        }
    }

    @Test
    void realAppFluxExactAdmissionLeavesOrdinaryNativePartialOutputAvailable() {
        FluxEnergyBuffer pending = new FluxEnergyBuffer();
        FluxEnergyOutputCapability appFlux = new FluxEnergyOutputCapability(pending);
        appFlux.refreshAdmissionBudget(1L);
        EnergyStorage nativeStorage = new EnergyStorage(3);
        CraftingContext context = context(appFlux, new EnergyHatchCapability(nativeStorage, IOType.OUTPUT));

        var full = context.planRequirements(List.of(output(5L)), 1L, Map.of());
        assertThat(full.successful()).isFalse();
        assertThat(full.plan()).isNull();
        assertThat(pending.amount()).isZero();
        assertThat(appFlux.admissionBudget()).isEqualTo(1L);
        assertThat(nativeStorage.getEnergyStored()).isZero();

        var partial = context.planRequirements(List.of(output(5L)), 1L, Map.of(0, OutputPolicy.ALLOW_PARTIAL));
        assertThat(partial.successful()).isTrue();
        assertThat(partial.outputSimulations()).singleElement().satisfies(simulation -> {
            assertThat(simulation.requested()).isEqualTo(5L);
            assertThat(simulation.accepted()).isEqualTo(3L);
            assertThat(simulation.fit()).isEqualTo(OutputFit.PARTIAL);
        });
        assertThat(pending.amount()).isZero();
        assertThat(appFlux.admissionBudget()).isEqualTo(1L);
        assertThat(nativeStorage.getEnergyStored()).isZero();
        assertThat(partial.plan().commit()).isTrue();
        assertThat(nativeStorage.getEnergyStored()).isEqualTo(3);
        assertThat(pending.amount()).isZero();
        assertThat(appFlux.admissionBudget()).isEqualTo(1L);
    }

    @Test
    void realAppFluxAcceptsTheWholeRemainingBudgetAfterASplitFluxOutput() {
        for (boolean mixed : List.of(false, true)) {
            FluxNetworkPlugHandler plug = plug(2L);
            FluxEnergyBuffer pending = new FluxEnergyBuffer();
            FluxEnergyOutputCapability appFlux = new FluxEnergyOutputCapability(pending);
            appFlux.refreshAdmissionBudget(3L);
            EnergyStorage fullNative = new EnergyStorage(1);
            fullNative.receiveEnergy(1, false);
            FluxNetworkOutputCapability flux = new FluxNetworkOutputCapability(plug);
            CraftingContext context = mixed ? context(flux, appFlux, new EnergyHatchCapability(fullNative, IOType.OUTPUT))
                    : context(flux, appFlux);

            var result = context.planRequirements(List.of(output(5L)), 1L, Map.of());

            assertThat(result.successful()).isTrue();
            assertThat(result.outputSimulations()).singleElement().satisfies(simulation -> {
                assertThat(simulation.accepted()).isEqualTo(5L);
                assertThat(simulation.fit()).isEqualTo(OutputFit.FULL);
            });
            assertThat(plug.getBuffer()).isZero();
            assertThat(pending.amount()).isZero();
            assertThat(appFlux.admissionBudget()).isEqualTo(3L);
            assertThat(result.plan().commit()).isTrue();
            assertThat(plug.getBuffer()).isEqualTo(2L);
            assertThat(pending.amount()).isEqualTo(3L);
            assertThat(appFlux.admissionBudget()).isZero();
            assertThat(fullNative.getEnergyStored()).isEqualTo(1);
        }
    }

    @Test
    void mixedNativePlanningStillSplitsOutputAcrossMultipleRealFluxPlugs() {
        EnergyStorage fullNative = new EnergyStorage(1);
        fullNative.receiveEnergy(1, false);
        FluxNetworkPlugHandler first = plug(20L);
        FluxNetworkPlugHandler second = plug(20L);
        CraftingContext context = context(new EnergyHatchCapability(fullNative, IOType.OUTPUT),
                new FluxNetworkOutputCapability(first), new FluxNetworkOutputCapability(second));

        var result = context.planRequirements(List.of(output(25L)), 1L, Map.of());

        assertThat(result.successful()).isTrue();
        assertThat(first.getBuffer()).isZero();
        assertThat(second.getBuffer()).isZero();
        assertThat(result.plan().commit()).isTrue();
        assertThat(first.getBuffer()).isEqualTo(20L);
        assertThat(second.getBuffer()).isEqualTo(5L);
        assertThat(fullNative.getEnergyStored()).isEqualTo(1);
    }

    @Test
    void ordinaryNativeStorageReservationsCannotBeReusedThroughAnAliasInAMixedPlan() {
        EnergyStorage nativeStorage = new EnergyStorage(10);
        nativeStorage.receiveEnergy(10, false);
        FluxNetworkPointHandler point = chargedPoint(3L);
        CraftingContext context = context(new EnergyHatchCapability(nativeStorage, IOType.INPUT),
                new EnergyHatchCapability(nativeStorage, IOType.INPUT),
                new FluxNetworkInputCapability(point, () -> "alias-input"));

        var result = context.planRequirements(List.of(new EnergyRequirement(16L)), 1L, Map.of());

        assertThat(result.successful()).isFalse();
        assertThat(nativeStorage.getEnergyStored()).isEqualTo(10);
        assertThat(point.getBuffer()).isEqualTo(3L);
    }

    @Test
    void mixedCaptureFallsBackForBothDirectionsAndNeverCallsLiveFluxOnWorker() throws Exception {
        Thread mainThread = Thread.currentThread();
        AtomicInteger liveCalls = new AtomicInteger();
        Runnable requireMainThread = () -> {
            assertThat(Thread.currentThread()).isSameAs(mainThread);
            liveCalls.incrementAndGet();
        };
        FluxNetworkPointHandler point = new FluxNetworkPointHandler(() -> {
            requireMainThread.run();
            return true;
        }, () -> {
            requireMainThread.run();
            return 1L;
        }, requireMainThread);
        point.setLimit(20L);
        point.requestWarmup(20L);
        point.onCycleStart();
        point.addToBuffer(point.getRequest());
        FluxNetworkPlugHandler plug = new FluxNetworkPlugHandler(() -> {
            requireMainThread.run();
            return true;
        }, () -> {
            requireMainThread.run();
            return 20L;
        }, requireMainThread);
        plug.setLimit(20L);
        EnergyStorage nativeInput = new EnergyStorage(10);
        nativeInput.receiveEnergy(10, false);
        EnergyStorage nativeOutput = new EnergyStorage(10);
        CraftingContext context = context(new EnergyHatchCapability(nativeInput, IOType.INPUT),
                new FluxNetworkInputCapability(point, () -> {
                    requireMainThread.run();
                    return "thread-checked-input";
                }), new EnergyHatchCapability(nativeOutput, IOType.OUTPUT), new FluxNetworkOutputCapability(plug));

        assertThat(context.captureAsyncCapabilities()).isEmpty();
        assertThat(context.captureAsyncCapabilities(Set.of(new EnergyRequirement(25L).type().id()))).isEmpty();
        List<MachineRequirement> requirements = List.of(new EnergyRequirement(25L), output(25L));
        var prepared = context.planAsync(requirements, 1L);
        assertThat(prepared.capabilities()).isEmpty();
        var smallPrepared = context.planAsync(List.of(new EnergyRequirement(5L)), 1L, List.of(7));
        int capturedCalls = liveCalls.get();
        var workerResult = CompletableFuture.supplyAsync(prepared::plan).get();
        assertThat(workerResult.operations()).isEmpty();
        assertThat(workerResult.mainThreadRequirements()).containsExactly(0, 1);
        var smallResult = CompletableFuture.supplyAsync(smallPrepared::plan).get();
        assertThat(smallResult.operations()).isEmpty();
        assertThat(smallResult.mainThreadRequirements()).containsExactly(7);
        assertThat(liveCalls.get()).isEqualTo(capturedCalls);
        assertThat(nativeInput.getEnergyStored()).isEqualTo(10);
        assertThat(point.getBuffer()).isEqualTo(20L);
        assertThat(plug.getBuffer()).isZero();

        var fallback = context.planRequirements(requirements, 1L, Map.of());
        assertThat(fallback.successful()).isTrue();
        assertThat(fallback.plan().commit()).isTrue();
        assertThat(liveCalls.get()).isGreaterThan(capturedCalls);
        assertThat(nativeInput.getEnergyStored() + point.getBuffer()).isEqualTo(5L);
        assertThat(nativeOutput.getEnergyStored() + plug.getBuffer()).isEqualTo(25L);
    }

    @Test
    void pureNativeEnergyStillCapturesAndPlansOnWorkerThenCommitsOnMainThread() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        TestServer server = (TestServer) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(TestServer.class);
        server.serverThread = Thread.currentThread();
        Field currentServer = ServerLifecycleHooks.class.getDeclaredField("currentServer");
        currentServer.setAccessible(true);
        Object previousServer = currentServer.get(null);
        currentServer.set(null, server);
        try {
            EnergyStorage storage = new EnergyStorage(30);
            storage.receiveEnergy(30, false);
            EnergyHatchCapability capability = new EnergyHatchCapability(storage, IOType.INPUT);
            CraftingContext context = context(capability);
            assertThat(context.captureAsyncCapabilities()).hasSize(1);
            var prepared = context.planAsync(List.of(new EnergyRequirement(25L)), 1L);
            var result = CompletableFuture.supplyAsync(prepared::plan).get();

            assertThat(result.mainThreadRequirements()).isEmpty();
            assertThat(result.operations()).singleElement().satisfies(operation -> {
                assertThat(operation.operation()).isEqualTo(new AsyncCapabilityOperation.Scalar(capability.type().id(),
                        25L, false));
                assertThat(storage.getEnergyStored()).isEqualTo(30);
                assertThat(capability.facet(AsyncPlanningFacet.class).orElseThrow()
                        .commit(operation.operation()).success()).isTrue();
            });
            assertThat(storage.getEnergyStored()).isEqualTo(5);
        } finally {
            currentServer.set(null, previousServer);
        }
    }

    private static CraftingContext context(MachineCapability... capabilities) {
        return new CraftingContext(new CapabilitySnapshot(List.of(capabilities)));
    }

    private static EnergyRequirement output(long amount) {
        return new EnergyRequirement(RecipeModifier.IOType.OUTPUT, amount);
    }

    private static FluxNetworkPointHandler chargedPoint(long amount) {
        FluxNetworkPointHandler point = new FluxNetworkPointHandler(() -> true, () -> 1L, () -> {});
        point.setLimit(amount);
        point.requestWarmup(amount);
        point.onCycleStart();
        point.addToBuffer(point.getRequest());
        return point;
    }

    private static FluxNetworkPlugHandler plug(long capacity) {
        FluxNetworkPlugHandler plug = new FluxNetworkPlugHandler(() -> true, () -> capacity, () -> {});
        plug.setLimit(capacity);
        return plug;
    }

    /** Minimal server-thread identity used by the existing async test harness.
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
