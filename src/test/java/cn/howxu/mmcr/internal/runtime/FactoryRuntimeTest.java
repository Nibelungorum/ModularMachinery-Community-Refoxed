package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static MachineRecipe recipe(String path, int duration) {
        return new MachineRecipe(Identifier.fromNamespaceAndPath(MMCR.MODID, path), MMCR.id("test_cube"),
                duration, List.of(), List.of(), List.of(), 0, 2);
    }
}
