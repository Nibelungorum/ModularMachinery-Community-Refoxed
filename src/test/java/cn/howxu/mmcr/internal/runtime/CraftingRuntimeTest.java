package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Final crafting runtime behavior tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class CraftingRuntimeTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
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
