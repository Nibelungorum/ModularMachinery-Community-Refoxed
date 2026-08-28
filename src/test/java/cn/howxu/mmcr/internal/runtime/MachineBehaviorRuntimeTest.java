package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehavior;
import cn.howxu.mmcr.api.publicapi.machine.RecipeBehavior;
import cn.howxu.mmcr.api.publicapi.machine.TickBehavior;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Verifies machine behavior callbacks at the runtime boundary.
 * @author howxu <dev@howxu.cn>
 */
class MachineBehaviorRuntimeTest {
    private static final net.minecraft.resources.Identifier TEST_MACHINE_ID = MMCR.id("test_cube");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void recipe_start_callback_can_cancel_without_consuming_inputs() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID, input);
        controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                .beforeStart(context -> context.cancel()).build()));
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.start(recipe("behavior_start_veto_recipe", input(Items.IRON_INGOT)), 1).isCrafting())
                .isFalse();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
    }

    @Test
    void recipe_behavior_runs_the_full_lifecycle_and_updates_screen_text() {
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger ticks = new AtomicInteger();
        AtomicInteger finishes = new AtomicInteger();
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID, input, output);
        Machine machine = machine(controller.machineId(), RecipeBehavior.builder()
                .beforeStart(context -> {
                    if (starts.getAndIncrement() == 0) context.cancel();
                })
                .recipeTick(context -> ticks.incrementAndGet())
                .beforeFinish(context -> {
                    finishes.incrementAndGet();
                    context.setOutputs(List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_NUGGET), 1F)));
                    controller.behaviorContext().screenText().append(ControllerScreenTextScope.OPERATION,
                            MMCR.id("behavior_lifecycle"), net.minecraft.network.chat.Component.literal("finished"));
                }).build());
        controller.setMachine(machine);
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.start(recipe("behavior_lifecycle", input(Items.IRON_INGOT), output(Items.IRON_NUGGET)), 1)
                .isCrafting()).isFalse();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);

        var secondStart = runtime.start(recipe("behavior_lifecycle", input(Items.IRON_INGOT), output(Items.IRON_NUGGET)), 1);
        assertThat(secondStart.isCrafting())
                .as("second start status=%s failure=%s", secondStart, runtime.failure())
                .isTrue();
        runtime.tick();
        runtime.finish();

        assertThat(starts).hasValue(2);
        assertThat(ticks).hasValue(1);
        assertThat(finishes).hasValue(1);
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).is(Items.GOLD_NUGGET)).isTrue();
        assertThat(((ControllerScreenTextState) controller.behaviorContext().screenText()).snapshot().lines())
                .anyMatch(line -> line.text().getString().equals("finished"));
    }

    @Test
    void finish_callback_can_cancel_and_leave_finish_pending() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID);
        controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                .beforeFinish(context -> context.cancel()).build()));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.start(recipe("behavior_finish_veto_recipe"), 1).isCrafting()).isTrue();
        runtime.tick();
        runtime.finish();

        assertThat(runtime.active()).isTrue();
        assertThat(runtime.finishPending()).isTrue();
    }

    @Test
    void finish_callback_replaces_the_committed_output() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID, output);
        controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                .beforeFinish(context -> context.setOutputs(List.of(
                        new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_NUGGET), 1F)))).build()));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.start(recipe("behavior_output_recipe", output(Items.IRON_NUGGET)), 1).isCrafting())
                .isTrue();
        runtime.tick();
        runtime.finish();

        assertThat(output.getItemStackHandler(null).getStackInSlot(0).is(Items.GOLD_NUGGET)).isTrue();
    }

    @Test
    void formed_tick_machine_runs_callback_without_starting_recipe_runtime() {
        AtomicInteger calls = new AtomicInteger();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID);
        Machine tickMachine = machine(controller.machineId(), TickBehavior.builder()
                .serverTick(context -> calls.incrementAndGet()).build());
        RuntimeTestFixtures.formStructure(controller, tickMachine);

        controller.tickRuntimeWork((net.minecraft.server.level.ServerLevel) controller.getLevel(), controller.getBlockPos());

        assertThat(calls).hasValue(1);
        assertThat(controller.currentRuntimeSnapshot().crafting().recipeId()).isNull();
        assertThat(controller.runtimeSnapshot().crafting().status().isCrafting()).isFalse();
    }

    @Test
    void factory_lane_uses_the_same_recipe_tick_callback() {
        AtomicInteger calls = new AtomicInteger();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID);
        controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                .recipeTick(context -> calls.incrementAndGet()).build()));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        MachineRecipe recipe = recipe("behavior_factory_recipe");

        runtime.tick(List.of(recipe), 1, 0L);
        runtime.tick(List.of(recipe), 1, 1L);

        assertThat(calls).hasValue(1);
    }

    @Test
    void idle_callbacks_surround_recipe_work_when_no_lane_is_active() {
        AtomicInteger idleStart = new AtomicInteger();
        AtomicInteger idleEnd = new AtomicInteger();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID);
        Machine recipeMachine = machine(controller.machineId(), RecipeBehavior.builder()
                .idleStart(context -> idleStart.incrementAndGet())
                .idleEnd(context -> idleEnd.incrementAndGet()).build());
        RuntimeTestFixtures.formStructure(controller, recipeMachine);

        controller.tickRuntimeWork((net.minecraft.server.level.ServerLevel) controller.getLevel(), controller.getBlockPos());

        assertThat(idleStart).hasValue(1);
        assertThat(idleEnd).hasValue(1);
    }

    @Test
    void callback_exception_cancels_start_without_consuming_inputs() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID, input);
        controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                .beforeStart(context -> { throw new IllegalStateException("test callback failure"); }).build()));
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThatCode(() -> runtime.start(recipe("behavior_exception_recipe", input(Items.IRON_INGOT)), 1))
                .doesNotThrowAnyException();
        assertThat(runtime.active()).isFalse();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
    }

    @Test
    void invalid_callback_output_does_not_start_a_finish_transaction() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID, output);
        controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                .beforeFinish(context -> context.setOutputs(List.of(
                        new MachineOutput.ItemOutput(ItemStack.EMPTY, 1F)))).build()));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.start(recipe("behavior_invalid_output", output(Items.IRON_NUGGET)), 1).isCrafting())
                .isTrue();
        runtime.tick();

        assertThat(runtime.finish().getStatus())
                .isEqualTo(cn.howxu.mmcr.api.recipe.helper.CraftingStatus.Status.NO_RECIPE);
        assertThat(runtime.active()).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
    }

    private static Machine machine(net.minecraft.resources.Identifier id, MachineBehavior behavior) {
        return new DynamicMachine(id, id.toString(), new BlockArray(Map.of()),
                MachineControllerSpec.defaultsFor(id), MachineAppearanceSpec.defaults(), PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), java.util.Map.of(), 1, false, false, 1, List.of(),
                MachineRole.NORMAL, Set.of(), List.of(), RecipeFailureActions.getDefaultAction(), behavior);
    }

    private static MachineRecipe recipe(String path, ItemRequirement... requirements) {
        return new MachineRecipe(MMCR.id(path), TEST_MACHINE_ID, 1, List.of(), List.of(),
                List.of(), 0, 1, false, List.of(), List.of(requirements));
    }

    private static ItemRequirement input(net.minecraft.world.item.Item item) {
        return new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(item), 1,
                ItemStack.EMPTY, 1F, List.of());
    }

    private static ItemRequirement output(net.minecraft.world.item.Item item) {
        return new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                new ItemStack(item), 1F, List.of());
    }
}
