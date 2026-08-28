package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.FactoryThreadSpec;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.multiblock.ComponentClaimPolicy;
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
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeThread;
import cn.howxu.mmcr.internal.recipe.FactorySearchContext;
import cn.howxu.mmcr.internal.recipe.RecipeSearchContextKey;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
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
    }

    @AfterEach
    void cleanup() {
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
    void indexed_candidate_without_current_input_still_publishes_failure_diagnostics() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        MachineRecipe candidate = itemInputRecipe("factory_index_missing_input", Items.IRON_INGOT);
        RecipeRegistry.register(candidate);
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
        RecipeRegistry.register(exact);
        RecipeRegistry.register(fallback);
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);

        runtime.tick(List.of(exact, fallback), 1, 0L);

        assertThat(runtime.activeRuntimes()).extracting(CraftingRuntime::recipe).containsExactly(fallback);
    }

    @Test
    void tickingAggregatesParallelismAcrossActiveLanes() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(2);
        MachineRecipe recipe = recipe("factory_parallel", 20);

        runtime.tick(List.of(recipe), 4);

        FactorySnapshot snapshot = runtime.snapshot();
        assertThat(snapshot.activeLaneCount()).isEqualTo(2);
        assertThat(snapshot.activeParallelism()).isEqualTo(8);
        assertThat(snapshot.presentationLanes()).allSatisfy(lane -> assertThat(lane.active()).isTrue());
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
    void recipeLockIsOwnedByTheRuntimeLaneAndPersistsInItsSnapshot() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        MachineRecipe recipe = recipe("factory_lock", 20);

        runtime.tick(List.of(recipe), 1);
        assertThat(runtime.toggleRecipeLock(0)).isTrue();

        FactoryRuntime.ThreadSnapshot lane = runtime.snapshot().presentationLanes().getFirst();
        assertThat(lane.locked()).isTrue();
        assertThat(lane.lockedRecipeId()).isEqualTo(recipe.id().toString());
    }

    @Test
    void recipe_lock_persists_with_the_lane_runtime_state() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe recipe = recipe("factory_lock_persisted", 20);
        RecipeRegistry.register(recipe);
        FactoryRuntime saved = new FactoryRuntime();
        saved.ensureBaseLane(controller);
        saved.tick(List.of(recipe), 1);
        assertThat(saved.toggleRecipeLock(0)).isTrue();

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        saved.save(output);
        FactoryRuntime restored = new FactoryRuntime();
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, EMPTY_LOOKUP, output.buildResult()), controller);

        FactoryRuntime.ThreadSnapshot lane = restored.snapshot().presentationLanes().getFirst();
        assertThat(lane.locked()).isTrue();
        assertThat(lane.lockedRecipeId()).isEqualTo(recipe.id().toString());
    }

    @Test
    void removingAQueuedLaneInvalidatesOnlyThatLane() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(2);
        runtime.tick(List.of(recipe("factory_remove", 20)), 1);
        CraftingRuntime removed = runtime.activeRuntimes().getLast();

        runtime.setLaneLimit(1);

        assertThat(runtime.activeLaneCount()).isEqualTo(1);
        assertThat(runtime.contains(removed)).isFalse();
        assertThat(removed.active()).isFalse();
    }

    @Test
    void idle_dynamic_lanes_are_cleaned_up_after_their_timeout() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(2);

        runtime.tick(List.of(recipe("factory_idle_cleanup", 1)), 1);
        assertThat(runtime.laneCount()).isEqualTo(2);
        for (int tick = 0; tick <= 200; tick++) runtime.tick(List.of(), 1);

        assertThat(runtime.laneCount()).isEqualTo(1);
        assertThat(runtime.activeLaneCount()).isZero();
    }

    @Test
    void shared_input_is_reserved_by_only_one_active_lane() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new net.minecraft.core.BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        ItemStack stack = new ItemStack(Items.IRON_INGOT, 1);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        input.getItemStackHandler(null).setStackInSlot(0, stack);
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(2);

        runtime.tick(List.of(new MachineRecipe(MMCR.id("factory_shared_input"), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 2, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                        ItemStack.EMPTY)))), 1);

        assertThat(runtime.activeLaneCount()).isEqualTo(1);
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isZero();
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
        EnergyInputHatchBlockEntity energy = RuntimeTestFixtures.energyInput(new net.minecraft.core.BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), energy);
        energy.energyStorage().setAmount(2);
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("factory_missing_energy"), MMCR.id("test_cube"), 3,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(new EnergyRequirement(2)));

        runtime.tick(List.of(recipe), 1);
        runtime.tick(List.of(recipe), 1);

        assertThat(runtime.snapshot().presentationLanes().getFirst().lastFailureUnloc())
                .isEqualTo("gui.mmcr.controller.failure.missing_energy");
    }

    @Test
    void failureInOneLaneIsPublishedWithoutDiscardingOtherActiveLanes() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new net.minecraft.core.BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        ItemStack stack = new ItemStack(Items.IRON_INGOT, 1);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        input.getItemStackHandler(null).setStackInSlot(0, stack);
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(2);
        MachineRecipe failingRecipe = new MachineRecipe(MMCR.id("factory_isolated_failure"), MMCR.id("test_cube"),
                20, List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                        ItemStack.EMPTY, 1F, List.of(), DataComponentPredicateSet.EMPTY, 0F)));
        MachineRecipe survivorRecipe = recipe("factory_isolated_survivor", 20);
        List<MachineRecipe> candidates = List.of(failingRecipe, survivorRecipe);
        runtime.tick(candidates, 1);
        assertThat(runtime.activeLaneCount()).isEqualTo(2);

        CraftingRuntime failed = runtime.activeRuntimes().getFirst();
        CraftingRuntime survivor = runtime.activeRuntimes().getLast();
        input.getItemStackHandler(null).setStackInSlot(0, ItemStack.EMPTY);
        runtime.tick(candidates, 1);

        FactorySnapshot snapshot = runtime.snapshot();
        assertThat(failed.recipe()).isEqualTo(failingRecipe);
        assertThat(failed.failure()).isNotNull();
        assertThat(snapshot.failure()).isNotNull();
        assertThat(snapshot.failure().details())
                .containsExactly(java.util.Map.entry("reason", "insufficient_resource"));
        assertThat(runtime.activeLaneCount()).isEqualTo(2);
        assertThat(failed.active()).isTrue();
        assertThat(survivor.active()).isTrue();
        assertThat(survivor.failure()).isNull();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isZero();
        assertThat(snapshot.presentationLanes()).hasSize(2);
        assertThat(snapshot.presentationLanes().get(0).active()).isTrue();
        assertThat(snapshot.lanes().get(0).failure()).isNotNull();
        assertThat(snapshot.lanes().get(0).failure().details())
                .containsExactly(java.util.Map.entry("reason", "insufficient_resource"));
        assertThat(snapshot.presentationLanes().get(1).active()).isTrue();
        assertThat(snapshot.lanes().get(1).failure()).isNull();
        assertThat(snapshot.presentationLanes().get(1).lastFailureUnloc()).isEmpty();
        assertThat(snapshot.presentationLanes().get(0).recipeId()).isEqualTo(failingRecipe.id().toString());
        assertThat(snapshot.presentationLanes().get(1).recipeId()).isEqualTo(survivorRecipe.id().toString());
    }

    @Test
    void smart_interface_change_invalidates_all_active_factory_lanes() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        runtime.setLaneLimit(2);
        MachineRecipe recipe = recipe("factory_smart_interface_change", 20);

        runtime.tick(List.of(recipe), 1);
        assertThat(runtime.activeLaneCount()).isEqualTo(2);

        runtime.invalidateForSmartInterfaceChange();

        assertThat(runtime.activeLaneCount()).isZero();
        assertThat(runtime.snapshot().presentationLanes())
                .allSatisfy(lane -> assertThat(lane.lastFailureUnloc())
                        .isEqualTo("gui.mmcr.controller.failure.smart_interface_changed"));
        assertThat(runtime.snapshot().failure()).isNotNull();
        assertThat(runtime.snapshot().failure().details()).containsEntry("reason", "smart_interface_changed");
    }

    @Test
    void runtimeStateRoundTripsThroughValuePersistence() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe recipe = recipe("factory_persisted", 20);
        RecipeRegistry.register(recipe);
        FactoryRuntime saved = new FactoryRuntime();
        saved.ensureBaseLane(controller);
        saved.setLaneLimit(2);
        saved.tick(List.of(recipe), 2);
        saved.pause();

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        saved.save(output);
        FactoryRuntime restored = new FactoryRuntime();
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, EMPTY_LOOKUP, output.buildResult()), controller);

        assertThat(restored.laneLimit()).isEqualTo(2);
        assertThat(restored.isPaused()).isTrue();
        assertThat(restored.laneCount()).isEqualTo(2);
        assertThat(restored.snapshot().presentationLanes()).hasSize(2);
    }

    @Test
    void loadingCorruptLaneCountsIsBoundedBeforeAllocatingLanes() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        output.putInt("lane_limit", Integer.MAX_VALUE);
        output.putInt("lane_count", 1025);

        FactoryRuntime restored = new FactoryRuntime();
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, EMPTY_LOOKUP, output.buildResult()), controller);

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
        assertThat(thread.runtime().failure().details()).containsEntry("reason", "recipe_search");
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
    void failed_lane_retries_a_same_id_recipe_from_the_new_catalog_on_the_next_tick() {
        Identifier recipeId = MMCR.id("factory_reload_failed_lane");
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
        MachineControllerBlockEntity controller = factoryController("factory_reload_pending");
        ServerLevel level = (ServerLevel) controller.getLevel();
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        assertThat(registry.claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        StructureClaimRegistry.ResourceDomain domain = controller.resourceDomain();
        assertThat(domain).isNotNull();

        Identifier recipeId = MMCR.id("factory_reload_pending_recipe");
        MachineRecipe oldRecipe = recipe(recipeId.getPath(), 20);
        MachineRecipe newRecipe = new MachineRecipe(recipeId, MMCR.id("factory_reload_pending"), 40,
                List.of(), List.of());
        RecipeRegistry.replaceDynamic(Map.of(recipeId, oldRecipe));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);

        assertThat(thread.searchAndStartRecipe(List.of(oldRecipe), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
        assertThat(thread.isStartPending()).isTrue();

        RecipeRegistry.replaceDynamic(Map.of(recipeId, newRecipe));
        SharedIoCoordinator.get(level).resolve(domain);

        assertThat(thread.runtime().active()).isFalse();
        assertThat(thread.isStartPending()).isFalse();
    }

    @Test
    void loading_active_embedded_recipe_clears_changed_last_recipe_before_restart_search() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe oldRecipe = new MachineRecipe(MMCR.id("factory_loaded_old"), MMCR.id("test_cube"), 1,
                List.of(), List.of());
        MachineRecipe replacement = new MachineRecipe(oldRecipe.id(), oldRecipe.machineId(), 20,
                List.of(), List.of());
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);

        assertThat(thread.searchAndStartRecipe(List.of(oldRecipe), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        thread.save(output);
        RecipeRegistry.replaceDynamic(Map.of(replacement.id(), replacement));

        FactoryRecipeThread restored = FactoryRecipeThread.load(
                TagValueInput.create(ProblemReporter.DISCARDING, HolderLookup.Provider.create(Stream.empty()),
                        output.buildResult()), controller, null, List.of(replacement));
        var snapshot = controller.runtimeSnapshot();

        assertThat(restored.runtime().recipe()).isNotNull();
        assertThat(restored.runtime().recipe().tickTime()).isEqualTo(1);
        assertThat(restored.tryRestartLastRecipe(List.of(replacement), 1, snapshot.structure().version(),
                snapshot.capabilityVersion(), snapshot.modifierVersion(), snapshot.stateVersion(), null)).isFalse();
        restored.tick();
        assertThat(restored.runtime().active()).isFalse();
        assertThat(restored.searchAndStartRecipe(List.of(replacement), 1,
                snapshot.structure().version())).isTrue();
        assertThat(restored.runtime().recipe().tickTime()).isEqualTo(20);
    }

    @Test
    void async_completion_searches_the_current_catalog_after_recipe_reload() {
        Identifier machineId = MMCR.id("factory_reload_completion");
        Identifier recipeId = MMCR.id("factory_reload_completion_recipe");
        MachineRecipe oldRecipe = new MachineRecipe(recipeId, machineId, 1,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of());
        MachineRecipe newRecipe = new MachineRecipe(recipeId, machineId, 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of());
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
        runtime.tick(List.of(newRecipe), 1, 2L);
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
        runtime.wakeSearches(Reason.INPUT_AVAILABLE, ItemResource.of(Items.IRON_INGOT));
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
        runtime.wakeSearches(Reason.INPUT_AVAILABLE, ItemResource.of(Items.GOLD_INGOT));
        runtime.tick(List.of(candidate), 1, 1L);
        assertThat(runtime.searchAttemptsForTesting()).isEqualTo(1);

        runtime.wakeSearches(Reason.INPUT_AVAILABLE, null);
        runtime.tick(List.of(candidate), 1, 2L);
        assertThat(runtime.searchAttemptsForTesting()).isEqualTo(1);

        runtime.wakeSearches(Reason.INPUT_AVAILABLE, ItemResource.of(Items.IRON_INGOT));
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
        assertThat(unknown.searchFailureReason()).isEqualTo("recipe_search");
        assertThat(unknown.matchesAvailability(Reason.OUTPUT_CAPACITY, null)).isFalse();
    }

    @Test
    void shared_finish_release_wakes_output_capacity_lane_on_the_next_tick() {
        Identifier machineId = MMCR.id("shared_finish_release");
        Identifier activeId = MMCR.id("shared_finish_release_active");
        Identifier blockedId = MMCR.id("shared_finish_release_blocked");
        MachineRecipe active = new MachineRecipe(activeId, machineId, 1,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of());
        MachineRecipe blocked = new MachineRecipe(blockedId, machineId, 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        new ItemStack(Items.IRON_NUGGET, 1))), false, List.of(), true);
        RecipeRegistry.register(active);
        RecipeRegistry.register(blocked);

        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = sharedFactoryController(machineId, blockedId, output);
        for (int slot = 0; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            output.getItemStackHandler(null).setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
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

        RuntimeTestFixtures.advanceGameTime(controller.getLevel());
        long beforeFinishEpoch = controller.resourceAvailabilityEpoch();
        runtime.tick(List.of(active, blocked), 1, 2L);
        resolveSharedRequests(controller);
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeFinishEpoch + 1L);

        output.getItemStackHandler(null).setStackInSlot(0, ItemStack.EMPTY);
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
        RecipeRegistry.register(candidate);
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);

        assertThat(thread.searchAndStartRecipe(List.of(candidate), 1, 0L)).isFalse();
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        thread.save(output);

        FactoryRecipeThread restored = FactoryRecipeThread.load(
                TagValueInput.create(ProblemReporter.DISCARDING, EMPTY_LOOKUP, output.buildResult()), controller);

        assertThat(restored.matchesAvailability(Reason.INPUT_AVAILABLE, ItemResource.of(Items.IRON_INGOT))).isTrue();
        assertThat(restored.matchesAvailability(Reason.INPUT_AVAILABLE, ItemResource.of(Items.GOLD_INGOT))).isFalse();
    }

    @Test
    void loading_a_locked_failed_lane_keeps_the_retry_gate_in_the_same_context() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        MachineRecipe candidate = cancellingInputRecipe("factory_locked_retry_restore");
        RecipeRegistry.register(candidate);
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));

        FactoryRuntime saved = new FactoryRuntime();
        saved.ensureBaseLane(controller);
        saved.tick(List.of(candidate), 1, 0L);
        assertThat(saved.activeLaneCount()).as("saved lane should start").isEqualTo(1);
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).isEmpty())
                .as("per-tick input should be retained at start").isFalse();
        assertThat(saved.toggleRecipeLock(0)).isTrue();
        input.getItemStackHandler(null).setStackInSlot(0, ItemStack.EMPTY);
        saved.tick(List.of(candidate), 1, 1L);
        assertThat(saved.activeLaneCount()).as("saved lane should be inactive after cancellable failure").isZero();

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        saved.save(output);
        FactoryRuntime restored = new FactoryRuntime();
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, EMPTY_LOOKUP, output.buildResult()), controller);
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));

        restored.tick(List.of(candidate), 1, 0L);

        assertThat(restored.threadSnapshots().getFirst().locked()).isTrue();
        assertThat(restored.activeLaneCount()).as("restored lane snapshots=%s", restored.threadSnapshots()).isZero();
    }

    @Test
    void repeated_failures_use_the_hundred_tick_fallback() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        RecipeSearchContextKey key = new RecipeSearchContextKey(1L, 1L, 1L, 1L, 1L, 1L, null, 1L);

        for (int failure = 0; failure < 6; failure++) thread.recordSearchFailure(key, 0L);

        assertThat(thread.canSearch(99L, key)).isFalse();
        assertThat(thread.canSearch(100L, key)).isTrue();
    }

    @Test
    void every_search_context_version_change_releases_the_retry_gate() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        RecipeSearchContextKey original = new RecipeSearchContextKey(1L, 2L, 3L, 4L, 5L, 6L,
                null, 7L);
        thread.recordSearchFailure(original, 0L);

        assertThat(thread.canSearch(4L, original)).isFalse();
        assertThat(thread.canSearch(1L, new RecipeSearchContextKey(8L, 2L, 3L, 4L, 5L, 6L, null, 7L))).isTrue();
        assertThat(thread.canSearch(1L, new RecipeSearchContextKey(1L, 8L, 3L, 4L, 5L, 6L, null, 7L))).isTrue();
        assertThat(thread.canSearch(1L, new RecipeSearchContextKey(1L, 2L, 8L, 4L, 5L, 6L, null, 7L))).isTrue();
        assertThat(thread.canSearch(1L, new RecipeSearchContextKey(1L, 2L, 3L, 8L, 5L, 6L, null, 7L))).isTrue();
        assertThat(thread.canSearch(1L, new RecipeSearchContextKey(1L, 2L, 3L, 4L, 8L, 6L, null, 7L))).isTrue();
        assertThat(thread.canSearch(1L, new RecipeSearchContextKey(1L, 2L, 3L, 4L, 5L, 8L, null, 7L))).isTrue();
        assertThat(thread.canSearch(1L, new RecipeSearchContextKey(1L, 2L, 3L, 4L, 5L, 6L,
                MMCR.id("locked"), 7L))).isTrue();
        assertThat(thread.canSearch(1L, new RecipeSearchContextKey(1L, 2L, 3L, 4L, 5L, 6L,
                null, 8L))).isTrue();
    }

    @Test
    void active_failure_arms_the_same_lane_retry_hook() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        MachineRecipe recipe = recipe("factory_active_failure", 20);

        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1, 0L)).isTrue();
        controller.componentRuntime().replaceModifiers(Map.of("changed", List.of()));
        RuntimeTestFixtures.republish(controller);
        thread.tick();

        assertThat(thread.runtime().failure()).isNotNull();
        assertThat(thread.canSearch(1L, searchKey(controller))).isFalse();
    }

    @Test
    void stale_async_runtime_request_completes_as_a_failed_lane_and_keeps_backoff() {
        MachineControllerBlockEntity controller = factoryController("factory_async_version_failure");
        MachineRecipe recipe = new MachineRecipe(MMCR.id("factory_async_version_failure_recipe"),
                MMCR.id("factory_async_version_failure"), 20, List.of(), List.of());
        RecipeRegistry.register(recipe);

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

        assertThat(controller.runtimeSnapshot().factory().failure()).isNotNull();
        assertThat(controller.runtimeSnapshot().factory().failure().details())
                .containsEntry("reason", "version_invalidated");

        controller.serverTick();
        assertThat(controller.runtimeSnapshot().factory().activeLaneCount()).isZero();
        resolveSharedRequests(controller);

        assertThat(controller.runtimeSnapshot().factory().activeLaneCount()).isZero();
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
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new net.minecraft.core.BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        input.linkControllerAppearance(controller.getBlockPos(), null);

        LevelStub.setGameTime(controller.getLevel(), 20L);
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        long afterIron = controller.resourceAvailabilityEpoch();

        LevelStub.setGameTime(controller.getLevel(), 21L);
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 1));

        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(afterIron + 1);
    }

    @Test
    void multi_slot_input_notifies_every_resource_available_after_a_committed_insert() {
        BlockPos inputPos = new BlockPos(1, 0, 0);
        ExtendedItemBusBlockEntity input = new ExtendedItemBusBlockEntity(inputPos,
                ModBlocks.BLOCKS.get("extended_item_input_bus_basic").get().defaultBlockState());
        RecordingController controller = new RecordingController(new BlockPos(0, 0, 0),
                ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        var level = cn.howxu.mmcr.LevelStub.create(
                Map.of(controller.getBlockPos(), controller.getBlockState().getBlock(),
                        input.getBlockPos(), input.getBlockState().getBlock()), List.of(controller, input));
        controller.setLevel(level);
        input.setLevel(level);
        input.linkControllerAppearance(controller.getBlockPos(), null);

        try (Transaction transaction = Transaction.openRoot()) {
            input.itemStorage().insert(0, ItemResource.of(Items.IRON_INGOT), 1L, transaction);
            transaction.commit();
        }
        controller.notifiedResources.clear();

        try (Transaction transaction = Transaction.openRoot()) {
            input.itemStorage().insert(1, ItemResource.of(Items.GOLD_INGOT), 1L, transaction);
            transaction.commit();
        }

        assertThat(controller.notifiedResources).contains(ItemResource.of(Items.IRON_INGOT),
                ItemResource.of(Items.GOLD_INGOT));
    }

    @Test
    void multi_slot_output_notifies_the_resource_released_from_the_changed_slot() {
        BlockPos outputPos = new BlockPos(1, 0, 0);
        ExtendedItemBusBlockEntity output = new ExtendedItemBusBlockEntity(outputPos,
                ModBlocks.BLOCKS.get("extended_item_output_bus_basic").get().defaultBlockState());
        RecordingController controller = new RecordingController(new BlockPos(0, 0, 0),
                ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        var level = cn.howxu.mmcr.LevelStub.create(
                Map.of(controller.getBlockPos(), controller.getBlockState().getBlock(),
                        output.getBlockPos(), output.getBlockState().getBlock()), List.of(controller, output));
        controller.setLevel(level);
        output.setLevel(level);
        output.linkControllerAppearance(controller.getBlockPos(), null);
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        ItemResource gold = ItemResource.of(Items.GOLD_INGOT);

        try (Transaction transaction = Transaction.openRoot()) {
            output.itemStorage().insert(0, iron, 2L, transaction);
            output.itemStorage().insert(1, gold, 2L, transaction);
            transaction.commit();
        }
        controller.notifiedOutputResources.clear();

        try (Transaction transaction = Transaction.openRoot()) {
            output.itemStorage().extract(1, gold, 1L, transaction);
            transaction.commit();
        }

        assertThat(controller.notifiedOutputResources).containsExactly(gold);
    }

    @Test
    void committed_mmcr_capability_operation_notifies_the_linked_controller() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new net.minecraft.core.BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        input.linkControllerAppearance(controller.getBlockPos(), null);
        ItemBusCapability capability = (ItemBusCapability) input.capabilitySnapshot().capabilities().getFirst();
        CapabilityRequests.ResourceRequest<ItemResource> request = new CapabilityRequests.ResourceRequest<>(
                capability.type(), capability.ioType(), 1,
                List.of(new CapabilityRequests.ResourceAction<>(0, ItemResource.of(Items.IRON_INGOT), 1, true)));

        LevelStub.setGameTime(controller.getLevel(), 20L);
        long initial = controller.resourceAvailabilityEpoch();
        try (Transaction transaction = Transaction.openRoot()) {
            CapabilityResult result = capability.prepare(request).commit(transaction);
            assertThat(result.success()).isTrue();
            transaction.commit();
        }

        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(initial + 1);
    }

    @Test
    void rolled_back_energy_capability_operation_does_not_notify_the_linked_controller() {
        EnergyInputHatchBlockEntity energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), energy);
        energy.linkControllerAppearance(controller.getBlockPos(), null);
        EnergyHatchCapability capability = (EnergyHatchCapability) energy.capabilitySnapshot().capabilities().getFirst();
        CapabilityRequests.ValueRequest request = new CapabilityRequests.ValueRequest(
                capability.type(), capability.ioType(), 1, 10L, true);

        LevelStub.setGameTime(controller.getLevel(), 20L);
        long initial = controller.resourceAvailabilityEpoch();
        try (Transaction transaction = Transaction.openRoot()) {
            CapabilityResult result = capability.prepare(request).commit(transaction);
            assertThat(result.success()).isTrue();
        }

        assertThat(capability.storage().amount()).isZero();
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(initial);
    }

    @Test
    void rolled_back_item_capability_operation_does_not_notify_the_linked_controller() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        input.linkControllerAppearance(controller.getBlockPos(), null);
        ItemBusCapability capability = (ItemBusCapability) input.capabilitySnapshot().capabilities().getFirst();
        CapabilityRequests.ResourceRequest<ItemResource> request = new CapabilityRequests.ResourceRequest<>(
                capability.type(), capability.ioType(), 1,
                List.of(new CapabilityRequests.ResourceAction<>(0, ItemResource.of(Items.IRON_INGOT), 1, true)));

        LevelStub.setGameTime(controller.getLevel(), 20L);
        long initial = controller.resourceAvailabilityEpoch();
        try (Transaction transaction = Transaction.openRoot()) {
            CapabilityResult result = capability.prepare(request).commit(transaction);
            assertThat(result.success()).isTrue();
        }

        assertThat(capability.storage().amount(0)).isZero();
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(initial);
    }

    @Test
    void external_item_capability_does_not_notify_before_root_transaction_commit() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        input.linkControllerAppearance(controller.getBlockPos(), null);
        ResourceHandler<ItemResource> handler = externalItemHandler(input);
        LevelStub.setGameTime(controller.getLevel(), 20L);
        long initial = controller.resourceAvailabilityEpoch();

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(handler.insert(0, ItemResource.of(Items.IRON_INGOT), 1, transaction)).isEqualTo(1);
            assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(initial);
        }

        assertThat(input.itemStorage().amount(0)).isZero();
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(initial);
    }

    @Test
    void external_item_capability_notifies_once_after_root_transaction_commit() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        input.linkControllerAppearance(controller.getBlockPos(), null);
        ResourceHandler<ItemResource> handler = externalItemHandler(input);
        LevelStub.setGameTime(controller.getLevel(), 20L);
        long initial = controller.resourceAvailabilityEpoch();

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(handler.insert(0, ItemResource.of(Items.IRON_INGOT), 1, transaction)).isEqualTo(1);
            assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(initial);
            transaction.commit();
            assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(initial + 1L);
        }

        assertThat(input.itemStorage().amount(0)).isEqualTo(1L);
    }

    @Test
    void adding_a_second_resource_wakes_only_the_lane_waiting_for_that_slot_resource() {
        ExtendedItemBusBlockEntity input = new ExtendedItemBusBlockEntity(new BlockPos(1, 0, 0),
                ModBlocks.BLOCKS.get("extended_item_input_bus_basic").get().defaultBlockState());
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        input.linkControllerAppearance(controller.getBlockPos(), null);
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        ItemResource gold = ItemResource.of(Items.GOLD_INGOT);
        try (Transaction transaction = Transaction.openRoot()) {
            input.itemStorage().insert(0, iron, 1L, transaction);
            transaction.commit();
        }

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
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(input.itemStorage().insert(1, gold, 1L, transaction)).isEqualTo(1L);
            transaction.commit();
        }
        assertThat(input.itemStorage().amount(1)).isEqualTo(1L);
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
        FactoryRecipeThread thread = FactoryRecipeThread.core(controller, "core", java.util.Set.of(candidate));
        thread.replaceRecipeSet(java.util.Set.of(candidate, other));
        var snapshot = controller.runtimeSnapshot();
        RecipeSearchContextKey key = new RecipeSearchContextKey(snapshot.structure().version(),
                snapshot.capabilityVersion(), snapshot.modifierVersion(), snapshot.stateVersion(),
                RecipeRegistry.catalog(MMCR.id("test_cube")).version(), controller.resourceAvailabilityEpoch(), null,
                thread.coreRecipeSetVersion());
        thread.recordSearchFailure(key, 0L);

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        thread.save(output);
        FactoryRecipeThread restored = FactoryRecipeThread.load(
                TagValueInput.create(ProblemReporter.DISCARDING, EMPTY_LOOKUP, output.buildResult()), controller,
                null, List.of(candidate, other));

        assertThat(restored.canSearch(1L, key)).isTrue();
    }

    @Test
    void loading_a_simple_lane_uses_the_supplied_candidate_for_last_recipe_retry() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        input.linkControllerAppearance(controller.getBlockPos(), null);
        MachineRecipe candidate = cancellingInputRecipe("factory_supplied_candidate_retry");
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);

        assertThat(thread.searchAndStartRecipe(List.of(candidate), 1, 0L)).isTrue();
        input.getItemStackHandler(null).setStackInSlot(0, ItemStack.EMPTY);
        thread.tick();
        assertThat(thread.runtime().active()).isFalse();

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        thread.save(output);
        FactoryRecipeThread restored = FactoryRecipeThread.load(
                TagValueInput.create(ProblemReporter.DISCARDING, EMPTY_LOOKUP, output.buildResult()), controller,
                null, List.of(candidate));
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        var snapshot = controller.runtimeSnapshot();

        assertThat(restored.tryRestartLastRecipe(List.of(candidate), 1, snapshot.structure().version(),
                snapshot.capabilityVersion(), snapshot.modifierVersion(), snapshot.stateVersion(), null)).isTrue();
    }

    @Test
    void core_lane_filters_context_candidates_without_dropping_fallback_candidates() {
        MachineRecipe core = recipe("factory_core_context", 20);
        MachineRecipe fallback = inputRecipe("factory_fallback_context");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.core(controller, "core", java.util.Set.of(core));
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
                controller.componentRuntime().modifierList(), contextCatalogVersion, contextResourceEpoch, 1, 0L);

        assertThat(thread.searchAndStartRecipe(context, contextSnapshot.structure().version(), null)).isFalse();

        assertThat(thread.searchFailureKey()).isEqualTo(new RecipeSearchContextKey(
                contextSnapshot.structure().version(), contextSnapshot.capabilityVersion(),
                contextSnapshot.modifierVersion(), contextStateVersion, contextCatalogVersion,
                contextResourceEpoch, null, thread.coreRecipeSetVersion()));
    }

    @Test
    void context_pending_start_is_validated_against_the_context_versions() {
        MachineControllerBlockEntity controller = factoryController("factory_context_pending_start");
        ServerLevel level = (ServerLevel) controller.getLevel();
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        assertThat(registry.claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        MachineRecipe candidate = recipe("factory_context_pending_recipe", 20);
        Identifier machineId = controller.structureSnapshot().machine().registryName();
        RecipeRegistry.replaceDynamic(Map.of(candidate.id(), candidate));
        ControllerRuntimeSnapshot live = controller.runtimeSnapshot();
        ControllerRuntimeSnapshot contextSnapshot = snapshotWithStateVersion(live, live.stateVersion() + 1L);
        FactorySearchContext context = new FactorySearchContext(contextSnapshot, List.of(candidate),
                controller.componentRuntime().capabilities(), controller.componentRuntime().modifierList(),
                RecipeRegistry.catalog(machineId).version(),
                controller.resourceAvailabilityEpoch(), 1, 0L);
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);

        assertThat(thread.searchAndStartRecipe(context, contextSnapshot.structure().version(), null)).isTrue();
        assertThat(thread.isStartPending()).isTrue();

        SharedIoCoordinator.get(level).resolve(controller.resourceDomain());

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
        List<MachineRecipe> candidates = RecipeRegistry.catalog(MMCR.id("test_cube")).recipes();

        FactorySearchContext firstContext = runtime.createSearchContext(controller.runtimeSnapshot(), candidates, 1, 0L);
        FactorySearchContext secondContext = runtime.createSearchContext(controller.runtimeSnapshot(), candidates, 1, 1L);

        assertThat(secondContext.orderedCandidates()).isSameAs(firstContext.orderedCandidates());
    }

    @Test
    void core_filter_cache_reuses_equal_candidate_sources() {
        MachineRecipe core = recipe("factory_core_equal_source", 20);
        MachineRecipe fallback = recipe("factory_core_equal_fallback", 20);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.core(controller, "core", java.util.Set.of(core));

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
        List<MachineRecipe> candidates = RecipeRegistry.catalog(MMCR.id("test_cube")).recipes();
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));

        FactorySearchContext ironContext = runtime.createSearchContext(controller.runtimeSnapshot(), candidates, 1, 0L);
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 1));
        FactorySearchContext goldContext = runtime.createSearchContext(controller.runtimeSnapshot(), candidates, 1, 1L);

        assertThat(ironContext.orderedCandidates()).containsExactly(iron);
        assertThat(goldContext.orderedCandidates()).containsExactly(gold);
    }

    private static ControllerRuntimeSnapshot snapshotWithStateVersion(ControllerRuntimeSnapshot snapshot,
                                                                       long stateVersion) {
        return new ControllerRuntimeSnapshot(snapshot.structure(), snapshot.capabilityVersion(),
                snapshot.modifierVersion(), stateVersion, snapshot.foundModifiers(), snapshot.foundLevels(),
                snapshot.linkedPortPositions(), snapshot.moduleConnectionStatus(), snapshot.installedModuleCount(),
                snapshot.capabilityAggregate(), snapshot.crafting(), snapshot.factory(),
                snapshot.componentPresentations(), snapshot.capabilityPresentations(), snapshot.foundLevelIds(),
                snapshot.machineId(), snapshot.machineName(), snapshot.controllerRole(), snapshot.factorySupported(),
                snapshot.factoryControllerPresent(), snapshot.parallelControllerCount(),
                snapshot.maxParallelControllerCount(), snapshot.maxParallelism());
    }

    private static final class RecordingController extends MachineControllerBlockEntity {
        private final List<Object> notifiedResources = new ArrayList<>();
        private final List<Object> notifiedOutputResources = new ArrayList<>();

        private RecordingController(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
            super(pos, state);
        }

        @Override
        public void notifyResourceAvailability(Reason reason, Object resource) {
            if (reason == Reason.INPUT_AVAILABLE) notifiedResources.add(resource);
            if (reason == Reason.OUTPUT_CAPACITY) notifiedOutputResources.add(resource);
        }
    }

    private static MachineControllerBlockEntity factoryController(String path) {
        Identifier machineId = MMCR.id(path);
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

    private static MachineControllerBlockEntity sharedFactoryController(Identifier machineId,
                                                                         Identifier coreRecipeId,
                                                                         IOPortBlockEntity output) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos schedulerPos = controller.getBlockPos().offset(-1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));
        DynamicMachine machine = new DynamicMachine(machineId, machineId.getPath(), pattern,
                MachineControllerSpec.defaultsFor(machineId), MachineAppearanceSpec.defaults(),
                PortRequirementSpec.none(), cn.howxu.mmcr.api.machine.PortTierRequirementSpec.none(),
                List.of(), Map.of(), 1, false, true, 2,
                List.of(new FactoryThreadSpec("blocked", List.of(coreRecipeId))), List.of());
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler, output);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null),
                new ProcessingComponent(new cn.howxu.mmcr.api.recipe.MachineComponent(output.kind(), output.ioType()),
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
        try {
            var field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
            field.setAccessible(true);
            return ((cn.howxu.mmcr.internal.tile.MachineControllerRuntime) field.get(controller)).factoryRuntime();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to access controller factory runtime", exception);
        }
    }

    private static void resolveSharedRequests(MachineControllerBlockEntity controller) {
        if (controller.resourceDomain() != null) {
            SharedIoCoordinator.get((ServerLevel) controller.getLevel()).resolve(controller.resourceDomain());
        }
    }

    @Test
    void missing_retry_fields_in_old_lane_nbt_clear_the_wait() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        thread.recordSearchFailure(searchKey(controller), 0L);

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        thread.save(output);
        var tag = output.buildResult();
        tag.remove("search_failure_streak");
        tag.remove("search_retry_remaining");

        FactoryRecipeThread restored = FactoryRecipeThread.load(
                TagValueInput.create(ProblemReporter.DISCARDING, EMPTY_LOOKUP, tag), controller);

        assertThat(restored.canSearch(0L, searchKey(controller))).isTrue();
    }

    @Test
    void restored_retry_remaining_is_clamped_to_one_hundred_ticks() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        thread.recordSearchFailure(searchKey(controller), 0L);

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        thread.save(output);
        var tag = output.buildResult();
        tag.putInt("search_retry_remaining", Integer.MAX_VALUE);

        FactoryRecipeThread restored = FactoryRecipeThread.load(
                TagValueInput.create(ProblemReporter.DISCARDING, EMPTY_LOOKUP, tag), controller);

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

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        thread.save(output);
        FactoryRecipeThread restored = FactoryRecipeThread.load(
                TagValueInput.create(ProblemReporter.DISCARDING, EMPTY_LOOKUP, output.buildResult()), controller);

        assertThat(restored.canSearch(1L, key)).isTrue();
    }

    private static MachineRecipe inputRecipe(String path) {
        return new MachineRecipe(MMCR.id(path), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                 ItemStack.EMPTY)));
    }

    private static MachineRecipe itemInputRecipe(String path, net.minecraft.world.item.Item item) {
        return new MachineRecipe(MMCR.id(path), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(item), 1, ItemStack.EMPTY)));
    }

    @SuppressWarnings("unchecked")
    private static ResourceHandler<ItemResource> externalItemHandler(ItemInputBusBlockEntity input) {
        try {
            Class<?> type = Class.forName("cn.howxu.mmcr.internal.event.ModCapabilities$ItemStackResourceHandler");
            var constructor = type.getDeclaredConstructor(net.neoforged.neoforge.items.ItemStackHandler.class,
                    boolean.class, boolean.class);
            constructor.setAccessible(true);
            return (ResourceHandler<ItemResource>) constructor.newInstance(input.getItemStackHandler(null), true, true);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to create the production item capability adapter", exception);
        }
    }

    private static MachineRecipe inputEnergyRecipe(String path) {
        return new MachineRecipe(MMCR.id(path), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 4)));
    }

    private static MachineRecipe cancellingInputRecipe(String path) {
        return new MachineRecipe(Identifier.fromNamespaceAndPath(MMCR.MODID, path), MMCR.id("test_cube"), 20,
                 List.of(), List.of(), List.of(), 0, 1, true, List.of(), List.of(
                 new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                 ItemStack.EMPTY, 1F, List.of(), DataComponentPredicateSet.EMPTY, 0F)));
    }

    private static MachineRecipe outputEnergyRecipe(String path) {
        return new MachineRecipe(MMCR.id(path), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 4)));
    }

    private static RecipeSearchContextKey searchKey(MachineControllerBlockEntity controller) {
        var snapshot = controller.runtimeSnapshot();
        return new RecipeSearchContextKey(snapshot.structure().version(), snapshot.capabilityVersion(),
                snapshot.modifierVersion(), snapshot.stateVersion(),
                RecipeRegistry.catalog(MMCR.id("test_cube")).version(), controller.resourceAvailabilityEpoch(),
                controller.lockedRecipeId(), 0L);
    }

    private static MachineRecipe recipe(String path, int duration) {
        return new MachineRecipe(Identifier.fromNamespaceAndPath(MMCR.MODID, path), MMCR.id("test_cube"),
                duration, List.of(), List.of(), List.of(), 0, 2);
    }
}
