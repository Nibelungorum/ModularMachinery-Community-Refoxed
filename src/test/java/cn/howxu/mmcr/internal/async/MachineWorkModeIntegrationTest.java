package cn.howxu.mmcr.internal.async;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.event.SharedIoEvents;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeThread;
import cn.howxu.mmcr.internal.recipe.MachineRecipeThread;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.InstalledRecipeProgram;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.ConfigTestSupport;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the controller execution modes at the server-level tick boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineWorkModeIntegrationTest {
    private ServerThreadTestScope serverThreadScope;
    private ServerLevel level;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        CommentedConfig config = CommentedConfig.inMemory();
        ServerConfig.SPEC.correct(config);
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig")
                .getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        ServerConfig.SPEC.acceptConfig((IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null));
    }

    @BeforeEach
    void installServerThread() throws Exception {
        serverThreadScope = ServerThreadTestScope.open();
    }

    @AfterEach
    void discardLevelCoordinators() throws Exception {
        try {
            ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
            RecipeRegistry.clearForTesting();
            if (level == null) return;
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        } finally {
            if (serverThreadScope != null) serverThreadScope.close();
        }
    }

    @Test
    void only_the_real_server_ticker_grants_the_current_round_permit() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, new DynamicMachine(MMCR.id("test_cube"), "ticker permit",
                new BlockArray(Map.of())));
        level = (ServerLevel) controller.getLevel();
        SharedIoCoordinator coordinator = SharedIoCoordinator.get(level);
        long round = RuntimeTestFixtures.beginLevelTick(level, true);
        controller.tickStructure(level, controller.getBlockPos());
        var permit = coordinator.controllerPermit(controller.getBlockPos());
        assertThat(permit.eligible(round, level.getGameTime(), true)).isFalse();

        controller.serverTick();
        permit = coordinator.controllerPermit(controller.getBlockPos());
        assertThat(permit.eligible(round, level.getGameTime(), true)).isTrue();
        SharedIoEvents.completeLevelTick(level);
        SharedIoEvents.completeLevelTick(level);
        assertThat(coordinator.executionRoundId()).isEqualTo(round);

        // A loaded controller not called by the chunk ticker gets no new-round qualification.
        long unticked = RuntimeTestFixtures.beginLevelTick(level, true);
        assertThat(permit.eligible(unticked, level.getGameTime(), true)).isFalse();
        SharedIoEvents.completeLevelTick(level);
        assertThat(coordinator.executionRoundId()).isEqualTo(unticked);
    }

    @Test
    void duplicate_production_post_does_not_repeat_a_qualified_shared_io_commit() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, new DynamicMachine(MMCR.id("test_cube"), "duplicate post",
                new BlockArray(Map.of())));
        level = (ServerLevel) controller.getLevel();
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        assertThat(registry.claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        var domain = registry.domainFor(controller.getBlockPos());
        SharedIoCoordinator coordinator = SharedIoCoordinator.get(level);
        long round = RuntimeTestFixtures.beginLevelTick(level, true);
        controller.serverTick();
        var permit = coordinator.controllerPermit(controller.getBlockPos());
        AtomicInteger consumed = new AtomicInteger();
        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), "base"), domain.id(), 0L,
                () -> { consumed.incrementAndGet(); return true; },
                () -> permit.eligible(coordinator.executionRoundId(), level.getGameTime(),
                        level.tickRateManager().runsNormally()), domain::id, () -> 0L));

        SharedIoEvents.onLevelTick(new LevelTickEvent.Post(() -> true, level));
        assertThat(consumed).hasValue(1);
        SharedIoEvents.onLevelTick(new LevelTickEvent.Post(() -> true, level));

        assertThat(consumed).hasValue(1);
        assertThat(coordinator.executionRoundId()).isEqualTo(round);
    }

    @Test
    void frozen_round_rejects_even_an_explicit_server_ticker_call() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, new DynamicMachine(MMCR.id("test_cube"), "frozen permit",
                new BlockArray(Map.of())));
        level = (ServerLevel) controller.getLevel();
        SharedIoCoordinator coordinator = SharedIoCoordinator.get(level);
        RuntimeTestFixtures.beginLevelTick(level, true);
        controller.serverTick();
        var permit = coordinator.controllerPermit(controller.getBlockPos());

        long frozen = RuntimeTestFixtures.beginLevelTick(level, false);
        controller.serverTick();
        assertThat(permit.eligible(frozen, level.getGameTime(), true)).isFalse();
        SharedIoEvents.completeLevelTick(level);
        SharedIoEvents.completeLevelTick(level);
        assertThat(coordinator.executionRoundId()).isEqualTo(frozen);
        long resumed = RuntimeTestFixtures.beginLevelTick(level, true);
        assertThat(permit.eligible(resumed, level.getGameTime(), true)).isFalse();
    }

    @Test
    void rebuilt_controller_at_the_same_position_cannot_reuse_the_old_instance_permit() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, new DynamicMachine(MMCR.id("test_cube"), "instance permit",
                new BlockArray(Map.of())));
        level = (ServerLevel) controller.getLevel();
        SharedIoCoordinator coordinator = SharedIoCoordinator.get(level);
        long round = RuntimeTestFixtures.beginLevelTick(level, true);
        controller.serverTick();
        var previous = coordinator.controllerPermit(controller.getBlockPos());
        MachineControllerBlockEntity replacement = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.replaceBlockEntity(controller, replacement);

        replacement.serverTick();
        var current = coordinator.controllerPermit(replacement.getBlockPos());

        assertThat(current).isNotSameAs(previous);
        assertThat(previous.eligible(round, level.getGameTime(), true)).isFalse();
        assertThat(current.eligible(round, level.getGameTime(), true)).isTrue();
    }

    @Test
    void removed_controller_cannot_acquire_a_current_round_permit() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, new DynamicMachine(MMCR.id("test_cube"), "removed permit",
                new BlockArray(Map.of())));
        level = (ServerLevel) controller.getLevel();
        SharedIoCoordinator coordinator = SharedIoCoordinator.get(level);
        RuntimeTestFixtures.beginLevelTick(level, true);
        controller.serverTick();
        var previous = coordinator.controllerPermit(controller.getBlockPos());
        controller.setRemoved();
        long round = RuntimeTestFixtures.beginLevelTick(level, true);

        controller.serverTick();

        assertThat(previous.eligible(round, level.getGameTime(), true)).isFalse();
        assertThat(coordinator.controllerPermit(controller.getBlockPos()).eligible(round, level.getGameTime(), true))
                .isFalse();
    }

    @Test
    void work_mode_change_replaces_the_permit_with_current_real_ticker_qualification() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, new DynamicMachine(MMCR.id("test_cube"), "mode permit",
                new BlockArray(Map.of())));
        level = (ServerLevel) controller.getLevel();
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.SYNC);
        long round = RuntimeTestFixtures.beginLevelTick(level, true);
        controller.serverTick();
        SharedIoCoordinator coordinator = SharedIoCoordinator.get(level);
        var previous = coordinator.controllerPermit(controller.getBlockPos());
        assertThat(previous.eligible(round, level.getGameTime(), true)).isTrue();

        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        controller.serverTick();
        var replacement = coordinator.controllerPermit(controller.getBlockPos());
        SharedIoEvents.completeLevelTick(level);
        SharedIoEvents.completeLevelTick(level);

        assertThat(replacement).isNotSameAs(previous);
        assertThat(previous.eligible(round, level.getGameTime(), true)).isFalse();
        assertThat(replacement.eligible(round, level.getGameTime(), true)).isTrue();
        assertThat(coordinator.executionRoundId()).isEqualTo(round);
    }

    @ParameterizedTest
    @MethodSource("modeSwitches")
    void unpaid_mode_switch_consumes_in_the_new_modes_real_execution_boundary(MachineWorkMode from, MachineWorkMode to)
            throws Exception {
        ModeSwitchFixture fixture = modeSwitchFixture(from);
        long afterStart = fixture.input().energyStorage().getAmountAsLong();
        RuntimeTestFixtures.advanceGameTime(level);
        long round = RuntimeTestFixtures.beginLevelTick(level, true);
        var previousPermit = fixture.shared().controllerPermit(fixture.controller().getBlockPos());
        ConfigTestSupport.setMachineWorkMode(to);

        fixture.controller().serverTick();

        assertThat(previousPermit.eligible(round, level.getGameTime(), true)).isFalse();
        assertThat(fixture.shared().controllerPermit(fixture.controller().getBlockPos())
                .eligible(round, level.getGameTime(), true)).isTrue();
        assertThat(fixture.controller().activeWorkMode()).isEqualTo(to);
        // SYNC must pay inside this BE call, not at Post or a later tick.
        int paidBeforePost = to == MachineWorkMode.SYNC ? 1 : 0;
        assertThat(fixture.runtime().tickCount()).isEqualTo(paidBeforePost);
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterStart - 7L * paidBeforePost);
        completeModeSwitchPost(fixture);
        int paidAfterPost = to == MachineWorkMode.ASYNC ? 0 : 1;
        assertThat(fixture.runtime().tickCount()).isEqualTo(paidAfterPost);
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterStart - 7L * paidAfterPost);

        // ASYNC's compiled rebind keeps its existing worker boundary; next real ticker prepares it.
        RuntimeTestFixtures.advanceGameTime(level);
        RuntimeTestFixtures.beginLevelTick(level, true);
        fixture.controller().serverTick();
        completeModeSwitchPost(fixture);
        assertThat(fixture.runtime().tickCount()).isEqualTo(paidAfterPost + 1);
        long after = fixture.input().energyStorage().getAmountAsLong();
        assertThat(after).isEqualTo(afterStart - 7L * (paidAfterPost + 1));
        completeModeSwitchPost(fixture);
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(after);
    }

    @ParameterizedTest
    @MethodSource("modeSwitches")
    void paid_mode_switch_preserves_the_same_tick_resource_fence(MachineWorkMode from, MachineWorkMode to)
            throws Exception {
        ModeSwitchFixture fixture = modeSwitchFixture(from);
        long afterStart = fixture.input().energyStorage().getAmountAsLong();
        RuntimeTestFixtures.advanceGameTime(level);
        RuntimeTestFixtures.beginLevelTick(level, true);
        fixture.controller().serverTick();
        completeModeSwitchPost(fixture);
        assertThat(fixture.runtime().tickCount()).isEqualTo(1);
        long afterPaid = fixture.input().energyStorage().getAmountAsLong();
        assertThat(afterPaid).isEqualTo(afterStart - 7L);
        var ledger = fixture.runtime().ledger();
        long paidAttempts = ledger.state().paidAttempts();
        ConfigTestSupport.setMachineWorkMode(to);

        fixture.controller().serverTick();
        completeModeSwitchPost(fixture);

        assertThat(fixture.runtime().ledger()).isSameAs(ledger);
        assertThat(ledger.state().paidAttempts()).isEqualTo(paidAttempts);
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterPaid);
        assertThat(fixture.runtime().tickCount()).isEqualTo(1);
        RuntimeTestFixtures.advanceGameTime(level);
        RuntimeTestFixtures.beginLevelTick(level, true);
        fixture.controller().serverTick();
        completeModeSwitchPost(fixture);
        assertThat(fixture.runtime().tickCount()).isEqualTo(2);
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterPaid - 7L);
    }

    @ParameterizedTest
    @MethodSource("modeSwitches")
    void debug_mode_switch_never_grants_a_permit_or_consumes_resources(MachineWorkMode from, MachineWorkMode to)
            throws Exception {
        ModeSwitchFixture fixture = modeSwitchFixture(from);
        long afterStart = fixture.input().energyStorage().getAmountAsLong();
        RuntimeTestFixtures.advanceGameTime(level);
        long round = RuntimeTestFixtures.beginLevelTick(level, true);
        ConfigTestSupport.setMachineWorkMode(to);

        fixture.controller().tickRuntimeWork(level, fixture.controller().getBlockPos());
        completeModeSwitchPost(fixture);

        assertThat(fixture.shared().controllerPermit(fixture.controller().getBlockPos())
                .eligible(round, level.getGameTime(), true)).isFalse();
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterStart);
        assertThat(fixture.runtime().tickCount()).isZero();
        assertThat(fixture.controller().activeWorkMode()).isEqualTo(to);
        // The debug call changes mode, but only this later real ticker can authorize consumption.
        fixture.controller().serverTick();
        assertThat(fixture.shared().controllerPermit(fixture.controller().getBlockPos())
                .eligible(round, level.getGameTime(), true)).isTrue();
        if (to == MachineWorkMode.SYNC) {
            assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterStart - 7L);
            assertThat(fixture.runtime().tickCount()).isEqualTo(1);
        }
    }

    @Test
    void level_unload_invalidates_the_old_coordinator_permit() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, new DynamicMachine(MMCR.id("test_cube"), "unloaded permit",
                new BlockArray(Map.of())));
        level = (ServerLevel) controller.getLevel();
        SharedIoCoordinator previous = SharedIoCoordinator.get(level);
        long round = RuntimeTestFixtures.beginLevelTick(level, true);
        controller.serverTick();
        var permit = previous.controllerPermit(controller.getBlockPos());
        assertThat(permit.eligible(round, level.getGameTime(), true)).isTrue();

        SharedIoEvents.onLevelUnload(new LevelEvent.Unload(level));
        SharedIoCoordinator replacement = SharedIoCoordinator.get(level);
        RuntimeTestFixtures.beginLevelTick(level, true);

        assertThat(replacement).isNotSameAs(previous);
        assertThat(previous.executionRoundId()).isZero();
        assertThat(permit.eligible(round, level.getGameTime(), true)).isFalse();
        assertThat(replacement.controllerPermit(controller.getBlockPos())).isNotSameAs(permit);
    }

    @Test
    void async_main_steps_complete_before_shared_io_round_robin_commits() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, new DynamicMachine(MMCR.id("test_cube"), "tick fence",
                new BlockArray(Map.of())));
        level = (ServerLevel) controller.getLevel();
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        assertThat(registry.claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        StructureClaimRegistry.ResourceDomain domain = registry.domainFor(controller.getBlockPos());
        assertThat(domain).isNotNull();
        List<String> order = new ArrayList<>();
        MachineAsyncCoordinator async = MachineAsyncCoordinator.forTesting(Runnable::run,
                ServerConfig.asyncMainThreadStepsPerLevelTick(), false);
        Field coordinators = MachineAsyncCoordinator.class.getDeclaredField("COORDINATORS");
        coordinators.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<ServerLevel, MachineAsyncCoordinator> installed =
                (Map<ServerLevel, MachineAsyncCoordinator>) coordinators.get(null);
        installed.put(level, async);
        RuntimeTestFixtures.beginLevelTick(level, true);
        controller.serverTick();

        assertThat(async.submit(new MachineAsyncCoordinator.TaskKey(controller.getBlockPos(),
                level.getGameTime(), controller.activeWorkMode(), "fence-order", controller.lifecycleEpoch()),
                ignored -> AsyncContinuation.Yield.mainThread(
                new MainThreadStep.TestStep(() -> order.add("async-main-step")),
                result -> context -> AsyncContinuation.Yield.complete()))).isTrue();
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.TickRequest(domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), "base"), domain.id(), 0L,
                () -> {
                    order.add("shared-io-resolve");
                    return true;
                }, () -> true, domain::id, () -> 0L));

        assertThat(async.hasPendingMainStepForTesting()).isTrue();
        assertThat(order).isEmpty();
        SharedIoEvents.completeLevelTick(level);

        assertThat(order).containsExactly("async-main-step", "shared-io-resolve");
        SharedIoEvents.completeLevelTick(level);
        assertThat(order).containsExactly("async-main-step", "shared-io-resolve");
    }

    @Test
    void redstone_pause_cancels_an_uncommitted_async_lane() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, new DynamicMachine(MMCR.id("test_cube"), "redstone pause",
                new BlockArray(Map.of())));
        level = (ServerLevel) controller.getLevel();
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        controller.tickRuntimeWork(level, controller.getBlockPos());
        CountDownLatch mainStepQueued = new CountDownLatch(1);
        AtomicBoolean committed = new AtomicBoolean();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.get(level);
        assertThat(coordinator.submit(new MachineAsyncCoordinator.TaskKey(controller.getBlockPos(), 2L), ignored -> {
            mainStepQueued.countDown();
            return AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> committed.set(true)),
                    result -> context -> AsyncContinuation.Yield.complete());
        })).isTrue();
        assertThat(mainStepQueued.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(hasPendingMainStep(coordinator)).isTrue();

        RuntimeTestFixtures.setDirectSignal(level, controller.getBlockPos(), 15);
        controller.tickRuntimeWork(level, controller.getBlockPos());
        coordinator.pumpMainThreadSteps();

        assertThat(controller.isRedstonePaused()).isTrue();
        assertThat(committed).isFalse();
    }

    @Test
    void level_discard_cancels_pending_async_tasks() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, new DynamicMachine(MMCR.id("test_cube"), "level unload",
                new BlockArray(Map.of())));
        level = (ServerLevel) controller.getLevel();
        AtomicBoolean committed = new AtomicBoolean();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.get(level);
        assertThat(coordinator.submit(new MachineAsyncCoordinator.TaskKey(controller.getBlockPos(), 1L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> committed.set(true)),
                        result -> context -> AsyncContinuation.Yield.complete()))).isTrue();
        assertThat(hasPendingMainStep(coordinator)).isTrue();

        MachineAsyncCoordinator.discard(level);
        coordinator.pumpMainThreadSteps();

        assertThat(committed).isFalse();
    }

    @Test
    void level_unload_event_cancels_pending_async_tasks() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, new DynamicMachine(MMCR.id("test_cube"), "level unload event",
                new BlockArray(Map.of())));
        level = (ServerLevel) controller.getLevel();
        AtomicBoolean committed = new AtomicBoolean();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.get(level);
        assertThat(coordinator.submit(new MachineAsyncCoordinator.TaskKey(controller.getBlockPos(), 1L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> committed.set(true)),
                        result -> context -> AsyncContinuation.Yield.complete()))).isTrue();
        assertThat(hasPendingMainStep(coordinator)).isTrue();

        SharedIoEvents.onLevelUnload(new LevelEvent.Unload(level));
        coordinator.pumpMainThreadSteps();

        assertThat(committed).isFalse();
    }

    @Test
    void structure_invalidation_cancels_an_uncommitted_async_lane() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, new DynamicMachine(MMCR.id("test_cube"), "structure invalidation",
                new BlockArray(Map.of())));
        level = (ServerLevel) controller.getLevel();
        AtomicBoolean committed = new AtomicBoolean();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.get(level);
        assertThat(coordinator.submit(new MachineAsyncCoordinator.TaskKey(controller.getBlockPos(), 1L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> committed.set(true)),
                        result -> context -> AsyncContinuation.Yield.complete()))).isTrue();
        assertThat(hasPendingMainStep(coordinator)).isTrue();

        controller.invalidateFormedStructure();
        coordinator.pumpMainThreadSteps();

        assertThat(committed).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = MachineWorkMode.class, names = {"ASYNC", "SEMI_SYNC"})
    void attached_base_start_uses_one_production_level_post_without_worker_phase_round_trips(MachineWorkMode mode)
            throws Exception {
        ResourceLocation machineId = MMCR.id("test_cube");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(machineId, BlockPos.ZERO);
        RuntimeTestFixtures.registerRecipePool(machineId);
        List<String> phases = new ArrayList<>();
        Thread serverThread = Thread.currentThread();
        RuntimeTestFixtures.formStructure(controller, normalMachine(machineId, RecipeBehavior.builder()
                .beforeStart(context -> {
                    assertThat(Thread.currentThread()).isSameAs(serverThread);
                    phases.add("before-start");
                }).build(), true));
        attachFactoryScheduler(controller);
        level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        ConfigTestSupport.setMachineWorkMode(mode);
        Queue<Runnable> workers = new java.util.ArrayDeque<>();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(workers::add, 3, false);
        Field coordinators = MachineAsyncCoordinator.class.getDeclaredField("COORDINATORS");
        coordinators.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<ServerLevel, MachineAsyncCoordinator> installed = (Map<ServerLevel, MachineAsyncCoordinator>) coordinators.get(null);
        installed.put(level, coordinator);
        FactoryRecipeThread lane = factoryBaseLane(controller);
        RuntimeTestFixtures.beginLevelTick(level, true);
        controller.serverTick();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("production_forwarded_start"), machineId, 20,
                List.of(), List.of());
        RecipeRegistry.registerStatic(recipe);
        assertThat(lane.searchAndStartRecipe(List.of(recipe), 1L, controller.currentStructureSnapshot().version())).isTrue();
        if (mode == MachineWorkMode.ASYNC) {
            assertThat(phases).isEmpty();
            assertThat(workers).hasSize(1);
            workers.remove().run();
        }

        SharedIoEvents.completeLevelTick(level);

        assertThat(lane.runtime().active()).isTrue();
        assertThat(phases).containsExactly("before-start");
        if (mode == MachineWorkMode.ASYNC) {
            // Initial compilation and receipt draining are boundary work, not repeated start phases or tick plans.
            Field tasksField = MachineAsyncCoordinator.class.getDeclaredField("tasks");
            tasksField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var tasks = (Map<MachineAsyncCoordinator.TaskKey, ?>) tasksField.get(coordinator);
            assertThat(tasks.keySet()).allMatch(key -> key.laneId().equals("base")
                    || key.laneId().equals("program/base"));
            assertThat(tasks.keySet()).anyMatch(key -> key.laneId().equals("program/base"));
            assertThat(workers).hasSize(1 + queuedReceiptWorkers(coordinator));
        }
        SharedIoEvents.completeLevelTick(level);
        assertThat(phases).containsExactly("before-start");
        assertThat(lane.runtime().tickCount()).isZero();
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void work_modes_produce_the_same_observable_recipe_progress(MachineWorkMode mode) {
        ResourceLocation machineId = MMCR.id("test_cube");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.registerRecipePool(machineId);
        RuntimeTestFixtures.formStructure(controller, new DynamicMachine(machineId, "work mode recipe progress",
                new BlockArray(Map.of())));
        level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("work_mode_recipe_progress"), machineId, 20,
                List.of(), List.of());
        RecipeRegistry.registerStatic(recipe);
        ConfigTestSupport.setMachineWorkMode(mode);

        tickAndComplete(controller);
        tickAndComplete(controller);

        assertThat(controller.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());
        assertThat(controller.runtimeSnapshot().crafting().status().isCrafting()).isTrue();
        assertThat(controller.runtimeSnapshot().crafting().tick()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void submitted_recipe_tick_is_surrounded_by_server_tick_hooks(MachineWorkMode mode) {
        List<String> phases = new ArrayList<>();
        ResourceLocation machineId = MMCR.id("test_cube");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(machineId, BlockPos.ZERO);
        RuntimeTestFixtures.registerRecipePool(machineId);
        RuntimeTestFixtures.formStructure(controller, normalMachine(machineId, RecipeBehavior.builder()
                .preServerTick(context -> phases.add("pre"))
                .recipeTick(context -> phases.add("recipe"))
                .postServerTick(context -> phases.add("post"))
                .build()));
        level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        RecipeRegistry.registerStatic(RecipeTestSupport.create(MMCR.id("callback_order_recipe"), machineId, 20,
                List.of(), List.of()));
        ConfigTestSupport.setMachineWorkMode(mode);

        RuntimeTestFixtures.beginLevelTick(level, true);
        controller.serverTick();
        completeAsyncLevelTick(level);
        assertThat(controller.runtimeSnapshot().crafting().status().isCrafting())
                .as("mode=%s state=%s", mode, controller.runtimeSnapshot().crafting())
                .isTrue();
        phases.clear();
        RuntimeTestFixtures.advanceGameTime(level);
        RuntimeTestFixtures.beginLevelTick(level, true);
        controller.serverTick();

        assertThat(phases).containsSubsequence("pre", "recipe", "post");
        assertThat(phases.indexOf("recipe")).isLessThan(phases.indexOf("post"));
    }

    @ParameterizedTest
    @EnumSource(value = MachineWorkMode.class, names = {"ASYNC", "SEMI_SYNC"})
    void cancelled_tick_does_not_repeat_preparation_until_world_time_advances(MachineWorkMode mode) {
        AtomicInteger callbacks = new AtomicInteger();
        ResourceLocation machineId = MMCR.id("test_cube");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(machineId, BlockPos.ZERO);
        RuntimeTestFixtures.registerRecipePool(machineId);
        RuntimeTestFixtures.formStructure(controller, normalMachine(machineId, RecipeBehavior.builder()
                .recipeTick(context -> callbacks.incrementAndGet()).build()));
        level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        ConfigTestSupport.setMachineWorkMode(mode);
        MachineRecipeThread thread = new MachineRecipeThread(controller);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("cancelled_tick_preparation"), machineId, 20,
                List.of(), List.of());
        assertThat(thread.runtime().start(recipe, 1).isCrafting()).isTrue();

        thread.tick();
        assertThat(callbacks).hasValue(1);
        thread.cancelAsyncState();
        thread.tick();
        assertThat(callbacks).hasValue(1);
        completeAsyncLevelTick(level);
        assertThat(thread.runtime().tickCount()).isZero();

        RuntimeTestFixtures.advanceGameTime(level);
        thread.tick();
        completeAsyncLevelTick(level);
        assertThat(callbacks).hasValue(2);
        assertThat(thread.runtime().tickCount()).isEqualTo(1);
        thread.tick();
        assertThat(callbacks).hasValue(2);
        completeAsyncLevelTick(level);
        assertThat(thread.runtime().tickCount()).isEqualTo(1);
    }

    @Test
    void reset_machine_cancels_an_uncommitted_async_lane() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, new DynamicMachine(MMCR.id("test_cube"), "ordinary reset",
                new BlockArray(Map.of())));
        level = (ServerLevel) controller.getLevel();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.get(level);
        AtomicBoolean committed = new AtomicBoolean();
        assertThat(coordinator.submit(new MachineAsyncCoordinator.TaskKey(controller.getBlockPos(), 1L), ignored ->
                AsyncContinuation.Yield.mainThread(new MainThreadStep.TestStep(() -> committed.set(true)),
                        result -> context -> AsyncContinuation.Yield.complete()))).isTrue();
        assertThat(hasPendingMainStep(coordinator)).isTrue();

        controller.onMachineDestroyed();
        coordinator.pumpMainThreadSteps();

        assertThat(committed).isFalse();
    }

    @Test
    void redstone_pause_cancels_recipe_thread_shared_io_start_before_its_fence_commits() throws Exception {
        assertLifecycleInterruptCancelsRecipeThreadSharedIo(controller -> {
            RuntimeTestFixtures.setDirectSignal(level, controller.getBlockPos(), 15);
            controller.tickRuntimeWork(level, controller.getBlockPos());
        });
    }

    @Test
    void reset_cancels_recipe_thread_shared_io_start_before_its_fence_commits() throws Exception {
        assertLifecycleInterruptCancelsRecipeThreadSharedIo(MachineControllerBlockEntity::invalidateFormedStructure);
    }

    @Test
    void sync_starts_the_recipe_in_the_originating_main_tick() {
        ResourceLocation machineId = MMCR.id("test_cube");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(machineId, BlockPos.ZERO);
        RuntimeTestFixtures.registerRecipePool(machineId);
        RuntimeTestFixtures.formStructure(controller, new DynamicMachine(machineId, "sync main tick",
                new BlockArray(Map.of())));
        level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("sync_main_tick_recipe"), machineId, 20,
                List.of(), List.of());
        RecipeRegistry.registerStatic(recipe);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.SYNC);

        controller.tickRuntimeWork(level, controller.getBlockPos());

        assertThat(controller.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());
        assertThat(controller.runtimeSnapshot().crafting().status().isCrafting()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void normal_controller_schedules_start_tick_and_finish_by_work_mode(MachineWorkMode mode) throws Exception {
        ResourceLocation machineId = MMCR.id("test_cube");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(machineId, BlockPos.ZERO);
        RuntimeTestFixtures.registerRecipePool(machineId);
        List<String> phases = new ArrayList<>();
        RuntimeTestFixtures.formStructure(controller, normalMachine(machineId, RecipeBehavior.builder()
                .beforeStart(context -> phases.add("start"))
                .recipeTick(context -> phases.add("tick"))
                .beforeFinish(context -> phases.add("finish"))
                .build()));
        level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("normal_mode_lifecycle"), machineId, 2,
                List.of(), List.of());
        RecipeRegistry.registerStatic(recipe);
        ConfigTestSupport.setMachineWorkMode(mode);

        RuntimeTestFixtures.beginLevelTick(level, true);
        controller.serverTick();
        if (mode == MachineWorkMode.ASYNC) {
            assertThat(hasPendingMainStep(MachineAsyncCoordinator.get(level))).isTrue();
        } else {
            assertThat(controller.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());
        }
        completeAsyncLevelTick(level);
        assertThat(controller.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());

        RuntimeTestFixtures.advanceGameTime(level);
        RuntimeTestFixtures.beginLevelTick(level, true);
        controller.serverTick();
        assertThat(phases).doesNotContain("finish");
        if (mode == MachineWorkMode.SYNC) {
            assertThat(controller.runtimeSnapshot().crafting().tick()).isEqualTo(1);
        } else {
            assertThat(controller.runtimeSnapshot().crafting().tick()).isZero();
            SharedIoCoordinator.get(level).resolve(level);
            assertThat(controller.runtimeSnapshot().crafting().tick()).isEqualTo(1);
        }
        completeAsyncLevelTick(level);
        assertThat(controller.runtimeSnapshot().crafting().tick()).isEqualTo(1);

        RuntimeTestFixtures.advanceGameTime(level);
        RuntimeTestFixtures.beginLevelTick(level, true);
        controller.serverTick();
        if (mode != MachineWorkMode.SYNC) assertThat(phases).doesNotContain("finish");
        completeAsyncLevelTick(level);
        assertThat(phases.stream().filter("finish"::equals).count()).isEqualTo(1L);
        completeAsyncLevelTick(level);
        assertThat(phases.stream().filter("finish"::equals).count()).isEqualTo(1L);
        assertThat(phases).containsSubsequence("start", "tick", "finish");
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void normal_controller_continues_the_last_recipe_without_an_idle_boundary(MachineWorkMode mode)
            throws ReflectiveOperationException {
        assertNormalControllerContinuesLastRecipe(mode);
    }

    private void assertNormalControllerContinuesLastRecipe(MachineWorkMode mode) throws ReflectiveOperationException {
        ResourceLocation machineId = MMCR.id("test_cube");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(machineId, BlockPos.ZERO);
        RuntimeTestFixtures.registerRecipePool(machineId);
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger finishes = new AtomicInteger();
        RuntimeTestFixtures.formStructure(controller, normalMachine(machineId, RecipeBehavior.builder()
                .beforeStart(context -> starts.incrementAndGet())
                .beforeFinish(context -> finishes.incrementAndGet())
                .build()));
        level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("normal_last_recipe_continuation"), machineId, 1,
                List.of(), List.of());
        RecipeRegistry.registerStatic(recipe);
        ConfigTestSupport.setMachineWorkMode(mode);
        Field threadField = MachineControllerBlockEntity.class.getDeclaredField("normalRecipeThread");
        threadField.setAccessible(true);
        MachineRecipeThread thread = (MachineRecipeThread) threadField.get(controller);

        RuntimeTestFixtures.beginLevelTick(level, true);
        controller.serverTick();
        completeAsyncLevelTick(level);
        assertThat(controller.runtimeSnapshot().crafting().status().isCrafting()).isTrue();
        assertThat(starts).hasValue(1);
        assertThat(finishes).hasValue(0);
        for (int tick = 0; tick < 6 && starts.get() < 2; tick++) {
            RuntimeTestFixtures.advanceGameTime(level);
            RuntimeTestFixtures.beginLevelTick(level, true);
            controller.serverTick();
            completeAsyncLevelTick(level);
            CraftingRuntime active = RuntimeTestFixtures.craftingRuntime(controller);
            assertThat(active.failure()).as("mode=%s starts=%s", mode, starts.get()).isNull();
            assertThat(active.active()).as("mode=%s starts=%s pending restart", mode, starts.get()).isTrue();
            assertThat(controller.runtimeSnapshot().crafting().status().isCrafting())
                    .as("mode=%s starts=%s state=%s", mode, starts.get(),
                            controller.runtimeSnapshot().crafting())
                    .isTrue();
            assertThat(controller.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());
        }

        assertThat(starts).hasValue(2);
        assertThat(finishes).hasValue(1);
        assertThat(RuntimeTestFixtures.craftingRuntime(controller).tickCount()).isZero();
        assertThat(thread.isStartPending()).isFalse();
        completeAsyncLevelTick(level);
        assertThat(starts).hasValue(2);
        assertThat(finishes).hasValue(1);
        assertThat(RuntimeTestFixtures.craftingRuntime(controller).active()).isTrue();
        assertThat(RuntimeTestFixtures.craftingRuntime(controller).tickCount()).isZero();
    }

    private static DynamicMachine normalMachine(ResourceLocation machineId, RecipeBehavior behavior) {
        return normalMachine(machineId, behavior, false);
    }

    private static DynamicMachine normalMachine(ResourceLocation machineId, RecipeBehavior behavior, boolean factory) {
        return new DynamicMachine(machineId, "normal mode lifecycle", new BlockArray(Map.of()),
                MachineControllerSpec.defaultsFor(machineId), MachineAppearanceSpec.defaults(), PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), Map.of(), 1, false, factory, 1, List.of(),
                MachineRole.NORMAL, Set.of(), List.of(), RecipeFailureActions.getDefaultAction(), behavior);
    }

    private static void attachFactoryScheduler(MachineControllerBlockEntity controller) {
        var scheduler = new FactorySchedulerBlockEntity(new BlockPos(4, 0, 0),
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.replaceBlockEntity(controller, scheduler);
        controller.componentRuntime().replaceComponents(List.of(new ProcessingComponent(null, scheduler,
                scheduler.getBlockPos(), scheduler.getBlockPos(), (String) null)));
        RuntimeTestFixtures.republish(controller);
        assertThat(controller.hasFactoryController()).isTrue();
    }

    private static Stream<Arguments> modeSwitches() {
        return Arrays.stream(MachineWorkMode.values()).flatMap(from -> Arrays.stream(MachineWorkMode.values())
                .filter(to -> to != from).map(to -> Arguments.of(from, to)));
    }

    @SuppressWarnings("unchecked")
    private ModeSwitchFixture modeSwitchFixture(MachineWorkMode mode) throws Exception {
        TestBootstrap.bootstrapCapabilities();
        RecipeRegistry.clearForTesting();
        ConfigTestSupport.setMachineWorkMode(mode);
        ResourceLocation machineId = MMCR.id("test_cube");
        RuntimeTestFixtures.registerRecipePool(machineId);
        EnergyInputHatchBlockEntity input = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(machineId, BlockPos.ZERO);
        RuntimeTestFixtures.formStructureWithComponents(controller, normalMachine(machineId, RecipeBehavior.defaults()), input);
        controller.componentRuntime().replaceComponents(List.of(new ProcessingComponent(
                new MachineComponent(input.kind(), input.ioType()), input, input.getBlockPos(), input.getBlockPos(), (String) null)));
        RuntimeTestFixtures.republish(controller);
        level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        ArrayDeque<Runnable> workers = new ArrayDeque<>();
        MachineAsyncCoordinator async = MachineAsyncCoordinator.forTesting(workers::addLast);
        Field coordinators = MachineAsyncCoordinator.class.getDeclaredField("COORDINATORS");
        coordinators.setAccessible(true);
        ((Map<ServerLevel, MachineAsyncCoordinator>) coordinators.get(null)).put(level, async);
        RuntimeTestFixtures.beginLevelTick(level, true);
        controller.serverTick();
        CraftingRuntime runtime = RuntimeTestFixtures.craftingRuntime(controller);
        SharedIoCoordinator shared = SharedIoCoordinator.get(level);
        input.energyStorage().setAmount(1_000L);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("mode_switch_energy_" + mode.name().toLowerCase()),
                machineId, 40, List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 7L)), List.of());
        assertThat(shared.reserveExecution(runtime, "base")).isTrue();
        assertThat(runtime.start(recipe, 1L).isCrafting()).isTrue();
        InstalledRecipeProgram program = runtime.ensureInstalledProgram();
        assertThat(program).isNotNull();
        assertThat(shared.install(program, shared.controllerPermit(controller.getBlockPos()))).isTrue();
        return new ModeSwitchFixture(controller, input, runtime, shared, async, workers);
    }

    private void completeModeSwitchPost(ModeSwitchFixture fixture) {
        drainModeSwitchWorkers(fixture);
        SharedIoEvents.completeLevelTick(level);
        drainModeSwitchWorkers(fixture);
    }

    private static void drainModeSwitchWorkers(ModeSwitchFixture fixture) {
        while (!fixture.workers().isEmpty() || fixture.async().hasPendingMainStepForTesting()) {
            while (!fixture.workers().isEmpty()) fixture.workers().removeFirst().run();
            fixture.async().pumpMainThreadSteps();
        }
    }

    /** Attached installed runtime, real FE storage and a deterministic boundary worker queue.
     * @author howxu <dev@howxu.cn>
     */
    private record ModeSwitchFixture(MachineControllerBlockEntity controller, EnergyInputHatchBlockEntity input,
                                     CraftingRuntime runtime, SharedIoCoordinator shared,
                                     MachineAsyncCoordinator async, ArrayDeque<Runnable> workers) { }

    private static void tickAndComplete(MachineControllerBlockEntity controller) {
        ServerLevel level = (ServerLevel) controller.getLevel();
        RuntimeTestFixtures.beginLevelTick(level, true);
        controller.serverTick();
        completeAsyncLevelTick(level);
        RuntimeTestFixtures.advanceGameTime(level);
    }

    private static void completeAsyncLevelTick(ServerLevel level) {
        SharedIoCoordinator sharedIo = SharedIoCoordinator.get(level);
        MachineAsyncCoordinator async = MachineAsyncCoordinator.get(level);
        long gameTime = level.getGameTime();
        sharedIo.drainReceiptAcknowledgements(level);
        sharedIo.beginLevelTick(gameTime);
        async.beginLevelTick(gameTime);
        sharedIo.resolve(level);
        async.completeUntilIdleForTesting(() -> sharedIo.resolve(level));
        // Installed finish can enqueue a restart without creating any worker batch.
        // completeUntilIdle only waits for tasks, so close the remaining shared-IO fence explicitly.
        sharedIo.resolve(level);
        async.pumpReceiptDrains();
        MachineControllerBlockEntity.flushQueuedAsyncRuntimeState(level);
    }


    private void assertLifecycleInterruptCancelsRecipeThreadSharedIo(
            java.util.function.Consumer<MachineControllerBlockEntity> interrupt) throws Exception {
        ResourceLocation machineId = MMCR.id("test_cube");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(machineId, BlockPos.ZERO);
        RuntimeTestFixtures.registerRecipePool(machineId);
        RuntimeTestFixtures.formStructure(controller, normalMachine(machineId, RecipeBehavior.defaults(), true));
        attachFactoryScheduler(controller);
        level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        controller.tickRuntimeWork(level, controller.getBlockPos());
        FactoryRecipeThread thread = factoryBaseLane(controller);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("recipe_thread_lifecycle"), machineId, 1,
                List.of(), List.of());

        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
        MachineAsyncCoordinator.get(level).completeTick(() -> 0);

        interrupt.accept(controller);
        SharedIoEvents.completeLevelTick(level);

        assertThat(thread.isStartPending()).isFalse();
        assertThat(thread.runtime().active()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static FactoryRecipeThread factoryBaseLane(MachineControllerBlockEntity controller)
            throws ReflectiveOperationException {
        Field runtimeField = MachineControllerBlockEntity.class.getDeclaredField("runtime");
        runtimeField.setAccessible(true);
        MachineControllerRuntime runtime = (MachineControllerRuntime) runtimeField.get(controller);
        FactoryRuntime factory = runtime.factoryRuntime();
        factory.ensureBaseLane(controller);
        Field lanesField = FactoryRuntime.class.getDeclaredField("lanes");
        lanesField.setAccessible(true);
        FactoryRecipeThread base = ((List<FactoryRecipeThread>) lanesField.get(factory)).getFirst();
        assertThat(base.runtime()).isNotSameAs(runtime.craftingRuntime());
        return base;
    }

    private static boolean hasPendingMainStep(MachineAsyncCoordinator coordinator) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (coordinator.hasPendingMainStepForTesting()) return true;
            Thread.sleep(10L);
        }
        return false;
    }

    private static int queuedReceiptWorkers(MachineAsyncCoordinator coordinator) throws ReflectiveOperationException {
        Field drainsField = MachineAsyncCoordinator.class.getDeclaredField("receiptDrains");
        drainsField.setAccessible(true);
        int queued = 0;
        for (Object drain : ((Map<?, ?>) drainsField.get(coordinator)).values()) {
            Field queuedField = drain.getClass().getDeclaredField("queued");
            queuedField.setAccessible(true);
            if (((AtomicBoolean) queuedField.get(drain)).get()) queued++;
        }
        return queued;
    }
}
