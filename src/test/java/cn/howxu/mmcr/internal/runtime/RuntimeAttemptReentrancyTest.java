package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.TickFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickContext;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickPhase;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickResult;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.ConfigTestSupport;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.config.IConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Controlled root notifications and phase cancellation, without natural ticks or sleeps.
 * @author howxu <dev@howxu.cn>
 */
class RuntimeAttemptReentrancyTest {
    private static final ResourceLocation MACHINE = MMCR.id("test_cube");
    private static final HolderLookup.Provider LOOKUP = HolderLookup.Provider.create(Stream.empty());
    private ServerThreadTestScope serverThreadScope;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        TestBootstrap.bootstrapCapabilities();
        CommentedConfig config = CommentedConfig.inMemory();
        ServerConfig.SPEC.correct(config);
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        ServerConfig.SPEC.acceptConfig((IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null));
    }

    @BeforeEach
    void installServerThread() throws Exception {
        serverThreadScope = ServerThreadTestScope.open();
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
            RecipeRegistry.clearForTesting();
        } finally {
            if (serverThreadScope != null) serverThreadScope.close();
        }
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void root_notification_and_after_reentry_share_one_attempt_across_resource_entries(MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        for (boolean installedEntry : List.of(false, true)) {
            for (boolean rootNotification : List.of(false, true)) {
                AtomicReference<CraftingRuntime> owner = new AtomicReference<>();
                AtomicBoolean armed = new AtomicBoolean();
                List<CapabilityTickPhase> phases = new ArrayList<>();
                Runnable reenter = () -> {
                    CraftingRuntime runtime = owner.get();
                    runtime.tick();
                    assertThat(runtime.prepareAsyncTick(runtime.controller().currentRuntimeSnapshot())).isFalse();
                    assertThat(runtime.prepareInstalledTick(99L)).isFalse();
                    assertThat(runtime.commitAsyncTick(null)).isFalse();
                    assertThat(runtime.completeAsyncTickAfterInputs()).isFalse();
                    runtime.completeAsyncTickAfterRecipe();
                    assertThat(runtime.installedProgram().tryAttempt(99L, 99L)).isFalse();
                    assertThat(runtime.installedProgram().advance()).isFalse();
                };
                LongEnergyStorage energy = storage(() -> {
                    if (rootNotification && armed.compareAndSet(true, false)) reenter.run();
                });
                TickCapability tick = new TickCapability(context -> {
                    phases.add(context.phase());
                    if (!rootNotification && context.phase() == CapabilityTickPhase.AFTER_RECIPE
                            && armed.compareAndSet(true, false)) reenter.run();
                    return CapabilityTickResult.empty();
                });
                CraftingRuntime runtime = runtime(energy, List.of(tick));
                owner.set(runtime);
                runtime.start(recipe("attempt_reentry", 5), 1L);
                InstalledRecipeProgram installed = runtime.ensureInstalledProgram();
                long before = energy.getAmountAsLong();
                long paid = runtime.ledger().state().paidAttempts();
                armed.set(true);
                LevelStub.setGameTime(runtime.controller().getLevel(), 1L);
                if (installedEntry) advance(runtime, installed, mode, 1L);
                else runtime.tick();
                assertThat(armed).isFalse();
                assertThat(energy.getAmountAsLong()).isEqualTo(before - 7L);
                assertThat(runtime.tickCount()).isEqualTo(1);
                assertThat(runtime.ledger().state().paidAttempts()).isEqualTo(paid + 1);
                assertThat(phases).containsExactly(CapabilityTickPhase.BEFORE_RECIPE,
                        CapabilityTickPhase.AFTER_INPUTS, CapabilityTickPhase.AFTER_RECIPE);
                runtime.tick();
                assertThat(installed.tryAttempt(99L, 1L)).isFalse();
                assertThat(energy.getAmountAsLong()).isEqualTo(before - 7L);
                // The finally guard releases ownership for the next legal attempt.
                LevelStub.setGameTime(runtime.controller().getLevel(), 2L);
                if (installedEntry) advance(runtime, installed, mode, 2L);
                else runtime.tick();
                assertThat(energy.getAmountAsLong()).isEqualTo(before - 14L);
                assertThat(runtime.tickCount()).isEqualTo(2);
                assertThat(runtime.ledger().state().paidAttempts()).isEqualTo(paid + 2);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void phase_cancellation_stops_old_work_and_retains_only_already_committed_native_mutations(MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        for (CapabilityTickPhase phase : List.of(CapabilityTickPhase.BEFORE_RECIPE, CapabilityTickPhase.AFTER_INPUTS,
                CapabilityTickPhase.AFTER_RECIPE)) {
            for (boolean inOperation : List.of(false, true)) {
                for (Cancellation cancellation : Cancellation.values()) {
                    AtomicReference<CraftingRuntime> owner = new AtomicReference<>();
                    LongEnergyStorage energy = storage(null);
                    LongEnergyStorage phaseResource = storage(null);
                    List<String> trace = new ArrayList<>();
                    TickCapability first = new TickCapability(context -> {
                        trace.add("first:" + context.phase());
                        if (context.phase() != phase) return CapabilityTickResult.empty();
                        if (!inOperation) cancellation.apply(owner.get());
                        return new CapabilityTickResult(List.of(() -> {
                            trace.add("firstOperation");
                            phaseResource.extractLong(3L, false);
                            if (inOperation) cancellation.apply(owner.get());
                            return CapabilityResult.successful();
                        }), null, false);
                    });
                    TickCapability second = new TickCapability(context -> {
                        trace.add("second:" + context.phase());
                        if (context.phase() != phase) return CapabilityTickResult.empty();
                        return new CapabilityTickResult(List.of(() -> {
                            trace.add("secondOperation");
                            phaseResource.extractLong(4L, false);
                            return CapabilityResult.successful();
                        }), null, false);
                    });
                    CraftingRuntime runtime = runtime(energy, List.of(first, second));
                    owner.set(runtime);
                    MachineRecipe recipe = recipe("attempt_cancel_" + phase.ordinal() + "_" + inOperation + "_"
                            + cancellation.ordinal(), 5);
                    RecipeRegistry.registerStatic(recipe);
                    runtime.start(recipe, 1L);
                    InstalledRecipeProgram installed = runtime.ensureInstalledProgram();
                    RecipeCommitLedger payment = runtime.ledger();
                    long before = energy.getAmountAsLong();
                    long paid = payment.state().paidAttempts();
                    LevelStub.setGameTime(runtime.controller().getLevel(), 1L);
                    if (mode != MachineWorkMode.SYNC) runtime.prepareInstalledTick(1L);
                    if (installed.tryAttempt(1L, 1L)) installed.advance();

                    assertThat(trace).doesNotContain("secondOperation");
                    if (inOperation) assertThat(trace).contains("second:" + phase, "firstOperation");
                    else assertThat(trace).doesNotContain("second:" + phase, "firstOperation");
                    if (phase != CapabilityTickPhase.AFTER_RECIPE) {
                        assertThat(trace).doesNotContain("first:" + CapabilityTickPhase.AFTER_RECIPE);
                    }
                    assertThat(phaseResource.getAmountAsLong()).isEqualTo(inOperation ? 97L : 100L);
                    boolean ordinaryPaid = phase != CapabilityTickPhase.BEFORE_RECIPE;
                    assertThat(energy.getAmountAsLong()).isEqualTo(before - (ordinaryPaid ? 7L : 0L));
                    assertThat(payment.state().paidAttempts()).isEqualTo(paid + (ordinaryPaid ? 1L : 0L));
                    if (cancellation != Cancellation.INVALIDATE) {
                        // AFTER_RECIPE follows the ordinary nonfinal grant; cancelling it cannot undo that grant.
                        assertThat(runtime.tickCount()).isEqualTo(phase == CapabilityTickPhase.AFTER_RECIPE ? 1 : 0);
                        if (ordinaryPaid && phase == CapabilityTickPhase.AFTER_INPUTS) {
                            assertThat(payment.state().phase()).isEqualTo(RecipeCommitLedger.Phase.RESOURCE_PAID);
                            CompoundTag saved = save(runtime);
                            var restored = new CraftingRuntime(runtime.controller(), runtime.controller().componentRuntime());
                            restored.load(saved, null, LOOKUP);
                            assertThat(restored.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.ERROR_PAUSED);
                            restored.resume();
                            restored.tick();
                            assertThat(energy.getAmountAsLong()).isEqualTo(before - 7L);
                            assertThat(restored.tickCount()).isZero();
                        }
                    }
                    if (cancellation == Cancellation.PAUSE) assertThat(runtime.snapshot().status().isPaused()).isTrue();
                    assertThat(installed.current()).isFalse();
                    assertThat(installed.advance()).isFalse();
                    assertThat(energy.getAmountAsLong()).isEqualTo(before - (ordinaryPaid ? 7L : 0L));
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void pending_async_attempt_keeps_ownership_through_ready_paid_and_after_windows(MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        AtomicReference<CraftingRuntime> owner = new AtomicReference<>();
        AtomicBoolean armed = new AtomicBoolean();
        Runnable reenter = () -> {
            CraftingRuntime runtime = owner.get();
            runtime.tick();
            assertThat(runtime.commitAsyncTick(null)).isFalse();
            assertThat(runtime.completeAsyncTickAfterInputs()).isFalse();
            runtime.completeAsyncTickAfterRecipe();
        };
        LongEnergyStorage energy = storage(() -> {
            if (armed.compareAndSet(true, false)) reenter.run();
        });
        List<CapabilityTickPhase> phases = new ArrayList<>();
        CraftingRuntime runtime = runtime(energy, List.of(new TickCapability(context -> {
            phases.add(context.phase());
            if (context.phase() == CapabilityTickPhase.AFTER_RECIPE) reenter.run();
            return CapabilityTickResult.empty();
        })));
        owner.set(runtime);
        runtime.start(recipe("attempt_async", 5), 1L);
        InstalledRecipeProgram installed = runtime.ensureInstalledProgram();
        LevelStub.setGameTime(runtime.controller().getLevel(), 1L);
        long before = energy.getAmountAsLong();
        long paid = runtime.ledger().state().paidAttempts();
        assertThat(runtime.prepareAsyncTickPlan(runtime.controller().currentRuntimeSnapshot())).isNotNull();
        runtime.tick();
        assertThat(runtime.prepareInstalledTick(1L)).isFalse();
        assertThat(installed.tryAttempt(1L, 1L)).isFalse();
        assertThat(installed.advance()).isFalse();
        assertThat(energy.getAmountAsLong()).isEqualTo(before);
        armed.set(true);
        assertThat(runtime.commitAsyncTick(null)).isTrue();
        assertThat(runtime.commitAsyncTick(null)).isFalse();
        assertThat(runtime.prepareInstalledTick(1L)).isFalse();
        runtime.tick();
        assertThat(runtime.completeAsyncTickAfterInputs()).isTrue();
        runtime.completeAsyncTickAfterRecipe();
        assertThat(runtime.tickCount()).isEqualTo(1);
        assertThat(runtime.ledger().state().paidAttempts()).isEqualTo(paid + 1L);
        assertThat(energy.getAmountAsLong()).isEqualTo(before - 7L);
        assertThat(phases).containsExactly(CapabilityTickPhase.BEFORE_RECIPE,
                CapabilityTickPhase.AFTER_INPUTS, CapabilityTickPhase.AFTER_RECIPE);
        LevelStub.setGameTime(runtime.controller().getLevel(), 2L);
        assertThat(runtime.prepareAsyncTickPlan(runtime.controller().currentRuntimeSnapshot())).isNotNull();
        assertThat(runtime.commitAsyncTick(null)).isTrue();
        assertThat(runtime.completeAsyncTickAfterInputs()).isTrue();
        runtime.completeAsyncTickAfterRecipe();
        assertThat(runtime.tickCount()).isEqualTo(2);
        assertThat(energy.getAmountAsLong()).isEqualTo(before - 14L);
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void controller_before_and_controlled_post_stop_cancelled_phase_work(MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        for (CapabilityTickPhase phase : List.of(CapabilityTickPhase.BEFORE_RECIPE, CapabilityTickPhase.AFTER_INPUTS)) {
            for (boolean inOperation : List.of(false, true)) {
                for (Cancellation cancellation : Cancellation.values()) {
                    AtomicReference<CraftingRuntime> owner = new AtomicReference<>();
                    LongEnergyStorage energy = storage(null);
                    LongEnergyStorage phaseResource = storage(null);
                    List<String> trace = new ArrayList<>();
                    CraftingRuntime fixture = runtime(energy, List.of(new TickCapability(context -> {
                        trace.add("first:" + context.phase());
                        if (context.phase() != phase) return CapabilityTickResult.empty();
                        if (!inOperation) cancellation.apply(owner.get());
                        return new CapabilityTickResult(List.of(() -> {
                            trace.add("firstOperation");
                            phaseResource.extractLong(3L, false);
                            if (inOperation) cancellation.apply(owner.get());
                            return CapabilityResult.successful();
                        }), null, false);
                    }), new TickCapability(context -> {
                        trace.add("second:" + context.phase());
                        return new CapabilityTickResult(List.of(() -> {
                            trace.add("secondOperation:" + context.phase());
                            return CapabilityResult.successful();
                        }), null, false);
                    })));
                    var controller = fixture.controller();
                    var components = List.copyOf(controller.componentRuntime().components());
                    ResourceLocation machineId = MMCR.id("attempt_be_fixture");
                    RuntimeTestFixtures.registerRecipePool(machineId);
                    assertThat(MachineRegistry.getCompiled(machineId)).isNull();
                    var pattern = new BlockArray(Map.of(BlockPos.ZERO,
                            new BlockPredicate.OfBlock(controller.getBlockState().getBlock())));
                    var spec = MachineControllerSpec.defaultsFor(machineId);
                    List<String> dispatch = new ArrayList<>();
                    var behavior = RecipeBehavior.builder().preServerTick(context -> dispatch.add("BE")).build();
                    Machine machine = new Machine() {
                        public ResourceLocation registryName() { return machineId; }
                        public BlockArray pattern() { return pattern; }
                        public MachineControllerSpec controller() { return spec; }
                        public RecipeBehavior behavior() { return behavior; }
                    };
                    RuntimeTestFixtures.formStructure(controller, machine);
                    controller.componentRuntime().replaceComponents(components);
                    RuntimeTestFixtures.republish(controller);
                    CraftingRuntime runtime = RuntimeTestFixtures.craftingRuntime(controller);
                    owner.set(runtime);
                    assertThat(runtime).isNotSameAs(fixture);
                    assertThat(runtime.controller()).isSameAs(controller);
                    assertThat(controller.structureSnapshot().formed()).isTrue();
                    assertThat(controller.structureSnapshot().structureAreaLoaded()).isTrue();
                    assertThat(controller.structureSnapshot().machine()).isSameAs(machine);
                    assertThat(controller.componentRuntime().components()).containsExactlyElementsOf(components);
                    assertThat(controller.hasFactoryController()).isFalse();
                    var recipe = RecipeTestSupport.create(MMCR.id("attempt_be_cancel"), machineId, 5,
                            List.of(new EnergyRequirement(7L)), List.of());
                    assertThat(runtime.start(recipe, 1L).isCrafting()).isTrue();
                    InstalledRecipeProgram installed = runtime.ensureInstalledProgram();
                    assertThat(installed.owner()).isSameAs(runtime);
                    assertThat(installed.current()).isTrue();
                    int compilations = runtime.programCompileCountForTesting();
                    long before = energy.getAmountAsLong();
                    RecipeCommitLedger payment = runtime.ledger();
                    long paid = payment.state().paidAttempts();
                    ServerLevel level = (ServerLevel) controller.getLevel();
                    RuntimeTestFixtures.advanceGameTime(level);
                    long round = RuntimeTestFixtures.beginLevelTick(level, true);
                    SharedIoCoordinator coordinator = SharedIoCoordinator.get(level);
                    assertThat(coordinator.controllerPermit(controller.getBlockPos())
                            .eligible(round, level.getGameTime(), true)).isFalse();
                    controller.serverTick();
                    assertThat(coordinator.controllerPermit(controller.getBlockPos())
                            .eligible(round, level.getGameTime(), true)).isTrue();
                    assertThat(dispatch).containsExactly("BE");
                    assertThat(trace).contains("first:" + CapabilityTickPhase.BEFORE_RECIPE);
                    assertThat(controller.componentRuntime().components()).containsExactlyElementsOf(components);
                    assertThat(runtime.programCompileCountForTesting()).isEqualTo(compilations);
                    assertThat(MachineAsyncCoordinator.get(level).hasPendingMainStepForTesting()).isFalse();
                    if (mode != MachineWorkMode.SYNC) {
                        assertThat(energy.getAmountAsLong()).isEqualTo(before);
                        assertThat(trace).doesNotContain("first:" + CapabilityTickPhase.AFTER_INPUTS);
                    }
                    coordinator.resolve(level);
                    MachineAsyncCoordinator.get(level).completeUntilIdleForTesting(() -> coordinator.resolve(level));
                    assertThat(trace).contains("first:" + phase);
                    assertThat(trace).doesNotContain("secondOperation:" + phase,
                            "first:" + CapabilityTickPhase.AFTER_RECIPE);
                    if (inOperation) assertThat(trace).contains("firstOperation", "second:" + phase);
                    else assertThat(trace).doesNotContain("firstOperation", "second:" + phase);
                    assertThat(phaseResource.getAmountAsLong()).isEqualTo(inOperation ? 97L : 100L);
                    boolean ordinaryPaid = phase == CapabilityTickPhase.AFTER_INPUTS;
                    assertThat(energy.getAmountAsLong()).isEqualTo(before - (ordinaryPaid ? 7L : 0L));
                    assertThat(payment.state().paidAttempts()).isEqualTo(paid + (ordinaryPaid ? 1L : 0L));
                    assertThat(runtime.tickCount()).isZero();
                    if (cancellation == Cancellation.PAUSE) assertThat(runtime.snapshot().status().isPaused()).isTrue();
                    assertThat(installed.current()).isFalse();
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void ordinary_after_failure_keeps_the_existing_paid_grant_semantics(MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        for (int duration : List.of(1, 5)) {
            for (CapabilityTickPhase phase : List.of(CapabilityTickPhase.AFTER_INPUTS, CapabilityTickPhase.AFTER_RECIPE)) {
                LongEnergyStorage energy = storage(null);
                List<CapabilityTickPhase> calls = new ArrayList<>();
                CraftingRuntime runtime = runtime(energy, List.of(new TickCapability(context -> {
                    calls.add(context.phase());
                    return context.phase() == phase ? new CapabilityTickResult(List.of(), tickFailure(), false)
                            : CapabilityTickResult.empty();
                })));
                runtime.start(recipe("attempt_normal_failure", duration), 1L);
                InstalledRecipeProgram installed = runtime.ensureInstalledProgram();
                long before = energy.getAmountAsLong();
                long paid = runtime.ledger().state().paidAttempts();
                advance(runtime, installed, mode, 1L);
                assertThat(runtime.tickCount()).isEqualTo(duration == 1 ? 0 : 1);
                assertThat(runtime.finishPending()).isEqualTo(duration == 1);
                assertThat(energy.getAmountAsLong()).isEqualTo(before - 7L);
                assertThat(runtime.ledger().state().paidAttempts()).isEqualTo(paid + 1);
                assertThat(runtime.failure()).isNotNull();
                if (phase == CapabilityTickPhase.AFTER_INPUTS) assertThat(calls).doesNotContain(CapabilityTickPhase.AFTER_RECIPE);
            }
        }
    }

    private static void advance(CraftingRuntime runtime, InstalledRecipeProgram installed, MachineWorkMode mode, long tick) {
        LevelStub.setGameTime(runtime.controller().getLevel(), tick);
        if (mode != MachineWorkMode.SYNC) assertThat(runtime.prepareInstalledTick(tick)).isTrue();
        assertThat(installed.tryAttempt(tick, tick)).isTrue();
        assertThat(installed.advance()).isTrue();
    }

    private static CraftingRuntime runtime(LongEnergyStorage energy, List<MachineCapability> facets) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MACHINE);
        List<MachineCapability> capabilities = new ArrayList<>();
        capabilities.add(new EnergyHatchCapability(energy, IOType.INPUT));
        capabilities.addAll(facets);
        var block = RuntimeTestFixtures.energyInput(BlockPos.ZERO);
        TestCapabilityHost host = new TestCapabilityHost(block, capabilities);
        controller.componentRuntime().replaceComponents(List.of(new ProcessingComponent(null, host,
                BlockPos.ZERO, BlockPos.ZERO, (String) null)));
        RuntimeTestFixtures.republish(controller);
        return new CraftingRuntime(controller, controller.componentRuntime());
    }

    private static LongEnergyStorage storage(Runnable notification) {
        var storage = new LongEnergyStorage(100L, 100L, notification);
        storage.setAmount(100L);
        return storage;
    }

    private static MachineRecipe recipe(String name, int duration) {
        return RecipeTestSupport.create(MMCR.id(name), MACHINE, duration, List.of(new EnergyRequirement(7L)), List.of());
    }

    private static CompoundTag save(CraftingRuntime runtime) {
        var output = new CompoundTag();
        runtime.save(output, LOOKUP);
        return output;
    }

    private static ExecutionStatus tickFailure() {
        return ExecutionStatus.blocked(MMCR.id("tick_failure"), MMCR.id("test"),
                FailureOccurrence.at(BuiltinFailureReasons.PER_TICK, MMCR.id("test"), FailurePhase.PER_TICK,
                        null, null, Map.of()));
    }

    /** @author howxu <dev@howxu.cn> */
    private enum Cancellation {
        INVALIDATE, PAUSE, ONLY_CLOSE;

        void apply(CraftingRuntime runtime) {
            switch (this) {
                case INVALIDATE -> runtime.invalidate();
                case PAUSE -> runtime.pause();
                case ONLY_CLOSE -> runtime.cancelInstalledProgram();
            }
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private record TickCapability(Function<CapabilityTickContext, CapabilityTickResult> planner)
            implements MachineCapability, TickFacet {
        public CapabilityType type() { return new CapabilityType(MMCR.id("attempt_tick")); }
        public CapabilityDirections directions() { return CapabilityDirections.input(); }
        public CapabilityView view() {
            return new CapabilityView() {
                public CapabilityType type() { return TickCapability.this.type(); }
                public CapabilityDirections directions() { return TickCapability.this.directions(); }
                public Set<Class<? extends CapabilityFacet>> facets() { return Set.of(TickFacet.class); }
            };
        }
        public CapabilityOperation prepare(CapabilityRequest request) { return () -> CapabilityResult.successful(); }
        public CapabilityTickResult plan(CapabilityTickContext context) { return planner.apply(context); }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class TestCapabilityHost extends BlockEntity implements CapabilityHost {
        private final CapabilitySnapshot snapshot;

        private TestCapabilityHost(BlockEntity block, List<MachineCapability> capabilities) {
            super(block.getType(), block.getBlockPos(), block.getBlockState());
            snapshot = new CapabilitySnapshot(capabilities);
        }

        public CapabilitySnapshot capabilitySnapshot() { return snapshot; }
    }
}
