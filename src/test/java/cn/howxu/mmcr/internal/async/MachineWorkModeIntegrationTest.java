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
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.event.SharedIoEvents;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeThread;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.ConfigTestSupport;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the controller execution modes at the server-level tick boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineWorkModeIntegrationTest {
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

    @AfterEach
    void discardLevelCoordinators() {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        RecipeRegistry.clearForTesting();
        if (level == null) return;
        MachineAsyncCoordinator.discard(level);
        SharedIoCoordinator.discard(level);
        StructureClaimRegistry.discard(level);
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

        MachineAsyncCoordinator.get(level).submit(new MachineAsyncCoordinator.TaskKey(controller.getBlockPos(),
                1L), ignored -> AsyncContinuation.Yield.mainThread(
                new MainThreadStep.TestStep(() -> order.add("async-main-step")),
                result -> context -> AsyncContinuation.Yield.complete()));
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.TickRequest(domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), "base"), domain.id(), 0L,
                () -> {
                    order.add("shared-io-resolve");
                    return true;
                }, () -> true, domain::id, () -> 0L));

        assertThat(hasPendingMainStep(MachineAsyncCoordinator.get(level))).isTrue();
        SharedIoEvents.completeLevelTick(level);

        assertThat(order).containsSubsequence("async-main-step", "shared-io-resolve");
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

        controller.serverTick();
        completeAsyncLevelTick(level);
        assertThat(controller.runtimeSnapshot().crafting().status().isCrafting())
                .as("mode=%s state=%s", mode, controller.runtimeSnapshot().crafting())
                .isTrue();
        phases.clear();
        RuntimeTestFixtures.advanceGameTime(level);
        controller.serverTick();

        assertThat(phases).containsSubsequence("pre", "recipe", "post");
        assertThat(phases.indexOf("recipe")).isLessThan(phases.indexOf("post"));
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

        controller.serverTick();
        if (mode == MachineWorkMode.ASYNC) {
            assertThat(hasPendingMainStep(MachineAsyncCoordinator.get(level))).isTrue();
        } else {
            assertThat(controller.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());
        }
        completeAsyncLevelTick(level);
        assertThat(controller.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());

        RuntimeTestFixtures.advanceGameTime(level);
        controller.serverTick();
        if (mode == MachineWorkMode.SYNC) {
            assertThat(controller.runtimeSnapshot().crafting().tick()).isEqualTo(1);
        } else {
            SharedIoCoordinator.get(level).resolve(level);
            assertThat(controller.runtimeSnapshot().crafting().tick()).isEqualTo(1);
            assertThat(hasPendingMainStep(MachineAsyncCoordinator.get(level))).isFalse();
        }
        completeAsyncLevelTick(level);

        for (int tick = 0; tick < 5 && !phases.contains("finish"); tick++) {
            RuntimeTestFixtures.advanceGameTime(level);
            controller.serverTick();
            completeAsyncLevelTick(level);
        }

        assertThat(phases).containsSubsequence("start", "tick", "finish");
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void normal_controller_continues_the_last_recipe_without_an_idle_boundary(MachineWorkMode mode) {
        assertNormalControllerContinuesLastRecipe(mode);
    }

    private void assertNormalControllerContinuesLastRecipe(MachineWorkMode mode) {
        ResourceLocation machineId = MMCR.id("test_cube");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(machineId, BlockPos.ZERO);
        RuntimeTestFixtures.registerRecipePool(machineId);
        AtomicInteger starts = new AtomicInteger();
        RuntimeTestFixtures.formStructure(controller, normalMachine(machineId, RecipeBehavior.builder()
                .beforeStart(context -> starts.incrementAndGet())
                .build()));
        level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("normal_last_recipe_continuation"), machineId, 1,
                List.of(), List.of());
        RecipeRegistry.registerStatic(recipe);
        ConfigTestSupport.setMachineWorkMode(mode);

        controller.serverTick();
        completeAsyncLevelTick(level);
        assertThat(controller.runtimeSnapshot().crafting().status().isCrafting()).isTrue();
        for (int tick = 0; tick < 6 && starts.get() < 2; tick++) {
            RuntimeTestFixtures.advanceGameTime(level);
            controller.serverTick();
            completeAsyncLevelTick(level);
            assertThat(controller.runtimeSnapshot().crafting().status().isCrafting())
                    .as("mode=%s starts=%s state=%s", mode, starts.get(),
                            controller.runtimeSnapshot().crafting())
                    .isTrue();
            assertThat(controller.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());
        }

        assertThat(starts).hasValueGreaterThanOrEqualTo(2);
    }

    private static DynamicMachine normalMachine(ResourceLocation machineId, RecipeBehavior behavior) {
        return new DynamicMachine(machineId, "normal mode lifecycle", new BlockArray(Map.of()),
                MachineControllerSpec.defaultsFor(machineId), MachineAppearanceSpec.defaults(), PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), Map.of(), 1, false, false, 1, List.of(),
                MachineRole.NORMAL, Set.of(), List.of(), RecipeFailureActions.getDefaultAction(), behavior);
    }

    private static void tickAndComplete(MachineControllerBlockEntity controller) {
        ServerLevel level = (ServerLevel) controller.getLevel();
        controller.tickRuntimeWork(level, controller.getBlockPos());
        completeAsyncLevelTick(level);
        RuntimeTestFixtures.advanceGameTime(level);
    }

    private static void completeAsyncLevelTick(ServerLevel level) {
        SharedIoCoordinator sharedIo = SharedIoCoordinator.get(level);
        MachineAsyncCoordinator async = MachineAsyncCoordinator.get(level);
        long gameTime = level.getGameTime();
        sharedIo.beginLevelTick(gameTime);
        async.beginLevelTick(gameTime);
        sharedIo.resolve(level);
        async.completeUntilIdleForTesting(() -> sharedIo.resolve(level));
        MachineControllerBlockEntity.flushQueuedAsyncRuntimeState(level);
    }

    private void assertLifecycleInterruptCancelsRecipeThreadSharedIo(
            java.util.function.Consumer<MachineControllerBlockEntity> interrupt) throws Exception {
        ResourceLocation machineId = MMCR.id("test_cube");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(machineId, BlockPos.ZERO);
        RuntimeTestFixtures.registerRecipePool(machineId);
        RuntimeTestFixtures.formStructure(controller, new DynamicMachine(machineId, "recipe thread lifecycle",
                new BlockArray(Map.of())));
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
        return ((List<FactoryRecipeThread>) lanesField.get(factory)).getFirst();
    }

    private static boolean hasPendingMainStep(MachineAsyncCoordinator coordinator) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (coordinator.hasPendingMainStepForTesting()) return true;
            Thread.sleep(10L);
        }
        return false;
    }
}
