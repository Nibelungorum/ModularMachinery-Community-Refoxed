package cn.howxu.mmcr.internal.async;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.event.SharedIoEvents;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeThread;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.FactorySnapshot;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.ConfigTestSupport;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies factory lane scheduling through the asynchronous execution chain.
 *
 * @author howxu <dev@howxu.cn>
 */
class AsyncFactoryExecutionTest {
    private ServerLevel level;
    private MinecraftServer previousServer;
    private boolean serverInstalled;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        CommentedConfig config = CommentedConfig.inMemory();
        ServerConfig.SPEC.correct(config);
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        ServerConfig.SPEC.acceptConfig((IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null));
    }

    @AfterEach
    void cleanup() throws Exception {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        RecipeRegistry.clearForTesting();
        if (serverInstalled) {
            var field = ServerLifecycleHooks.class.getDeclaredField("currentServer");
            field.setAccessible(true);
            field.set(null, previousServer);
        }
        if (level == null) return;
        MachineAsyncCoordinator.discard(level);
        SharedIoCoordinator.discard(level);
        StructureClaimRegistry.discard(level);
        MachineControllerBlockEntity.clearFormedControllerIndex(level);
    }

    @Test
    void async_factory_advances_each_active_lane_once_after_same_tick_shared_io_grants() {
        MachineControllerBlockEntity controller = factoryController();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("async_factory_lanes"), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 2);
        RecipeRegistry.registerStatic(recipe);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);

        controller.serverTick();

        assertThat(controller.runtimeSnapshot().factory().activeLaneCount()).isZero();

        completeAsyncLevelTick(level);

        assertThat(controller.runtimeSnapshot().factory().activeLaneCount()).isEqualTo(1);

        RuntimeTestFixtures.advanceGameTime(level);
        controller.serverTick();
        completeAsyncLevelTick(level);

        assertThat(controller.runtimeSnapshot().factory().presentationLanes())
                .filteredOn(lane -> lane.active())
                .extracting(lane -> lane.laneId())
                .containsExactlyInAnyOrder("base", "factory-0");
        assertThat(controller.runtimeSnapshot().factory().presentationLanes())
                .filteredOn(lane -> lane.active())
                .extracting(lane -> lane.tick())
                .containsExactly(1, 0);
    }

    @Test
    void real_level_post_with_an_older_pending_batch_commits_four_attached_lanes_and_publishes_once() throws Exception {
        MachineControllerBlockEntity controller = factoryController(4);
        var workMode = MachineControllerBlockEntity.class.getDeclaredField("activeWorkMode");
        workMode.setAccessible(true);
        workMode.set(controller, MachineWorkMode.SYNC);
        FactoryRuntime factory = factoryRuntime(controller);
        factory.ensureBaseLane(controller);
        factory.setLaneLimit(4);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("older_batch_factory_publication"), MMCR.id("test_cube"),
                20, List.of(), List.of(), List.of(), 0, 4);
        RecipeRegistry.registerStatic(recipe);
        factory.tick(List.of(recipe), 1, 0L);
        List<CraftingRuntime> lanes = factory.activeRuntimes();
        assertThat(lanes).hasSize(4);
        assertThat(lanes).extracting(CraftingRuntime::tickCount).containsExactly(0, 0, 0, 0);
        RuntimeTestFixtures.republish(controller);
        var published = controller.runtimeSnapshot();

        workMode.set(controller, MachineWorkMode.ASYNC);
        MachineAsyncCoordinator async = MachineAsyncCoordinator.get(level);
        assertThat(async.submitMainThread(new MachineAsyncCoordinator.TaskKey(new BlockPos(9999, 0, 0), -1L),
                new MainThreadStep.Deferred(MainThreadStep.Kind.BEFORE_START, () -> { }), null,
                MachineAsyncCoordinator.TaskHooks.defaults())).isEqualTo(MachineAsyncCoordinator.SubmissionResult.ACCEPTED);
        var laneField = FactoryRuntime.class.getDeclaredField("lanes");
        laneField.setAccessible(true);
        for (Object lane : (List<?>) laneField.get(factory)) ((FactoryRecipeThread) lane).tick();
        var runtimeField = MachineControllerBlockEntity.class.getDeclaredField("runtime");
        runtimeField.setAccessible(true);
        MachineControllerRuntime runtime = (MachineControllerRuntime) runtimeField.get(controller);
        var publicationCount = MachineControllerRuntime.class.getDeclaredMethod("snapshotBuildCountForTesting");
        publicationCount.setAccessible(true);
        int before = (int) publicationCount.invoke(runtime);

        // The older Deferred batch keeps the newer lanes' grants between the two real fences.
        SharedIoEvents.completeLevelTick(level);

        assertThat(lanes).extracting(CraftingRuntime::tickCount).containsExactly(1, 1, 1, 1);
        assertThat(controller.runtimeSnapshot().factory().presentationLanes())
                .extracting(FactoryRuntime.ThreadSnapshot::tick).containsExactly(1, 1, 1, 1);
        assertThat((int) publicationCount.invoke(runtime)).isEqualTo(before + 1);
        assertThat(published.factory().presentationLanes())
                .extracting(FactoryRuntime.ThreadSnapshot::tick).containsExactly(0, 0, 0, 0);
    }

    @Test
    void attached_multi_controller_finish_callbacks_and_outputs_complete_in_one_real_level_post() throws Exception {
        List<String> phases = new ArrayList<>();
        List<EnergyInputHatchBlockEntity> inputs = new ArrayList<>();
        List<ItemOutputBusBlockEntity> outputs = new ArrayList<>();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("attached_finish_timing"), MMCR.id("test_cube"), 1,
                List.of(), List.of(), List.of(), 0, 2, false, List.of(), List.of(new EnergyRequirement(20L),
                        new ItemRequirement(IOType.OUTPUT, null, 0, new ItemStack(Items.IRON_NUGGET, 1))));
        RecipeRegistry.registerStatic(recipe);
        List<MachineControllerBlockEntity> controllers = timingControllers(recipe, phases, inputs, outputs);
        List<Long> energyAfterStart = inputs.stream().map(input -> input.energyStorage().getAmountAsLong()).toList();
        List<FactoryRecipeThread> lanes = new ArrayList<>();
        for (MachineControllerBlockEntity controller : controllers) lanes.addAll(attachedLanes(controller));
        // The first asynchronous request is a real recipe tick; finishing is requested at the following gameTime.
        Queue<Runnable> workers = new ArrayDeque<>();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(workers::add, 12, false);
        installCoordinator(coordinator);
        for (FactoryRecipeThread lane : lanes) lane.tick();
        SharedIoEvents.completeLevelTick(level);
        assertThat(lanes).allSatisfy(lane -> assertThat(lane.runtime().finishPending()).isTrue());
        for (int index = 0; index < inputs.size(); index++) {
            assertThat(inputs.get(index).energyStorage().getAmountAsLong()).isEqualTo(energyAfterStart.get(index) - 40L);
        }
        assertThat(outputs).allSatisfy(output -> assertThat(output.itemStorage().amount(0)).isZero());
        List<FactorySnapshot> oldSnapshots = controllers.stream()
                .map(controller -> controller.runtimeSnapshot().factory()).toList();
        // The final tick is published only after outputs commit; finishPending does not advance its counter.
        assertThat(lanes).allSatisfy(lane -> assertThat(lane.runtime().tickCount()).isZero());
        phases.clear();
        RuntimeTestFixtures.advanceGameTime(level);
        for (int index = 0; index < lanes.size(); index++) {
            int laneIndex = index;
            FactoryRecipeThread lane = lanes.get(index);
            lane.setFinishContinuation(() -> {
                assertThat(outputs.get(laneIndex / 2).itemStorage().amount(0)).isPositive();
                phases.add("committed-" + laneIndex);
            });
            lane.tick();
        }
        assertThat(workers).hasSize(4);
        while (!workers.isEmpty()) workers.remove().run();
        assertThat(phases).isEmpty();

        SharedIoEvents.completeLevelTick(level);

        assertThat(lanes).allSatisfy(lane -> assertThat(lane.runtime().active()).isFalse());
        assertThat(outputs).allSatisfy(output -> {
            assertThat(output.itemStorage().resource(0)).isEqualTo(ItemResource.of(Items.IRON_NUGGET));
            assertThat(output.itemStorage().amount(0)).isEqualTo(2L);
        });
        assertThat(phases).hasSize(8);
        assertThat(phases.subList(0, 4)).containsExactly("before-finish-0", "before-finish-0",
                "before-finish-1", "before-finish-1");
        assertThat(phases.subList(4, 8)).containsExactlyInAnyOrder("committed-0", "committed-1", "committed-2", "committed-3");
        assertThat(phases).containsSubsequence("committed-0", "committed-1");
        assertThat(phases).containsSubsequence("committed-2", "committed-3");
        assertThat(oldSnapshots).allSatisfy(snapshot -> assertThat(snapshot.presentationLanes())
                .allSatisfy(lane -> {
                    assertThat(lane.active()).isTrue();
                    assertThat(lane.tick()).isZero();
                }));
        assertThat(controllers).allSatisfy(controller -> assertThat(controller.runtimeSnapshot().factory().presentationLanes())
                .allSatisfy(lane -> assertThat(lane.active()).isFalse()));
        while (!workers.isEmpty()) workers.remove().run();
        SharedIoEvents.completeLevelTick(level);
        assertThat(outputs).allSatisfy(output -> assertThat(output.itemStorage().amount(0)).isEqualTo(2L));
        for (int index = 0; index < inputs.size(); index++) {
            assertThat(inputs.get(index).energyStorage().getAmountAsLong()).isEqualTo(energyAfterStart.get(index) - 40L);
        }
        assertThat(phases).hasSize(8);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 4})
    void attached_multi_controller_request_commit_next_request_timelines_remain_bounded(int budget) throws Exception {
        List<String> phases = new ArrayList<>();
        List<EnergyInputHatchBlockEntity> inputs = new ArrayList<>();
        List<ItemOutputBusBlockEntity> outputs = new ArrayList<>();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("attached_tick_timeline"), MMCR.id("test_cube"), 20,
                List.of(new EnergyRequirement(20L)), List.of(), List.of(), 0, 2);
        RecipeRegistry.registerStatic(recipe);
        List<MachineControllerBlockEntity> controllers = timingControllers(recipe, phases, inputs, outputs);
        List<Long> energyAfterStart = inputs.stream().map(input -> input.energyStorage().getAmountAsLong()).toList();
        List<FactoryRecipeThread> lanes = new ArrayList<>();
        for (MachineControllerBlockEntity controller : controllers) lanes.addAll(attachedLanes(controller));
        Queue<Runnable> workers = new ArrayDeque<>();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(workers::add, budget, false);
        installCoordinator(coordinator);
        // A real worker-only tail in the oldest batch keeps newer lane work on the bounded ready scan.
        coordinator.submit(new MachineAsyncCoordinator.TaskKey(new BlockPos(9999, 0, 0), -1L), ignored ->
                AsyncContinuation.Yield.mainThread(MainThreadStep.Result::success,
                        result -> context -> AsyncContinuation.Yield.complete()));
        workers.remove().run();
        coordinator.beginLevelTick(0L);
        coordinator.completeTick();
        assertThat(workers).hasSize(1);
        List<List<Integer>> commits = new ArrayList<>();
        for (int worldTick = 0; worldTick < 4; worldTick++) {
            int before = lanes.stream().mapToInt(lane -> lane.runtime().tickCount()).sum();
            for (FactoryRecipeThread lane : lanes) lane.tick();
            SharedIoEvents.completeLevelTick(level);
            List<Integer> progress = lanes.stream().map(lane -> lane.runtime().tickCount()).toList();
            commits.add(progress);
            assertThat(progress.stream().mapToInt(Integer::intValue).sum() - before).isEqualTo(budget);
            for (int controllerIndex = 0; controllerIndex < controllers.size(); controllerIndex++) {
                int consumedTicks = progress.get(controllerIndex * 2) + progress.get(controllerIndex * 2 + 1);
                assertThat(inputs.get(controllerIndex).energyStorage().getAmountAsLong())
                        .isEqualTo(energyAfterStart.get(controllerIndex) - consumedTicks * 20L);
            }
            for (FactoryRecipeThread lane : lanes) lane.tick();
            SharedIoEvents.completeLevelTick(level);
            assertThat(lanes).extracting(lane -> lane.runtime().tickCount()).containsExactlyElementsOf(progress);
            // A grant for an older request may free a lane to prepare its first request for this gameTime.
            // Once every free lane has had that opportunity, further same-time tickers are duplicates.
            List<String> callbacks = List.copyOf(phases);
            for (FactoryRecipeThread lane : lanes) lane.tick();
            SharedIoEvents.completeLevelTick(level);
            assertThat(phases).containsExactlyElementsOf(callbacks);
            assertThat(lanes).extracting(lane -> lane.runtime().tickCount()).containsExactlyElementsOf(progress);
            for (int controllerIndex = 0; controllerIndex < controllers.size(); controllerIndex++) {
                int consumedTicks = progress.get(controllerIndex * 2) + progress.get(controllerIndex * 2 + 1);
                assertThat(inputs.get(controllerIndex).energyStorage().getAmountAsLong())
                        .isEqualTo(energyAfterStart.get(controllerIndex) - consumedTicks * 20L);
                String request = "request-" + controllerIndex + "@" + level.getGameTime();
                assertThat(phases.stream().filter(request::equals).count()).isLessThanOrEqualTo(2L);
            }
            RuntimeTestFixtures.advanceGameTime(level);
        }
        if (budget == 4) {
            assertThat(commits).containsExactly(List.of(1, 1, 1, 1), List.of(2, 2, 2, 2), List.of(3, 3, 3, 3), List.of(4, 4, 4, 4));
            assertThat(phases).containsExactly("request-0@1", "request-0@1", "request-1@1", "request-1@1",
                    "request-0@2", "request-0@2", "request-1@2", "request-1@2",
                    "request-0@3", "request-0@3", "request-1@3", "request-1@3",
                    "request-0@4", "request-0@4", "request-1@4", "request-1@4");
        } else {
            assertThat(commits.getFirst()).containsExactly(1, 1, 0, 0);
            assertThat(commits.getLast()).allSatisfy(progress -> assertThat(progress).isPositive());
        }
        assertThat(workers).hasSize(1); // No planning or extra worker drain was hidden in the fences.
    }

    @ParameterizedTest
    @CsvSource({"false, false", "true, false", "false, true"})
    void stale_state_requests_release_only_their_attached_lane_and_allow_retry(boolean finish,
                                                                              boolean replacePending) throws Exception {
        List<String> phases = new ArrayList<>();
        List<EnergyInputHatchBlockEntity> inputs = new ArrayList<>();
        List<ItemOutputBusBlockEntity> outputs = new ArrayList<>();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("stale_state_request"), MMCR.id("test_cube"),
                finish ? 1 : 20, List.of(), List.of(), List.of(), 0, 2, false, List.of(),
                List.of(new EnergyRequirement(20L),
                        new ItemRequirement(IOType.OUTPUT, null, 0, new ItemStack(Items.IRON_NUGGET, 1))));
        RecipeRegistry.registerStatic(recipe);
        List<MachineControllerBlockEntity> controllers = timingControllers(recipe, phases, inputs, outputs);
        MachineControllerBlockEntity controller = controllers.getFirst();
        FactoryRecipeThread lane = attachedLanes(controller).getFirst();
        if (finish) {
            for (MachineControllerBlockEntity owner : controllers) {
                for (FactoryRecipeThread attached : attachedLanes(owner)) attached.tick();
            }
            SharedIoEvents.completeLevelTick(level);
            assertThat(lane.runtime().finishPending()).isTrue();
            RuntimeTestFixtures.advanceGameTime(level);
        }
        Queue<Runnable> workers = new ArrayDeque<>();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(workers::add, replacePending ? 3 : 2, false);
        installCoordinator(coordinator);
        MachineAsyncCoordinator.TaskKey oldKey = new MachineAsyncCoordinator.TaskKey(controller.getBlockPos(),
                level.getGameTime(), MachineWorkMode.ASYNC, lane.asyncLaneId(), controller.lifecycleEpoch());
        lane.tick();
        if (finish) {
            assertThat(workers).hasSize(1);
            workers.remove().run();
            coordinator.beginLevelTick(level.getGameTime());
            coordinator.completeTick(); // Leave the third, shared-IO stage ready in the older batch.
        }
        RuntimeTestFixtures.advanceGameTime(level);
        long energy = inputs.getFirst().energyStorage().getAmountAsLong();
        long capabilityVersion = controller.componentRuntime().capabilityVersion();
        long structureVersion = controller.currentStructureSnapshot().version();
        List<String> mutations = new ArrayList<>();
        assertThat(coordinator.submitMainThread(new MachineAsyncCoordinator.TaskKey(
                controllers.getLast().getBlockPos(), level.getGameTime()), () -> {
            assertThat(outputs.getFirst().itemStorage().amount(0)).isZero();
            assertThat(inputs.getFirst().energyStorage().getAmountAsLong()).isEqualTo(energy);
            assertThat(controller.componentRuntime().replaceLinkedPortPositions(Set.of(new BlockPos(99, 0, 0)))).isTrue();
            assertThat(controller.componentRuntime().capabilityVersion()).isEqualTo(capabilityVersion);
            assertThat(controller.currentStructureSnapshot().version()).isEqualTo(structureVersion);
            assertThat(lane.runtime().versionsCurrent()).isTrue();
            mutations.add("state-only");
            if (replacePending) {
                lane.cancelAsyncState();
                lane.tick();
            }
            return MainThreadStep.Result.success();
        }, null, MachineAsyncCoordinator.TaskHooks.defaults()))
                .isEqualTo(MachineAsyncCoordinator.SubmissionResult.ACCEPTED);

        SharedIoEvents.completeLevelTick(level);

        assertThat(mutations).containsExactly("state-only");
        assertThat(outputs.getFirst().itemStorage().amount(0)).isZero();
        assertThat(lane.runtime().tickCount()).isEqualTo(replacePending ? 1 : 0);
        assertThat(inputs.getFirst().energyStorage().getAmountAsLong()).isEqualTo(energy - (replacePending ? 20L : 0L));
        var tasksField = MachineAsyncCoordinator.class.getDeclaredField("tasks");
        tasksField.setAccessible(true);
        assertThat(((Map<?, ?>) tasksField.get(coordinator)).containsKey(oldKey)).isFalse();
        assertThat(attachedLanes(controller).getLast().runtime().active()).isTrue();
        assertThat(attachedLanes(controllers.getLast())).allSatisfy(peer -> assertThat(peer.runtime().active()).isTrue());
        SharedIoEvents.completeLevelTick(level);
        assertThat(mutations).hasSize(1);
        assertThat(inputs.getFirst().energyStorage().getAmountAsLong()).isEqualTo(energy - (replacePending ? 20L : 0L));

        RuntimeTestFixtures.advanceGameTime(level);
        lane.tick();
        if (finish) assertThat(workers).hasSize(1);
        for (int count = workers.size(); count > 0; count--) workers.remove().run();
        SharedIoEvents.completeLevelTick(level);
        if (finish) {
            // Retrying respects the same two-step budget: the third finish stage waits for another gameTime.
            assertThat(lane.runtime().active()).isTrue();
            assertThat(outputs.getFirst().itemStorage().amount(0)).isZero();
            RuntimeTestFixtures.advanceGameTime(level);
            SharedIoEvents.completeLevelTick(level);
            assertThat(lane.runtime().active()).isFalse();
            assertThat(outputs.getFirst().itemStorage().amount(0)).isEqualTo(1L);
            assertThat(inputs.getFirst().energyStorage().getAmountAsLong()).isEqualTo(energy);
        } else {
            assertThat(lane.runtime().tickCount()).isEqualTo(replacePending ? 2 : 1);
            assertThat(inputs.getFirst().energyStorage().getAmountAsLong()).isEqualTo(energy - (replacePending ? 40L : 20L));
        }
    }

    private List<MachineControllerBlockEntity> timingControllers(MachineRecipe recipe, List<String> phases,
                                                                List<EnergyInputHatchBlockEntity> inputs,
                                                                List<ItemOutputBusBlockEntity> outputs) throws Exception {
        List<MachineControllerBlockEntity> controllers = new ArrayList<>();
        ServerLevel sharedLevel = null;
        Thread testThread = Thread.currentThread();
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        MinecraftServer server = (MinecraftServer) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(DedicatedServer.class);
        var serverThread = MinecraftServer.class.getDeclaredField("serverThread");
        serverThread.setAccessible(true);
        serverThread.set(server, testThread);
        var currentServer = ServerLifecycleHooks.class.getDeclaredField("currentServer");
        currentServer.setAccessible(true);
        previousServer = (MinecraftServer) currentServer.get(null);
        serverInstalled = true;
        currentServer.set(null, server);
        for (int index = 0; index < 2; index++) {
            int controllerIndex = index;
            BlockPos pos = new BlockPos(index * 16, 0, 0);
            EnergyInputHatchBlockEntity input = RuntimeTestFixtures.energyInput(pos.offset(2, 0, 0));
            ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(pos.offset(3, 0, 0));
            input.energyStorage().setAmount(1_000L);
            MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), pos);
            FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(pos.offset(-1, 0, 0),
                    ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
            RecipeBehavior behavior = RecipeBehavior.builder()
                    .recipeTick(context -> phases.add("request-" + controllerIndex + "@" + controller.getLevel().getGameTime()))
                    .beforeFinish(context -> {
                        assertThat(Thread.currentThread()).isSameAs(testThread);
                        assertThat(output.itemStorage().amount(0)).isZero();
                        phases.add("before-finish-" + controllerIndex);
                    }).build();
            DynamicMachine machine = new DynamicMachine(MMCR.id("test_cube"), "attached timing factory",
                    new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(scheduler.getBlockState().getBlock()))),
                    MachineControllerSpec.defaultsFor(MMCR.id("test_cube")), MachineAppearanceSpec.defaults(),
                    PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of(), 1, false, true, 2,
                    List.of(), MachineRole.NORMAL, Set.of(), List.of(), RecipeFailureActions.getDefaultAction(), behavior);
            RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler, input, output);
            ServerLevel originalLevel = (ServerLevel) controller.getLevel();
            if (sharedLevel == null) {
                sharedLevel = originalLevel;
                level = sharedLevel;
            } else {
                MachineAsyncCoordinator.discard(originalLevel);
                SharedIoCoordinator.discard(originalLevel);
                StructureClaimRegistry.discard(originalLevel);
                MachineControllerBlockEntity.clearFormedControllerIndex(originalLevel);
                for (var owner : List.of(controller, scheduler, input, output)) {
                    RuntimeTestFixtures.replaceBlockEntity(controllers.getFirst(), owner);
                    sharedLevel.setBlock(owner.getBlockPos(), owner.getBlockState(), 3);
                }
            }
            controller.componentRuntime().replaceComponents(List.of(
                    new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null),
                    new ProcessingComponent(new MachineComponent(input.kind(), input.ioType()), input,
                            input.getBlockPos(), input.getBlockPos(), (String) null),
                    new ProcessingComponent(new MachineComponent(output.kind(), output.ioType()), output,
                            output.getBlockPos(), output.getBlockPos(), (String) null)));
            input.linkControllerAppearance(pos, null);
            output.linkControllerAppearance(pos, null);
            RuntimeTestFixtures.republish(controller);
            assertThat(StructureClaimRegistry.get(sharedLevel).claim(pos, List.of()).accepted()).isTrue();
            setWorkMode(controller, MachineWorkMode.SYNC);
            FactoryRuntime factory = factoryRuntime(controller);
            factory.ensureBaseLane(controller);
            factory.setLaneLimit(2);
            factory.tick(List.of(recipe), 1L, sharedLevel.getGameTime());
            assertThat(factory.activeRuntimes()).hasSize(2);
            assertThat(factory.activeRuntimes()).extracting(CraftingRuntime::tickCount).containsExactly(0, 0);
            RuntimeTestFixtures.republish(controller);
            setWorkMode(controller, MachineWorkMode.ASYNC);
            controllers.add(controller);
            inputs.add(input);
            outputs.add(output);
        }
        level = sharedLevel;
        phases.clear();
        return controllers;
    }

    @SuppressWarnings("unchecked")
    private void installCoordinator(MachineAsyncCoordinator coordinator) throws Exception {
        var field = MachineAsyncCoordinator.class.getDeclaredField("COORDINATORS");
        field.setAccessible(true);
        ((Map<ServerLevel, MachineAsyncCoordinator>) field.get(null)).put(level, coordinator);
    }

    @SuppressWarnings("unchecked")
    private static List<FactoryRecipeThread> attachedLanes(MachineControllerBlockEntity controller) throws Exception {
        var field = FactoryRuntime.class.getDeclaredField("lanes");
        field.setAccessible(true);
        return List.copyOf((List<FactoryRecipeThread>) field.get(factoryRuntime(controller)));
    }

    private static void setWorkMode(MachineControllerBlockEntity controller, MachineWorkMode mode) throws Exception {
        var field = MachineControllerBlockEntity.class.getDeclaredField("activeWorkMode");
        field.setAccessible(true);
        field.set(controller, mode);
    }

    @Test
    void async_factory_keeps_base_and_factory_lane_order_fair_across_ticks() {
        MachineControllerBlockEntity controller = factoryController(3);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("async_factory_lane_order"), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 3);
        RecipeRegistry.registerStatic(recipe);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);

        controller.serverTick();
        completeAsyncLevelTick(level);

        assertThat(controller.runtimeSnapshot().factory().presentationLanes())
                .filteredOn(lane -> lane.active())
                .extracting(lane -> lane.laneId())
                .containsExactly("base");

        RuntimeTestFixtures.advanceGameTime(level);
        controller.serverTick();
        completeAsyncLevelTick(level);

        assertThat(controller.runtimeSnapshot().factory().presentationLanes())
                .filteredOn(lane -> lane.active())
                .extracting(lane -> lane.laneId())
                .containsExactly("base", "factory-0", "factory-1");
        assertThat(controller.runtimeSnapshot().factory().presentationLanes())
                .filteredOn(lane -> lane.active())
                .extracting(lane -> lane.parallelism())
                .containsExactly(1L, 1L, 1L);

        RuntimeTestFixtures.advanceGameTime(level);
        controller.serverTick();
        completeAsyncLevelTick(level);

        assertThat(controller.runtimeSnapshot().factory().presentationLanes())
                .filteredOn(lane -> lane.active())
                .extracting(lane -> lane.tick())
                .containsExactly(2, 1, 1);
    }

    @Test
    void async_factory_retries_a_cancelled_search_after_the_pause_clears() {
        MachineControllerBlockEntity controller = factoryController();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("cancelled_async_factory_search"), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 2);
        RecipeRegistry.registerStatic(recipe);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);

        controller.serverTick();
        RuntimeTestFixtures.setDirectSignal(level, controller.getBlockPos(), 15);
        controller.serverTick();
        completeAsyncLevelTick(level);

        assertThat(controller.runtimeSnapshot().factory().activeLaneCount()).isZero();

        RuntimeTestFixtures.setDirectSignal(level, controller.getBlockPos(), 0);
        RuntimeTestFixtures.advanceGameTime(level);
        controller.serverTick();
        completeAsyncLevelTick(level);

        assertThat(controller.runtimeSnapshot().factory().activeLaneCount()).isEqualTo(1);

        RuntimeTestFixtures.advanceGameTime(level);
        controller.serverTick();
        completeAsyncLevelTick(level);

        assertThat(controller.runtimeSnapshot().factory().activeLaneCount()).isEqualTo(2);
    }

    @Test
    void async_factory_does_not_create_speculative_lanes_when_no_recipe_can_start() {
        MachineControllerBlockEntity controller = factoryController(8);
        RecipeRegistry.registerStatic(RecipeTestSupport.create(MMCR.id("idle_factory_missing_input"),
                MMCR.id("test_cube"), 20, List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                        new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                                ItemStack.EMPTY))));

        controller.serverTick();
        completeAsyncLevelTick(level);

        assertThat(factoryRuntime(controller).laneCount()).isEqualTo(1);
        assertThat(controller.runtimeSnapshot().factory().activeLaneCount()).isZero();
    }

    @Test
    void async_factory_finish_release_wakes_and_restarts_its_lane() {
        MachineControllerBlockEntity controller = factoryController(1);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("async_factory_finish_restart"), MMCR.id("test_cube"), 1,
                List.of(), List.of(), List.of(), 0, 1);
        RecipeRegistry.registerStatic(recipe);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);

        controller.serverTick();
        completeAsyncLevelTick(level);
        long beforeFinishEpoch = controller.resourceAvailabilityEpoch();

        for (int pass = 0; pass < 3 && controller.resourceAvailabilityEpoch() == beforeFinishEpoch; pass++) {
            RuntimeTestFixtures.advanceGameTime(level);
            controller.serverTick();
            completeAsyncLevelTick(level);
        }

        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeFinishEpoch + 1L);
        for (int pass = 0; pass < 8
                && !controller.runtimeSnapshot().factory().presentationLanes().getFirst().active(); pass++) {
            RuntimeTestFixtures.advanceGameTime(level);
            controller.serverTick();
            completeAsyncLevelTick(level);
        }
        assertThat(controller.runtimeSnapshot().factory().presentationLanes()).singleElement().satisfies(lane -> {
            assertThat(lane.active()).isTrue();
            assertThat(lane.tick()).isZero();
        });
    }

    @Test
    void async_factory_does_not_delay_a_fallback_when_the_more_specific_output_is_blocked() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = factoryController(input);
        MachineRecipe specific = RecipeTestSupport.create(MMCR.id("async_factory_blocked_specific"), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY),
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.GOLD_INGOT), 1, ItemStack.EMPTY),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(Items.IRON_NUGGET, 1))));
        MachineRecipe fallback = RecipeTestSupport.create(MMCR.id("async_factory_output_blocked_fallback"), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)));
        RecipeRegistry.replaceDynamic(Map.of(specific.id(), specific, fallback.id(), fallback));
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        setItem(input.itemStorage(), new ItemStack(Items.IRON_INGOT, 1));

        for (int pass = 0; pass < 8 && controller.runtimeSnapshot().factory().activeLaneCount() == 0; pass++) {
            controller.serverTick();
            completeAsyncLevelTick(level);
            RuntimeTestFixtures.advanceGameTime(level);
        }

        assertThat(controller.runtimeSnapshot().factory().presentationLanes()).singleElement().satisfies(lane -> {
            assertThat(lane.active()).isTrue();
            assertThat(lane.recipeId()).isEqualTo(fallback.id().toString());
        });
    }

    @Test
    void async_factory_matches_sync_delay_for_a_level_blocked_specific_recipe_with_pending_input() {
        assertThat(fallbackStartIsDelayedBySpecificPendingInput(MachineWorkMode.SYNC)).isTrue();
        assertThat(fallbackStartIsDelayedBySpecificPendingInput(MachineWorkMode.ASYNC)).isTrue();
    }

    @Test
    void shrinking_factory_invalidates_queued_lane_searches_before_their_continuations_commit() {
        MachineControllerBlockEntity controller = factoryController();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("shrunk_async_factory_search"), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 2);
        RecipeRegistry.registerStatic(recipe);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);

        controller.serverTick();
        factoryRuntime(controller).setLaneLimit(1);
        completeAsyncLevelTick(level);

        assertThat(controller.runtimeSnapshot().factory().presentationLanes()).hasSize(1);
        assertThat(controller.runtimeSnapshot().factory().activeLaneCount()).isEqualTo(1);
    }

    private MachineControllerBlockEntity factoryController() {
        return factoryController(2);
    }

    private MachineControllerBlockEntity factoryController(int laneLimit) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos schedulerPos = controller.getBlockPos().offset(-1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));
        DynamicMachine machine = new DynamicMachine(MMCR.id("test_cube"), "async factory", pattern,
                MachineControllerSpec.defaultsFor(MMCR.id("test_cube")), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, laneLimit);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler);
        List<ProcessingComponent> components = new ArrayList<>();
        components.add(new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null));
        controller.componentRuntime().replaceComponents(components);
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        return controller;
    }

    private MachineControllerBlockEntity factoryController(ItemInputBusBlockEntity input) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos schedulerPos = controller.getBlockPos().offset(-1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));
        DynamicMachine machine = new DynamicMachine(MMCR.id("test_cube"), "async factory", pattern,
                MachineControllerSpec.defaultsFor(MMCR.id("test_cube")), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler, input);
        List<ProcessingComponent> components = new ArrayList<>();
        components.add(new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null));
        components.add(new ProcessingComponent(new MachineComponent(input.kind(), input.ioType()), input,
                input.getBlockPos(), input.getBlockPos(), (String) null));
        controller.componentRuntime().replaceComponents(components);
        controller.setFormed(true);
        input.linkControllerAppearance(controller.getBlockPos(), null);
        RuntimeTestFixtures.republish(controller);
        level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        return controller;
    }

    private boolean fallbackStartIsDelayedBySpecificPendingInput(MachineWorkMode mode) {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = factoryController(input, output);
        Identifier levelType = MMCR.id("factory_pending_input_level_type");
        Identifier requiredLevel = MMCR.id("factory_pending_input_level");
        TestBootstrap.registerType(new LevelType(levelType, Component.literal("Factory Test Level")));
        TestBootstrap.registerLevel(new MachineLevel(requiredLevel, levelType, 1,
                new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()), ItemStack.EMPTY,
                ModifierDefinition.EMPTY));
        MachineRecipe specific = RecipeTestSupport.create(MMCR.id("level_blocked_specific"), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY),
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.GOLD_INGOT), 1, ItemStack.EMPTY),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(Items.IRON_NUGGET, 1))), false,
                List.of(LevelRequirement.input(levelType, requiredLevel)));
        MachineRecipe fallback = RecipeTestSupport.create(MMCR.id("level_blocked_fallback"), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)));
        RecipeRegistry.replaceDynamic(Map.of(specific.id(), specific, fallback.id(), fallback));
        ConfigTestSupport.setMachineWorkMode(mode);
        setItem(input.itemStorage(), new ItemStack(Items.IRON_INGOT, 1));

        for (int pass = 0; pass < 8; pass++) {
            controller.serverTick();
            completeAsyncLevelTick(level);
            RuntimeTestFixtures.advanceGameTime(level);
        }

        return controller.runtimeSnapshot().factory().activeLaneCount() == 0;
    }

    private MachineControllerBlockEntity factoryController(ItemInputBusBlockEntity input, ItemOutputBusBlockEntity output) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos schedulerPos = controller.getBlockPos().offset(-1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));
        DynamicMachine machine = new DynamicMachine(MMCR.id("test_cube"), "async factory", pattern,
                MachineControllerSpec.defaultsFor(MMCR.id("test_cube")), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler, input, output);
        List<ProcessingComponent> components = new ArrayList<>();
        components.add(new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null));
        components.add(new ProcessingComponent(new MachineComponent(input.kind(), input.ioType()), input,
                input.getBlockPos(), input.getBlockPos(), (String) null));
        components.add(new ProcessingComponent(new MachineComponent(output.kind(), output.ioType()), output,
                output.getBlockPos(), output.getBlockPos(), (String) null));
        controller.componentRuntime().replaceComponents(components);
        controller.setFormed(true);
        input.linkControllerAppearance(controller.getBlockPos(), null);
        output.linkControllerAppearance(controller.getBlockPos(), null);
        RuntimeTestFixtures.republish(controller);
        level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        return controller;
    }

    private static void setItem(ResourceStorage<ItemResource> storage, ItemStack stack) {
        try (Transaction transaction = Transaction.openRoot()) {
            ItemResource current = storage.resource(0);
            if (current != null && !current.isEmpty()) storage.extract(0, current, storage.amount(0), transaction);
            if (!stack.isEmpty()) storage.insert(0, ItemResource.of(stack), stack.getCount(), transaction);
            transaction.commit();
        }
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


    private static FactoryRuntime factoryRuntime(MachineControllerBlockEntity controller) {
        try {
            var field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
            field.setAccessible(true);
            return ((MachineControllerRuntime) field.get(controller)).factoryRuntime();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to access controller factory runtime", exception);
        }
    }
}
