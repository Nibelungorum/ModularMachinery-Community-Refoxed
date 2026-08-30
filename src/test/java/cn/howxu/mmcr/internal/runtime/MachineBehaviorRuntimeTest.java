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
import cn.howxu.mmcr.api.publicapi.machine.MachineBehaviorContext;
import cn.howxu.mmcr.api.publicapi.machine.MachineIoPlan;
import cn.howxu.mmcr.api.publicapi.machine.RecipeBehavior;
import cn.howxu.mmcr.api.publicapi.machine.TickBehavior;
import cn.howxu.mmcr.api.publicapi.machine.TickBehaviorContext;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.publicapi.machine.OutputPolicy;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.internal.tile.DataStorageBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.UpgradeBusBlockEntity;
import cn.howxu.mmcr.internal.port.UpgradeBusSize;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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

    @AfterEach
    void cleanupRecipes() {
        RecipeRegistry.clearForTesting();
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
                .recipeTick(context -> {
                    ticks.incrementAndGet();
                    context.machineContext().screenText().append(ControllerScreenTextScope.OPERATION,
                            MMCR.id("recipe_tick_status"), Component.literal("running"));
                })
                .beforeFinish(context -> {
                    finishes.incrementAndGet();
                    context.setOutputs(List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_NUGGET), 1F)));
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
                .anyMatch(line -> line.text().getString().equals("running"));
    }

    @Test
    void recipe_callbacks_use_the_assigned_lane_screen_text() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID, input, output);
        ControllerScreenTextState laneText = new ControllerScreenTextState();
        controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                .beforeStart(context -> context.machineContext().screenText().append(
                        ControllerScreenTextScope.CONTROLLER, MMCR.id("lane_progress"), Component.literal("start")))
                .recipeTick(context -> {
                    context.machineContext().screenText().replace(MMCR.id("lane_progress"),
                            Component.literal("tick"));
                    context.machineContext().screenText().append(
                            ControllerScreenTextScope.OPERATION, MMCR.id("lane_operation"), Component.literal("running"));
                })
                .beforeFinish(context -> {
                    context.machineContext().screenText().append(
                            ControllerScreenTextScope.CONTROLLER, MMCR.id("lane_finished"), Component.literal("finish"));
                    context.setOutputs(List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_NUGGET), 1F)));
                }).build()));
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        runtime.setScreenText(laneText);

        assertThat(runtime.start(recipe("lane_text", input(Items.IRON_INGOT)), 1).isCrafting()).isTrue();
        runtime.tick();
        runtime.finish();

        assertThat(laneText.snapshot().lines()).extracting(ControllerScreenTextSnapshot.Line::text)
                .extracting(Component::getString)
                .containsExactly("tick", "running", "finish");
        assertThat(((ControllerScreenTextState) controller.behaviorContext().screenText()).snapshot().lines())
                .isEmpty();
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
    void tick_context_exposes_effective_factory_threads_and_parallelism() {
        Identifier machineId = MMCR.id("tick_context_components");
        BlockPos factoryPos = new BlockPos(1, 0, 0);
        BlockPos parallelPos = new BlockPos(2, 0, 0);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(factoryPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        scheduler.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.STICK, 2));
        ParallelControllerBlockEntity parallel = new ParallelControllerBlockEntity(ParallelTier.NORMAL, parallelPos,
                ModBlocks.BLOCKS.get("parallel_controller_normal").get().defaultBlockState());
        parallel.setCurrentParallelism(4);
        AtomicInteger calls = new AtomicInteger();
        BlockArray pattern = new BlockArray(Map.of(
                factoryPos, new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()),
                parallelPos, new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("parallel_controller_normal").get())));
        Machine machine = new DynamicMachine(machineId, machineId.toString(), pattern,
                MachineControllerSpec.defaultsFor(machineId), MachineAppearanceSpec.defaults(), PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), Map.of(), 8, true, true, 3, List.of(), MachineRole.NORMAL,
                Set.of(), List.of(), RecipeFailureActions.getDefaultAction(), TickBehavior.builder()
                        .serverTick(context -> {
                            calls.incrementAndGet();
                            assertThat(context.factoryThreadCount()).isEqualTo(5);
                            assertThat(context.parallelism()).isEqualTo(4L);
                        }).build());
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(TEST_MACHINE_ID, BlockPos.ZERO);
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler, parallel);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, factoryPos, factoryPos, (String) null),
                new ProcessingComponent(null, parallel, parallelPos, parallelPos, (String) null)));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        assertThat(scheduler.threadCount()).isEqualTo(3);
        assertThat(controller.factorySchedulerThreadCount()).isEqualTo(3);
        assertThat(controller.structureSnapshot().machine().factoryThreadLimit()).isEqualTo(3);

        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());

        assertThat(calls).hasValue(1);
        assertThat(controller.runtimeSnapshot().factory().active()).isFalse();
    }

    @Test
    void tick_context_uses_neutral_component_values_when_features_are_disabled() {
        Identifier machineId = MMCR.id("tick_context_disabled_components");
        BlockPos factoryPos = new BlockPos(1, 0, 0);
        BlockPos parallelPos = new BlockPos(2, 0, 0);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(factoryPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        scheduler.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.STICK, 2));
        ParallelControllerBlockEntity parallel = new ParallelControllerBlockEntity(ParallelTier.NORMAL, parallelPos,
                ModBlocks.BLOCKS.get("parallel_controller_normal").get().defaultBlockState());
        parallel.setCurrentParallelism(4);
        AtomicInteger calls = new AtomicInteger();
        BlockArray pattern = new BlockArray(Map.of(
                factoryPos, new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()),
                parallelPos, new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("parallel_controller_normal").get())));
        Machine machine = new DynamicMachine(machineId, machineId.toString(), pattern,
                MachineControllerSpec.defaultsFor(machineId), MachineAppearanceSpec.defaults(), PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), Map.of(), 1, false, false, 1, List.of(), MachineRole.NORMAL,
                Set.of(), List.of(), RecipeFailureActions.getDefaultAction(), TickBehavior.builder()
                        .serverTick(context -> {
                            calls.incrementAndGet();
                            assertThat(context.factoryThreadCount()).isEqualTo(1);
                            assertThat(context.parallelism()).isEqualTo(1L);
                        }).build());
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(TEST_MACHINE_ID, BlockPos.ZERO);
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler, parallel);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, factoryPos, factoryPos, (String) null),
                new ProcessingComponent(null, parallel, parallelPos, parallelPos, (String) null)));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);

        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());

        assertThat(calls).hasValue(1);
        assertThat(controller.effectiveFactoryThreadLimit()).isEqualTo(1);
        assertThat(controller.runtimeSnapshot().factory().active()).isFalse();
    }

    @Test
    void tick_context_keeps_upgrade_items_when_recipe_modifiers_are_disabled() {
        UpgradeBusBlockEntity bus = new UpgradeBusBlockEntity(UpgradeBusSize.NORMAL, new BlockPos(1, 0, 0),
                ModBlocks.BLOCKS.get("upgrade_bus_normal").get().defaultBlockState());
        ItemStack source = new ItemStack(Items.IRON_INGOT, 2);
        bus.itemStackHandler().setStackInSlot(0, source);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(TEST_MACHINE_ID, BlockPos.ZERO);
        Machine machine = machine(TEST_MACHINE_ID, new BlockArray(Map.of(
                new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("upgrade_bus_normal").get()))),
                TickBehavior.builder().serverTick(context -> {
                    assertThat(context.upgradeItems()).singleElement().satisfies(stack -> {
                        assertThat(stack).isNotSameAs(source);
                        assertThat(stack.getCount()).isEqualTo(2);
                    });
                }).build());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, bus);

        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());
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
                    assertThat(context.dataStorage()).isSameAs(dataStorage.storage());
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
        int outputCount = 0;
        for (int slot = 0; slot < firstOutput.getItemStackHandler(null).getSlots(); slot++) {
            outputCount += firstOutput.getItemStackHandler(null).getStackInSlot(slot).getCount();
        }
        for (int slot = 0; slot < secondOutput.getItemStackHandler(null).getSlots(); slot++) {
            outputCount += secondOutput.getItemStackHandler(null).getStackInSlot(slot).getCount();
        }
        assertThat(outputCount).isEqualTo(66);
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
        Machine recipeMachine = machine(TEST_MACHINE_ID, RecipeBehavior.builder()
                .idleStart(context -> idleStart.incrementAndGet())
                .idleEnd(context -> idleEnd.incrementAndGet()).build());
        RuntimeTestFixtures.formStructure(controller, recipeMachine);

        controller.tickRuntimeWork((net.minecraft.server.level.ServerLevel) controller.getLevel(), controller.getBlockPos());

        assertThat(idleStart).hasValue(1);
        assertThat(idleEnd).hasValue(1);
    }

    @Test
    void recipe_machine_hooks_surround_existing_recipe_work_with_fresh_contexts() {
        Identifier machineId = MMCR.id("recipe_hook_lifecycle_machine");
        List<String> phases = new ArrayList<>();
        List<MachineBehaviorContext> preContexts = new ArrayList<>();
        List<MachineBehaviorContext> postContexts = new ArrayList<>();
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(-1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(-2, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(TEST_MACHINE_ID, BlockPos.ZERO);
        BlockArray pattern = new BlockArray(Map.of(
                new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()),
                new BlockPos(2, 0, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_output_bus").get())));
        Machine recipeMachine = machine(machineId, pattern, RecipeBehavior.builder()
                .preServerTick(context -> {
                    preContexts.add(context);
                    phases.add("pre");
                    assertThat(context.ioView()).isNotNull();
                    context.screenText().append(ControllerScreenTextScope.CONTROLLER,
                            MMCR.id("recipe_hook_status"), Component.literal("pre"));
                })
                .beforeStart(context -> phases.add("beforeStart"))
                .recipeTick(context -> phases.add("recipeTick"))
                .beforeFinish(context -> phases.add("beforeFinish"))
                .postServerTick(context -> {
                    postContexts.add(context);
                    phases.add("post");
                    context.screenText().append(ControllerScreenTextScope.CONTROLLER,
                            MMCR.id("recipe_hook_status"), Component.literal("post"));
                })
                .build());
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        RuntimeTestFixtures.formStructureWithComponents(controller, recipeMachine, input, output);
        MachineRecipe lifecycleRecipe = recipe("behavior_recipe_hook_lifecycle", machineId,
                input(Items.IRON_INGOT), output(Items.GOLD_NUGGET));
        RecipeRegistry.registerStatic(lifecycleRecipe);
        assertThat(controller.structureSnapshot().machine()).isSameAs(recipeMachine);
        assertThat(RecipeRegistry.byMachineId(machineId)).containsExactly(lifecycleRecipe);
        assertThat(controller.componentRuntime().capabilities())
                .as("components=%s capabilities=%s", controller.componentRuntime().components(),
                        controller.componentRuntime().capabilities())
                .hasSize(2);
        assertThat(controller.behaviorContext().ioView().itemAmount(Ingredient.of(Items.IRON_INGOT))).isEqualTo(1L);

        controller.tickRuntimeWork((net.minecraft.server.level.ServerLevel) controller.getLevel(),
                controller.getBlockPos());
        assertThat(controller.runtimeSnapshot().crafting().recipeId())
                .as("registered recipe starts: status=%s failure=%s", controller.runtimeSnapshot().crafting().status(),
                        controller.runtimeSnapshot().crafting().failure())
                .isEqualTo(lifecycleRecipe.id());
        RuntimeTestFixtures.advanceGameTime(controller.getLevel());
        controller.tickRuntimeWork((net.minecraft.server.level.ServerLevel) controller.getLevel(),
                controller.getBlockPos());

        assertThat(phases).containsExactly("pre", "beforeStart", "post", "pre", "recipeTick", "beforeFinish", "post");
        assertThat(preContexts).hasSize(2);
        assertThat(postContexts).hasSize(2);
        assertThat(preContexts.get(0)).isNotSameAs(postContexts.get(0));
        assertThat(preContexts.get(1)).isNotSameAs(postContexts.get(1));
        assertThat(preContexts.get(0)).isNotSameAs(preContexts.get(1));
        assertThat(postContexts.get(0)).isNotSameAs(postContexts.get(1));
        assertThat(preContexts.get(0).screenText()).isSameAs(postContexts.get(0).screenText());
        assertThat(preContexts.get(1).screenText()).isSameAs(postContexts.get(1).screenText());
        assertThat(((ControllerScreenTextState) preContexts.get(1).screenText()).snapshot().lines())
                 .singleElement().satisfies(line -> assertThat(line.text()).isEqualTo(Component.literal("post")));
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).is(Items.GOLD_NUGGET)).isTrue();
    }

    @Test
    void recipe_machine_hook_failure_does_not_skip_the_other_hook() {
        AtomicInteger postCalls = new AtomicInteger();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID);
        Machine recipeMachine = machine(controller.machineId(), RecipeBehavior.builder()
                .preServerTick(context -> { throw new IllegalStateException("pre hook failure"); })
                .postServerTick(context -> postCalls.incrementAndGet())
                .build());
        RuntimeTestFixtures.formStructure(controller, recipeMachine);

        assertThatCode(() -> controller.tickRuntimeWork(
                (net.minecraft.server.level.ServerLevel) controller.getLevel(), controller.getBlockPos()))
                .doesNotThrowAnyException();
        assertThat(postCalls).hasValue(1);
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
        return recipe(path, TEST_MACHINE_ID, requirements);
    }

    private static MachineRecipe recipe(String path, Identifier machineId, ItemRequirement... requirements) {
        return new MachineRecipe(MMCR.id(path), machineId, 1, List.of(), List.of(),
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
