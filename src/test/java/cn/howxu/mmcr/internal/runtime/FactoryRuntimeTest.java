package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.FactoryThreadSpec;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.definition.RecipeStartContext;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.multiblock.ComponentClaimPolicy;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ExtendedItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier.Reason;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeThread;
import cn.howxu.mmcr.internal.recipe.FactorySearchContext;
import cn.howxu.mmcr.internal.recipe.RecipeSearchContextKey;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.ConfigTestSupport;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.fml.config.IConfigSpec;
import com.electronwill.nightconfig.core.CommentedConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.ArrayList;
import java.util.AbstractList;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Final factory runtime behavior tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class FactoryRuntimeTest {
    private static final HolderLookup.Provider EMPTY_LOOKUP = HolderLookup.Provider.create(Stream.empty());

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        CommentedConfig config = CommentedConfig.inMemory();
        ServerConfig.SPEC.correct(config);
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        ServerConfig.SPEC.acceptConfig((IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null));
    }

    @BeforeEach
    void bootstrapCapabilities() throws Exception {
        TestBootstrap.bootstrapCapabilities();
    }

    @AfterEach
    void cleanup() {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        RecipeRegistry.clearForTesting();
    }

    @Test
    void baseLaneCreationAndLaneLimitProduceImmutablePresentationSlots() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();

        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(3);

        FactorySnapshot snapshot = runtime.snapshot();
        assertThat(runtime.laneCount()).isEqualTo(1);
        assertThat(snapshot.laneLimit()).isEqualTo(3);
        assertThat(snapshot.presentationLanes()).hasSize(3).isUnmodifiable();
        assertThat(snapshot.lanes()).isUnmodifiable();
    }

    @Test
    void advancing_an_active_lane_reuses_its_recipe_presentation() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        MachineRecipe recipe = recipe("factory_presentation_cache", 20);

        runtime.tick(List.of(recipe), 1);
        runtime.snapshot();
        int presentationBuilds = runtime.presentationBuildCountForTesting();

        runtime.tick(List.of(recipe), 1);
        runtime.snapshot();

        assertThat(runtime.presentationBuildCountForTesting()).isEqualTo(presentationBuilds);
    }

    @Test
    void full_active_factory_skips_async_recipe_search_scan() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = asyncFactoryController(input);
        ServerLevel level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        MachineRecipe recipe = recipe("factory_full_active_search_skip", 20);

        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        runtime.tick(List.of(recipe), 0, level.getGameTime());
        assertThat(runtime.asyncSearchScansForTesting()).isEqualTo(1L);

        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.SYNC);
        runtime.tick(List.of(recipe), 1, 0L);
        assertThat(runtime.activeLaneCount()).isEqualTo(1);

        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        long searchScans = runtime.asyncSearchScansForTesting();
        runtime.tick(List.of(recipe), 1, level.getGameTime());

        assertThat(runtime.asyncSearchScansForTesting()).isEqualTo(searchScans);
    }

    @Test
    void validating_an_attached_lane_does_not_rebuild_four_lane_factory_state() {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.SYNC);
        MachineControllerBlockEntity controller = asyncFactoryController(
                RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0)));
        FactoryRuntime runtime = controllerFactoryRuntime(controller);
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(4);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("factory_light_validation"), MMCR.id("test_cube"),
                20, List.of(), List.of(), List.of(), 0, 4);
        runtime.tick(List.of(recipe), 1, 0L);
        assertThat(runtime.activeRuntimes()).hasSize(4);
        FactorySnapshot before = runtime.snapshot();
        CraftingRuntime lane = runtime.activeRuntimes().getFirst();
        lane.activeRecipe().setTick(lane.tickCount() + 1);
        runtime.markLaneRuntimeChanged(lane);
        int builds = runtime.laneSnapshotBuildCountForTesting();

        assertThat(lane.versionsCurrent()).isTrue();

        assertThat(runtime.laneSnapshotBuildCountForTesting()).isEqualTo(builds);
        assertThat(before.lanes()).allSatisfy(state -> assertThat(state.tick()).isZero());
    }

    @Test
    void advancing_one_attached_lane_reuses_the_other_three_crafting_and_thread_snapshots() {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.SYNC);
        MachineControllerBlockEntity controller = asyncFactoryController(
                RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0)));
        FactoryRuntime runtime = controllerFactoryRuntime(controller);
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(4);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("factory_one_lane_snapshot"), MMCR.id("test_cube"),
                20, List.of(), List.of(), List.of(), 0, 4);
        runtime.tick(List.of(recipe), 1, 0L);
        assertThat(runtime.activeRuntimes()).hasSize(4);
        FactorySnapshot before = runtime.snapshot();
        int builds = runtime.laneSnapshotBuildCountForTesting();
        long epoch = runtime.stateEpoch();
        CraftingRuntime lane = runtime.activeRuntimes().getFirst();
        lane.activeRecipe().setTick(lane.tickCount() + 1);
        runtime.markLaneRuntimeChanged(lane);

        assertThat(runtime.stateEpoch()).isGreaterThan(epoch);
        FactorySnapshot after = runtime.snapshot();

        assertThat(after).isNotSameAs(before).isSameAs(runtime.snapshot());
        assertThat(after.lanes()).hasSize(4).isUnmodifiable();
        assertThat(after.presentationLanes()).hasSize(4).isUnmodifiable();
        assertThat(after.lanes().getFirst()).isNotSameAs(before.lanes().getFirst());
        assertThat(after.presentationLanes().getFirst()).isNotSameAs(before.presentationLanes().getFirst());
        assertThat(after.lanes().getFirst().tick()).isEqualTo(1);
        assertThat(after.presentationLanes().getFirst().tick()).isEqualTo(1);
        for (int index = 1; index < 4; index++) {
            assertThat(after.lanes().get(index)).isSameAs(before.lanes().get(index));
            assertThat(after.presentationLanes().get(index)).isSameAs(before.presentationLanes().get(index));
        }
        List<FactoryRuntime.ThreadSnapshot> threads = runtime.threadSnapshots();
        for (int index = 0; index < 4; index++) {
            assertThat(threads.get(index)).isSameAs(after.presentationLanes().get(index));
        }
        assertThat(runtime.laneSnapshotBuildCountForTesting() - builds).isEqualTo(1);
        assertThat(before.lanes()).allSatisfy(state -> assertThat(state.tick()).isZero());
        assertThat(before.presentationLanes()).allSatisfy(thread -> assertThat(thread.tick()).isZero());
    }

    @Test
    void many_lane_snapshots_collect_state_once_and_keep_previous_progress_on_pause_and_finish() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(16);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("many_lane_snapshot"), MMCR.id("test_cube"),
                20, List.of(), List.of(), List.of(), 0, 16);
        runtime.tick(List.of(recipe), 1, 0L);
        int builds = runtime.laneSnapshotBuildCountForTesting();
        FactorySnapshot started = runtime.snapshot();
        assertThat(started.lanes()).hasSize(16);
        assertThat(runtime.laneSnapshotBuildCountForTesting() - builds).isEqualTo(runtime.laneCount());
        assertThat(runtime.snapshot()).isSameAs(started);
        runtime.threadSnapshots();
        assertThat(runtime.laneSnapshotBuildCountForTesting() - builds).isEqualTo(runtime.laneCount());

        runtime.tick(List.of(recipe), 1, 1L);
        FactorySnapshot progressed = runtime.snapshot();
        assertThat(progressed.lanes()).allSatisfy(lane -> assertThat(lane.tick()).isGreaterThan(0));
        assertThat(started.lanes()).allSatisfy(lane -> assertThat(lane.tick()).isZero());
        for (int index = 0; index < progressed.lanes().size(); index++) {
            assertThat(progressed.presentationLanes().get(index).tick()).isEqualTo(progressed.lanes().get(index).tick());
            assertThat(progressed.presentationLanes().get(index).recipeId()).isEqualTo(recipe.id().toString());
        }

        runtime.pause();
        FactorySnapshot paused = runtime.snapshot();
        assertThat(paused.paused()).isTrue();
        assertThat(paused.lanes()).allSatisfy(lane -> assertThat(lane.status().isPaused()).isTrue());
        assertThat(progressed.lanes()).allSatisfy(lane -> assertThat(lane.status().isPaused()).isFalse());
        for (int index = 0; index < paused.lanes().size(); index++) {
            assertThat(paused.lanes().get(index)).isNotSameAs(progressed.lanes().get(index));
            assertThat(paused.presentationLanes().get(index)).isSameAs(progressed.presentationLanes().get(index));
        }
        String pausedMessage = paused.lanes().getFirst().status().getUnlocMessage();
        paused.lanes().getFirst().status().overrideStatusMessage("mmcr.test.consumer_mutation");
        assertThat(runtime.snapshot()).isSameAs(paused);
        assertThat(paused.lanes().getFirst().status().getUnlocMessage()).isEqualTo(pausedMessage);
        runtime.resume();
        FactorySnapshot resumed = runtime.snapshot();
        assertThat(resumed.paused()).isFalse();
        assertThat(resumed.lanes()).allSatisfy(lane -> assertThat(lane.status().isPaused()).isFalse());
        assertThat(paused.lanes()).allSatisfy(lane -> assertThat(lane.status().isPaused()).isTrue());
        for (int index = 0; index < resumed.lanes().size(); index++) {
            assertThat(resumed.lanes().get(index)).isNotSameAs(paused.lanes().get(index));
            assertThat(resumed.presentationLanes().get(index)).isSameAs(paused.presentationLanes().get(index));
        }
        List<CraftingRuntime> active = runtime.activeRuntimes();
        for (CraftingRuntime lane : active) {
            for (int step = 0; step < 20; step++) lane.tick();
            assertThat(lane.finish().isFailure()).isFalse();
            runtime.markLaneRuntimeChanged(lane);
        }
        FactorySnapshot finished = runtime.snapshot();
        assertThat(finished.active()).isFalse();
        assertThat(finished.presentationLanes()).allSatisfy(lane -> assertThat(lane.active()).isFalse());
        assertThat(paused.active()).isTrue();
        runtime.tick(List.of(recipe), 1, 2L);
        FactorySnapshot restarted = runtime.snapshot();
        assertThat(restarted.lanes()).hasSize(16).allSatisfy(lane -> assertThat(lane.tick()).isZero());
        assertThat(restarted.presentationLanes()).allSatisfy(lane -> assertThat(lane.active()).isTrue());
        for (int index = 0; index < restarted.presentationLanes().size(); index++) {
            assertThat(restarted.presentationLanes().get(index)).isNotSameAs(finished.presentationLanes().get(index));
        }
        assertThat(finished.presentationLanes()).allSatisfy(lane -> assertThat(lane.active()).isFalse());
    }

    @Test
    void lane_failure_and_effective_output_revision_refresh_presentation_without_mutating_old_snapshot() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.tick(List.of(recipe("factory_revision_snapshot", 20)), 1, 0L);
        FactorySnapshot before = runtime.snapshot();
        CraftingRuntime lane = runtime.activeRuntimes().getFirst();
        lane.activeRecipe().setEffectiveExecutionSnapshot(new RecipeStartContext.ExecutionSnapshot(
                20, List.of(), List.of(new MachineOutput.ItemOutput(new ItemStack(Items.IRON_NUGGET), 1F,
                DataComponentPredicateSet.EMPTY))));
        runtime.markLaneRuntimeChanged(lane);

        int builds = runtime.laneSnapshotBuildCountForTesting();
        FactorySnapshot outputs = runtime.snapshot();
        assertThat(outputs.lanes().getFirst()).isSameAs(before.lanes().getFirst());
        assertThat(runtime.laneSnapshotBuildCountForTesting()).isEqualTo(builds);
        assertThat(outputs.presentationLanes().getFirst()).isNotSameAs(before.presentationLanes().getFirst());
        assertThat(outputs.presentationLanes().getFirst().presentation().outputs()).hasSize(1);
        assertThat(before.presentationLanes().getFirst().presentation().outputs()).isEmpty();

        lane.recordSearchFailure(null);
        runtime.markLaneRuntimeChanged(lane);
        FactorySnapshot revised = runtime.snapshot();
        assertThat(revised.failure()).isNotNull();
        assertThat(revised.lanes().getFirst().failure()).isEqualTo(revised.failure());
        assertThat(revised.presentationLanes().getFirst().failure()).isEqualTo(revised.failure());
        assertThat(revised.presentationLanes().getFirst().presentation().outputs()).hasSize(1);
        assertThat(revised.lanes().getFirst()).isNotSameAs(outputs.lanes().getFirst());
        assertThat(revised.presentationLanes().getFirst()).isNotSameAs(outputs.presentationLanes().getFirst());
        assertThat(outputs.failure()).isNull();
        assertThat(before.failure()).isNull();
        assertThat(before.presentationLanes().getFirst().presentation().outputs()).isEmpty();
        assertThat(before.lanes().getFirst().failure()).isNull();
    }

    @Test
    void searchContextUsesTheConfiguredMachineRecipePoolCatalog() {
        ResourceLocation machineId = MMCR.id("factory_shared_pool_machine");
        ResourceLocation recipePoolId = MMCR.id("factory_shared_pool");
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(machineId).recipePoolId(recipePoolId).build());
        MachineControllerBlockEntity controller = factoryController(machineId.getPath());
        MachineRecipe candidate = RecipeTestSupport.create(MMCR.id("factory_shared_pool_recipe"), recipePoolId,
                20, List.of(), List.of());
        RecipeRegistry.registerStatic(candidate);
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);

        FactorySearchContext context = runtime.createSearchContext(controller.runtimeSnapshot(), List.of(candidate), 1, 0L);

        assertThat(context.catalogVersion()).isEqualTo(RecipeRegistry.catalogForPool(recipePoolId).version());
        assertThat(context.orderedCandidates()).containsExactly(candidate);
    }

    @Test
    void search_context_does_not_expose_a_recipe_from_a_different_pool() {
        MachineControllerBlockEntity controller = factoryController("test_cube");
        MachineRecipe foreign = RecipeTestSupport.create(MMCR.id("factory_foreign_pool_recipe"),
                MMCR.id("factory_foreign_pool"), 20, List.of(), List.of());
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);

        FactorySearchContext context = runtime.createSearchContext(controller.runtimeSnapshot(), List.of(foreign), 1, 0L);

        assertThat(context.orderedCandidates()).isEmpty();
    }

    @Test
    void indexed_candidate_without_current_input_still_publishes_failure_diagnostics() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        MachineRecipe candidate = itemInputRecipe("factory_index_missing_input", Items.IRON_INGOT);
        RecipeRegistry.registerStatic(candidate);
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);

        runtime.tick(List.of(candidate), 1, 0L);

        assertThat(runtime.threadSnapshots().getFirst().lastFailureUnloc())
                .isEqualTo("gui.mmcr.controller.failure.missing_input");
    }

    @Test
    void indexed_candidates_keep_fallback_recipes_when_exact_inputs_are_unavailable() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        MachineRecipe exact = itemInputRecipe("factory_index_exact", Items.IRON_INGOT);
        MachineRecipe fallback = recipe("factory_index_fallback", 20);
        RecipeRegistry.registerStatic(exact);
        RecipeRegistry.registerStatic(fallback);
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);

        runtime.tick(List.of(exact, fallback), 1, 0L);

        assertThat(runtime.activeRuntimes()).extracting(CraftingRuntime::recipe).containsExactly(fallback);
    }

    @Test
    void stage_one_controller_rejects_a_stage_two_recipe_before_starting() {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.SYNC);
        ResourceLocation machineId = MMCR.id("test_cube");
        MachineControllerBlockEntity controller = stagedController(1);
        FactoryRuntime runtime = new FactoryRuntime();
        MachineRecipe recipe = stageRecipe("factory_stage_one_rejected", machineId);
        runtime.ensureBaseLane(controller);

        assertThat(controller.runtimeSnapshot().structure().matchedStage()).isEqualTo(1);
        runtime.tick(List.of(recipe), 1, 0L);

        assertThat(runtime.activeRuntimes()).isEmpty();
        assertThat(runtime.threadSnapshots().getFirst().failure().reason().id())
                .isEqualTo(BuiltinFailureReasons.STAGE_INSUFFICIENT.id());
    }

    @Test
    void stage_two_controller_starts_a_stage_two_recipe() {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.SYNC);
        ResourceLocation machineId = MMCR.id("test_cube");
        MachineControllerBlockEntity controller = stagedController(2);
        FactoryRuntime runtime = new FactoryRuntime();
        MachineRecipe recipe = stageRecipe("factory_stage_two_started", machineId);
        runtime.ensureBaseLane(controller);

        assertThat(controller.runtimeSnapshot().structure().matchedStage()).isEqualTo(2);
        runtime.tick(List.of(recipe), 1, 0L);

        assertThat(runtime.activeRuntimes()).extracting(CraftingRuntime::recipe).containsExactly(recipe);
    }

    @Test
    void unstaged_controller_treats_stage_zero_as_stage_one() {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.SYNC);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        MachineRecipe recipe = stageRecipe("factory_stage_zero_rejected", MMCR.id("test_cube"));
        runtime.ensureBaseLane(controller);

        assertThat(controller.runtimeSnapshot().structure().matchedStage()).isZero();
        runtime.tick(List.of(recipe), 1, 0L);

        assertThat(runtime.activeRuntimes()).isEmpty();
        assertThat(runtime.threadSnapshots().getFirst().failure().reason().id())
                .isEqualTo(BuiltinFailureReasons.STAGE_INSUFFICIENT.id());
    }

    @Test
    void ticking_caps_each_active_lane_to_the_controller_parallelism() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(2);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("factory_parallel"), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 2, false,
                true, List.of(), false, Set.of());

        runtime.tick(List.of(recipe), 4);

        FactorySnapshot snapshot = runtime.snapshot();
        assertThat(snapshot.activeLaneCount()).isEqualTo(2);
        assertThat(snapshot.maxParallelism()).isEqualTo(4);
        assertThat(snapshot.presentationLanes()).allSatisfy(lane -> {
            assertThat(lane.active()).isTrue();
            assertThat(lane.parallelism()).isEqualTo(1L);
        });
    }

    @Test
    void tick_returns_the_aggregate_factory_result_and_caches_its_snapshot() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(2);

        FactoryTickResult result = runtime.tick(List.of(recipe("factory_tick_result", 20)), 1, 0L);

        assertThat(result.activeLaneCount()).isEqualTo(2);
        assertThat(result.factoryFailure()).isNull();
        assertThat(result.laneStateChanged()).isTrue();
        assertThat(result.snapshotChanged()).isTrue();
        FactorySnapshot snapshot = runtime.snapshot();
        assertThat(runtime.snapshot()).isSameAs(snapshot);
    }

    @Test
    void async_pending_start_is_not_active_before_shared_io_grants_it() {
        MachineControllerBlockEntity controller = factoryController("test_cube");
        ServerLevel level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(2);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("factory_async_pending_limit"), MMCR.id("test_cube"),
                20, List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of());

        runtime.tick(List.of(recipe), 1, 0L);

        assertThat(runtime.activeLaneCount()).isZero();
        resolveSharedRequests(controller);
        assertThat(runtime.activeLaneCount()).isEqualTo(1);
    }

    @Test
    void recipe_pool_change_discards_pending_factory_starts() {
        MachineControllerBlockEntity controller = factoryController("test_cube");
        ServerLevel level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(2);
        runtime.tick(List.of(recipe("factory_recipe_pool_pending_discard", 20)), 1, 0L);
        assertThat(runtime.activeLaneCount()).isZero();

        runtime.discardForRecipePoolChange();
        resolveSharedRequests(controller);

        assertThat(runtime.activeLaneCount()).isZero();
    }

    @Test
    void recipe_pool_change_discards_factory_work_without_removing_lane_configuration() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(2);
        runtime.tick(List.of(recipe("factory_recipe_pool_active_discard", 20)), 1, 0L);
        assertThat(runtime.activeRuntimes()).isNotEmpty();
        runtime.pause();
        int laneCount = runtime.laneCount();

        runtime.discardForRecipePoolChange();

        assertThat(runtime.activeRuntimes()).isEmpty();
        assertThat(runtime.laneCount()).isEqualTo(laneCount);
        assertThat(runtime.laneLimit()).isEqualTo(2);
        assertThat(runtime.isPaused()).isTrue();
    }

    @Test
    void unchanged_lane_limit_does_not_rebuild_the_factory_snapshot() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(2);
        FactorySnapshot snapshot = runtime.snapshot();
        int laneCount = runtime.laneCount();

        runtime.setLaneLimit(2);

        assertThat(runtime.laneCount()).isEqualTo(laneCount);
        assertThat(runtime.snapshot()).isSameAs(snapshot);
    }

    @Test
    void clearing_and_rebuilding_lanes_drops_cached_thread_snapshots() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(2);
        MachineRecipe recipe = recipe("factory_snapshot_clear", 20);
        runtime.tick(List.of(recipe), 1, 0L);
        FactorySnapshot before = runtime.snapshot();
        Map<FactoryRecipeThread, FactoryRuntime.ThreadSnapshot> cache = laneThreadSnapshots(runtime);
        List<FactoryRecipeThread> previousLanes = List.copyOf(cache.keySet());
        assertThat(cache).hasSize(2);

        runtime.clear();

        assertThat(cache).isEmpty();
        assertThat(runtime.snapshot().lanes()).isEmpty();
        runtime.ensureBaseLane(controller);
        runtime.tick(List.of(recipe), 1, 1L);
        FactorySnapshot rebuilt = runtime.snapshot();
        assertThat(cache).hasSize(2);
        assertThat(cache.keySet()).doesNotContainAnyElementsOf(previousLanes);
        assertThat(rebuilt.presentationLanes().getFirst().laneId()).isEqualTo(before.presentationLanes().getFirst().laneId());
        assertThat(rebuilt.presentationLanes().getFirst()).isNotSameAs(before.presentationLanes().getFirst());
        assertThat(before.presentationLanes()).allSatisfy(lane -> assertThat(lane.active()).isTrue());
    }

    @Test
    void reordering_and_removing_core_lanes_updates_indexes_and_drops_removed_cache_entries() {
        ResourceLocation machineId = MMCR.id("test_cube");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(machineId);
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.setLaneLimit(3);
        FactoryThreadSpec first = new FactoryThreadSpec("first", List.of());
        FactoryThreadSpec second = new FactoryThreadSpec("second", List.of());
        DynamicMachine machine = new DynamicMachine(machineId, "factory snapshot order", new BlockArray(Map.of()),
                MachineControllerSpec.defaultsFor(machineId), MachineAppearanceSpec.defaults(),
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of(), 1, false, true, 3,
                List.of(first, second), List.of());
        runtime.syncCoreLanes(controller, machine, List.of());
        FactorySnapshot before = runtime.snapshot();
        Map<FactoryRecipeThread, FactoryRuntime.ThreadSnapshot> cache = laneThreadSnapshots(runtime);
        FactoryRecipeThread firstLane = cache.keySet().stream()
                .filter(lane -> lane.laneId().equals("core-first")).findFirst().orElseThrow();
        FactoryRecipeThread secondLane = cache.keySet().stream()
                .filter(lane -> lane.laneId().equals("core-second")).findFirst().orElseThrow();
        DynamicMachine reordered = new DynamicMachine(machineId, "factory snapshot order", new BlockArray(Map.of()),
                MachineControllerSpec.defaultsFor(machineId), MachineAppearanceSpec.defaults(),
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of(), 1, false, true, 3,
                List.of(second, first), List.of());

        runtime.syncCoreLanes(controller, reordered, List.of());
        FactorySnapshot after = runtime.snapshot();

        assertThat(cache).hasSize(3).containsKeys(firstLane, secondLane);
        assertThat(after.presentationLanes().getFirst()).isSameAs(before.presentationLanes().getFirst());
        assertThat(after.presentationLanes().get(1).laneId()).isEqualTo("core-second");
        assertThat(after.presentationLanes().get(1).index()).isEqualTo(1);
        assertThat(after.presentationLanes().get(1)).isNotSameAs(before.presentationLanes().get(2));
        assertThat(after.presentationLanes().get(1).presentation()).isSameAs(before.presentationLanes().get(2).presentation());
        assertThat(after.presentationLanes().get(2).laneId()).isEqualTo("core-first");
        assertThat(after.presentationLanes().get(2).index()).isEqualTo(2);
        assertThat(after.presentationLanes().get(2)).isNotSameAs(before.presentationLanes().get(1));
        assertThat(before.presentationLanes().get(1).index()).isEqualTo(1);
        assertThat(before.presentationLanes().get(2).index()).isEqualTo(2);

        runtime.syncCoreLanes(controller, null, List.of());

        assertThat(cache).hasSize(1).doesNotContainKeys(firstLane, secondLane);
        assertThat(runtime.snapshot().presentationLanes().getFirst()).isSameAs(before.presentationLanes().getFirst());
    }

    @Test
    void lowering_lane_limit_keeps_active_lanes_until_they_finish() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(2);
        MachineRecipe recipe = recipe("factory_remove", 2);
        runtime.tick(List.of(recipe), 1);
        CraftingRuntime removed = runtime.activeRuntimes().getLast();
        runtime.snapshot();
        Map<FactoryRecipeThread, FactoryRuntime.ThreadSnapshot> cache = laneThreadSnapshots(runtime);
        FactoryRecipeThread removedLane = cache.keySet().stream()
                .filter(lane -> lane.runtime() == removed).findFirst().orElseThrow();

        runtime.setLaneLimit(1);

        assertThat(runtime.activeLaneCount()).isEqualTo(2);
        assertThat(runtime.contains(removed)).isTrue();
        assertThat(removed.active()).isTrue();
        assertThat(cache).containsKey(removedLane);

        runtime.tick(List.of(recipe), 1, 1L);
        runtime.tick(List.of(recipe), 1, 2L);

        assertThat(runtime.laneCount()).isEqualTo(1);
        assertThat(runtime.contains(removed)).isFalse();
        assertThat(cache).doesNotContainKey(removedLane);
    }

    @Test
    void pattern_reservations_count_only_the_same_recipe_for_thread_admission() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(2);
        MachineRecipe first = RecipeTestSupport.create(MMCR.id("factory_reserved_first"), MMCR.id("test_cube"),
                20, List.of(), List.of(), List.of(), 0, 1);
        MachineRecipe second = RecipeTestSupport.create(MMCR.id("factory_reserved_second"), MMCR.id("test_cube"),
                20, List.of(), List.of(), List.of(), 0, 1);

        assertThat(runtime.reservePatternStart(first, 1L, List.of())).isNotNull();
        assertThat(runtime.reservePatternStart(second, 1L, List.of())).isNotNull();
    }

    @Test
    void idle_dynamic_lanes_are_cleaned_up_after_their_timeout() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(2);

        runtime.tick(List.of(recipe("factory_idle_cleanup", 1)), 1);
        assertThat(runtime.laneCount()).isEqualTo(2);
        runtime.snapshot();
        Map<FactoryRecipeThread, FactoryRuntime.ThreadSnapshot> cache = laneThreadSnapshots(runtime);
        FactoryRecipeThread removedLane = cache.keySet().stream()
                .filter(lane -> !lane.isBaseThread()).findFirst().orElseThrow();
        for (int tick = 1; tick <= 201; tick++) runtime.tick(List.of(), 1, tick);

        assertThat(runtime.laneCount()).isEqualTo(1);
        assertThat(runtime.activeLaneCount()).isZero();
        assertThat(cache).hasSize(1).doesNotContainKey(removedLane);
    }

    @Test
    void shared_input_is_reserved_by_only_one_active_lane() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        ItemStack stack = new ItemStack(Items.IRON_INGOT, 1);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        setItem(input.itemHandler(), 0, stack);
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(2);

        runtime.tick(List.of(RecipeTestSupport.create(MMCR.id("factory_shared_input"), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 2, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                        ItemStack.EMPTY)))), 1);

        assertThat(runtime.activeLaneCount()).isEqualTo(1);
        assertThat(input.itemHandler().amount(0)).isZero();
    }

    @Test
    void async_worker_fallback_replans_and_commits_at_available_parallelism() {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = asyncFactoryController(input);
        setItem(input.itemHandler(), 0, new ItemStack(Items.IRON_INGOT, 1));
        ServerLevel level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);

        runtime.tick(List.of(inputRecipe("async_worker_fallback")), 2, level.getGameTime());
        resolveSharedRequests(controller);

        assertThat(runtime.activeRuntimes()).singleElement().satisfies(active -> {
            assertThat(active.parallelism()).isEqualTo(1L);
            assertThat(active.recipe().id()).isEqualTo(MMCR.id("async_worker_fallback"));
        });
        assertThat(input.itemHandler().amount(0)).isZero();
    }

    @Test
    void async_planning_values_delay_a_less_specific_candidate_when_a_competing_input_is_missing() {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = asyncFactoryController(input);
        setItem(input.itemHandler(), 0, new ItemStack(Items.IRON_INGOT, 1));
        ServerLevel level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        MachineRecipe specific = RecipeTestSupport.create(MMCR.id("async_specific_candidate"), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY),
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.GOLD_INGOT), 1, ItemStack.EMPTY)));
        MachineRecipe fallback = inputRecipe("async_fallback_candidate");

        runtime.tick(List.of(specific, fallback), 1, level.getGameTime());
        resolveSharedRequests(controller);

        assertThat(runtime.activeRuntimes()).isEmpty();
    }

    @Test
    void finished_lane_restarts_from_the_queued_candidate() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        MachineRecipe recipe = recipe("factory_restart", 1);

        runtime.tick(List.of(recipe), 1);
        runtime.tick(List.of(recipe), 1);

        assertThat(runtime.activeLaneCount()).isEqualTo(1);
        assertThat(runtime.activeRuntimes().getFirst().recipe()).isEqualTo(recipe);
        assertThat(runtime.activeRuntimes().getFirst().tickCount()).isZero();
    }

    @Test
    void finished_lane_restarts_its_last_recipe_before_candidate_ordering() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        MachineRecipe cached = recipe("z_factory_cached", 1);
        MachineRecipe fallback = recipe("a_factory_fallback", 1);

        runtime.tick(List.of(cached), 1);
        runtime.tick(List.of(fallback, cached), 1);

        assertThat(runtime.activeRuntimes()).hasSize(1);
        assertThat(runtime.activeRuntimes().getFirst().recipe()).isEqualTo(cached);
    }

    // Take out
    // @ParameterizedTest
    // @EnumSource(MachineWorkMode.class)
    // void factory_lane_continues_the_last_recipe_without_an_idle_boundary(MachineWorkMode mode) {
    //     assertFactoryLaneContinuesLastRecipe(mode, false);
    // }

    // @ParameterizedTest
    // @EnumSource(MachineWorkMode.class)
    // void locked_factory_lane_continues_the_last_recipe_without_an_idle_boundary(MachineWorkMode mode) {
    //     assertFactoryLaneContinuesLastRecipe(mode, true);
    // }

    private void assertFactoryLaneContinuesLastRecipe(MachineWorkMode mode) {
        MachineControllerBlockEntity controller = factoryController("test_cube");
        ServerLevel level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        MachineRecipe recipe = recipe("factory_last_recipe_continuation", 1);
        RecipeRegistry.registerStatic(recipe);
        ConfigTestSupport.setMachineWorkMode(mode);

        runtime.tick(List.of(recipe), 1, level.getGameTime());
        resolveSharedRequests(controller);
        assertThat(runtime.activeRuntimes()).hasSize(1);
        for (int tick = 0; tick < 4; tick++) {
            RuntimeTestFixtures.advanceGameTime(level);
            runtime.tick(List.of(recipe), 1, level.getGameTime());
            resolveSharedRequests(controller);
            assertThat(runtime.activeRuntimes()).hasSize(1);
            assertThat(runtime.activeRuntimes().getFirst().recipe()).isEqualTo(recipe);
        }

        assertThat(runtime.searchAttemptsForTesting()).isEqualTo(1L);
    }

    @Test
    void completed_lane_does_not_restart_a_recipe_removed_from_the_current_catalog() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        MachineRecipe removed = recipe("z_factory_removed", 1);
        MachineRecipe replacement = recipe("a_factory_replacement", 1);
        RecipeRegistry.replaceDynamic(Map.of(removed.id(), removed));

        runtime.tick(List.of(removed), 1, 0L);
        RecipeRegistry.replaceDynamic(Map.of(replacement.id(), replacement));
        runtime.tick(List.of(removed, replacement), 1, 1L);

        assertThat(runtime.activeRuntimes()).extracting(CraftingRuntime::recipe)
                .containsExactly(replacement);
    }

    @Test
    void factory_lane_missing_energy_is_not_reported_as_missing_input() {
        EnergyInputHatchBlockEntity energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), energy);
        energy.energyStorage().setAmount(2);
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("factory_missing_energy"), MMCR.id("test_cube"), 3,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(new EnergyRequirement(2)));

        runtime.tick(List.of(recipe), 1);
        runtime.tick(List.of(recipe), 1);

        assertThat(runtime.snapshot().presentationLanes().getFirst().lastFailureUnloc())
                .isEqualTo("gui.mmcr.controller.failure.missing_energy");
    }

    @Test
    void failureInOneLaneIsPublishedWithoutDiscardingOtherActiveLanes() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        ItemStack stack = new ItemStack(Items.IRON_INGOT, 1);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        setItem(input.itemHandler(), 0, stack);
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(2);
        MachineRecipe failingRecipe = RecipeTestSupport.create(MMCR.id("factory_isolated_failure"), MMCR.id("test_cube"),
                20, List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                        ItemStack.EMPTY, 1F, List.of(), DataComponentPredicateSet.EMPTY, 0F)));
        MachineRecipe survivorRecipe = recipe("factory_isolated_survivor", 20);
        List<MachineRecipe> candidates = List.of(failingRecipe, survivorRecipe);
        runtime.tick(candidates, 1);
        assertThat(runtime.activeLaneCount()).isEqualTo(2);

        CraftingRuntime failed = runtime.activeRuntimes().getFirst();
        CraftingRuntime survivor = runtime.activeRuntimes().getLast();
        setItem(input.itemHandler(), 0, ItemStack.EMPTY);
        runtime.tick(candidates, 1);

        FactorySnapshot snapshot = runtime.snapshot();
        assertThat(failed.recipe()).isEqualTo(failingRecipe);
        assertThat(failed.failure()).isNotNull();
        assertThat(snapshot.failure()).isNotNull();
        assertThat(snapshot.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_INPUT);
        assertThat(runtime.activeLaneCount()).isEqualTo(2);
        assertThat(failed.active()).isTrue();
        assertThat(survivor.active()).isTrue();
        assertThat(survivor.failure()).isNull();
        assertThat(input.itemHandler().amount(0)).isZero();
        assertThat(snapshot.presentationLanes()).hasSize(2);
        assertThat(snapshot.presentationLanes().get(0).active()).isTrue();
        assertThat(snapshot.lanes().get(0).failure()).isNotNull();
        assertThat(snapshot.lanes().get(0).failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_INPUT);
        assertThat(snapshot.presentationLanes().get(1).active()).isTrue();
        assertThat(snapshot.lanes().get(1).failure()).isNull();
        assertThat(snapshot.presentationLanes().get(1).lastFailureUnloc()).isEmpty();
        assertThat(snapshot.presentationLanes().get(0).recipeId()).isEqualTo(failingRecipe.id().toString());
        assertThat(snapshot.presentationLanes().get(1).recipeId()).isEqualTo(survivorRecipe.id().toString());
    }

    @Test
    void smart_interface_change_keeps_active_factory_lanes_on_their_effective_snapshots() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(2);
        MachineRecipe recipe = recipe("factory_smart_interface_change", 20);

        runtime.tick(List.of(recipe), 1);
        assertThat(runtime.activeLaneCount()).isEqualTo(2);

        runtime.invalidateForSmartInterfaceChange();

        assertThat(runtime.activeLaneCount()).isEqualTo(2);
        assertThat(runtime.snapshot().presentationLanes()).allSatisfy(lane -> {
            assertThat(lane.active()).isTrue();
            assertThat(lane.lastFailureUnloc()).isEmpty();
        });
        assertThat(runtime.snapshot().failure()).isNull();
    }

    @Test
    void runtimeStateRoundTripsThroughValuePersistence() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe recipe = recipe("factory_persisted", 20);
        RecipeRegistry.registerStatic(recipe);
        FactoryRuntime saved = new FactoryRuntime();
        saved.ensureBaseLane(controller);
        saved.setLaneLimit(2);
        saved.tick(List.of(recipe), 2);
        saved.pause();
        FactorySnapshot before = saved.snapshot();

        CompoundTag output = new CompoundTag();
        saved.save(output, EMPTY_LOOKUP);
        FactoryRuntime restored = new FactoryRuntime();
        restored.ensureBaseLane(controller);
        restored.snapshot();
        Map<FactoryRecipeThread, FactoryRuntime.ThreadSnapshot> cache = laneThreadSnapshots(restored);
        FactoryRecipeThread previousBase = cache.keySet().iterator().next();
        restored.load(output, controller, EMPTY_LOOKUP);

        assertThat(cache).isEmpty();
        assertThat(restored.laneLimit()).isEqualTo(2);
        assertThat(restored.isPaused()).isTrue();
        assertThat(restored.laneCount()).isEqualTo(2);
        FactorySnapshot loaded = restored.snapshot();
        assertThat(loaded.presentationLanes()).hasSize(2);
        assertThat(loaded.presentationLanes().getFirst().laneId()).isEqualTo(before.presentationLanes().getFirst().laneId());
        assertThat(loaded.presentationLanes().getFirst()).isNotSameAs(before.presentationLanes().getFirst());
        assertThat(cache).hasSize(2).doesNotContainKey(previousBase);
    }

    @Test
    void loadingCorruptLaneCountsIsBoundedBeforeAllocatingLanes() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CompoundTag output = new CompoundTag();
        output.putInt("lane_limit", Integer.MAX_VALUE);
        output.putInt("lane_count", 1025);

        FactoryRuntime restored = new FactoryRuntime();
        restored.load(output, controller, EMPTY_LOOKUP);

        assertThat(restored.laneLimit()).isLessThanOrEqualTo(1024);
        assertThat(restored.laneCount()).isLessThanOrEqualTo(1024);
    }

    @Test
    void factory_search_exception_becomes_runtime_failure_instead_of_escaping() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        List<MachineRecipe> candidates = new ArrayList<>();
        candidates.add(null);

        assertThatCode(() -> thread.searchAndStartRecipe(candidates, 1, 0L)).doesNotThrowAnyException();
        assertThat(thread.runtime().failure()).isNotNull();
        assertThat(thread.runtime().failure().reason()).isEqualTo(BuiltinFailureReasons.RECIPE_SEARCH);
    }

    @Test
    void failed_lane_searches_only_at_the_initial_and_fifth_tick() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        MachineRecipe candidate = inputRecipe("factory_retry_schedule");

        for (long gameTime = 0; gameTime <= 5; gameTime++) {
            runtime.tick(List.of(candidate), 1, gameTime);
        }

        assertThat(runtime.searchAttemptsForTesting()).isEqualTo(2);
    }

    @Test
    void unchanged_failed_lane_does_not_read_candidates_before_its_retry_tick() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        MachineRecipe candidate = inputRecipe("factory_idle_fast_path");

        runtime.tick(List.of(candidate), 1L, 0L);
        List<MachineRecipe> unreadableCandidates = new AbstractList<>() {
            @Override
            public MachineRecipe get(int index) {
                throw new AssertionError("candidate list was read");
            }

            @Override
            public int size() {
                throw new AssertionError("candidate list was read");
            }
        };

        runtime.tick(unreadableCandidates, 1L, 1L);

        assertThat(runtime.searchAttemptsForTesting()).isEqualTo(1L);
    }

    @Test
    void sleeping_dynamic_lane_does_not_force_candidate_context_rebuild() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(2);
        runtime.tick(List.of(recipe("factory_dynamic_sleep", 1)), 1L, 0L);
        runtime.tick(List.of(inputRecipe("factory_dynamic_sleep_blocked")), 1L, 1L);
        List<MachineRecipe> unreadableCandidates = new AbstractList<>() {
            @Override
            public MachineRecipe get(int index) {
                throw new AssertionError("candidate list was read");
            }

            @Override
            public int size() {
                throw new AssertionError("candidate list was read");
            }
        };

        runtime.tick(unreadableCandidates, 1L, 2L);

        assertThat(runtime.laneCount()).isEqualTo(2);
    }

    @Test
    void failed_lane_retries_a_same_id_recipe_from_the_new_catalog_on_the_next_tick() {
        ResourceLocation recipeId = MMCR.id("factory_reload_failed_lane");
        MachineRecipe oldRecipe = itemInputRecipe(recipeId.getPath(), Items.IRON_INGOT);
        MachineRecipe newRecipe = recipe(recipeId.getPath(), 20);
        RecipeRegistry.replaceDynamic(Map.of(recipeId, oldRecipe));

        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.tick(List.of(oldRecipe), 1, 0L);

        RecipeRegistry.replaceDynamic(Map.of(recipeId, newRecipe));
        runtime.tick(List.of(newRecipe), 1, 1L);

        assertThat(runtime.activeRuntimes()).extracting(CraftingRuntime::recipe).containsExactly(newRecipe);
    }

    @Test
    void pending_start_is_discarded_when_the_recipe_catalog_changes_before_resolution() {
        MachineControllerBlockEntity controller = factoryController("test_cube");
        ServerLevel level = (ServerLevel) controller.getLevel();
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        assertThat(registry.claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        StructureClaimRegistry.ResourceDomain domain = controller.resourceDomain();
        assertThat(domain).isNotNull();

        ResourceLocation recipeId = MMCR.id("factory_reload_pending_recipe");
        MachineRecipe oldRecipe = recipe(recipeId.getPath(), 20);
        MachineRecipe newRecipe = RecipeTestSupport.create(recipeId, MMCR.id("test_cube"), 40,
                List.of(), List.of());
        RecipeRegistry.replaceDynamic(Map.of(recipeId, oldRecipe));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);

        assertThat(thread.searchAndStartRecipe(List.of(oldRecipe), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
        assertThat(thread.isStartPending()).isTrue();

        RecipeRegistry.replaceDynamic(Map.of(recipeId, newRecipe));
        resolveSharedRequests(controller);

        assertThat(thread.runtime().active()).isFalse();
        assertThat(thread.isStartPending()).isFalse();
    }

    @Test
    void catalog_change_before_shared_tick_commit_stops_the_old_recipe_without_advancing_it() {
        ResourceLocation recipeId = MMCR.id("factory_reload_pending_tick_recipe");
        MachineRecipe oldRecipe = RecipeTestSupport.create(recipeId, MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(cn.howxu.mmcr.api.recipe.requirement.StageRequirement.input(1)));
        MachineRecipe replacement = RecipeTestSupport.create(recipeId, MMCR.id("test_cube"), 40,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(cn.howxu.mmcr.api.recipe.requirement.StageRequirement.input(1)));
        RecipeRegistry.replaceDynamic(Map.of(recipeId, oldRecipe));

        MachineControllerBlockEntity controller = factoryController("test_cube");
        ServerLevel level = (ServerLevel) controller.getLevel();
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        assertThat(registry.claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);

        assertThat(thread.searchAndStartRecipe(List.of(oldRecipe), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
        resolveSharedRequests(controller);
        assertThat(thread.runtime().tickCount()).isZero();

        thread.tick();
        RecipeRegistry.replaceDynamic(Map.of(recipeId, replacement));
        resolveSharedRequests(controller);

        assertThat(thread.runtime().active()).isFalse();
        assertThat(thread.runtime().tickCount()).isZero();
        assertThat(thread.runtime().failure()).isNotNull();
        assertThat(thread.runtime().failure().reason()).isEqualTo(BuiltinFailureReasons.VERSION_INVALIDATED);
    }

    @Test
    void catalog_change_after_shared_tick_commit_invalidates_before_async_continuation() {
        ResourceLocation recipeId = MMCR.id("factory_reload_async_tick_recipe");
        MachineRecipe oldRecipe = RecipeTestSupport.create(recipeId, MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(cn.howxu.mmcr.api.recipe.requirement.StageRequirement.input(1)));
        MachineRecipe replacement = RecipeTestSupport.create(recipeId, MMCR.id("test_cube"), 40,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(cn.howxu.mmcr.api.recipe.requirement.StageRequirement.input(1)));
        RecipeRegistry.replaceDynamic(Map.of(recipeId, oldRecipe));

        MachineControllerBlockEntity controller = factoryController("test_cube");
        ServerLevel level = (ServerLevel) controller.getLevel();
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        assertThat(registry.claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);

        assertThat(thread.searchAndStartRecipe(List.of(oldRecipe), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
        resolveSharedRequests(controller);
        thread.tick();
        SharedIoCoordinator.get(level).resolve(controller.resourceDomain());
        RecipeRegistry.replaceDynamic(Map.of(recipeId, replacement));
        MachineAsyncCoordinator.get(level).completeUntilIdleForTesting(() -> SharedIoCoordinator.get(level).resolve(level));

        assertThat(thread.runtime().active()).isFalse();
        assertThat(thread.runtime().tickCount()).isZero();
        assertThat(thread.runtime().failure()).isNotNull();
        assertThat(thread.runtime().failure().reason()).isEqualTo(BuiltinFailureReasons.VERSION_INVALIDATED);
    }

    @Test
    void loading_a_last_recipe_does_not_use_the_global_registry_fallback() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        ResourceLocation recipeId = MMCR.id("factory_foreign_last_recipe");
        ResourceLocation foreignPool = MMCR.id("factory_foreign_last_pool");
        RuntimeTestFixtures.registerRecipePool(foreignPool);
        MachineRecipe foreign = RecipeTestSupport.create(recipeId, foreignPool, 20, List.of(), List.of());
        RecipeRegistry.replaceDynamic(Map.of(recipeId, foreign));

        CompoundTag output = new CompoundTag();
        output.putBoolean("has_last", true);
        output.putString("last_recipe", recipeId.toString());

        FactoryRecipeThread restored = FactoryRecipeThread.load(output, controller, EMPTY_LOOKUP, List.of());

        assertThat(restored.lastRecipeId()).isNull();
    }

    @Test
    void rebinding_versions_clears_a_last_recipe_from_the_previous_machine_pool() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        MachineRecipe recipe = recipe("factory_pool_rebind", 20);

        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
        thread.runtime().invalidate();
        ResourceLocation foreignMachineId = MMCR.id("factory_foreign_pool_machine");
        RuntimeTestFixtures.registerRecipePool(foreignMachineId);
        controller.setMachine(new DynamicMachine(foreignMachineId, "foreign pool machine", new BlockArray(Map.of())));

        thread.rebindCurrentVersions();

        assertThat(thread.lastRecipeId()).isNull();
    }

    @Test
    void loading_replaced_embedded_recipe_fails_before_searching_the_current_catalog() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe oldRecipe = RecipeTestSupport.create(MMCR.id("factory_loaded_old"), MMCR.id("test_cube"), 1,
                List.of(), List.of());
        MachineRecipe replacement = RecipeTestSupport.create(oldRecipe.id(), oldRecipe.recipePoolId(), 20,
                List.of(), List.of());
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);

        assertThat(thread.searchAndStartRecipe(List.of(oldRecipe), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
        CompoundTag output = new CompoundTag();
        thread.save(output, EMPTY_LOOKUP);
        RecipeRegistry.replaceDynamic(Map.of(replacement.id(), replacement));

        FactoryRecipeThread restored = FactoryRecipeThread.load(output, controller, EMPTY_LOOKUP, List.of(replacement));
        var snapshot = controller.runtimeSnapshot();

        assertThat(restored.runtime().active()).isFalse();
        assertThat(restored.runtime().failure()).isNotNull();
        assertThat(restored.runtime().failure().reason()).isEqualTo(BuiltinFailureReasons.RECIPE_LOAD);
        assertThat(restored.searchAndStartRecipe(List.of(replacement), 1,
                snapshot.structure().version())).isTrue();
        assertThat(restored.runtime().recipe().tickTime()).isEqualTo(20);
    }

    @Test
    void async_catalog_invalidation_stops_the_old_recipe_before_retrying_the_current_catalog() {
        ResourceLocation machineId = MMCR.id("test_cube");
        ResourceLocation recipeId = MMCR.id("factory_reload_completion_recipe");
        MachineRecipe oldRecipe = RecipeTestSupport.create(recipeId, machineId, 1,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(cn.howxu.mmcr.api.recipe.requirement.StageRequirement.input(1)));
        MachineRecipe newRecipe = RecipeTestSupport.create(recipeId, machineId, 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(cn.howxu.mmcr.api.recipe.requirement.StageRequirement.input(1)));
        RecipeRegistry.replaceDynamic(Map.of(recipeId, oldRecipe));

        MachineControllerBlockEntity controller = factoryController(machineId.getPath());
        ServerLevel level = (ServerLevel) controller.getLevel();
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        assertThat(registry.claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);

        runtime.tick(List.of(oldRecipe), 1, 0L);
        resolveSharedRequests(controller);
        assertThat(runtime.activeRuntimes()).extracting(CraftingRuntime::recipe).containsExactly(oldRecipe);

        runtime.tick(List.of(oldRecipe), 1, 1L);
        RecipeRegistry.replaceDynamic(Map.of(recipeId, newRecipe));
        resolveSharedRequests(controller);

        assertThat(runtime.activeRuntimes()).isEmpty();
        runtime.tick(List.of(newRecipe), 1, 2L);
        assertThat(runtime.threadSnapshots().getFirst().failure()).isNotNull();
        assertThat(runtime.threadSnapshots().getFirst().failure().reason())
                .isEqualTo(BuiltinFailureReasons.VERSION_INVALIDATED);

        runtime.tick(List.of(newRecipe), 1, 6L);
        resolveSharedRequests(controller);

        assertThat(runtime.activeRuntimes()).extracting(CraftingRuntime::recipe).containsExactly(newRecipe);
    }

    @Test
    void input_availability_wakes_a_failed_lane_before_its_backoff_expires() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        MachineRecipe candidate = inputRecipe("factory_input_wakeup");

        runtime.tick(List.of(candidate), 1, 0L);
        runtime.wakeSearches(Reason.INPUT_AVAILABLE, new ItemStack(Items.IRON_INGOT));
        runtime.tick(List.of(candidate), 1, 1L);

        assertThat(runtime.searchAttemptsForTesting()).isEqualTo(2);
    }

    @Test
    void input_availability_wakeup_requires_a_matching_resource() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        MachineRecipe candidate = inputRecipe("factory_resource_targeted_wakeup");

        runtime.tick(List.of(candidate), 1, 0L);
        runtime.wakeSearches(Reason.INPUT_AVAILABLE, new ItemStack(Items.GOLD_INGOT));
        runtime.tick(List.of(candidate), 1, 1L);
        assertThat(runtime.searchAttemptsForTesting()).isEqualTo(1);

        runtime.wakeSearches(Reason.INPUT_AVAILABLE, null);
        runtime.tick(List.of(candidate), 1, 2L);
        assertThat(runtime.searchAttemptsForTesting()).isEqualTo(1);

        runtime.wakeSearches(Reason.INPUT_AVAILABLE, new ItemStack(Items.IRON_INGOT));
        runtime.tick(List.of(candidate), 1, 3L);

        assertThat(runtime.searchAttemptsForTesting()).isEqualTo(2);
    }

    @Test
    void unrelated_or_unknown_availability_reasons_do_not_wake_an_input_failure() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        MachineRecipe candidate = inputRecipe("factory_targeted_wakeup");

        runtime.tick(List.of(candidate), 1, 0L);
        runtime.wakeSearches(Reason.ENERGY_AVAILABLE, null);
        runtime.tick(List.of(candidate), 1, 1L);
        runtime.wakeSearches(Reason.OUTPUT_CAPACITY, null);
        runtime.tick(List.of(candidate), 1, 2L);
        runtime.wakeSearches(null, null);
        runtime.tick(List.of(candidate), 1, 3L);

        assertThat(runtime.searchAttemptsForTesting()).isEqualTo(1);

        FactoryRecipeThread unknown = FactoryRecipeThread.simple(controller, "unknown-failure");
        List<MachineRecipe> invalidCandidates = new ArrayList<>();
        invalidCandidates.add(null);
        assertThat(unknown.searchAndStartRecipe(invalidCandidates, 1, 0L)).isFalse();
        assertThat(unknown.searchFailureReason()).isEqualTo(BuiltinFailureReasons.RECIPE_SEARCH.id());
        assertThat(unknown.matchesAvailability(Reason.OUTPUT_CAPACITY, null)).isFalse();
    }

    @Test
    void shared_finish_release_wakes_output_capacity_lane_on_the_next_tick() {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.SYNC);
        ResourceLocation machineId = MMCR.id("test_cube");
        ResourceLocation activeId = MMCR.id("shared_finish_release_active");
        ResourceLocation blockedId = MMCR.id("shared_finish_release_blocked");
        MachineRecipe active = RecipeTestSupport.create(activeId, machineId, 1,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of());
        MachineRecipe blocked = RecipeTestSupport.create(blockedId, machineId, 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        new ItemStack(Items.IRON_NUGGET, 1))), false, List.of(), true);
        RecipeRegistry.registerStatic(active);
        RecipeRegistry.registerStatic(blocked);

        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = sharedFactoryController(machineId, blockedId, output);
        for (int slot = 0; slot < output.itemHandler().size(); slot++) {
            setItem(output.itemHandler(), slot, new ItemStack(Items.COBBLESTONE, 64));
        }

        assertThat(controller.hasFactoryController()).isTrue();
        assertThat(controller.structureSnapshot().machine().factoryThreads()).hasSize(1);
        FactoryRuntime runtime = controllerFactoryRuntime(controller);
        runtime.setLaneLimit(2);
        runtime.syncCoreLanes(controller, controller.structureSnapshot().machine(), List.of(active, blocked));
        runtime.tick(List.of(active, blocked), 1, 1L);
        resolveSharedRequests(controller);
        assertThat(runtime.activeLaneCount()).isEqualTo(1);
        assertThat(runtime.threadSnapshots().get(1).lastFailureUnloc())
                .isEqualTo("gui.mmcr.controller.failure.missing_output");

        long beforeFinishEpoch = controller.resourceAvailabilityEpoch();
        runtime.tick(List.of(active, blocked), 1, 2L);
        resolveSharedRequests(controller);
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeFinishEpoch + 1L);

        setItem(output.itemHandler(), 0, ItemStack.EMPTY);
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeFinishEpoch + 1L);
        RuntimeTestFixtures.advanceGameTime(controller.getLevel());
        runtime.tick(List.of(active, blocked), 1, 3L);
        resolveSharedRequests(controller);

        assertThat(runtime.activeLaneCount()).isEqualTo(2);
        assertThat(runtime.threadSnapshots())
                .anySatisfy(lane -> assertThat(lane.recipeId()).isEqualTo(blockedId.toString()));
    }

    @Test
    void output_energy_capacity_wakes_a_failed_output_energy_lane() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        MachineRecipe candidate = outputEnergyRecipe("factory_output_energy_wakeup");

        runtime.tick(List.of(candidate), 1, 0L);
        runtime.wakeSearches(Reason.OUTPUT_CAPACITY, new CapabilityType(EnergyRequirement.TYPE.id()));
        runtime.tick(List.of(candidate), 1, 1L);

        assertThat(runtime.searchAttemptsForTesting()).isEqualTo(2);
    }

    @Test
    void output_capacity_does_not_wake_a_failed_input_energy_lane() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        MachineRecipe candidate = inputEnergyRecipe("factory_input_energy_output_event");

        runtime.tick(List.of(candidate), 1, 0L);
        runtime.wakeSearches(Reason.OUTPUT_CAPACITY, new CapabilityType(EnergyRequirement.TYPE.id()));
        runtime.tick(List.of(candidate), 1, 1L);

        assertThat(runtime.searchAttemptsForTesting()).isEqualTo(1);
    }

    @Test
    void loaded_retry_failure_rebuilds_the_matching_resource_predicate() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe candidate = inputRecipe("factory_retry_matcher_restore");
        RecipeRegistry.registerStatic(candidate);
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);

        assertThat(thread.searchAndStartRecipe(List.of(candidate), 1, 0L)).isFalse();
        CompoundTag output = new CompoundTag();
        thread.save(output, EMPTY_LOOKUP);

        FactoryRecipeThread restored = FactoryRecipeThread.load(output, controller, EMPTY_LOOKUP);

        assertThat(restored.matchesAvailability(Reason.INPUT_AVAILABLE, new ItemStack(Items.IRON_INGOT))).isTrue();
        assertThat(restored.matchesAvailability(Reason.INPUT_AVAILABLE, new ItemStack(Items.GOLD_INGOT))).isFalse();
    }

    @Test
    void repeated_failures_use_the_hundred_tick_fallback() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        RecipeSearchContextKey key = new RecipeSearchContextKey(1L, 1L, 1L, 1L, 1L, 1L, 1L);

        for (int failure = 0; failure < 6; failure++) thread.recordSearchFailure(key, 0L);

        assertThat(thread.canSearch(99L, key)).isFalse();
        assertThat(thread.canSearch(100L, key)).isTrue();
    }

    @Test
    void every_search_context_version_change_releases_the_retry_gate() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        RecipeSearchContextKey original = new RecipeSearchContextKey(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        thread.recordSearchFailure(original, 0L);

        assertThat(thread.canSearch(4L, original)).isFalse();
        assertThat(thread.canSearch(1L, new RecipeSearchContextKey(8L, 2L, 3L, 4L, 5L, 6L, 7L))).isTrue();
        assertThat(thread.canSearch(1L, new RecipeSearchContextKey(1L, 8L, 3L, 4L, 5L, 6L, 7L))).isTrue();
        assertThat(thread.canSearch(1L, new RecipeSearchContextKey(1L, 2L, 8L, 4L, 5L, 6L, 7L))).isTrue();
        assertThat(thread.canSearch(1L, new RecipeSearchContextKey(1L, 2L, 3L, 8L, 5L, 6L, 7L))).isTrue();
        assertThat(thread.canSearch(1L, new RecipeSearchContextKey(1L, 2L, 3L, 4L, 8L, 6L, 7L))).isTrue();
        assertThat(thread.canSearch(1L, new RecipeSearchContextKey(1L, 2L, 3L, 4L, 5L, 8L, 7L))).isTrue();
        assertThat(thread.canSearch(1L, new RecipeSearchContextKey(1L, 2L, 3L, 4L, 5L, 6L, 8L))).isTrue();
    }

    @Test
    void modifier_change_does_not_arm_retry_for_an_active_lane() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        MachineRecipe recipe = recipe("factory_active_failure", 20);

        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1, 0L)).isTrue();
        controller.componentRuntime().replaceModifiers(Map.of("changed", List.of()));
        RuntimeTestFixtures.republish(controller);
        thread.tick();

        assertThat(thread.runtime().failure()).isNull();
        assertThat(thread.runtime().active()).isTrue();
    }

    @Test
    void modifier_change_during_async_tick_keeps_the_active_lane() {
        MachineControllerBlockEntity controller = factoryController("test_cube");
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("factory_async_version_failure_recipe"),
                MMCR.id("test_cube"), 20, List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(cn.howxu.mmcr.api.recipe.requirement.StageRequirement.input(1)));
        RecipeRegistry.registerStatic(recipe);

        assertThat(controller.structureSnapshot().formed()).isTrue();
        assertThat(controller.structureSnapshot().structureAreaLoaded()).isTrue();
        controller.serverTick();
        resolveSharedRequests(controller);
        assertThat(controller.runtimeSnapshot().factory().activeLaneCount())
                .as("factory=%s hasFactoryController=%s domain=%s", controller.runtimeSnapshot().factory(),
                        controller.hasFactoryController(), controller.resourceDomain())
                .isEqualTo(1);

        RuntimeTestFixtures.advanceGameTime(controller.getLevel());
        controller.serverTick();
        controller.componentRuntime().replaceModifiers(Map.of("changed", List.of()));
        RuntimeTestFixtures.republish(controller);
        resolveSharedRequests(controller);

        assertThat(controller.runtimeSnapshot().factory().failure()).isNull();

        controller.serverTick();
        assertThat(controller.runtimeSnapshot().factory().activeLaneCount()).isEqualTo(1);
        resolveSharedRequests(controller);

        assertThat(controller.runtimeSnapshot().factory().activeLaneCount()).isEqualTo(1);
    }

    @Test
    void resource_availability_notifications_are_coalesced_per_server_tick() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        long initial = controller.resourceAvailabilityEpoch();

        LevelStub.setGameTime(controller.getLevel(), 10L);
        controller.notifyResourceAvailability(Reason.INPUT_AVAILABLE, null);
        controller.notifyResourceAvailability(Reason.INPUT_AVAILABLE, null);
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(initial + 1);

        LevelStub.setGameTime(controller.getLevel(), 11L);
        controller.notifyResourceAvailability(Reason.INPUT_AVAILABLE, null);
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(initial + 2);
    }

    @Test
    void same_amount_resource_replacement_notifies_the_linked_controller() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        input.linkControllerAppearance(controller.getBlockPos(), null);

        LevelStub.setGameTime(controller.getLevel(), 20L);
        setItem(input.itemHandler(), 0, new ItemStack(Items.IRON_INGOT, 1));
        long afterIron = controller.resourceAvailabilityEpoch();

        LevelStub.setGameTime(controller.getLevel(), 21L);
        setItem(input.itemHandler(), 0, new ItemStack(Items.GOLD_INGOT, 1));

        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(afterIron + 1);
    }

    @Test
    void multi_slot_input_notifies_every_resource_available_after_a_committed_insert() {
        BlockPos inputPos = new BlockPos(101, 20, 30);
        ExtendedItemBusBlockEntity input = new ExtendedItemBusBlockEntity(inputPos,
                ModBlocks.BLOCKS.get("extended_item_input_bus_basic").get().defaultBlockState());
        RecordingController controller = new RecordingController(inputPos.offset(-1, 0, 0),
                ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        var level = LevelStub.create(
                Map.of(controller.getBlockPos(), controller.getBlockState().getBlock(),
                        input.getBlockPos(), input.getBlockState().getBlock()), List.of(controller, input));
        controller.setLevel(level);
        input.setLevel(level);
        input.linkControllerAppearance(controller.getBlockPos(), null);

        input.itemHandler().forceInsert(0, new ItemStack(Items.IRON_INGOT), 1L, false);
        controller.notifiedResources.clear();
        controller.notifiedSources.clear();

        input.itemHandler().forceInsert(1, new ItemStack(Items.GOLD_INGOT), 1L, false);

        assertThat(controller.notifiedResources).hasSize(2)
                .anySatisfy(resource -> assertThat((ItemStack) resource).matches(stack -> stack.is(Items.IRON_INGOT)))
                .anySatisfy(resource -> assertThat((ItemStack) resource).matches(stack -> stack.is(Items.GOLD_INGOT)));
        assertThat(controller.notifiedSources).containsExactly(inputPos, inputPos);
        assertThat(inputPos).isNotEqualTo(inputPos.subtract(controller.getBlockPos()));
    }

    @Test
    void multi_slot_output_notifies_the_resource_released_from_the_changed_slot() {
        BlockPos outputPos = new BlockPos(101, 20, 30);
        ExtendedItemBusBlockEntity output = new ExtendedItemBusBlockEntity(outputPos,
                ModBlocks.BLOCKS.get("extended_item_output_bus_basic").get().defaultBlockState());
        RecordingController controller = new RecordingController(outputPos.offset(-1, 0, 0),
                ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        var level = LevelStub.create(
                Map.of(controller.getBlockPos(), controller.getBlockState().getBlock(),
                        output.getBlockPos(), output.getBlockState().getBlock()), List.of(controller, output));
        controller.setLevel(level);
        output.setLevel(level);
        output.linkControllerAppearance(controller.getBlockPos(), null);
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        ItemStack gold = new ItemStack(Items.GOLD_INGOT);

        output.itemHandler().forceInsert(0, iron, 2L, false);
        output.itemHandler().forceInsert(1, gold, 2L, false);
        controller.notifiedOutputResources.clear();
        controller.notifiedOutputSources.clear();

        output.itemHandler().forceExtract(1, 1L, false);

        assertThat(controller.notifiedOutputResources).singleElement()
                .satisfies(resource -> assertThat((ItemStack) resource).matches(stack -> stack.is(Items.GOLD_INGOT)));
        assertThat(controller.notifiedOutputSources).containsExactly(outputPos);
        assertThat(outputPos).isNotEqualTo(outputPos.subtract(controller.getBlockPos()));
    }

    @Test
    void legacy_availability_entry_records_each_resource_once_with_unknown_source() {
        RecordingController controller = new RecordingController(BlockPos.ZERO,
                ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        ItemStack gold = new ItemStack(Items.GOLD_INGOT);

        controller.notifyResourceAvailability(Reason.INPUT_AVAILABLE, iron);
        controller.notifyResourceAvailability(Reason.OUTPUT_CAPACITY, gold);

        assertThat(controller.notifiedResources).containsExactly(iron);
        assertThat(controller.notifiedOutputResources).containsExactly(gold);
        assertThat(controller.notifiedSources).containsExactly((BlockPos) null);
        assertThat(controller.notifiedOutputSources).containsExactly((BlockPos) null);
    }

    @Test
    void committed_mmcr_capability_operation_notifies_the_linked_controller() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        input.linkControllerAppearance(controller.getBlockPos(), null);
        ItemBusCapability capability = (ItemBusCapability) input.capabilitySnapshot().capabilities().getFirst();
        CapabilityRequests.ItemRequest request = new CapabilityRequests.ItemRequest(
                capability.type(), IOType.INPUT, 1,
                List.of(new CapabilityRequests.ItemAction(0, new ItemStack(Items.IRON_INGOT), 1, true)));

        LevelStub.setGameTime(controller.getLevel(), 20L);
        long initial = controller.resourceAvailabilityEpoch();
        assertThat(capability.prepare(request).commit().success()).isTrue();

        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(initial + 1);
    }

    @Test
    void external_item_capability_notifies_once_after_insert() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        input.linkControllerAppearance(controller.getBlockPos(), null);
        IItemHandler handler = externalItemHandler(input);
        LevelStub.setGameTime(controller.getLevel(), 20L);
        long initial = controller.resourceAvailabilityEpoch();

        assertThat(handler.insertItem(0, new ItemStack(Items.IRON_INGOT), false).isEmpty()).isTrue();
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(initial + 1L);

        assertThat(input.itemHandler().amount(0)).isEqualTo(1L);
    }

    @Test
    void adding_a_second_resource_wakes_only_the_lane_waiting_for_that_slot_resource() {
        ExtendedItemBusBlockEntity input = new ExtendedItemBusBlockEntity(new BlockPos(1, 0, 0),
                ModBlocks.BLOCKS.get("extended_item_input_bus_basic").get().defaultBlockState());
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        input.linkControllerAppearance(controller.getBlockPos(), null);
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        ItemStack gold = new ItemStack(Items.GOLD_INGOT);
        input.itemHandler().forceInsert(0, iron, 1L, false);

        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(2);
        MachineRecipe ironRecipe = itemInputRecipe("factory_slot_iron", Items.IRON_INGOT);
        MachineRecipe goldRecipe = itemInputRecipe("factory_slot_gold", Items.GOLD_INGOT);
        List<MachineRecipe> candidates = List.of(ironRecipe, goldRecipe);

        runtime.tick(candidates, 1, 0L);
        assertThat(runtime.searchAttemptsForTesting()).isEqualTo(2);
        assertThat(runtime.threadSnapshots().get(1).lastFailureUnloc())
                .isEqualTo("gui.mmcr.controller.failure.missing_input");

        runtime.wakeSearches(Reason.INPUT_AVAILABLE, iron);
        runtime.tick(candidates, 1, 1L);
        runtime.wakeSearches(Reason.INPUT_AVAILABLE, null);
        runtime.tick(candidates, 1, 2L);
        assertThat(runtime.searchAttemptsForTesting()).isEqualTo(2);

        LevelStub.setGameTime(controller.getLevel(), 3L);
        long beforeGoldInsertEpoch = controller.resourceAvailabilityEpoch();
        assertThat(input.itemHandler().forceInsert(1, gold, 1L, false)).isEqualTo(1L);
        assertThat(input.itemHandler().amount(1)).isEqualTo(1L);
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeGoldInsertEpoch + 1L);
        assertThat(runtime.threadSnapshots().get(1).lastFailureUnloc())
                .isEqualTo("gui.mmcr.controller.failure.missing_input");
        runtime.wakeSearches(Reason.INPUT_AVAILABLE, gold);
        runtime.tick(candidates, 1, 3L);

        assertThat(runtime.searchAttemptsForTesting()).isEqualTo(3);
        assertThat(runtime.activeRuntimes()).extracting(CraftingRuntime::recipe)
                .containsExactlyInAnyOrder(ironRecipe, goldRecipe);
    }

    @Test
    void output_energy_capacity_release_wakes_a_failed_output_energy_lane() {
        var energy = RuntimeTestFixtures.energyOutput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), energy);
        energy.linkControllerAppearance(controller.getBlockPos(), null);
        energy.energyStorage().setAmount(energy.energyStorage().getCapacityAsLong());
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        MachineRecipe candidate = outputEnergyRecipe("factory_output_energy_port_wakeup");

        runtime.tick(List.of(candidate), 1, 0L);
        assertThat(runtime.searchAttemptsForTesting()).isEqualTo(1);
        LevelStub.setGameTime(controller.getLevel(), 1L);
        long beforeReleaseEpoch = controller.resourceAvailabilityEpoch();
        assertThat(energy.energyStorage().forceExtract(1L, false)).isEqualTo(1L);
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeReleaseEpoch + 1L);
        runtime.wakeSearches(Reason.OUTPUT_CAPACITY, new CapabilityType(EnergyRequirement.TYPE.id()));
        runtime.tick(List.of(candidate), 1, 1L);

        assertThat(runtime.searchAttemptsForTesting()).isEqualTo(2);
    }

    @Test
    void input_energy_insertion_wakes_a_failed_input_energy_lane_but_output_release_does_not() {
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), energy);
        energy.linkControllerAppearance(controller.getBlockPos(), null);
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        MachineRecipe candidate = inputEnergyRecipe("factory_input_energy_port_wakeup");

        runtime.tick(List.of(candidate), 1, 0L);
        runtime.wakeSearches(Reason.OUTPUT_CAPACITY, new CapabilityType(EnergyRequirement.TYPE.id()));
        runtime.tick(List.of(candidate), 1, 1L);
        assertThat(runtime.searchAttemptsForTesting()).isEqualTo(1);

        LevelStub.setGameTime(controller.getLevel(), 1L);
        long beforeInsertEpoch = controller.resourceAvailabilityEpoch();
        assertThat(energy.energyStorage().forceInsert(4L, false)).isEqualTo(4L);
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeInsertEpoch + 1L);
        runtime.wakeSearches(Reason.ENERGY_AVAILABLE, new CapabilityType(EnergyRequirement.TYPE.id()));
        runtime.tick(List.of(candidate), 1, 2L);

        assertThat(runtime.searchAttemptsForTesting()).isEqualTo(2);
    }

    @Test
    void loading_a_core_lane_clears_retry_wait_when_recipe_set_version_cannot_be_verified() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe candidate = inputRecipe("factory_core_retry_candidate");
        MachineRecipe other = recipe("factory_core_retry_other", 20);
        FactoryRecipeThread thread = FactoryRecipeThread.core(controller, "core", Set.of(candidate));
        thread.replaceRecipeSet(Set.of(candidate, other));
        var snapshot = controller.runtimeSnapshot();
        RecipeSearchContextKey key = new RecipeSearchContextKey(snapshot.structure().version(),
                snapshot.capabilityVersion(), snapshot.modifierVersion(), snapshot.stateVersion(),
                RecipeRegistry.catalogForMachine(MMCR.id("test_cube")).version(), controller.resourceAvailabilityEpoch(),
                thread.coreRecipeSetVersion());
        thread.recordSearchFailure(key, 0L);

        CompoundTag output = new CompoundTag();
        thread.save(output, EMPTY_LOOKUP);
        FactoryRecipeThread restored = FactoryRecipeThread.load(output, controller, EMPTY_LOOKUP,
                List.of(candidate, other));

        assertThat(restored.canSearch(1L, key)).isTrue();
    }

    @Test
    void loading_a_simple_lane_uses_the_supplied_candidate_for_last_recipe_retry() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        input.linkControllerAppearance(controller.getBlockPos(), null);
        MachineRecipe candidate = cancellingInputRecipe("factory_supplied_candidate_retry");
        setItem(input.itemHandler(), 0, new ItemStack(Items.IRON_INGOT, 1));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);

        assertThat(thread.searchAndStartRecipe(List.of(candidate), 1, 0L)).isTrue();
        setItem(input.itemHandler(), 0, ItemStack.EMPTY);
        thread.tick();
        assertThat(thread.runtime().active()).isFalse();

        CompoundTag output = new CompoundTag();
        thread.save(output, EMPTY_LOOKUP);
        FactoryRecipeThread restored = FactoryRecipeThread.load(output, controller, EMPTY_LOOKUP,
                List.of(candidate));
        setItem(input.itemHandler(), 0, new ItemStack(Items.IRON_INGOT, 1));
        var snapshot = controller.runtimeSnapshot();

        assertThat(restored.tryRestartLastRecipe(List.of(candidate), 1, snapshot.structure().version(),
                snapshot.capabilityVersion(), snapshot.modifierVersion(), snapshot.stateVersion())).isTrue();
    }

    @Test
    void core_lane_filters_context_candidates_without_dropping_fallback_candidates() {
        MachineRecipe core = recipe("factory_core_context", 20);
        MachineRecipe fallback = inputRecipe("factory_fallback_context");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.core(controller, "core", Set.of(core));
        FactorySearchContext context = new FactorySearchContext(controller.runtimeSnapshot(),
                List.of(core, fallback), List.of(), List.of(), 1L, 0L, 1, 0L);

        assertThat(thread.candidatesFor(context.orderedCandidates())).containsExactly(core);
        assertThat(context.orderedCandidates()).containsExactly(core, fallback);
    }

    @Test
    void context_search_uses_its_immutable_versions_for_failure_retry_key() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        ControllerRuntimeSnapshot live = controller.runtimeSnapshot();
        long contextStateVersion = live.stateVersion() + 11L;
        long contextCatalogVersion = 1234L;
        long contextResourceEpoch = 5678L;
        ControllerRuntimeSnapshot contextSnapshot = snapshotWithStateVersion(live, contextStateVersion);
        FactorySearchContext context = new FactorySearchContext(contextSnapshot,
                List.of(inputRecipe("factory_context_retry_key")), controller.componentRuntime().capabilities(),
                controller.componentRuntime().modifierList(),
                contextCatalogVersion, contextResourceEpoch, 1, 0L);

        assertThat(thread.searchAndStartRecipe(context, contextSnapshot.structure().version())).isFalse();

        assertThat(thread.searchFailureKey()).isEqualTo(new RecipeSearchContextKey(
                contextSnapshot.structure().version(), contextSnapshot.capabilityVersion(),
                contextSnapshot.modifierVersion(), contextStateVersion, contextCatalogVersion,
                contextResourceEpoch, thread.coreRecipeSetVersion()));
    }

    @Test
    void context_pending_start_is_validated_against_the_context_versions() {
        MachineControllerBlockEntity controller = factoryController("test_cube");
        ServerLevel level = (ServerLevel) controller.getLevel();
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        assertThat(registry.claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        MachineRecipe candidate = recipe("factory_context_pending_recipe", 20);
        ResourceLocation machineId = controller.structureSnapshot().machine().registryName();
        RecipeRegistry.replaceDynamic(Map.of(candidate.id(), candidate));
        ControllerRuntimeSnapshot live = controller.runtimeSnapshot();
        ControllerRuntimeSnapshot contextSnapshot = snapshotWithStateVersion(live, live.stateVersion() + 1L);
        FactorySearchContext context = new FactorySearchContext(contextSnapshot, List.of(candidate),
                controller.componentRuntime().capabilities(),
                controller.componentRuntime().modifierList(),
                RecipeRegistry.catalogForMachine(machineId).version(),
                controller.resourceAvailabilityEpoch(), 1, 0L);
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);

        assertThat(thread.searchAndStartRecipe(context, contextSnapshot.structure().version())).isTrue();
        assertThat(thread.isStartPending()).isTrue();

        resolveSharedRequests(controller);

        assertThat(thread.isStartPending()).isFalse();
        assertThat(thread.runtime().active()).isFalse();
    }

    @Test
    void repeated_context_creation_reuses_the_catalog_ordered_candidate_source() {
        MachineRecipe first = recipe("factory_ordered_source_first", 20);
        MachineRecipe second = recipe("factory_ordered_source_second", 20);
        RecipeRegistry.registerStaticBatch(List.of(first, second));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        List<MachineRecipe> candidates = RecipeRegistry.recipesForPool(MMCR.id("test_cube"));

        FactorySearchContext firstContext = runtime.createSearchContext(controller.runtimeSnapshot(), candidates, 1, 0L);
        FactorySearchContext secondContext = runtime.createSearchContext(controller.runtimeSnapshot(), candidates, 1, 1L);

        assertThat(secondContext.orderedCandidates()).isSameAs(firstContext.orderedCandidates());
    }

    @Test
    void core_filter_cache_reuses_equal_candidate_sources() {
        MachineRecipe core = recipe("factory_core_equal_source", 20);
        MachineRecipe fallback = recipe("factory_core_equal_fallback", 20);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.core(controller, "core", Set.of(core));

        List<MachineRecipe> firstSource = List.of(core, fallback);
        List<MachineRecipe> equalSource = List.of(core, fallback);
        List<MachineRecipe> firstFiltered = thread.candidatesFor(firstSource, 9L);
        List<MachineRecipe> secondFiltered = thread.candidatesFor(equalSource, 9L);

        assertThat(secondFiltered).isSameAs(firstFiltered);
    }

    @Test
    void candidate_context_cache_tracks_input_index_changes() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        MachineRecipe iron = itemInputRecipe("factory_index_cache_iron", Items.IRON_INGOT);
        MachineRecipe gold = itemInputRecipe("factory_index_cache_gold", Items.GOLD_INGOT);
        RecipeRegistry.registerStaticBatch(List.of(iron, gold));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        List<MachineRecipe> candidates = RecipeRegistry.recipesForPool(MMCR.id("test_cube"));
        setItem(input.itemHandler(), 0, new ItemStack(Items.IRON_INGOT, 1));

        FactorySearchContext ironContext = runtime.createSearchContext(controller.runtimeSnapshot(), candidates, 1, 0L);
        setItem(input.itemHandler(), 0, new ItemStack(Items.GOLD_INGOT, 1));
        FactorySearchContext goldContext = runtime.createSearchContext(controller.runtimeSnapshot(), candidates, 1, 1L);

        assertThat(ironContext.orderedCandidates()).containsExactly(iron);
        assertThat(goldContext.orderedCandidates()).containsExactly(gold);
    }

    private static ControllerRuntimeSnapshot snapshotWithStateVersion(ControllerRuntimeSnapshot snapshot,
                                                                        long stateVersion) {
        return new ControllerRuntimeSnapshot(snapshot.structure(), snapshot.capabilityVersion(),
                snapshot.modifierVersion(), stateVersion, snapshot.foundModifiers(), snapshot.foundLevels(),
                snapshot.linkedPortPositions(), snapshot.moduleConnectionStatus(), snapshot.installedModuleCount(),
                snapshot.crafting(), snapshot.factory(),
                snapshot.componentPresentations(), snapshot.capabilityPresentations(), snapshot.foundLevelIds(),
                snapshot.machineId(), snapshot.machineName(), snapshot.controllerRole(), snapshot.factorySupported(),
                snapshot.factoryControllerPresent(), snapshot.parallelControllerCount(),
                snapshot.maxParallelControllerCount(), snapshot.maxParallelism(), snapshot.dataStorageValues());
    }

    /** Records both availability entry points without duplicating legacy forwarding.
     * @author howxu <dev@howxu.cn>
     */
    private static final class RecordingController extends MachineControllerBlockEntity {
        private final List<Object> notifiedResources = new ArrayList<>();
        private final List<Object> notifiedOutputResources = new ArrayList<>();
        private final List<BlockPos> notifiedSources = new ArrayList<>();
        private final List<BlockPos> notifiedOutputSources = new ArrayList<>();

        private RecordingController(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        public void notifyResourceAvailability(Reason reason, Object resource) {
            super.notifyResourceAvailability(reason, resource);
        }

        @Override
        public void notifyResourceAvailability(Reason reason, Object resource, BlockPos sourcePos) {
            if (reason == Reason.INPUT_AVAILABLE) {
                notifiedResources.add(resource);
                notifiedSources.add(sourcePos);
            }
            if (reason == Reason.OUTPUT_CAPACITY) {
                notifiedOutputResources.add(resource);
                notifiedOutputSources.add(sourcePos);
            }
            super.notifyResourceAvailability(reason, resource, sourcePos);
        }
    }

    private static MachineControllerBlockEntity factoryController(String path) {
        ResourceLocation machineId = MMCR.id(path);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos schedulerPos = controller.getBlockPos().offset(-1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));
        DynamicMachine machine = new DynamicMachine(machineId, path, pattern,
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null)));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        return controller;
    }

    private static MachineControllerBlockEntity stagedController(int stage) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        ControllerRuntimeSnapshot snapshot = controller.runtimeSnapshot();
        try {
            var method = MachineControllerRuntime.class.getDeclaredMethod("publishStructureState",
                    boolean.class, boolean.class, Machine.class, int.class);
            method.setAccessible(true);
            method.invoke(controllerRuntime(controller), true, true,
                    snapshot.structure().configuredMachine(), stage);
            return controller;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to publish staged controller snapshot", exception);
        }
    }

    private static MachineControllerBlockEntity asyncFactoryController(ItemInputBusBlockEntity input) {
        ResourceLocation machineId = MMCR.id("test_cube");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(machineId, BlockPos.ZERO);
        BlockPos schedulerPos = controller.getBlockPos().offset(-1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));
        DynamicMachine machine = new DynamicMachine(machineId, "async factory", pattern,
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler, input);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null),
                new ProcessingComponent(new MachineComponent(input.kind(), input.ioType()), input,
                        input.getBlockPos(), input.getBlockPos(), (String) null)));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        return controller;
    }

    private static MachineControllerBlockEntity sharedFactoryController(ResourceLocation machineId,
                                                                         ResourceLocation coreRecipeId,
                                                                         IOPortBlockEntity output) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos schedulerPos = controller.getBlockPos().offset(-1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));
        DynamicMachine machine = new DynamicMachine(machineId, machineId.getPath(), pattern,
                MachineControllerSpec.defaultsFor(machineId), MachineAppearanceSpec.defaults(),
                PortRequirementSpec.none(), PortTierRequirementSpec.none(),
                List.of(), Map.of(), 1, false, true, 2,
                List.of(new FactoryThreadSpec("blocked", List.of(coreRecipeId))), List.of());
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler, output);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null),
                new ProcessingComponent(new MachineComponent(output.kind(), output.ioType()),
                        output, output.getBlockPos(), output.getBlockPos(), (String) null)));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        output.linkControllerAppearance(controller.getBlockPos(), null);
        ServerLevel level = (ServerLevel) controller.getLevel();
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        registry.claim(controller.getBlockPos(), List.of(
                new StructureClaimRegistry.Claim(schedulerPos, ComponentClaimPolicy.SHARED_CAPACITY),
                new StructureClaimRegistry.Claim(output.getBlockPos(), ComponentClaimPolicy.SHARED_SERIALIZED)));
        MachineControllerBlockEntity secondController = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), new BlockPos(10, 0, 0));
        registry.claim(secondController.getBlockPos(), List.of(
                new StructureClaimRegistry.Claim(output.getBlockPos(), ComponentClaimPolicy.SHARED_SERIALIZED)));
        return controller;
    }

    private static FactoryRuntime controllerFactoryRuntime(MachineControllerBlockEntity controller) {
        return controllerRuntime(controller).factoryRuntime();
    }

    @SuppressWarnings("unchecked")
    private static Map<FactoryRecipeThread, FactoryRuntime.ThreadSnapshot> laneThreadSnapshots(FactoryRuntime runtime) {
        try {
            var field = FactoryRuntime.class.getDeclaredField("laneThreadSnapshots");
            field.setAccessible(true);
            return (Map<FactoryRecipeThread, FactoryRuntime.ThreadSnapshot>) field.get(runtime);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to access factory lane snapshot cache", exception);
        }
    }

    private static MachineControllerRuntime controllerRuntime(MachineControllerBlockEntity controller) {
        try {
            var field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
            field.setAccessible(true);
            return (MachineControllerRuntime) field.get(controller);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to access controller runtime", exception);
        }
    }

    private static void resolveSharedRequests(MachineControllerBlockEntity controller) {
        if (controller.resourceDomain() != null) {
            ServerLevel level = (ServerLevel) controller.getLevel();
            SharedIoCoordinator sharedIo = SharedIoCoordinator.get(level);
            MachineAsyncCoordinator async = MachineAsyncCoordinator.get(level);
            long gameTime = level.getGameTime();
            sharedIo.beginLevelTick(gameTime);
            async.beginLevelTick(gameTime);
            sharedIo.resolve(controller.resourceDomain());
            async.completeUntilIdleForTesting(() -> sharedIo.resolve(level));
            MachineControllerBlockEntity.flushQueuedAsyncRuntimeState(level);
        }
    }

    @Test
    void missing_retry_fields_in_old_lane_nbt_clear_the_wait() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        thread.recordSearchFailure(searchKey(controller), 0L);

        CompoundTag output = new CompoundTag();
        thread.save(output, EMPTY_LOOKUP);
        var tag = output;
        tag.remove("search_failure_streak");
        tag.remove("search_retry_remaining");

        FactoryRecipeThread restored = FactoryRecipeThread.load(tag, controller, EMPTY_LOOKUP);

        assertThat(restored.canSearch(0L, searchKey(controller))).isTrue();
    }

    @Test
    void restored_retry_remaining_is_clamped_to_one_hundred_ticks() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        assertThat(thread.searchAndStartRecipe(List.of(inputRecipe("retry_remaining_clamped")), 1, 0L)).isFalse();

        CompoundTag output = new CompoundTag();
        thread.save(output, EMPTY_LOOKUP);
        var tag = output;
        tag.putInt("search_retry_remaining", Integer.MAX_VALUE);

        FactoryRecipeThread restored = FactoryRecipeThread.load(tag, controller, EMPTY_LOOKUP);

        assertThat(restored.canSearch(99L, searchKey(controller))).isFalse();
        assertThat(restored.canSearch(100L, searchKey(controller))).isTrue();
    }

    @Test
    void waking_a_failed_lane_round_trips_with_no_remaining_retry_wait() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        RecipeSearchContextKey key = searchKey(controller);
        thread.recordSearchFailure(key, 0L);
        thread.wakeSearch();
        LevelStub.setGameTime(controller.getLevel(), 1L);

        CompoundTag output = new CompoundTag();
        thread.save(output, EMPTY_LOOKUP);
        FactoryRecipeThread restored = FactoryRecipeThread.load(output, controller, EMPTY_LOOKUP);

        assertThat(restored.canSearch(1L, key)).isTrue();
    }

    private static MachineRecipe inputRecipe(String path) {
        return RecipeTestSupport.create(MMCR.id(path), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                 ItemStack.EMPTY)));
    }

    private static MachineRecipe stageRecipe(String path, ResourceLocation machineId) {
        return RecipeTestSupport.create(MMCR.id(path), machineId, 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(cn.howxu.mmcr.api.recipe.requirement.StageRequirement.input(2)));
    }

    private static MachineRecipe itemInputRecipe(String path, Item item) {
        return RecipeTestSupport.create(MMCR.id(path), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(item), 1, ItemStack.EMPTY)));
    }

    private static void setItem(LongItemStorage storage, int slot, ItemStack stack) {
        storage.setContents(slot, stack, stack.getCount());
    }

    @SuppressWarnings("unchecked")
    private static IItemHandler externalItemHandler(ItemInputBusBlockEntity input) {
        try {
            Class<?> type = Class.forName("cn.howxu.mmcr.internal.event.ModCapabilities$DirectionalItemHandler");
            var constructor = type.getDeclaredConstructor(IItemHandler.class, boolean.class, boolean.class);
            constructor.setAccessible(true);
            return (IItemHandler) constructor.newInstance(input.nativeItemHandler(), true, true);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to create the production item capability adapter", exception);
        }
    }

    private static MachineRecipe inputEnergyRecipe(String path) {
        return RecipeTestSupport.create(MMCR.id(path), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 4)));
    }

    private static MachineRecipe cancellingInputRecipe(String path) {
        return RecipeTestSupport.create(MMCR.id(path), MMCR.id("test_cube"), 20,
                 List.of(), List.of(), List.of(), 0, 1, true, List.of(), List.of(
                 new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                 ItemStack.EMPTY, 1F, List.of(), DataComponentPredicateSet.EMPTY, 0F)));
    }

    private static MachineRecipe outputEnergyRecipe(String path) {
        return RecipeTestSupport.create(MMCR.id(path), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 4)));
    }

    private static RecipeSearchContextKey searchKey(MachineControllerBlockEntity controller) {
        var snapshot = controller.runtimeSnapshot();
        return new RecipeSearchContextKey(snapshot.structure().version(), snapshot.capabilityVersion(),
                snapshot.modifierVersion(), snapshot.stateVersion(),
                RecipeRegistry.catalogForMachine(MMCR.id("test_cube")).version(), controller.resourceAvailabilityEpoch(),
                0L);
    }

    private static MachineRecipe recipe(String path, int duration) {
        return RecipeTestSupport.create(MMCR.id(path), MMCR.id("test_cube"),
                duration, List.of(), List.of(), List.of(), 0, 2);
    }
}
