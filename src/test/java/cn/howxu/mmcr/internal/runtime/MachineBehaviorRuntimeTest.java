package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.TickFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickContext;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickPhase;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickResult;
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
import cn.howxu.mmcr.api.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.machine.definition.MachineBehavior;
import cn.howxu.mmcr.api.machine.definition.MachineBehaviorContext;
import cn.howxu.mmcr.api.machine.definition.MachineIoPlan;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.machine.definition.RecipeTickContext;
import cn.howxu.mmcr.api.machine.definition.TickBehavior;
import cn.howxu.mmcr.api.machine.definition.TickBehaviorContext;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.internal.tile.DataStorageBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.UpgradeBusBlockEntity;
import cn.howxu.mmcr.internal.port.UpgradeBusSize;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.ConfigTestSupport;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.fluids.FluidStack;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.neoforged.fml.config.IConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Verifies machine behavior callbacks at the runtime boundary.
 * @author howxu <dev@howxu.cn>
 */
class MachineBehaviorRuntimeTest {
    private ServerThreadTestScope serverThreadScope;
    private static final ResourceLocation TEST_MACHINE_ID = MMCR.id("test_cube");

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

    @BeforeEach
    void installServerThread() throws Exception {
        serverThreadScope = ServerThreadTestScope.open();
    }

    @AfterEach
    void cleanupRecipes() throws Exception {
        try {
            ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
            RecipeRegistry.clearForTesting();
        } finally {
            if (serverThreadScope != null) serverThreadScope.close();
        }
    }

