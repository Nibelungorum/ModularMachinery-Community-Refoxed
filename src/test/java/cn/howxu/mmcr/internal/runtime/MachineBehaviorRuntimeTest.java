package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
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
import cn.howxu.mmcr.api.publicapi.machine.MachineIoPlan;
import cn.howxu.mmcr.api.publicapi.machine.RecipeBehavior;
import cn.howxu.mmcr.api.publicapi.machine.TickBehavior;
import cn.howxu.mmcr.api.publicapi.machine.TickBehaviorContext;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.internal.tile.DataStorageBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
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
    void recipe_start_callback_freezes_the_effective_definition_for_the_lifecycle() {
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger ticks = new AtomicInteger();
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID, input, output);
        controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                .beforeStart(context -> {
                    starts.incrementAndGet();
                    assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(2);
                    context.setDuration(2);
                    context.setRequirements(List.of(
                            new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2,
                                    ItemStack.EMPTY),
                            output(Items.GOLD_NUGGET)));
                })
                .recipeTick(context -> {
                    ticks.incrementAndGet();
                    assertThat(context.totalTick()).isEqualTo(2);
                    assertThat(((ItemRequirement) context.requirements().getFirst()).count()).isEqualTo(2);
                    ((MachineOutput.ItemOutput) context.outputs().getFirst()).stack().setCount(64);
                }).build()));
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        MachineRecipe recipe = new MachineRecipe(MMCR.id("behavior_start_snapshot"), TEST_MACHINE_ID, 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                input(Items.IRON_INGOT), output(Items.IRON_NUGGET)));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        assertThat(starts).hasValue(1);
        assertThat(recipe.requirements()).hasSize(2);
        assertThat(((ItemRequirement) recipe.requirements().getFirst()).count()).isEqualTo(1);
        assertThat(runtime.totalTick()).isEqualTo(2);
        assertThat(((ItemRequirement) runtime.activeRecipe().effectiveRequirements().getFirst()).count()).isEqualTo(2);

        runtime.tick();
        runtime.tick();
        runtime.finish();

        assertThat(starts).hasValue(1);
        assertThat(ticks).hasValue(2);
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).is(Items.GOLD_NUGGET)).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
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
    void recipe_tick_callback_cannot_mutate_effective_item_or_fluid_outputs() {
        ItemOutputBusBlockEntity itemOutput = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        FluidOutputHatchBlockEntity fluidOutput = RuntimeTestFixtures.fluidOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID, itemOutput, fluidOutput);
        controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                .recipeTick(context -> {
                    ((MachineOutput.ItemOutput) context.outputs().get(0)).stack().setCount(64);
                    ((MachineOutput.FluidOutput) context.outputs().get(1)).stack().setAmount(2_000);
                }).build()));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = new MachineRecipe(MMCR.id("behavior_tick_stack_isolation"), TEST_MACHINE_ID,
                1, List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                output(Items.IRON_NUGGET),
                new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        new FluidStack(Fluids.WATER, 1_000))));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        runtime.tick();
        runtime.finish();

        assertThat(itemOutput.getItemStackHandler(null).getStackInSlot(0).is(Items.IRON_NUGGET)).isTrue();
        assertThat(itemOutput.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
        assertThat(fluidOutput.fluidStorage().getFluidStack().getAmount()).isEqualTo(1_000);
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
    void formed_tick_callback_commits_one_snapshot_io_and_data_transaction_atomically() {
        BlockPos firstInputPos = new BlockPos(-1, 0, 0);
        BlockPos secondInputPos = new BlockPos(-2, 0, 0);
        BlockPos firstOutputPos = new BlockPos(-3, 0, 0);
        BlockPos secondOutputPos = new BlockPos(-4, 0, 0);
        BlockPos storagePos = new BlockPos(-5, 0, 0);
        ItemInputBusBlockEntity firstInput = RuntimeTestFixtures.itemInput(firstInputPos);
        ItemInputBusBlockEntity secondInput = RuntimeTestFixtures.itemInput(secondInputPos);
        ItemOutputBusBlockEntity firstOutput = RuntimeTestFixtures.itemOutput(firstOutputPos);
        ItemOutputBusBlockEntity secondOutput = RuntimeTestFixtures.itemOutput(secondOutputPos);
        DataStorageBlockEntity dataStorage = (DataStorageBlockEntity) ModBlockEntities.DATA_STORAGE.get().create(
                storagePos, ModBlocks.DATA_STORAGE.get().defaultBlockState());
        firstInput.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        secondInput.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        ItemStack initialOutput = new ItemStack(Items.GOLD_NUGGET, 63);
        initialOutput.set(DataComponents.MAX_STACK_SIZE, 64);
        firstOutput.getItemStackHandler(null).setStackInSlot(0, initialOutput);
        dataStorage.storage().set("ticks", DataValue.of(0L));

        AtomicBoolean failFirstCommit = new AtomicBoolean(true);
        AtomicInteger calls = new AtomicInteger();
        Machine tickMachine = machine(MMCR.id("test_tick_io"), new BlockArray(Map.of(
                new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(
                        ModBlocks.BLOCKS.get("item_input_bus").get()),
                new BlockPos(2, 0, 0), new BlockPredicate.OfBlock(
                        ModBlocks.BLOCKS.get("item_input_bus").get()),
                new BlockPos(3, 0, 0), new BlockPredicate.OfBlock(
                        ModBlocks.BLOCKS.get("item_output_bus").get()),
                new BlockPos(4, 0, 0), new BlockPredicate.OfBlock(
                        ModBlocks.BLOCKS.get("item_output_bus").get()),
                new BlockPos(5, 0, 0), new BlockPredicate.OfBlock(ModBlocks.DATA_STORAGE.get()))),
                TickBehavior.builder().serverTick(context -> {
                    calls.incrementAndGet();
                    assertThat(context).isInstanceOf(TickBehaviorContext.class);
                    assertThat(context.ioView().itemAmount(Ingredient.of(Items.IRON_INGOT))).isEqualTo(2L);
                    assertThat(context.dataStorage(storagePos)).contains(dataStorage.storage());
                    assertThat(dataStorage.storage().get("ticks")).contains(DataValue.of(0L));
                    ItemStack outputStack = new ItemStack(Items.GOLD_NUGGET, 3);
                    outputStack.set(DataComponents.MAX_STACK_SIZE, 64);
                    MachineIoPlan plan = context.ioPlan()
                            .addInput(MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(
                                    Ingredient.of(Items.IRON_INGOT), 2)))
                            .addOutput(MachineRequirement.itemOutput(outputStack),
                                    OutputPolicy.REQUIRE_FULL);
                    assertThat(plan.simulate().inputsSatisfied()).isTrue();
                    assertThat(plan.simulate().outputs()).singleElement()
                            .satisfies(output -> {
                                assertThat(output.requested()).isEqualTo(3L);
                                assertThat(output.accepted()).isEqualTo(3L);
                            });
                    assertThat(dataStorage.storage().get("ticks")).contains(DataValue.of(0L));
                    if (failFirstCommit.getAndSet(false)) {
                        plan.commit(transaction -> {
                            dataStorage.storage().set("ticks", DataValue.of(1L), transaction);
                            throw new IllegalStateException("expected tick transaction failure");
                        });
                    } else {
                        assertThat(plan.commit(transaction ->
                                dataStorage.storage().set("ticks", DataValue.of(1L), transaction)).successful())
                                .isTrue();
                    }
                }).build());
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(TEST_MACHINE_ID, BlockPos.ZERO);
        RuntimeTestFixtures.formStructureWithComponents(controller, tickMachine,
                firstInput, secondInput, firstOutput, secondOutput, dataStorage);
        assertThat(controller.structureSnapshot().configuredMachine()).isSameAs(tickMachine);
        assertThat(controller.structureSnapshot().pattern().pattern().keySet())
                .contains(firstInputPos, secondInputPos, firstOutputPos, secondOutputPos, storagePos);
        assertThat(controller.componentRuntime().components())
                .as("components=%s capabilities=%s", controller.componentRuntime().components(),
                        controller.componentRuntime().capabilities())
                .hasSize(4);
        assertThat(controller.componentRuntime().capabilities()).hasSize(4);

        ServerLevel level = (ServerLevel) controller.getLevel();
        controller.tickRuntimeWork(level, controller.getBlockPos());

        assertThat(calls).hasValue(1);
        assertThat(firstInput.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
        assertThat(secondInput.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
        assertThat(firstOutput.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(63);
        assertThat(secondOutput.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        assertThat(dataStorage.storage().get("ticks")).contains(DataValue.of(0L));

        RuntimeTestFixtures.advanceGameTime(level);
        controller.tickRuntimeWork(level, controller.getBlockPos());

        assertThat(calls).hasValue(2);
        assertThat(firstInput.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        assertThat(secondInput.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        assertThat(firstOutput.getItemStackHandler(null).getStackInSlot(0).getCount()
                + secondOutput.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(66);
        assertThat(dataStorage.storage().get("ticks")).contains(DataValue.of(1L));
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
        return machine(id, new BlockArray(Map.of()), behavior);
    }

    private static Machine machine(net.minecraft.resources.Identifier id, BlockArray pattern,
                                   MachineBehavior behavior) {
        return new DynamicMachine(id, id.toString(), pattern,
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
