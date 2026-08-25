package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.LevelStub;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Final crafting runtime behavior tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class CraftingRuntimeTest {
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
    void startTickAndFinishCommitInputsAndOutputs() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input, output);
        input.getItemStackHandler(null).setStackInSlot(0, stack(Items.IRON_INGOT, 2));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_complete", 1, List.of(
                input(Items.IRON_INGOT, 2), output(Items.IRON_NUGGET, 1)));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        assertThat(runtime.tick().isCrafting()).isTrue();
        assertThat(runtime.finish().getStatus()).isEqualTo(cn.howxu.mmcr.api.recipe.helper.CraftingStatus.Status.IDLE);
        ItemStack result = output.getItemStackHandler(null).getStackInSlot(0);
        assertThat(result.getItem()).isEqualTo(Items.IRON_NUGGET);
        assertThat(result.getCount()).isEqualTo(1);
    }

    @Test
    void failedStartReportsStructuredMissingResourceAndRollsBackRootTransaction() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        input.getItemStackHandler(null).setStackInSlot(0, stack(Items.IRON_INGOT, 1));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_missing_input", 20, List.of(
                input(Items.IRON_INGOT, 2)));

        runtime.start(recipe, 1);

        assertThat(runtime.active()).isFalse();
        assertThat(runtime.failure()).isNotNull();
        assertThat(runtime.failure().details()).containsEntry("reason", "insufficient_resource");
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
    }

    @Test
    void duplicateInputRequirementsRemainAtomicWhenCombinedStorageIsInsufficient() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        input.getItemStackHandler(null).setStackInSlot(0, stack(Items.IRON_INGOT, 1));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_duplicate_input", 20, List.of(
                input(Items.IRON_INGOT, 1), input(Items.IRON_INGOT, 1)));

        runtime.start(recipe, 1);

        assertThat(runtime.active()).isFalse();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
    }

    @Test
    void duplicateOutputRequirementsCommit_each_output_to_the_real_storage() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_duplicate_output", 1, List.of(
                output(Items.IRON_NUGGET, 1), output(Items.IRON_NUGGET, 1)));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        runtime.tick();
        assertThat(runtime.finish().getStatus()).isEqualTo(cn.howxu.mmcr.api.recipe.helper.CraftingStatus.Status.IDLE);
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(2);
    }

    @Test
    void redstonePauseAndResumeKeepTheActiveRuntime() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_pause", 20, List.of());

        runtime.start(recipe, 1);
        runtime.pause();
        assertThat(runtime.snapshot().status().isPaused()).isTrue();
        assertThat(runtime.active()).isTrue();

        runtime.resume();
        assertThat(runtime.snapshot().status().isCrafting()).isTrue();
    }

    @Test
    void capabilityVersionInvalidationCancelsTheActiveRuntime() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        input.getItemStackHandler(null).setStackInSlot(0, stack(Items.IRON_INGOT, 1));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_invalidation", 20, List.of(input(Items.IRON_INGOT, 1)));

        runtime.start(recipe, 1);
        controller.componentRuntime().replaceComponents(List.of());
        controller.setMachine(controller.runtimeSnapshot().structure().configuredMachine());

        runtime.tick();

        assertThat(runtime.active()).isFalse();
        assertThat(runtime.failure().details()).containsEntry("reason", "version_invalidated");
    }

    @Test
    void perTickEnergyIsCommittedThroughTheRealEnergyCapability() {
        EnergyInputHatchBlockEntity energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), energy);
        energy.energyStorage().setAmount(10);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = new MachineRecipe(MMCR.id("runtime_energy"), MMCR.id("test_cube"), 3,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(new EnergyRequirement(2)));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(8);
        runtime.tick();

        assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(6);
        assertThat(runtime.active()).isTrue();
    }

    @Test
    void modifier_and_component_state_changes_invalidate_an_active_runtime() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_version_changes", 20, List.of());

        runtime.start(recipe, 1);
        controller.componentRuntime().replaceModifiers(Map.of("changed", List.of()));
        RuntimeTestFixtures.republish(controller);
        runtime.tick();
        assertThat(runtime.failure().details()).containsEntry("reason", "version_invalidated");

        runtime.start(recipe, 1);
        controller.componentRuntime().replaceModuleConnectionState(ModuleConnectionStatus.connected(MMCR.id("host")), 1);
        RuntimeTestFixtures.republish(controller);
        runtime.tick();
        assertThat(runtime.failure().details()).containsEntry("reason", "version_invalidated");
    }

    @Test
    void zero_consume_chance_retains_the_input_across_start_and_tick() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        input.getItemStackHandler(null).setStackInSlot(0, stack(Items.IRON_INGOT, 1));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = new MachineRecipe(MMCR.id("runtime_retain_input"), MMCR.id("test_cube"), 2,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                        ItemStack.EMPTY, 1F, List.of(),
                        DataComponentPredicateSet.EMPTY, 0F)));

        runtime.start(recipe, 1);
        runtime.tick();

        assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
    }

    @Test
    void zeroChanceOutputDoesNotMutateStorageAtFinish() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_zero_chance", 1, List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        stack(Items.IRON_NUGGET, 2), 0F, List.of())));

        runtime.start(recipe, 1);
        runtime.tick();
        runtime.finish();

        assertThat(output.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
    }

    @Test
    void positive_output_and_consume_chance_commit_through_real_item_storage() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input, output);
        input.getItemStackHandler(null).setStackInSlot(0, stack(Items.IRON_INGOT, 1));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        ItemRequirement consumed = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                ItemStack.EMPTY, 1F, List.of(), DataComponentPredicateSet.EMPTY, 1F);
        ItemRequirement produced = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                stack(Items.IRON_NUGGET, 1), 1F, List.of(), DataComponentPredicateSet.EMPTY, 1F);
        MachineRecipe recipe = recipe("runtime_positive_chance", 1, List.of(consumed, produced));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        runtime.tick();
        runtime.finish();

        assertThat(output.getItemStackHandler(null).getStackInSlot(0).is(Items.IRON_NUGGET)).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
    }

    @Test
    void partialOutputCommitsAvailableStorageWithoutLeakingTheRemainder() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        for (int slot = 1; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            output.getItemStackHandler(null).setStackInSlot(slot, stack(Items.COBBLESTONE, 64));
        }
        output.getItemStackHandler(null).setStackInSlot(0, stack(Items.IRON_INGOT, 44));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = new MachineRecipe(MMCR.id("runtime_partial_output"), MMCR.id("test_cube"), 1,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(output(Items.IRON_INGOT, 64)), false, List.of(), true);

        runtime.start(recipe, 1);
        runtime.tick();
        runtime.finish();

        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(64);
    }

    @Test
    void blocked_finish_stays_pending_until_the_output_retry_window_opens() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), output);
        var level = LevelStub.createWithBlockEntities(List.of(controller, output));
        controller.setLevel(level);
        output.setLevel(level);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_finish_retry", 1, List.of(output(Items.IRON_NUGGET, 1)));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        for (int slot = 0; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            output.getItemStackHandler(null).setStackInSlot(slot, stack(Items.COBBLESTONE, 64));
        }
        runtime.tick();

        assertThat(runtime.finishPending()).isTrue();
        assertThat(runtime.finish().getStatus()).isEqualTo(cn.howxu.mmcr.api.recipe.helper.CraftingStatus.Status.NO_RECIPE);
        assertThat(runtime.active()).isTrue();
        assertThat(runtime.shouldRetryFinish()).isFalse();

        output.getItemStackHandler(null).setStackInSlot(0, ItemStack.EMPTY);
        LevelStub.setGameTime(level, 10);

        assertThat(runtime.shouldRetryFinish()).isTrue();
        assertThat(runtime.finish().getStatus()).isEqualTo(cn.howxu.mmcr.api.recipe.helper.CraftingStatus.Status.IDLE);
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).is(Items.IRON_NUGGET)).isTrue();
    }

    @Test
    void active_runtime_persists_finish_state_and_input_plan() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe recipe = recipe("runtime_persisted", 1, List.of());
        RecipeRegistry.register(recipe);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        runtime.start(recipe, 1);
        runtime.tick();
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        runtime.save(output);

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, EMPTY_LOOKUP, output.buildResult()), null);

        assertThat(restored.active()).isTrue();
        assertThat(restored.finishPending()).isTrue();
        assertThat(restored.recipe()).isEqualTo(recipe);
        assertThat(restored.activeRecipe().inputConsumptionPlan().consumedBatches(0)).isZero();
    }

    private static MachineRecipe recipe(String path, int duration, List<ItemRequirement> requirements) {
        return new MachineRecipe(MMCR.id(path), MMCR.id("test_cube"), duration,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), new java.util.ArrayList<>(requirements));
    }

    private static ItemRequirement input(net.minecraft.world.item.Item item, int count) {
        return new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(item), count, ItemStack.EMPTY);
    }

    private static ItemRequirement output(net.minecraft.world.item.Item item, int count) {
        return new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, stack(item, count));
    }

    private static ItemStack stack(net.minecraft.world.item.Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        return stack;
    }
}