    @Test
    void recipe_start_callback_can_cancel_without_consuming_inputs() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID, input);
        controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                .beforeStart(context -> context.cancel()).build()));
        setItem(input.itemHandler(), 0, new ItemStack(Items.IRON_INGOT));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.start(recipe("behavior_start_veto_recipe", input(Items.IRON_INGOT)), 1).isCrafting())
                .isFalse();
        assertThat(input.itemHandler().amount(0)).isEqualTo(1L);
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
        setItem(input.itemHandler(), 0, new ItemStack(Items.IRON_INGOT));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.start(recipe("behavior_lifecycle", input(Items.IRON_INGOT), output(Items.IRON_NUGGET)), 1)
                .isCrafting()).isFalse();
        assertThat(input.itemHandler().amount(0)).isEqualTo(1L);

        var secondStart = runtime.start(recipe("behavior_lifecycle", input(Items.IRON_INGOT), output(Items.IRON_NUGGET)), 1);
        assertThat(secondStart.isCrafting())
                .as("second start status=%s failure=%s", secondStart, runtime.failure())
                .isTrue();
        runtime.tick();
        runtime.finish();

        assertThat(starts).hasValue(2);
        assertThat(ticks).hasValue(1);
        assertThat(finishes).hasValue(1);
        assertThat(item(output.itemHandler(), 0).is(Items.GOLD_NUGGET)).isTrue();
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
        setItem(input.itemHandler(), 0, new ItemStack(Items.IRON_INGOT));
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
                    assertThat(input.itemHandler().amount(0)).isEqualTo(2L);
                    context.setDuration(2);
                    context.setRequirements(MachineRequirement
                            .copyList(List.of(new ItemRequirement(RecipeModifier.IOType.INPUT,
                                    Ingredient.of(Items.IRON_INGOT), 2, ItemStack.EMPTY), output(Items.GOLD_NUGGET))));
                })
                .recipeTick(context -> {
                    ticks.incrementAndGet();
                    assertThat(context.totalTick()).isEqualTo(2);
                    assertThat(((cn.howxu.mmcr.api.recipe.requirement.ItemRequirement)
                            context.requirements().getFirst()).count()).isEqualTo(2);
                    ((MachineOutput.ItemOutput) context.outputs().getFirst()).stack().setCount(64);
                }).build()));
        setItem(input.itemHandler(), 0, new ItemStack(Items.IRON_INGOT, 2));
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("behavior_start_snapshot"), TEST_MACHINE_ID, 20,
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
        assertThat(item(output.itemHandler(), 0).is(Items.GOLD_NUGGET)).isTrue();
        assertThat(output.itemHandler().amount(0)).isEqualTo(1L);
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

        assertThat(item(output.itemHandler(), 0).is(Items.GOLD_NUGGET)).isTrue();
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
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("behavior_tick_stack_isolation"), TEST_MACHINE_ID,
                1, List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                output(Items.IRON_NUGGET),
                new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        new FluidStack(Fluids.WATER, 1_000))));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        runtime.tick();
        runtime.finish();

        assertThat(item(itemOutput.itemHandler(), 0).is(Items.IRON_NUGGET)).isTrue();
        assertThat(itemOutput.itemHandler().amount(0)).isEqualTo(1L);
        assertThat(fluidOutput.fluidHandler(null).getAmountAsLong()).isEqualTo(1_000);
    }

    @Test
    void recipe_tick_contexts_are_fresh_and_isolate_outputs_in_both_execution_paths() {
        for (boolean async : List.of(false, true)) {
            List<RecipeTickContext> contexts = new ArrayList<>();
            ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
            MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID, output);
            controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                    .recipeTick(context -> {
                        contexts.add(context);
                        assertThat(((MachineOutput.ItemOutput) context.outputs().getFirst()).stack().getCount())
                                .isEqualTo(1);
                        ((MachineOutput.ItemOutput) context.outputs().getFirst()).stack().setCount(64);
                    }).build()));
            CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
            MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("fresh_tick_context_" + async), TEST_MACHINE_ID,
                    2, List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(output(Items.IRON_NUGGET)));
            assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
            int machineContexts = controller.behaviorContextBuildCountForTesting();

            for (int tick = 0; tick < 2; tick++) {
                if (async) {
                    assertThat(runtime.prepareAsyncTickPlan(controller.currentRuntimeSnapshot())).isNotNull();
                    assertThat(runtime.commitAsyncTick(null)).isTrue();
                    assertThat(runtime.completeAsyncTickAfterInputs()).isTrue();
                    runtime.completeAsyncTickAfterRecipe();
                    runtime.flushAsyncScreenText();
                } else {
                    runtime.tick();
                }
            }

            assertThat(contexts).hasSize(2);
            assertThat(contexts.get(0)).isNotSameAs(contexts.get(1));
            assertThat(contexts.get(0).machineContext()).isNotSameAs(contexts.get(1).machineContext());
            assertThat(contexts).extracting(RecipeTickContext::currentTick).containsExactly(0, 1);
            assertThat(runtime.recipeTickContextBuildCountForTesting()).isEqualTo(2);
            assertThat(runtime.capabilityTickContextBuildCountForTesting()).isZero();
            assertThat(controller.behaviorContextBuildCountForTesting()).isEqualTo(machineContexts + 2);
            assertThat(runtime.finish().getStatus()).isEqualTo(CraftingStatus.Status.IDLE);
            assertThat(output.itemHandler().amount(0)).isEqualTo(1L);
        }
    }

    @Test
    void dynamic_tick_facets_are_detected_again_at_each_phase_in_both_execution_paths() {
        for (boolean async : List.of(false, true)) {
            for (int absentQueries : List.of(1, 2)) {
                List<CapabilityTickContext> phases = new ArrayList<>();
                MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID);
                DynamicTickCapability capability = new DynamicTickCapability(absentQueries, context -> {
                    phases.add(context);
                    return CapabilityTickResult.empty();
                });
                installTickCapability(controller, capability);
                CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
                assertThat(runtime.start(RecipeTestSupport.create(MMCR.id("dynamic_tick_" + async + "_" + absentQueries),
                        TEST_MACHINE_ID, 4, List.of(), List.of()), 1).isCrafting()).isTrue();
                capability.queries.set(0);
                if (async) {
                    assertThat(runtime.prepareAsyncTickPlan(controller.currentRuntimeSnapshot())).isNotNull();
                    assertThat(runtime.commitAsyncTick(null)).isTrue();
                    assertThat(runtime.completeAsyncTickAfterInputs()).isTrue();
                    runtime.completeAsyncTickAfterRecipe();
                } else {
                    runtime.tick();
                }

                if (absentQueries == 1) {
                    assertThat(phases).extracting(CapabilityTickContext::phase).containsExactly(
                            CapabilityTickPhase.BEFORE_RECIPE, CapabilityTickPhase.AFTER_INPUTS,
                            CapabilityTickPhase.AFTER_RECIPE);
                } else {
                    assertThat(phases).extracting(CapabilityTickContext::phase).containsExactly(
                            CapabilityTickPhase.AFTER_INPUTS, CapabilityTickPhase.AFTER_RECIPE);
                }
                assertThat(runtime.tickCount()).isEqualTo(1);
                assertThat(runtime.recipeTickContextBuildCountForTesting()).isEqualTo(1);
                assertThat(runtime.capabilityTickContextBuildCountForTesting()).isEqualTo(phases.size());
                RecipeTickContext tickContext = phases.getFirst().recipeState();
                assertThat(tickContext.currentTick()).isZero();
                assertThat(phases).allSatisfy(context -> assertThat(context.recipeState()).isSameAs(tickContext));
                assertThat(phases.getFirst()).isNotSameAs(phases.getLast());
            }
        }
    }

    @Test
    void lifecycle_trace_keeps_callback_and_resource_commit_order_in_both_execution_paths() {
        for (boolean async : List.of(false, true)) {
            List<String> trace = new ArrayList<>();
            ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
            ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
            EnergyInputHatchBlockEntity energy = RuntimeTestFixtures.energyInput(new BlockPos(3, 0, 0));
            MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID, input, output, energy);
            controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                    .beforeStart(context -> trace.add("start:" + controller.getLevel().getGameTime() + ":"
                            + input.itemHandler().amount(0)))
                    .recipeTick(context -> trace.add("tick:" + controller.getLevel().getGameTime() + ":"
                            + context.currentTick() + ":" + energy.energyStorage().getAmountAsLong()))
                    .beforeFinish(context -> trace.add("finish:" + controller.getLevel().getGameTime() + ":"
                            + energy.energyStorage().getAmountAsLong() + ":" + output.itemHandler().amount(0)))
                    .build()));
            setItem(input.itemHandler(), 0, new ItemStack(Items.IRON_INGOT, 2));
            energy.energyStorage().setAmount(200L);
            CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
            MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("lifecycle_resource_trace_" + async), TEST_MACHINE_ID,
                    2, List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                    List.of(input(Items.IRON_INGOT), new EnergyRequirement(7), output(Items.IRON_NUGGET)));
            LevelStub.setGameTime(controller.getLevel(), 0);
            assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
            long afterStart = energy.energyStorage().getAmountAsLong();
            assertThat(input.itemHandler().amount(0)).isEqualTo(1L);

            for (int tick = 1; tick <= 2; tick++) {
                LevelStub.setGameTime(controller.getLevel(), tick);
                if (async) {
                    assertThat(runtime.prepareAsyncTickPlan(controller.currentRuntimeSnapshot())).isNotNull();
                    assertThat(runtime.tickCount()).isEqualTo(tick - 1);
                    assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(afterStart - 7L * (tick - 1));
                    assertThat(runtime.commitAsyncTick(null)).isTrue();
                    assertThat(runtime.completeAsyncTickAfterInputs()).isTrue();
                    runtime.completeAsyncTickAfterRecipe();
                } else {
                    runtime.tick();
                }
                assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(afterStart - 7L * tick);
            }
            assertThat(runtime.finishPending()).isTrue();
            assertThat(runtime.finish().getStatus()).isEqualTo(CraftingStatus.Status.IDLE);
            assertThat(output.itemHandler().amount(0)).isEqualTo(1L);
            LevelStub.setGameTime(controller.getLevel(), 3);
            assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();

            assertThat(trace).containsExactly("start:0:2", "tick:1:0:" + afterStart,
                    "tick:2:1:" + (afterStart - 7L), "finish:2:" + (afterStart - 14L) + ":0", "start:3:1");
            assertThat(input.itemHandler().amount(0)).isZero();
        }
    }

    @Test
    void async_facet_screen_text_flush_survives_discarding_the_tick_preparation() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID);
        ResourceLocation lineId = MMCR.id("async_facet_status");
        ControllerScreenTextState text = (ControllerScreenTextState) controller.recipeScreenText("base");
        installTickCapability(controller, new TickCapability(context -> {
            if (context.phase() == CapabilityTickPhase.AFTER_RECIPE) {
                context.machineContext().screenText().replace(lineId, Component.literal("after"));
            }
            return CapabilityTickResult.empty();
        }));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        runtime.setScreenText(text);
        assertThat(runtime.start(RecipeTestSupport.create(MMCR.id("async_facet_screen_text"), TEST_MACHINE_ID,
                4, List.of(), List.of()), 1).isCrafting()).isTrue();
        text.append(ControllerScreenTextScope.OPERATION, lineId, Component.literal("before"));
        int machineContexts = controller.behaviorContextBuildCountForTesting();
        assertThat(runtime.prepareAsyncTickPlan(controller.currentRuntimeSnapshot())).isNotNull();
        assertThat(runtime.commitAsyncTick(null)).isTrue();
        assertThat(runtime.completeAsyncTickAfterInputs()).isTrue();
        runtime.completeAsyncTickAfterRecipe();
        runtime.flushAsyncScreenText();

        assertThat(text.snapshot().lines()).singleElement()
                .satisfies(line -> assertThat(line.text()).isEqualTo(Component.literal("after")));
        assertThat(controller.behaviorContextBuildCountForTesting()).isEqualTo(machineContexts + 1);
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void installed_callbacks_are_prepared_once_before_non_sync_resources_and_after_phases_follow_payment(MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        energy.energyStorage().setAmount(100L);
        var controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID, energy);
        List<String> trace = new ArrayList<>();
        List<RecipeTickContext> contexts = new ArrayList<>();
        controller.setMachine(machine(TEST_MACHINE_ID, RecipeBehavior.builder().recipeTick(context -> {
            trace.add("recipe:" + context.currentTick());
            contexts.add(context);
        }).build()));
        var members = new ArrayList<>(controller.componentRuntime().components());
        members.add(new ProcessingComponent(null, new TickCapabilityHost(new TickCapability(context -> {
            trace.add(context.phase().name() + ":" + context.recipeState().currentTick() + ":" + energy.energyStorage().getAmountAsLong());
            return CapabilityTickResult.empty();
        })), BlockPos.ZERO, BlockPos.ZERO, (String) null));
        controller.componentRuntime().replaceComponents(members);
        RuntimeTestFixtures.republish(controller);
        var runtime = new CraftingRuntime(controller, controller.componentRuntime());
        runtime.start(RecipeTestSupport.create(MMCR.id("installed_phase_boundary"), TEST_MACHINE_ID, 10,
                List.of(new EnergyRequirement(7L)), List.of()), 1);
        var installed = runtime.ensureInstalledProgram();
        long afterStart = energy.energyStorage().getAmountAsLong();
        for (int tick = 1; tick <= 2; tick++) {
            trace.clear();
            LevelStub.setGameTime(controller.getLevel(), tick);
            if (mode != MachineWorkMode.SYNC) {
                assertThat(runtime.prepareInstalledTick(tick)).isTrue();
                assertThat(runtime.prepareInstalledTick(tick)).isTrue();
                assertThat(trace).containsExactly("BEFORE_RECIPE:" + (tick - 1) + ":" + (afterStart - 7L * (tick - 1)),
                        "recipe:" + (tick - 1));
                assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(afterStart - 7L * (tick - 1));
            }
            assertThat(installed.tryAttempt(tick, tick)).isTrue();
            assertThat(installed.advance()).isTrue();
            assertThat(trace).containsExactly("BEFORE_RECIPE:" + (tick - 1) + ":" + (afterStart - 7L * (tick - 1)),
                    "recipe:" + (tick - 1), "AFTER_INPUTS:" + (tick - 1) + ":" + (afterStart - 7L * tick),
                    "AFTER_RECIPE:" + (tick - 1) + ":" + (afterStart - 7L * tick));
        }
        assertThat(contexts).hasSize(2);
        assertThat(contexts.get(0)).isNotSameAs(contexts.get(1));
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void installed_after_failure_keeps_exactly_one_paid_final_grant_and_finish_retry_gate(MachineWorkMode mode) {
        for (CapabilityTickPhase failingPhase : List.of(CapabilityTickPhase.AFTER_INPUTS, CapabilityTickPhase.AFTER_RECIPE)) {
            ConfigTestSupport.setMachineWorkMode(mode);
            var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
            energy.energyStorage().setAmount(100L);
            var controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID, energy);
            var members = new ArrayList<>(controller.componentRuntime().components());
            AtomicInteger afterAttempts = new AtomicInteger();
            members.add(new ProcessingComponent(null, new TickCapabilityHost(new TickCapability(context -> {
                if (context.phase() == failingPhase) {
                    afterAttempts.incrementAndGet();
                    return new CapabilityTickResult(List.of(), tickFailure(), false);
                }
                return CapabilityTickResult.empty();
            })), BlockPos.ZERO, BlockPos.ZERO, (String) null));
            controller.componentRuntime().replaceComponents(members);
            RuntimeTestFixtures.republish(controller);
            var runtime = new CraftingRuntime(controller, controller.componentRuntime());
            runtime.start(RecipeTestSupport.create(MMCR.id("installed_final_failure_" + failingPhase.ordinal()),
                    TEST_MACHINE_ID, 1, List.of(new EnergyRequirement(7L)), List.of()), 1);
            var installed = runtime.ensureInstalledProgram();
            long afterStart = energy.energyStorage().getAmountAsLong();
            LevelStub.setGameTime(controller.getLevel(), 1);
            if (mode != MachineWorkMode.SYNC) runtime.prepareInstalledTick(1);
            assertThat(installed.tryAttempt(1, 1)).isTrue();
            assertThat(installed.advance()).isTrue();
            assertThat(runtime.finishPending()).isTrue();
            assertThat(runtime.ledger().state().finalTickPaid()).isTrue();
            assertThat(runtime.shouldRetryFinish()).isFalse();
            assertThat(afterAttempts.get()).isEqualTo(1);
            LevelStub.setGameTime(controller.getLevel(), 2);
            runtime.tick();
            runtime.finish();
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(afterStart - 7L);
            assertThat(afterAttempts.get()).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void installed_finish_runs_dynamic_callback_on_each_legal_attempt_with_fresh_context_and_no_final_repayment(MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        var output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        energy.energyStorage().setAmount(100L);
        var controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID, energy, output);
        AtomicInteger attempts = new AtomicInteger();
        List<MachineBehaviorContext> contexts = new ArrayList<>();
        controller.setMachine(machine(TEST_MACHINE_ID, RecipeBehavior.builder().beforeFinish(context -> {
            contexts.add(context.machineContext());
            if (attempts.incrementAndGet() == 1) context.cancel();
            else context.setOutputs(List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_NUGGET), 1F)));
        }).build()));
        var runtime = new CraftingRuntime(controller, controller.componentRuntime());
        runtime.start(RecipeTestSupport.create(MMCR.id("installed_dynamic_finish"), TEST_MACHINE_ID, 1,
                List.of(new EnergyRequirement(7L)),
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.IRON_NUGGET), 1F))), 1L);
        var execution = runtime.ensureInstalledProgram();
        assertThat(execution.finishReady()).isTrue();
        assertThat(execution.finishAttemptReady()).isFalse();
        long afterStart = energy.energyStorage().getAmountAsLong();
        LevelStub.setGameTime(controller.getLevel(), 1L);
        if (mode != MachineWorkMode.SYNC) runtime.prepareInstalledTick(1L);
        assertThat(execution.tryAttempt(1L, 1L)).isTrue();
        assertThat(execution.advance()).isTrue();
        assertThat(attempts.get()).isEqualTo(1);
        assertThat(runtime.finishPending()).isTrue();
        assertThat(execution.finishAttemptReady()).isFalse();
        assertThat(runtime.ledger().state().finalTickPaid()).isTrue();
        assertThat(output.itemHandler().amount(0)).isZero();
        assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(afterStart - 7L);
        LevelStub.setGameTime(controller.getLevel(), 2L);
        assertThat(execution.tryAttempt(2L, 2L)).isTrue();
        assertThat(execution.advance()).isFalse();
        assertThat(attempts.get()).isEqualTo(1);
        LevelStub.setGameTime(controller.getLevel(), 11L);
        assertThat(execution.finishAttemptReady()).isTrue();
        assertThat(execution.tryAttempt(11L, 11L)).isTrue();
        assertThat(execution.advance()).isTrue();
        assertThat(runtime.active()).isFalse();
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(contexts.get(0)).isNotSameAs(contexts.get(1));
        assertThat(contexts.get(0).gameTime()).isEqualTo(1L);
        assertThat(contexts.get(1).gameTime()).isEqualTo(11L);
        assertThat(output.itemHandler().resource(0).is(Items.GOLD_NUGGET)).isTrue();
        assertThat(output.itemHandler().amount(0)).isEqualTo(1L);
        assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(afterStart - 7L);
        assertThat(execution.ledger().state().outputPaid()).isTrue();
    }

    @Test
    void disappearing_tick_facet_clears_its_previous_failure_before_recipe_callback() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID);
        AtomicBoolean available = new AtomicBoolean(true);
        AtomicReference<CraftingRuntime> runtimeReference = new AtomicReference<>();
        AtomicBoolean callbackSawClearedFailure = new AtomicBoolean();
        controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder().recipeTick(context ->
                callbackSawClearedFailure.set(runtimeReference.get().failure() == null)).build()));
        TickCapability delegate = new TickCapability(context -> new CapabilityTickResult(List.of(), tickFailure(), false));
        MachineCapability capability = new MachineCapability() {
            @Override
            public CapabilityType type() {
                return delegate.type();
            }

            @Override
            public CapabilityDirections directions() {
                return delegate.directions();
            }

            @Override
            public CapabilityView view() {
                return delegate.view();
            }

            @Override
            public <F extends CapabilityFacet> Optional<F> facet(Class<F> facetType) {
                return available.get() ? delegate.facet(facetType) : Optional.empty();
            }

            @Override
            public CapabilityOperation prepare(CapabilityRequest request) {
                return delegate.prepare(request);
            }
        };
        installTickCapability(controller, capability);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        runtimeReference.set(runtime);
        assertThat(runtime.start(RecipeTestSupport.create(MMCR.id("disappearing_tick_failure"), TEST_MACHINE_ID,
                4, List.of(), List.of()), 1).isCrafting()).isTrue();
        runtime.tick();
        assertThat(runtime.failure()).isNotNull();
        assertThat(callbackSawClearedFailure).isFalse();
        available.set(false);

        runtime.tick();

        assertThat(callbackSawClearedFailure).isTrue();
        assertThat(runtime.failure()).isNull();
        assertThat(runtime.tickCount()).isEqualTo(1);
    }

    @Test
    void formed_tick_machine_runs_callback_without_starting_recipe_runtime() {
        AtomicInteger calls = new AtomicInteger();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID);
        Machine tickMachine = machine(controller.machineId(), TickBehavior.builder()
                .serverTick(context -> calls.incrementAndGet()).build());
        RuntimeTestFixtures.formStructure(controller, tickMachine);

        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());

        assertThat(calls).hasValue(1);
        assertThat(controller.currentRuntimeSnapshot().crafting().recipeId()).isNull();
        assertThat(controller.runtimeSnapshot().crafting().status().isCrafting()).isFalse();
    }

    @Test
    void direct_tick_failure_is_published_and_prevents_the_behavior_callback() {
        AtomicInteger callbacks = new AtomicInteger();
        AtomicInteger phases = new AtomicInteger();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID);
        Machine tickMachine = machine(controller.machineId(), TickBehavior.builder()
                .serverTick(context -> callbacks.incrementAndGet()).build());
        RuntimeTestFixtures.formStructure(controller, tickMachine);
        installTickCapability(controller, new TickCapability(context -> {
            phases.incrementAndGet();
            return new CapabilityTickResult(List.of(), tickFailure(), true);
        }));

        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());

        assertThat(phases).hasValue(1);
        assertThat(callbacks).hasValue(0);
        assertThat(controller.runtimeSnapshot().crafting().failure()).isNotNull();
    }

    @Test
    void ordinary_idle_machine_runs_one_idle_phase_when_no_recipe_is_active() {
        AtomicInteger idleCalls = new AtomicInteger();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID);
        Machine machine = machine(controller.machineId(), RecipeBehavior.defaults());
        RuntimeTestFixtures.formStructure(controller, machine);
        installTickCapability(controller, new TickCapability(context -> {
            if (context.phase() == CapabilityTickPhase.IDLE) idleCalls.incrementAndGet();
            return CapabilityTickResult.empty();
        }));

        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());

        assertThat(idleCalls).hasValue(1);
    }

    @Test
    void absent_server_hooks_do_not_build_callback_only_contexts() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID);
        RuntimeTestFixtures.formStructure(controller, machine(controller.machineId(), RecipeBehavior.defaults()));
        int before = controller.behaviorContextBuildCountForTesting();

        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());

        assertThat(controller.behaviorContextBuildCountForTesting()).isEqualTo(before);
    }

    @Test
    void explicit_server_hooks_each_receive_a_fresh_context() {
        List<MachineBehaviorContext> contexts = new ArrayList<>();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID);
        RuntimeTestFixtures.formStructure(controller, machine(controller.machineId(), RecipeBehavior.builder()
                .preServerTick(contexts::add)
                .postServerTick(contexts::add)
                .build()));
        int before = controller.behaviorContextBuildCountForTesting();

        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());

        assertThat(controller.behaviorContextBuildCountForTesting()).isEqualTo(before + 2);
        assertThat(contexts).hasSize(2);
        assertThat(contexts.get(0)).isNotSameAs(contexts.get(1));
    }

    @Test
    void direct_tick_without_callback_runs_capability_phase_with_one_context() {
        AtomicInteger phases = new AtomicInteger();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID);
        RuntimeTestFixtures.formStructure(controller, machine(controller.machineId(), TickBehavior.defaults()));
        installTickCapability(controller, new TickCapability(context -> {
            phases.incrementAndGet();
            return CapabilityTickResult.empty();
        }));
        int before = controller.behaviorContextBuildCountForTesting();

        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());

        assertThat(phases).hasValue(1);
        assertThat(controller.behaviorContextBuildCountForTesting()).isEqualTo(before + 1);
    }

    @Test
    void factory_idle_phase_propagates_a_blocked_result_to_the_factory_failure() {
        AtomicInteger idleCalls = new AtomicInteger();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID);
        installTickCapability(controller, new TickCapability(context -> {
            if (context.phase() == CapabilityTickPhase.IDLE) idleCalls.incrementAndGet();
            return new CapabilityTickResult(List.of(), tickFailure(), true);
        }));
        FactoryRuntime factory = new FactoryRuntime();
        factory.ensureBaseLane(controller);

        var result = factory.tick(List.of(), 1L, 0L);

        assertThat(idleCalls).hasValue(1);
        assertThat(result.factoryFailure()).isNotNull();
    }

    @Test
    void tick_context_exposes_effective_factory_threads_and_parallelism() {
        ResourceLocation machineId = MMCR.id("tick_context_components");
        BlockPos factoryPos = new BlockPos(1, 0, 0);
        BlockPos parallelPos = new BlockPos(2, 0, 0);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(factoryPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        setItem(scheduler.itemHandler(), 0, new ItemStack(ModItems.THREAD_DISPERSER.get(), 2));
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
        ResourceLocation machineId = MMCR.id("tick_context_disabled_components");
        BlockPos factoryPos = new BlockPos(1, 0, 0);
        BlockPos parallelPos = new BlockPos(2, 0, 0);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(factoryPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        setItem(scheduler.itemHandler(), 0, new ItemStack(ModItems.THREAD_DISPERSER.get(), 2));
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
        setItem(bus.itemHandler(), 0, source);
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
        setItem(firstInput.itemHandler(), 0, new ItemStack(Items.IRON_INGOT));
        setItem(secondInput.itemHandler(), 0, new ItemStack(Items.IRON_INGOT));
        ItemStack initialOutput = new ItemStack(Items.GOLD_NUGGET, 63);
        initialOutput.set(DataComponents.MAX_STACK_SIZE, 64);
        setItem(firstOutput.itemHandler(), 0, initialOutput);
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
                    assertThat(context.dataStorage().get("ticks"))
                            .contains(cn.howxu.mmcr.api.data.view.DataValue.of(0L));
                    assertThat(dataStorage.storage().get("ticks")).contains(DataValue.of(0L));
                    ItemStack outputStack = new ItemStack(Items.GOLD_NUGGET, 3);
                    outputStack.set(DataComponents.MAX_STACK_SIZE, 64);
                    MachineIoPlan plan = context.ioPlan()
                            .addInput(new cn.howxu.mmcr.api.recipe.requirement.ItemRequirement(
                                    RecipeModifier.IOType.INPUT,
                                    Ingredient.of(Items.IRON_INGOT), 2, ItemStack.EMPTY, 1F,
                                    DataComponentPredicateSet.EMPTY, 1F))
                            .addOutput(new cn.howxu.mmcr.api.recipe.requirement.ItemRequirement(
                                    RecipeModifier.IOType.OUTPUT, null, 0, outputStack, 1F,
                                    DataComponentPredicateSet.EMPTY, 1F),
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
        assertThat(firstInput.itemHandler().amount(0)).isEqualTo(1L);
        assertThat(secondInput.itemHandler().amount(0)).isEqualTo(1L);
        assertThat(firstOutput.itemHandler().amount(0)).isEqualTo(63L);
        assertThat(secondOutput.itemHandler().amount(0)).isZero();
        assertThat(dataStorage.storage().get("ticks")).contains(DataValue.of(0L));

        RuntimeTestFixtures.advanceGameTime(level);
        controller.tickRuntimeWork(level, controller.getBlockPos());

        assertThat(calls).hasValue(2);
        assertThat(firstInput.itemHandler().amount(0)).isZero();
        assertThat(secondInput.itemHandler().amount(0)).isZero();
        long outputCount = itemAmount(firstOutput.itemHandler()) + itemAmount(secondOutput.itemHandler());
        assertThat(outputCount).isEqualTo(66L);
        assertThat(dataStorage.storage().get("ticks")).contains(DataValue.of(1L));
    }

    @Test
    void factory_lane_uses_the_same_recipe_tick_callback() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID);
        controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                .recipeTick(context -> calls.incrementAndGet()).build()));
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(controller);
        MachineRecipe recipe = recipe("behavior_factory_recipe");

        try (var mode = ConfigTestSupport.openMachineWorkMode(MachineWorkMode.SYNC)) {
            runtime.tick(List.of(recipe), 1, 0L);
            runtime.tick(List.of(recipe), 1, 1L);
        }

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

        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());

        assertThat(idleStart).hasValue(1);
        assertThat(idleEnd).hasValue(1);
    }

    @Test
    void recipe_machine_hooks_surround_existing_recipe_work_with_fresh_contexts() {
        ResourceLocation machineId = MMCR.id("recipe_hook_lifecycle_machine");
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
        RuntimeTestFixtures.registerRecipePool(machineId);
        setItem(input.itemHandler(), 0, new ItemStack(Items.IRON_INGOT));
        RuntimeTestFixtures.formStructureWithComponents(controller, recipeMachine, input, output);
        MachineRecipe lifecycleRecipe = recipe("behavior_recipe_hook_lifecycle", machineId,
                input(Items.IRON_INGOT), output(Items.GOLD_NUGGET));
        RecipeRegistry.registerStatic(lifecycleRecipe);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.SYNC);
        assertThat(controller.structureSnapshot().machine()).isSameAs(recipeMachine);
        assertThat(RecipeRegistry.catalogForMachine(machineId).recipes()).containsExactly(lifecycleRecipe);
        assertThat(controller.componentRuntime().capabilities())
                .as("components=%s capabilities=%s", controller.componentRuntime().components(),
                        controller.componentRuntime().capabilities())
                .hasSize(2);
        assertThat(controller.behaviorContext().ioView().itemAmount(Ingredient.of(Items.IRON_INGOT))).isEqualTo(1L);

        controller.tickRuntimeWork((ServerLevel) controller.getLevel(),
                controller.getBlockPos());
        assertThat(controller.runtimeSnapshot().crafting().recipeId())
                .as("registered recipe starts: status=%s failure=%s", controller.runtimeSnapshot().crafting().status(),
                        controller.runtimeSnapshot().crafting().failure())
                .isEqualTo(lifecycleRecipe.id());
        RuntimeTestFixtures.advanceGameTime(controller.getLevel());
        controller.tickRuntimeWork((ServerLevel) controller.getLevel(),
                controller.getBlockPos());
        assertThat(phases).containsExactly("pre", "beforeStart", "post", "pre", "recipeTick", "beforeFinish",
                "beforeStart", "post");
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
        assertThat(item(output.itemHandler(), 0).is(Items.GOLD_NUGGET)).isTrue();
    }

    @Test
    void async_recipe_tick_callback_runs_on_the_controller_tick_thread() {
        ResourceLocation machineId = TEST_MACHINE_ID;
        AtomicReference<Thread> callbackThread = new AtomicReference<>();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(machineId, BlockPos.ZERO);
        RuntimeTestFixtures.registerRecipePool(machineId);
        RuntimeTestFixtures.formStructure(controller, machine(machineId, RecipeBehavior.builder()
                .recipeTick(context -> callbackThread.set(Thread.currentThread()))
                .build()));
        ServerLevel level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        RecipeRegistry.registerStatic(RecipeTestSupport.create(MMCR.id("recipe_callback_thread"), machineId, 20,
                List.of(), List.of()));
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);

        try {
            controller.serverTick();
            completeAsyncLevelTick(level);
            RuntimeTestFixtures.advanceGameTime(level);
            Thread controllerTickThread = Thread.currentThread();

            controller.serverTick();

            assertThat(callbackThread).hasValue(controllerTickThread);
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
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
                (ServerLevel) controller.getLevel(), controller.getBlockPos()))
                .doesNotThrowAnyException();
        assertThat(postCalls).hasValue(1);
    }

    @Test
    void callback_exception_cancels_start_without_consuming_inputs() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(TEST_MACHINE_ID, input);
        controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                .beforeStart(context -> { throw new IllegalStateException("test callback failure"); }).build()));
        setItem(input.itemHandler(), 0, new ItemStack(Items.IRON_INGOT));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThatCode(() -> runtime.start(recipe("behavior_exception_recipe", input(Items.IRON_INGOT)), 1))
                .doesNotThrowAnyException();
        assertThat(runtime.active()).isFalse();
        assertThat(input.itemHandler().amount(0)).isEqualTo(1L);
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
                .isEqualTo(CraftingStatus.Status.NO_RECIPE);
        assertThat(runtime.active()).isTrue();
        assertThat(output.itemHandler().amount(0)).isZero();
    }

    private static Machine machine(ResourceLocation id, MachineBehavior behavior) {
        return machine(id, new BlockArray(Map.of()), behavior);
    }

    private static Machine machine(ResourceLocation id, BlockArray pattern,
                                   MachineBehavior behavior) {
        return new DynamicMachine(id, id.toString(), pattern,
                MachineControllerSpec.defaultsFor(id), MachineAppearanceSpec.defaults(), PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), Map.of(), 1, false, false, 1, List.of(),
                MachineRole.NORMAL, Set.of(), List.of(), RecipeFailureActions.getDefaultAction(), behavior);
    }

    private static MachineRecipe recipe(String path, ItemRequirement... requirements) {
        return recipe(path, TEST_MACHINE_ID, requirements);
    }

    private static MachineRecipe recipe(String path, ResourceLocation machineId, ItemRequirement... requirements) {
        return RecipeTestSupport.create(MMCR.id(path), machineId, 1, List.of(), List.of(),
                List.of(), 0, 1, false, List.of(), List.of(requirements));
    }

    private static ItemRequirement input(Item item) {
        return new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(item), 1,
                ItemStack.EMPTY, 1F, List.of());
    }

    private static void setItem(LongItemStorage storage, int slot, ItemStack stack) {
        storage.setContents(slot, stack, stack.getCount());
    }

    private static ItemStack item(LongItemStorage storage, int slot) {
        return storage.getStackInSlot(slot);
    }

    private static long itemAmount(LongItemStorage storage) {
        long amount = 0L;
        for (int slot = 0; slot < storage.size(); slot++) amount += storage.amount(slot);
        return amount;
    }

    private static ItemRequirement output(Item item) {
        return new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                new ItemStack(item), 1F, List.of());
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

    private static void installTickCapability(MachineControllerBlockEntity controller, MachineCapability capability) {
        TickCapabilityHost host = new TickCapabilityHost(capability);
        controller.componentRuntime().replaceComponents(List.of(new ProcessingComponent(null, host, BlockPos.ZERO,
                BlockPos.ZERO, (String) null)));
        RuntimeTestFixtures.republish(controller);
    }

    private static ExecutionStatus tickFailure() {
        return ExecutionStatus.blocked(MMCR.id("tick_failure"), MMCR.id("test"),
                FailureOccurrence.at(BuiltinFailureReasons.PER_TICK, MMCR.id("test"), FailurePhase.PER_TICK,
                        null, null, Map.of()));
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class DynamicTickCapability implements MachineCapability, TickFacet {
        private final AtomicInteger queries = new AtomicInteger();
        private final int absentQueries;
        private final TickCapability delegate;

        private DynamicTickCapability(int absentQueries, Function<CapabilityTickContext, CapabilityTickResult> planner) {
            this.absentQueries = absentQueries;
            delegate = new TickCapability(planner);
        }

        @Override
        public <F extends CapabilityFacet> Optional<F> facet(Class<F> facetType) {
            return facetType == TickFacet.class && queries.incrementAndGet() > absentQueries
                    ? Optional.of(facetType.cast(this)) : Optional.empty();
        }

        @Override
        public CapabilityType type() {
            return delegate.type();
        }

        @Override
        public CapabilityDirections directions() {
            return delegate.directions();
        }

        @Override
        public CapabilityView view() {
            return delegate.view();
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            return delegate.prepare(request);
        }

        @Override
        public CapabilityTickResult plan(CapabilityTickContext context) {
            return delegate.plan(context);
        }
    }

    private record TickCapability(Function<CapabilityTickContext, CapabilityTickResult> planner)
            implements MachineCapability, TickFacet {
        @Override
        public CapabilityType type() {
            return new CapabilityType(MMCR.id("test_tick"));
        }

        @Override
        public CapabilityDirections directions() {
            return CapabilityDirections.input();
        }

        @Override
        public CapabilityView view() {
            return new CapabilityView() {
                @Override
                public CapabilityType type() {
                    return TickCapability.this.type();
                }

                @Override
                public CapabilityDirections directions() {
                    return TickCapability.this.directions();
                }

                @Override
                public Set<Class<? extends CapabilityFacet>> facets() {
                    return Set.of(TickFacet.class);
                }
            };
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            return CapabilityResult::successful;
        }

        @Override
        public CapabilityTickResult plan(CapabilityTickContext context) {
            return planner.apply(context);
        }
    }

    private static final class TickCapabilityHost extends BlockEntity implements CapabilityHost {
        private final CapabilitySnapshot snapshot;

        private TickCapabilityHost(MachineCapability capability) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), BlockPos.ZERO,
                    ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
            snapshot = new CapabilitySnapshot(List.of(capability));
        }

        @Override
        public CapabilitySnapshot capabilitySnapshot() {
            return snapshot;
        }
    }
}
