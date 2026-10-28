package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.CompiledMachinePattern;
import cn.howxu.mmcr.api.machine.MachinePatternCompiler;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.compat.mekanism.MekanismBridgeBootstrap;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.compat.mekanism.loaded.HeatPortCapability;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.event.SharedIoEvents;
import cn.howxu.mmcr.internal.multiblock.ComponentClaimPolicy;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.RecipeCommitLedger;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.EnergyOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.test.ConfigTestSupport;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.util.IOType;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.fml.config.IConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Verifies machine-pool boundaries for ordinary recipe threads.
 *
 * @author howxu <dev@howxu.cn>
 */
class RecipeThreadTest {
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
        FaultRequirement.copyFault.set(() -> { });
        FaultRequirement.planFault.set(() -> { });
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        RecipeRegistry.clearForTesting();
        if (serverInstalled) {
            Field field = ServerLifecycleHooks.class.getDeclaredField("currentServer");
            field.setAccessible(true);
            field.set(null, previousServer);
        }
    }

    @Test
    void ordinary_search_ignores_a_recipe_from_a_different_pool() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe foreign = RecipeTestSupport.create(MMCR.id("ordinary_foreign_pool_recipe"),
                MMCR.id("ordinary_foreign_pool"), 20, List.of(), List.of());
        MachineRecipeThread thread = new MachineRecipeThread(controller);

        assertThat(thread.searchAndStartRecipe(List.of(foreign), 1,
                controller.runtimeSnapshot().structure().version())).isEqualTo(RecipeThread.StartResult.NO_MATCH);
        assertThat(thread.runtime().active()).isFalse();
        assertThat(thread.runtime().failure()).isNotNull();
        assertThat(thread.runtime().failure().reason()).isEqualTo(BuiltinFailureReasons.RECIPE_SEARCH);
    }

    @Test
    void main_thread_only_tick_plan_does_not_dispatch_worker_planning() {
        var plan = new AsyncRequirementPlanner.PreparedPlan(List.of(), List.of(), List.of(0));

        assertThat(RecipeThread.requiresWorkerPlanning(plan)).isFalse();
    }

    @Test
    void worker_safe_tick_requirement_dispatches_worker_planning() {
        var requirement = new AsyncRequirementPlanner.Requirement(0, 1,
                List.of(new AsyncCapabilityRequest.Scalar(MMCR.id("test"), 1, 1, false)));
        var plan = new AsyncRequirementPlanner.PreparedPlan(List.of(requirement), List.of(), List.of());

        assertThat(RecipeThread.requiresWorkerPlanning(plan)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = MachineWorkMode.class, names = {"ASYNC", "SEMI_SYNC"})
    void energy_only_ticks_progress_without_dispatching_workers(MachineWorkMode mode) throws Exception {
        ConfigTestSupport.setMachineWorkMode(mode);
        EnergyInputHatchBlockEntity energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        EnergyOutputHatchBlockEntity output = RuntimeTestFixtures.energyOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = controllerWithPorts(energy, output);
        ServerLevel level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(command -> {
            throw new AssertionError("energy-only ticks must not dispatch a worker");
        });
        installCoordinator(level, coordinator);
        try {
            energy.nativeEnergyStorage().setAmount(1_000L);
            FactoryRecipeThread thread = attachedBaseLane(controller);
            MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("energy_tick_timing"), MMCR.id("test_cube"),
                    20, List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 20L),
                            new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 10L)), List.of());
            assertThat(thread.runtime().start(recipe, 1).isCrafting()).isTrue();
            long energyAfterStart = energy.nativeEnergyStorage().getAmountAsLong();

            for (int tick = 1; tick <= 4; tick++) {
                thread.tick();
                assertThat(thread.tickPendingForTesting()).isTrue();
                SharedIoEvents.completeLevelTick(level);

                assertThat(thread.tickPendingForTesting()).isFalse();
                assertThat(thread.runtime().tickCount()).isEqualTo(tick);
                assertThat(energy.nativeEnergyStorage().getAmountAsLong()).isEqualTo(energyAfterStart - tick * 20L);
                assertThat(output.nativeEnergyStorage().getAmountAsLong()).isEqualTo(tick * 10L);
                RuntimeTestFixtures.advanceGameTime(level);
            }
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void attached_installed_lane_uses_the_mode_boundary_and_runs_while_receipt_workers_are_stopped(MachineWorkMode mode)
            throws Exception {
        TestBootstrap.bootstrapCapabilities();
        ResourceLocation fixtureId = MMCR.id("thread_installed_attached_" + mode.name().toLowerCase(Locale.ROOT));
        RuntimeTestFixtures.registerRecipePool(fixtureId);
        ConfigTestSupport.setMachineWorkMode(mode);
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        var controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), energy);
        Machine original = controller.structureSnapshot().configuredMachine();
        var portComponents = controller.componentRuntime().components();
        controller.setFormed(false);
        RuntimeTestFixtures.formStructureWithComponents(controller, new Machine() {
            public ResourceLocation registryName() { return fixtureId; }
            public BlockArray pattern() {
                return new BlockArray(Map.of(BlockPos.ZERO,
                        new BlockPredicate.OfBlock(controller.getBlockState().getBlock())));
            }
            public MachineControllerSpec controller() { return original.controller(); }
            public boolean hasFactory() { return true; }
        }, energy);
        controller.componentRuntime().replaceComponents(portComponents);
        attachFactoryScheduler(controller);
        ServerLevel level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        var workers = new ArrayDeque<Runnable>();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(workers::add));
        try {
            energy.energyStorage().setAmount(1000L);
            FactoryRecipeThread thread = attachedBaseLane(controller);
            assertThat(controller.hasFactoryController()).isTrue();
            assertThat(thread.runtime()).isNotSameAs(RuntimeTestFixtures.craftingRuntime(controller));
            var recipe = RecipeTestSupport.create(MMCR.id("installed_attached_mode"), fixtureId,
                    200, List.of(new EnergyRequirement(7L)), List.of());
            RecipeRegistry.registerStatic(recipe);
            assertThat(thread.runtime().start(recipe, 1L).isCrafting()).isTrue();
            var installed = thread.runtime().ensureInstalledProgram();
            long afterStart = energy.energyStorage().getAmountAsLong();
            for (int tick = 1; tick <= 12; tick++) {
                RuntimeTestFixtures.advanceGameTime(level);
                RuntimeTestFixtures.beginLevelTick(level, true);
                controller.serverTick();
                thread.tick(); // Reentrant lane dispatch must not prepare or commit twice.
                assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(afterStart
                        - 7L * (mode == MachineWorkMode.SYNC ? tick : tick - 1));
                assertThat(thread.tickPendingForTesting()).isFalse();
                SharedIoCoordinator.get(level).executeInstalled(level);
                SharedIoCoordinator.get(level).executeInstalled(level);
                assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(afterStart - 7L * tick);
                assertThat(thread.runtime().tickCount()).isEqualTo(tick);
                assertThat(thread.runtime().installedProgram()).isSameAs(installed);
            }
            var async = MachineAsyncCoordinator.get(level);
            async.pumpReceiptDrains();
            assertReceiptWorkersOnly(async, workers);
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    @ParameterizedTest
    @MethodSource("shortRecipeModes")
    void short_installed_recipes_finish_and_restart_at_new_parallelism_before_receipt_workers_run(MachineWorkMode mode,
                                                                                                  boolean factory)
            throws Exception {
        TestBootstrap.bootstrapCapabilities();
        ResourceLocation fixtureId = MMCR.id("thread_installed_short_" + mode.name().toLowerCase(Locale.ROOT)
                + (factory ? "_factory" : "_normal"));
        RuntimeTestFixtures.registerRecipePool(fixtureId);
        ConfigTestSupport.setMachineWorkMode(mode);
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        var controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), energy, output);
        Machine original = controller.structureSnapshot().configuredMachine();
        var portComponents = controller.componentRuntime().components();
        controller.setFormed(false);
        RuntimeTestFixtures.formStructureWithComponents(controller, new Machine() {
            public ResourceLocation registryName() { return fixtureId; }
            public BlockArray pattern() {
                return new BlockArray(Map.of(BlockPos.ZERO,
                        new BlockPredicate.OfBlock(controller.getBlockState().getBlock())));
            }
            public MachineControllerSpec controller() { return original.controller(); }
            public boolean hasFactory() { return factory; }
            public boolean parallelizable() { return true; }
            public long maxParallelism() { return 16L; }
        }, energy, output);
        controller.componentRuntime().replaceComponents(portComponents);
        RuntimeTestFixtures.republish(controller);
        if (factory) attachFactoryScheduler(controller);
        var parallel = new ParallelControllerBlockEntity(ParallelTier.PLUS, new BlockPos(3, 0, 0),
                ModBlocks.BLOCKS.get(ParallelTier.PLUS.idSuffix()).get().defaultBlockState());
        RuntimeTestFixtures.replaceBlockEntity(controller, parallel);
        var members = new ArrayList<>(controller.componentRuntime().components());
        members.add(new ProcessingComponent(null, parallel, parallel.getBlockPos(), parallel.getBlockPos(), (String) null));
        controller.componentRuntime().replaceComponents(members);
        RuntimeTestFixtures.republish(controller);
        ServerLevel level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        var workers = new ArrayDeque<Runnable>();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(workers::add));
        try {
            RecipeThread thread;
            if (factory) {
                thread = attachedBaseLane(controller);
                assertThat(controller.hasFactoryController()).isTrue();
                assertThat(thread.runtime()).isNotSameAs(RuntimeTestFixtures.craftingRuntime(controller));
            } else {
                Field threadField = MachineControllerBlockEntity.class.getDeclaredField("normalRecipeThread");
                threadField.setAccessible(true);
                thread = (MachineRecipeThread) threadField.get(controller);
            }
            var recipe = RecipeTestSupport.create(MMCR.id("installed_short_worker_stopped"), fixtureId, 2,
                    List.of(new EnergyRequirement(7L), new ItemRequirement(RecipeModifier.IOType.OUTPUT,
                            Ingredient.of(Items.GOLD_NUGGET), 1, new ItemStack(Items.GOLD_NUGGET))),
                    List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_NUGGET), 1F)),
                    List.of(), 0, 1, false, true, List.of(), false, Set.of());
            RecipeRegistry.registerStatic(recipe);
            energy.energyStorage().setAmount(21L);
            assertThat(RecipeRegistry.getRecipe(recipe.id())).isSameAs(recipe);
            assertThat(thread.runtime().start(recipe, 16L).isCrafting())
                    .as("start failure: %s", thread.runtime().failure()).isTrue();
            thread.onStarted();
            thread.runtime().ensureInstalledProgram();
            assertThat(thread.runtime().parallelism()).isEqualTo(3L);
            for (int tick = 1; tick <= 4; tick++) {
                energy.energyStorage().setAmount(tick == 2 ? 91L : tick == 4 ? 70L : 1000L);
                RuntimeTestFixtures.advanceGameTime(level);
                RuntimeTestFixtures.beginLevelTick(level, true);
                controller.serverTick();
                SharedIoCoordinator.get(level).executeInstalled(level);
                SharedIoCoordinator.get(level).executeInstalled(level);
                assertThat(thread.tickPendingForTesting()).isFalse();
                if (tick == 1) {
                    assertThat(thread.runtime().tickCount()).isEqualTo(1);
                    assertThat(output.itemHandler().amount(0)).isZero();
                    assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(1000L - 3L * 7L);
                } else if (tick == 2) {
                    assertThat(output.itemHandler().amount(0)).isEqualTo(3L);
                    assertThat(thread.runtime().active()).isTrue();
                    assertThat(thread.runtime().parallelism()).isEqualTo(10L);
                    assertThat(thread.runtime().tickCount()).isZero();
                    assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(70L);
                } else if (tick == 3) {
                    assertThat(thread.runtime().tickCount()).isEqualTo(1);
                    assertThat(output.itemHandler().amount(0)).isEqualTo(3L);
                    assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(1000L - 10L * 7L);
                } else {
                    assertThat(output.itemHandler().amount(0)).isEqualTo(13L);
                    assertThat(output.itemHandler().resource(0).is(Items.GOLD_NUGGET)).isTrue();
                    assertThat(thread.runtime().active()).isFalse();
                    assertThat(energy.energyStorage().getAmountAsLong()).isZero();
                }
            }
            // Two unacknowledged executions may finish; the bounded receipt protocol then backpressures a third start.
            energy.energyStorage().setAmount(1000L);
            assertThat(SharedIoCoordinator.get(level).reserveExecution(thread.runtime(), thread.asyncLaneId())).isFalse();
            MachineAsyncCoordinator.get(level).pumpReceiptDrains();
            assertReceiptWorkersOnly(MachineAsyncCoordinator.get(level), workers);
            assertThat(workers).isNotEmpty();
            while (!workers.isEmpty()) workers.remove().run();
            SharedIoCoordinator.get(level).drainReceiptAcknowledgements(level);
            assertThat(SharedIoCoordinator.get(level).reserveExecution(thread.runtime(), thread.asyncLaneId())).isTrue();
            SharedIoCoordinator.get(level).releaseExecutionReservation(thread.runtime(), thread.asyncLaneId());
            long beforeSearch = factory ? controllerFactorySearchAttempts(controller) : normalSearchAttempts(controller);
            RuntimeTestFixtures.advanceGameTime(level);
            RuntimeTestFixtures.beginLevelTick(level, true);
            controller.serverTick();
            SharedIoCoordinator.get(level).executeInstalled(level);
            assertThat(thread.runtime().active()).isTrue();
            assertThat(thread.isStartPending()).isFalse();
            assertThat(thread.runtime().parallelism()).isEqualTo(16L);
            assertThat(thread.runtime().tickCount()).isZero();
            assertThat(thread.runtime().installedProgram()).isNotNull();
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(1000L);
            assertThat(output.itemHandler().amount(0)).isEqualTo(13L);
            assertThat(factory ? controllerFactorySearchAttempts(controller) : normalSearchAttempts(controller))
                    .isEqualTo(beforeSearch);
            assertReceiptWorkersOnly(MachineAsyncCoordinator.get(level), workers);
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    private static Stream<Arguments> shortRecipeModes() {
        return Stream.of(MachineWorkMode.values()).flatMap(mode -> Stream.of(Arguments.of(mode, false), Arguments.of(mode, true)));
    }

    private static long normalSearchAttempts(MachineControllerBlockEntity controller) throws ReflectiveOperationException {
        Field field = MachineControllerBlockEntity.class.getDeclaredField("recipeSearchAttemptCounter");
        field.setAccessible(true);
        return field.getLong(controller);
    }

    private static long controllerFactorySearchAttempts(MachineControllerBlockEntity controller)
            throws ReflectiveOperationException {
        Field field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
        field.setAccessible(true);
        return ((MachineControllerRuntime) field.get(controller)).factoryRuntime().searchAttemptsForTesting();
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void attached_normal_cached_start_failure_searches_once_and_starts_the_available_alternative(MachineWorkMode mode)
            throws Exception {
        TestBootstrap.bootstrapCapabilities();
        ConfigTestSupport.setMachineWorkMode(mode);
        var input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        var output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        var controller = controllerWithPorts(input, output);
        var firstStarts = new AtomicInteger();
        var alternativeStarts = new AtomicInteger();
        var finishes = new AtomicInteger();
        ResourceLocation firstId = MMCR.id("cached_start_failure_first");
        installBehavior(controller, RecipeBehavior.builder().beforeStart(context -> {
            if (context.recipeId().equals(firstId)) firstStarts.incrementAndGet();
            else alternativeStarts.incrementAndGet();
        }).beforeFinish(context -> finishes.incrementAndGet()).build());
        MachineRecipe first = RecipeTestSupport.create(firstId, controller.currentRecipePoolId(), 1,
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY),
                        new ItemRequirement(RecipeModifier.IOType.OUTPUT, Ingredient.of(Items.GOLD_NUGGET), 1,
                                new ItemStack(Items.GOLD_NUGGET))),
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_NUGGET), 1F)));
        MachineRecipe alternative = RecipeTestSupport.create(MMCR.id("cached_start_failure_alternative"),
                controller.currentRecipePoolId(), 20,
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.COPPER_INGOT), 1, ItemStack.EMPTY)), List.of());
        RecipeRegistry.registerStatic(first);
        RecipeRegistry.registerStatic(alternative);
        input.itemHandler().setContents(0, new ItemStack(Items.IRON_INGOT), 1L);
        input.itemHandler().setContents(1, new ItemStack(Items.COPPER_INGOT), 1L);
        ServerLevel level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(Runnable::run));
        try {
            MachineRecipeThread thread = attachedNormalThread(controller);
            assertThat(thread.runtime().start(first, 1L).isCrafting()).isTrue();
            thread.onStarted();
            thread.runtime().ensureInstalledProgram();
            long beforeSearch = normalSearchAttempts(controller);

            RuntimeTestFixtures.advanceGameTime(level);
            RuntimeTestFixtures.beginLevelTick(level, true);
            controller.serverTick();
            SharedIoCoordinator.get(level).executeInstalled(level);
            SharedIoCoordinator.get(level).resolve(level);
            assertThat(output.itemHandler().amount(0)).isEqualTo(1L);
            assertThat(finishes).hasValue(1);
            assertThat(firstStarts).hasValue(2);
            assertThat(alternativeStarts).hasValue(0);
            assertThat(thread.runtime().active()).isFalse();
            assertThat(thread.isStartPending()).isFalse();
            assertThat(thread.hasFullSearchPending()).isTrue();
            assertThat(input.itemHandler().amount(0)).isZero();
            assertThat(input.itemHandler().amount(1)).isEqualTo(1L);
            assertThat(normalSearchAttempts(controller)).isEqualTo(beforeSearch);

            RuntimeTestFixtures.advanceGameTime(level);
            RuntimeTestFixtures.beginLevelTick(level, true);
            controller.serverTick();
            completeAsyncLevelTick(level);
            assertThat(thread.runtime().active()).isTrue();
            assertThat(thread.runtime().recipe()).isSameAs(alternative);
            assertThat(thread.isStartPending()).isFalse();
            assertThat(thread.hasFullSearchPending()).isFalse();
            assertThat(normalSearchAttempts(controller)).isEqualTo(beforeSearch + 1L);
            assertThat(firstStarts).hasValue(2);
            assertThat(alternativeStarts).hasValue(1);
            assertThat(finishes).hasValue(1);
            assertThat(input.itemHandler().amount(1)).isZero();
            assertThat(output.itemHandler().amount(0)).isEqualTo(1L);
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void before_start_cancellation_is_not_missing_input_or_a_full_search_obligation(MachineWorkMode mode)
            throws Exception {
        ConfigTestSupport.setMachineWorkMode(mode);
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        var controller = controllerWithPorts(energy);
        var callbacks = new AtomicInteger();
        installBehavior(controller, RecipeBehavior.builder().beforeStart(context -> {
            callbacks.incrementAndGet();
            context.cancel();
        }).build());
        var recipe = RecipeTestSupport.create(MMCR.id("cancelled_last_start"), controller.currentRecipePoolId(),
                20, List.of(new EnergyRequirement(7L)), List.of());
        var level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(Runnable::run));
        try {
            energy.energyStorage().setAmount(100L);
            var thread = new RecordingRecipeThread(controller);
            RecipeThread.StartResult result = thread.startRecipe(recipe, 1L,
                    controller.currentRuntimeSnapshot().structure().version(), null, RecipeThread.StartOrigin.LAST_RECIPE);
            assertThat(result).isEqualTo(mode == MachineWorkMode.ASYNC
                    ? RecipeThread.StartResult.PENDING : RecipeThread.StartResult.CANCELLED);
            completeAsyncLevelTick(level);
            assertThat(thread.lastResults).filteredOn(value -> value != RecipeThread.StartResult.PENDING)
                    .containsExactly(RecipeThread.StartResult.CANCELLED);
            assertThat(callbacks).hasValue(1);
            assertThat(thread.isStartPending()).isFalse();
            assertThat(thread.runtime().active()).isFalse();
            assertThat(thread.runtime().failure()).isNull();
            assertThat(thread.hasFullSearchPending()).isFalse();
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(100L);
            assertThat(normalSearchAttempts(controller)).isZero();
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"structure", "remove", "replace"})
    void stale_shared_start_cannot_consume_a_new_generation_search_obligation(String action) throws Exception {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        var controller = controllerWithPorts(energy);
        var level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(Runnable::run));
        try {
            energy.energyStorage().setAmount(100L);
            var thread = new RecordingRecipeThread(controller);
            var recipe = RecipeTestSupport.create(MMCR.id("stale_shared_last_start"), controller.currentRecipePoolId(),
                    20, List.of(new EnergyRequirement(7L)), List.of());
            assertThat(thread.enqueueAsyncFinishRestart(recipe, 1L,
                    controller.currentRuntimeSnapshot().structure().version(), null, RecipeThread.StartOrigin.LAST_RECIPE))
                    .isEqualTo(RecipeThread.StartResult.PENDING);
            assertThat(thread.isStartPending()).isTrue();
            MachineRecipeThread replacementThread = null;
            if (action.equals("structure")) controller.invalidateFormedStructure();
            else if (action.equals("remove")) controller.setRemoved();
            else {
                var replacement = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controller.getBlockPos());
                RuntimeTestFixtures.replaceBlockEntity(controller, replacement);
                replacementThread = attachedNormalThread(replacement);
                replacementThread.requestFullSearch();
            }
            thread.requestFullSearch();
            completeAsyncLevelTick(level);
            completeAsyncLevelTick(level);

            assertThat(thread.lastResults).containsExactly(RecipeThread.StartResult.PENDING);
            assertThat(thread.hasFullSearchPending()).isTrue();
            assertThat(thread.isStartPending()).isFalse();
            assertThat(thread.runtime().active()).isFalse();
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(100L);
            assertThat(normalSearchAttempts(controller)).isZero();
            if (replacementThread != null) {
                assertThat(replacementThread.hasFullSearchPending()).isTrue();
                assertThat(replacementThread.runtime().active()).isFalse();
            }
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    @Test
    void failed_cached_shared_start_dispatches_only_once_and_requests_full_search() throws Exception {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        var controller = controllerWithPorts(energy);
        var level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(Runnable::run));
        try {
            var thread = new RecordingRecipeThread(controller);
            var recipe = RecipeTestSupport.create(MMCR.id("failed_shared_last_start"), controller.currentRecipePoolId(),
                    20, List.of(new EnergyRequirement(7L)), List.of());
            assertThat(thread.enqueueAsyncFinishRestart(recipe, 1L,
                    controller.currentRuntimeSnapshot().structure().version(), null, RecipeThread.StartOrigin.LAST_RECIPE))
                    .isEqualTo(RecipeThread.StartResult.PENDING);
            completeAsyncLevelTick(level);
            completeAsyncLevelTick(level);
            assertThat(thread.lastResults).containsExactly(RecipeThread.StartResult.PENDING, RecipeThread.StartResult.FAILED);
            assertThat(thread.hasFullSearchPending()).isTrue();
            assertThat(thread.isStartPending()).isFalse();
            assertThat(thread.runtime().active()).isFalse();
            assertThat(energy.energyStorage().getAmountAsLong()).isZero();
            assertThat(normalSearchAttempts(controller)).isZero();
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    @Test
    void discarded_shared_start_cannot_fail_or_cancel_a_replacement_token() throws Exception {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        var controller = controllerWithPorts(energy);
        var level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(Runnable::run));
        try {
            energy.energyStorage().setAmount(100L);
            var thread = new RecordingRecipeThread(controller);
            var oldRecipe = RecipeTestSupport.create(MMCR.id("old_shared_start_token"), controller.currentRecipePoolId(),
                    20, List.of(new EnergyRequirement(7L)), List.of());
            var replacement = RecipeTestSupport.create(MMCR.id("new_shared_start_token"), controller.currentRecipePoolId(),
                    20, List.of(new EnergyRequirement(7L)), List.of());
            long version = controller.currentRuntimeSnapshot().structure().version();
            assertThat(thread.enqueueAsyncFinishRestart(oldRecipe, 1L, version, null, RecipeThread.StartOrigin.LAST_RECIPE))
                    .isEqualTo(RecipeThread.StartResult.PENDING);
            thread.cancelAsyncState();
            assertThat(thread.enqueueAsyncFinishRestart(replacement, 1L, version, null, RecipeThread.StartOrigin.LAST_RECIPE))
                    .isEqualTo(RecipeThread.StartResult.PENDING);
            completeAsyncLevelTick(level);
            completeAsyncLevelTick(level);
            assertThat(thread.lastResults).containsExactly(RecipeThread.StartResult.PENDING,
                    RecipeThread.StartResult.PENDING, RecipeThread.StartResult.STARTED);
            assertThat(thread.isStartPending()).isFalse();
            assertThat(thread.runtime().active()).isTrue();
            assertThat(thread.runtime().recipe()).isSameAs(replacement);
            assertThat(thread.hasFullSearchPending()).isFalse();
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(100L);
            assertThat(normalSearchAttempts(controller)).isZero();
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    @Test
    void catalog_removal_ends_a_cached_pending_start_with_one_full_search_obligation() throws Exception {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        var controller = controllerWithPorts(energy);
        var level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(Runnable::run));
        try {
            energy.energyStorage().setAmount(100L);
            var thread = new RecordingRecipeThread(controller);
            var recipe = RecipeTestSupport.create(MMCR.id("removed_cached_pending_start"), controller.currentRecipePoolId(),
                    20, List.of(new EnergyRequirement(7L)), List.of());
            RecipeRegistry.replaceDynamic(Map.of(recipe.id(), recipe));
            assertThat(thread.enqueueAsyncFinishRestart(recipe, 1L,
                    controller.currentRuntimeSnapshot().structure().version(), null, RecipeThread.StartOrigin.LAST_RECIPE))
                    .isEqualTo(RecipeThread.StartResult.PENDING);
            RecipeRegistry.replaceDynamic(Map.of());
            completeAsyncLevelTick(level);
            completeAsyncLevelTick(level);
            assertThat(thread.lastResults).containsExactly(RecipeThread.StartResult.PENDING, RecipeThread.StartResult.NO_MATCH);
            assertThat(thread.hasFullSearchPending()).isTrue();
            assertThat(thread.isStartPending()).isFalse();
            assertThat(thread.runtime().active()).isFalse();
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(100L);
            assertThat(normalSearchAttempts(controller)).isZero();
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    @Test
    void changed_domain_defers_the_old_request_and_accepts_the_candidate_in_the_new_domain() throws Exception {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        var controller = controllerWithPorts(energy);
        var level = (ServerLevel) controller.getLevel();
        var claims = StructureClaimRegistry.get(level);
        assertThat(claims.claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(Runnable::run));
        try {
            energy.energyStorage().setAmount(100L);
            var thread = new RecordingRecipeThread(controller);
            var recipe = RecipeTestSupport.create(MMCR.id("domain_shared_last_start"), controller.currentRecipePoolId(),
                    20, List.of(new EnergyRequirement(7L)), List.of());
            long version = controller.currentRuntimeSnapshot().structure().version();
            assertThat(thread.enqueueAsyncFinishRestart(recipe, 1L, version, null, RecipeThread.StartOrigin.LAST_RECIPE))
                    .isEqualTo(RecipeThread.StartResult.PENDING);
            var oldDomain = controller.resourceDomain();
            claims.release(controller.getBlockPos());
            assertThat(claims.claim(controller.getBlockPos(), List.of(
                    new StructureClaimRegistry.Claim(energy.getBlockPos(), ComponentClaimPolicy.SHARED_SERIALIZED)))
                    .accepted()).isTrue();
            assertThat(controller.resourceDomain()).isNotEqualTo(oldDomain);
            completeAsyncLevelTick(level);
            assertThat(thread.lastResults).containsExactly(RecipeThread.StartResult.PENDING, RecipeThread.StartResult.DEFERRED);
            assertThat(thread.isStartPending()).isFalse();
            assertThat(thread.runtime().active()).isFalse();
            assertThat(thread.hasFullSearchPending()).isFalse();
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(100L);
            assertThat(thread.enqueueAsyncFinishRestart(recipe, 1L, version, null, RecipeThread.StartOrigin.LAST_RECIPE))
                    .isEqualTo(RecipeThread.StartResult.PENDING);
            completeAsyncLevelTick(level);
            assertThat(thread.lastResults).containsExactly(RecipeThread.StartResult.PENDING, RecipeThread.StartResult.DEFERRED,
                    RecipeThread.StartResult.PENDING, RecipeThread.StartResult.STARTED);
            assertThat(thread.runtime().recipe()).isSameAs(recipe);
            assertThat(thread.isStartPending()).isFalse();
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(100L);
            assertThat(normalSearchAttempts(controller)).isZero();
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void paid_start_notification_failure_is_an_owned_execution_and_does_not_pay_twice(boolean shared) throws Exception {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.SYNC);
        var notification = new AtomicReference<Runnable>(() -> { });
        var storage = new LongItemStorage(1, 64L, () -> notification.get().run());
        var input = new ItemInputBusBlockEntity(new BlockPos(1, 0, 0),
                ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState()) {
            @Override public LongItemStorage itemHandler() { return storage; }
            @Override public LongItemStorage nativeItemHandler() { return storage; }
        };
        var controller = controllerWithPorts(input);
        var starts = new AtomicInteger();
        installBehavior(controller, RecipeBehavior.builder().beforeStart(context -> starts.incrementAndGet()).build());
        var level = (ServerLevel) controller.getLevel();
        if (shared) assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(Runnable::run));
        try {
            storage.setContents(0, new ItemStack(Items.IRON_INGOT), 2L);
            notification.set(() -> { throw new IllegalStateException("paid start notification"); });
            var thread = new RecordingRecipeThread(controller);
            var recipe = RecipeTestSupport.create(MMCR.id("paid_paused_thread_start"), controller.currentRecipePoolId(), 20,
                    List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)), List.of());
            long version = controller.currentRuntimeSnapshot().structure().version();
            RecipeThread.StartResult result = shared
                    ? thread.enqueueAsyncFinishRestart(recipe, 1L, version, null, RecipeThread.StartOrigin.LAST_RECIPE)
                    : thread.startRecipe(recipe, 1L, version, null, RecipeThread.StartOrigin.LAST_RECIPE);
            assertThat(result).isEqualTo(shared ? RecipeThread.StartResult.PENDING : RecipeThread.StartResult.STARTED);
            completeAsyncLevelTick(level);
            assertThat(thread.runtime().active()).isTrue();
            assertThat(thread.runtime().recipe()).isSameAs(recipe);
            assertThat(thread.runtime().ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.ERROR_PAUSED);
            assertThat(thread.runtime().failure()).isNotNull();
            var paid = thread.runtime().ledger().state();
            assertThat(thread.startRecipe(recipe, 1L, version, null, RecipeThread.StartOrigin.LAST_RECIPE))
                    .isEqualTo(RecipeThread.StartResult.DEFERRED);
            assertThat(thread.runtime().ledger().state()).isSameAs(paid);
            assertThat(thread.hasFullSearchPending()).isFalse();
            assertThat(thread.isStartPending()).isFalse();
            assertThat(thread.lastResults).doesNotContain(RecipeThread.StartResult.FAILED);
            assertThat(storage.amount(0)).isEqualTo(1L);
            assertThat(starts).hasValue(1);
            assertThat(normalSearchAttempts(controller)).isZero();
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void before_start_lane_eligibility_change_is_rechecked_before_payment(MachineWorkMode mode) throws Exception {
        ConfigTestSupport.setMachineWorkMode(mode);
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        var controller = controllerWithPorts(energy);
        var eligible = new AtomicBoolean(true);
        var callbacks = new AtomicInteger();
        installBehavior(controller, RecipeBehavior.builder().beforeStart(context -> {
            callbacks.incrementAndGet();
            eligible.set(false);
        }).build());
        var level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(Runnable::run));
        try {
            energy.energyStorage().setAmount(100L);
            var thread = new RecordingRecipeThread(controller);
            thread.eligibility = eligible;
            var recipe = RecipeTestSupport.create(MMCR.id("callback_lane_changed"), controller.currentRecipePoolId(),
                    20, List.of(new EnergyRequirement(7L)), List.of());
            RecipeThread.StartResult result = thread.startRecipe(recipe, 1L,
                    controller.currentRuntimeSnapshot().structure().version(), null, RecipeThread.StartOrigin.LAST_RECIPE);
            assertThat(result).isEqualTo(mode == MachineWorkMode.ASYNC
                    ? RecipeThread.StartResult.PENDING : RecipeThread.StartResult.NO_MATCH);
            completeAsyncLevelTick(level);
            assertThat(thread.lastResults).filteredOn(value -> value != RecipeThread.StartResult.PENDING)
                    .containsExactly(RecipeThread.StartResult.NO_MATCH);
            assertThat(thread.hasFullSearchPending()).isTrue();
            assertThat(thread.isStartPending()).isFalse();
            assertThat(thread.runtime().active()).isFalse();
            assertThat(callbacks).hasValue(1);
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(100L);
            assertThat(SharedIoCoordinator.get(level).reserveExecution(thread.runtime(), thread.asyncLaneId())).isTrue();
            SharedIoCoordinator.get(level).releaseExecutionReservation(thread.runtime(), thread.asyncLaneId());
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void attached_search_cancellation_does_not_try_the_second_candidate_or_record_resource_backoff(MachineWorkMode mode)
            throws Exception {
        ConfigTestSupport.setMachineWorkMode(mode);
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        var controller = controllerWithPorts(energy);
        var first = RecipeTestSupport.create(MMCR.id("a_search_cancelled"), controller.currentRecipePoolId(),
                20, List.of(new EnergyRequirement(7L)), List.of());
        var second = RecipeTestSupport.create(MMCR.id("b_search_cancelled_alternative"), controller.currentRecipePoolId(),
                20, List.of(new EnergyRequirement(3L)), List.of());
        RecipeRegistry.registerStatic(first);
        RecipeRegistry.registerStatic(second);
        var callbacks = new AtomicInteger();
        installBehavior(controller, RecipeBehavior.builder().beforeStart(context -> {
            assertThat(context.recipeId()).isEqualTo(first.id());
            callbacks.incrementAndGet();
            context.cancel();
        }).build());
        var level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(Runnable::run));
        try {
            energy.energyStorage().setAmount(100L);
            var thread = attachedNormalThread(controller);
            controller.serverTick();
            completeAsyncLevelTick(level);
            completeAsyncLevelTick(level);
            assertThat(callbacks).hasValue(1);
            assertThat(thread.isStartPending()).isFalse();
            assertThat(thread.hasPendingAsyncSearch()).isFalse();
            assertThat(thread.runtime().active()).isFalse();
            assertThat(thread.runtime().failure()).isNull();
            assertThat(thread.hasFullSearchPending()).isFalse();
            assertThat(normalSearchAttempts(controller)).isEqualTo(1L);
            Field retry = MachineControllerBlockEntity.class.getDeclaredField("recipeSearchRetryCounter");
            retry.setAccessible(true);
            assertThat(retry.getInt(controller)).isZero();
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(100L);
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void before_start_state_change_defers_old_preparation_and_retries_once_with_current_state(MachineWorkMode mode)
            throws Exception {
        ConfigTestSupport.setMachineWorkMode(mode);
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        var controller = controllerWithPorts(energy);
        var callbacks = new AtomicInteger();
        installBehavior(controller, RecipeBehavior.builder().beforeStart(context -> {
            if (callbacks.getAndIncrement() == 0) controller.componentRuntime()
                    .replaceLinkedPortPositions(Set.of(new BlockPos(99, 0, 0)));
        }).build());
        var level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(Runnable::run));
        try {
            energy.energyStorage().setAmount(100L);
            var thread = new RecordingRecipeThread(controller);
            var recipe = RecipeTestSupport.create(MMCR.id("callback_state_changed"), controller.currentRecipePoolId(),
                    20, List.of(new EnergyRequirement(7L)), List.of());
            long epoch = controller.lifecycleEpoch();
            long state = controller.currentRuntimeSnapshot().stateVersion();
            thread.startRecipe(recipe, 1L, controller.currentRuntimeSnapshot().structure().version(), null,
                    RecipeThread.StartOrigin.LAST_RECIPE);
            completeAsyncLevelTick(level);
            assertThat(controller.lifecycleEpoch()).isEqualTo(epoch);
            assertThat(controller.currentRuntimeSnapshot().stateVersion()).isNotEqualTo(state);
            assertThat(thread.lastResults).filteredOn(value -> value != RecipeThread.StartResult.PENDING)
                    .containsExactly(RecipeThread.StartResult.DEFERRED);
            assertThat(thread.isStartPending()).isFalse();
            assertThat(thread.runtime().active()).isFalse();
            assertThat(thread.hasFullSearchPending()).isFalse();
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(100L);
            assertThat(callbacks).hasValue(1);

            RuntimeTestFixtures.advanceGameTime(level);
            RecipeThread.StartResult retry = thread.startRecipe(recipe, 1L,
                    controller.currentRuntimeSnapshot().structure().version(), null, RecipeThread.StartOrigin.LAST_RECIPE);
            assertThat(retry).isEqualTo(mode == MachineWorkMode.ASYNC
                    ? RecipeThread.StartResult.PENDING : RecipeThread.StartResult.STARTED);
            completeAsyncLevelTick(level);
            assertThat(callbacks).hasValue(2);
            assertThat(thread.runtime().active()).isTrue();
            assertThat(thread.runtime().recipe()).isSameAs(recipe);
            assertThat(thread.isStartPending()).isFalse();
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(100L);
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    static Stream<Arguments> isolationCallbackRoundtrips() {
        return Arrays.stream(MachineWorkMode.values()).flatMap(mode -> Stream.of(false, true)
                .flatMap(factory -> Stream.of("isolation", "linked", "module")
                        .map(change -> Arguments.of(mode, factory, change))));
    }

    @ParameterizedTest
    @MethodSource("isolationCallbackRoundtrips")
    void before_start_only_attributes_pure_isolation_state_increments(MachineWorkMode mode, boolean factory,
                                                                      String change) throws Exception {
        ConfigTestSupport.setMachineWorkMode(mode);
        var input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        var controller = controllerWithPorts(input);
        controller.setFullyInputIsolated(true);
        var callbacks = new AtomicInteger();
        installBehavior(controller, RecipeBehavior.builder().beforeStart(context -> {
            callbacks.incrementAndGet();
            input.setInputIsolated(true);
            controller.onInputIsolationChanged();
            input.setInputIsolated(false);
            controller.onInputIsolationChanged();
            var components = controller.componentRuntime();
            if (change.equals("linked")) {
                var linked = components.linkedPortPositions();
                components.replaceLinkedPortPositions(Set.of(new BlockPos(99, 0, 0)));
                components.replaceLinkedPortPositions(linked);
            } else if (change.equals("module")) {
                var status = components.moduleConnectionStatus();
                int installed = components.installedModuleCount();
                components.replaceModuleConnectionState(ModuleConnectionStatus.connected(MMCR.id("callback_roundtrip_host")), 1);
                components.replaceModuleConnectionState(status, installed);
            }
        }).build());
        var level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(Runnable::run));
        try {
            input.itemHandler().insertItem(0, new ItemStack(Items.IRON_INGOT), false);
            var recipe = RecipeTestSupport.create(MMCR.id("callback_state_attribution"), controller.currentRecipePoolId(), 20,
                    List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)), List.of());
            RecipeRegistry.registerStatic(recipe);
            RecipeThread thread = factory ? FactoryRecipeThread.simple(controller) : new MachineRecipeThread(controller);
            var linked = controller.componentRuntime().linkedPortPositions();
            var modules = controller.componentRuntime().moduleConnectionStatus();
            thread.startRecipe(recipe, 1L, controller.currentStructureSnapshot().version(), null, RecipeThread.StartOrigin.LAST_RECIPE);
            completeAsyncLevelTick(level);

            assertThat(callbacks).hasValue(1);
            assertThat(input.isInputIsolated()).isFalse();
            assertThat(controller.componentRuntime().linkedPortPositions()).isEqualTo(linked);
            assertThat(controller.componentRuntime().moduleConnectionStatus()).isEqualTo(modules);
            assertThat(thread.isStartPending()).isFalse();
            if (change.equals("isolation")) {
                assertThat(thread.runtime().active()).isTrue();
                assertThat(input.itemHandler().amount(0)).isZero();
            } else {
                assertThat(thread.runtime().active()).isFalse();
                assertThat(thread.runtime().ledger().state().paidAttempts()).isZero();
                assertThat(input.itemHandler().amount(0)).isEqualTo(1L);
            }
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    @ParameterizedTest
    @MethodSource("callbackCatalogChanges")
    void attached_cached_before_start_catalog_change_cannot_pay_the_old_source(MachineWorkMode mode, boolean changed)
            throws Exception {
        ConfigTestSupport.setMachineWorkMode(mode);
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        var controller = controllerWithPorts(energy);
        var recipe = RecipeTestSupport.create(MMCR.id("callback_catalog_source"), controller.currentRecipePoolId(),
                1, List.of(new EnergyRequirement(7L)), List.of());
        var replacement = RecipeTestSupport.create(recipe.id(), recipe.recipePoolId(),
                20, List.of(new EnergyRequirement(3L)), List.of());
        RecipeRegistry.replaceDynamic(Map.of(recipe.id(), recipe));
        var callbacks = new AtomicInteger();
        installBehavior(controller, RecipeBehavior.builder().beforeStart(context -> {
            if (callbacks.incrementAndGet() == 2) RecipeRegistry.replaceDynamic(
                    changed ? Map.of(replacement.id(), replacement) : Map.of());
        }).build());
        var level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(Runnable::run));
        try {
            energy.energyStorage().setAmount(100L);
            var thread = attachedNormalThread(controller);
            assertThat(thread.runtime().start(recipe, 1L).isCrafting()).isTrue();
            thread.onStarted();
            thread.runtime().ensureInstalledProgram();
            long epoch = controller.lifecycleEpoch();
            long beforeSearch = normalSearchAttempts(controller);
            RuntimeTestFixtures.advanceGameTime(level);
            RuntimeTestFixtures.beginLevelTick(level, true);
            controller.serverTick();
            SharedIoCoordinator.get(level).executeInstalled(level);
            SharedIoCoordinator.get(level).resolve(level);
            assertThat(controller.lifecycleEpoch()).isEqualTo(epoch);
            assertThat(callbacks).hasValue(2);
            assertThat(thread.runtime().active()).isFalse();
            assertThat(thread.isStartPending()).isFalse();
            assertThat(thread.hasFullSearchPending()).isTrue();
            assertThat(normalSearchAttempts(controller)).isEqualTo(beforeSearch);
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(93L);
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    private static Stream<Arguments> callbackCatalogChanges() {
        return Stream.of(MachineWorkMode.SYNC, MachineWorkMode.SEMI_SYNC)
                .flatMap(mode -> Stream.of(Arguments.of(mode, false), Arguments.of(mode, true)));
    }

    @Test
    void duplicate_start_submission_defers_the_new_token_without_resource_failure() throws Exception {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        var controller = controllerWithPorts(energy);
        var callbacks = new AtomicInteger();
        installBehavior(controller, RecipeBehavior.builder().beforeStart(context -> callbacks.incrementAndGet()).build());
        var level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        var workers = new ArrayDeque<Runnable>();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(workers::add));
        try {
            energy.energyStorage().setAmount(100L);
            var thread = new RecordingRecipeThread(controller);
            var recipe = RecipeTestSupport.create(MMCR.id("duplicate_cached_submission"), controller.currentRecipePoolId(),
                    20, List.of(new EnergyRequirement(7L)), List.of());
            Runnable drain = () -> {
                MachineAsyncCoordinator async = MachineAsyncCoordinator.get(level);
                while (!workers.isEmpty() || async.hasPendingMainStepForTesting()) {
                    while (!workers.isEmpty()) workers.removeFirst().run();
                    async.pumpMainThreadSteps();
                    SharedIoCoordinator.get(level).resolve(level);
                }
            };
            long epoch = controller.lifecycleEpoch();
            long time = level.getGameTime();
            long version = controller.currentRuntimeSnapshot().structure().version();
            assertThat(thread.startRecipe(recipe, 1L, version, null, RecipeThread.StartOrigin.LAST_RECIPE))
                    .isEqualTo(RecipeThread.StartResult.PENDING);
            thread.cancelAsyncState();
            assertThat(thread.startRecipe(recipe, 1L, version, null, RecipeThread.StartOrigin.LAST_RECIPE))
                    .isEqualTo(RecipeThread.StartResult.DEFERRED);
            assertThat(controller.lifecycleEpoch()).isEqualTo(epoch);
            assertThat(level.getGameTime()).isEqualTo(time);
            assertThat(thread.lastResults).containsExactly(RecipeThread.StartResult.PENDING, RecipeThread.StartResult.DEFERRED);
            assertThat(thread.hasFullSearchPending()).isFalse();
            assertThat(thread.isStartPending()).isFalse();
            assertThat(thread.runtime().active()).isFalse();
            assertThat(callbacks).hasValue(0);
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(100L);
            drain.run();
            assertThat(thread.lastResults).doesNotContain(RecipeThread.StartResult.FAILED);

            RuntimeTestFixtures.advanceGameTime(level);
            RuntimeTestFixtures.beginLevelTick(level, true);
            SharedIoCoordinator.get(level).markControllerEligible(controller, level.getGameTime());
            assertThat(thread.startRecipe(recipe, 1L, version, null, RecipeThread.StartOrigin.LAST_RECIPE))
                    .isEqualTo(RecipeThread.StartResult.PENDING);
            drain.run();
            assertThat(thread.runtime().active()).isTrue();
            assertThat(thread.runtime().recipe()).isSameAs(recipe);
            assertThat(thread.isStartPending()).isFalse();
            assertThat(callbacks).hasValue(1);
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(100L);
            assertThat(normalSearchAttempts(controller)).isZero();
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"sync-copy", "sync-plan", "fast-copy", "shared-plan", "continuation-copy"})
    void extension_start_exceptions_finish_the_owned_token_once_and_leave_a_usable_reservation(String boundary)
            throws Exception {
        boolean async = !boundary.startsWith("sync");
        ConfigTestSupport.setMachineWorkMode(async ? MachineWorkMode.ASYNC : MachineWorkMode.SYNC);
        registerFaultRequirement();
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        var controller = controllerWithPorts(energy);
        var callbacks = new AtomicInteger();
        installBehavior(controller, RecipeBehavior.builder().beforeStart(context -> callbacks.incrementAndGet()).build());
        var recipe = RecipeTestSupport.create(MMCR.id("exception_cached_start"), controller.currentRecipePoolId(),
                20, List.of(new EnergyRequirement(7L), new FaultRequirement()), List.of());
        var alternative = RecipeTestSupport.create(MMCR.id("exception_alternative_start"), controller.currentRecipePoolId(),
                20, List.of(new EnergyRequirement(3L)), List.of());
        var level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(Runnable::run));
        try {
            energy.energyStorage().setAmount(100L);
            RuntimeTestFixtures.beginLevelTick(level, true);
            SharedIoCoordinator.get(level).markControllerEligible(controller, level.getGameTime());
            var thread = new RecordingRecipeThread(controller);
            var unpaid = thread.runtime().ledger().state();
            long version = controller.currentRuntimeSnapshot().structure().version();
            Runnable fault = () -> { throw new IllegalStateException("extension start " + boundary); };
            if (boundary.endsWith("copy")) FaultRequirement.copyFault.set(fault);
            else if (boundary.equals("sync-plan")) FaultRequirement.planFault.set(fault);
            var result = new AtomicReference<RecipeThread.StartResult>();
            assertThatCode(() -> result.set(boundary.startsWith("fast") || boundary.startsWith("shared")
                    ? thread.enqueueAsyncFinishRestart(recipe, 1L, version, null, RecipeThread.StartOrigin.LAST_RECIPE)
                    : thread.startRecipe(recipe, 1L, version, null, RecipeThread.StartOrigin.LAST_RECIPE)))
                    .doesNotThrowAnyException();
            assertThat(result.get()).isEqualTo(boundary.equals("shared-plan") || boundary.equals("continuation-copy")
                    ? RecipeThread.StartResult.PENDING : RecipeThread.StartResult.FAILED);
            if (boundary.equals("shared-plan")) FaultRequirement.planFault.set(fault);
            assertThatCode(() -> completeAsyncLevelTick(level)).doesNotThrowAnyException();
            completeAsyncLevelTick(level);
            assertThat(thread.lastResults).filteredOn(value -> value != RecipeThread.StartResult.PENDING)
                    .containsExactly(RecipeThread.StartResult.FAILED);
            assertThat(thread.hasFullSearchPending()).isTrue();
            assertThat(thread.isStartPending()).isFalse();
            assertThat(thread.runtime().active()).isFalse();
            assertThat(thread.runtime().ledger().state()).isEqualTo(unpaid);
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(100L);
            assertThat(SharedIoCoordinator.get(level).reserveExecution(thread.runtime(), thread.asyncLaneId())).isTrue();
            SharedIoCoordinator.get(level).releaseExecutionReservation(thread.runtime(), thread.asyncLaneId());

            RuntimeTestFixtures.advanceGameTime(level);
            RuntimeTestFixtures.beginLevelTick(level, true);
            SharedIoCoordinator.get(level).markControllerEligible(controller, level.getGameTime());
            assertThat(thread.searchAndStartRecipe(List.of(alternative), 1L, version).ownsStart()).isTrue();
            completeAsyncLevelTick(level);
            assertThat(thread.runtime().active()).isTrue();
            assertThat(thread.runtime().recipe()).isSameAs(alternative);
            assertThat(thread.isStartPending()).isFalse();
            assertThat(thread.hasFullSearchPending()).isFalse();
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(100L);
            assertThat(callbacks).hasValue(boundary.endsWith("plan") ? 2 : 1);
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"sync-copy", "fast-copy", "shared-plan"})
    void next_start_extension_exception_does_not_lose_the_output_paid_completion(String boundary) throws Exception {
        ConfigTestSupport.setMachineWorkMode(boundary.startsWith("sync") ? MachineWorkMode.SYNC : MachineWorkMode.ASYNC);
        registerFaultRequirement();
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        var output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        var controller = controllerWithPorts(energy, output);
        var starts = new AtomicInteger();
        var finishes = new AtomicInteger();
        installBehavior(controller, RecipeBehavior.builder().beforeStart(context -> starts.incrementAndGet())
                .beforeFinish(context -> finishes.incrementAndGet()).build());
        var recipe = RecipeTestSupport.create(MMCR.id("exception_completed_source"), controller.currentRecipePoolId(), 1,
                List.of(new EnergyRequirement(7L), new FaultRequirement(),
                        new ItemRequirement(RecipeModifier.IOType.OUTPUT, Ingredient.of(Items.GOLD_NUGGET), 1,
                                new ItemStack(Items.GOLD_NUGGET))),
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_NUGGET), 1F)));
        RecipeRegistry.registerStatic(recipe);
        var level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(Runnable::run));
        try {
            energy.energyStorage().setAmount(100L);
            var thread = new RecordingRecipeThread(controller);
            assertThat(thread.runtime().start(recipe, 1L).isCrafting()).isTrue();
            thread.runtime().ensureInstalledProgram();
            thread.finishedAttempt = () -> {
                long version = controller.currentRuntimeSnapshot().structure().version();
                Runnable fault = () -> { throw new IllegalStateException("next start " + boundary); };
                if (boundary.endsWith("copy")) FaultRequirement.copyFault.set(fault);
                if (boundary.startsWith("sync")) {
                    assertThat(thread.startRecipe(recipe, 1L, version, null, RecipeThread.StartOrigin.LAST_RECIPE))
                            .isEqualTo(RecipeThread.StartResult.FAILED);
                } else {
                    assertThat(thread.enqueueAsyncFinishRestart(recipe, 1L, version, null, RecipeThread.StartOrigin.LAST_RECIPE))
                            .isEqualTo(boundary.equals("shared-plan")
                                    ? RecipeThread.StartResult.PENDING : RecipeThread.StartResult.FAILED);
                    if (boundary.equals("shared-plan")) FaultRequirement.planFault.set(fault);
                }
            };
            RuntimeTestFixtures.advanceGameTime(level);
            RuntimeTestFixtures.beginLevelTick(level, true);
            SharedIoCoordinator.get(level).markControllerEligible(controller, level.getGameTime());
            assertThatCode(() -> {
                thread.tick();
                SharedIoCoordinator.get(level).executeInstalled(level);
                completeAsyncLevelTick(level);
            }).doesNotThrowAnyException();
            thread.tick();
            completeAsyncLevelTick(level);
            assertThat(thread.finishedNotifications).isEqualTo(1);
            assertThat(finishes).hasValue(1);
            assertThat(starts).hasValue(boundary.equals("shared-plan") ? 2 : 1);
            assertThat(thread.lastResults).filteredOn(value -> value != RecipeThread.StartResult.PENDING)
                    .containsExactly(RecipeThread.StartResult.FAILED);
            assertThat(thread.runtime().active()).isFalse();
            assertThat(thread.isStartPending()).isFalse();
            assertThat(thread.hasFullSearchPending()).isTrue();
            assertThat(output.itemHandler().amount(0)).isEqualTo(1L);
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(93L);
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preparation_exception_after_replacement_does_not_clear_a_new_token_or_epoch(boolean changeEpoch)
            throws Exception {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        registerFaultRequirement();
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        var controller = controllerWithPorts(energy);
        var recipe = RecipeTestSupport.create(MMCR.id("exception_obsolete_preparation"), controller.currentRecipePoolId(),
                20, List.of(new EnergyRequirement(7L), new FaultRequirement()), List.of());
        var replacement = RecipeTestSupport.create(MMCR.id("exception_new_preparation"), controller.currentRecipePoolId(),
                20, List.of(new EnergyRequirement(3L)), List.of());
        var level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(Runnable::run));
        try {
            energy.energyStorage().setAmount(100L);
            var thread = new RecordingRecipeThread(controller);
            FaultRequirement.copyFault.set(() -> {
                thread.cancelAsyncState();
                if (changeEpoch) {
                    controller.invalidateFormedStructure();
                    thread.requestFullSearch();
                } else {
                    assertThat(thread.enqueueAsyncFinishRestart(replacement, 1L,
                            controller.currentRuntimeSnapshot().structure().version(), null, RecipeThread.StartOrigin.SEARCH))
                            .isEqualTo(RecipeThread.StartResult.PENDING);
                    thread.requestFullSearch();
                }
                throw new IllegalStateException("obsolete copy after replacement");
            });
            assertThat(thread.enqueueAsyncFinishRestart(recipe, 1L,
                    controller.currentRuntimeSnapshot().structure().version(), null, RecipeThread.StartOrigin.LAST_RECIPE))
                    .isEqualTo(RecipeThread.StartResult.CANCELLED);
            assertThat(thread.lastResults).isEmpty();
            assertThat(thread.hasFullSearchPending()).isTrue();
            assertThat(thread.isStartPending()).isEqualTo(!changeEpoch);
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(100L);
            completeAsyncLevelTick(level);
            assertThat(thread.lastResults).isEmpty();
            assertThat(thread.isStartPending()).isFalse();
            assertThat(thread.runtime().active()).isEqualTo(!changeEpoch);
            assertThat(thread.hasFullSearchPending()).isEqualTo(changeEpoch);
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(100L);
            if (!changeEpoch) assertThat(thread.runtime().recipe()).isSameAs(replacement);
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    private static void registerFaultRequirement() {
        if (RequirementHandlerRegistry.typeFor(FaultRequirement.TYPE.id()) == null) {
            RequirementHandlerRegistry.register(FaultRequirement.TYPE);
        }
    }

    /** Extension faults are injected at the real copy and live planner boundaries. @author howxu <dev@howxu.cn> */
    private record FaultRequirement() implements MachineRequirement {
        private static final AtomicReference<Runnable> copyFault = new AtomicReference<>(() -> { });
        private static final AtomicReference<Runnable> planFault = new AtomicReference<>(() -> { });
        private static final RequirementType<FaultRequirement> TYPE = new RequirementType.Definition<>(
                MMCR.id("thread_fault_requirement"), Codec.STRING.fieldOf("type")
                        .xmap(ignored -> new FaultRequirement(), ignored -> MMCR.id("thread_fault_requirement").toString()),
                (requirement, capabilities, context) -> {
                    planFault.getAndSet(() -> { }).run();
                    return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
                }, requirement -> {
                    copyFault.getAndSet(() -> { }).run();
                    return requirement;
                });

        @Override public RequirementType<FaultRequirement> type() { return TYPE; }
        @Override public RecipeModifier.IOType io() { return RecipeModifier.IOType.INPUT; }
    }

    private static MachineRecipeThread attachedNormalThread(MachineControllerBlockEntity controller)
            throws ReflectiveOperationException {
        Field field = MachineControllerBlockEntity.class.getDeclaredField("normalRecipeThread");
        field.setAccessible(true);
        return (MachineRecipeThread) field.get(controller);
    }

    private static void installBehavior(MachineControllerBlockEntity controller, RecipeBehavior behavior)
            throws ReflectiveOperationException {
        Machine original = controller.currentStructureSnapshot().configuredMachine();
        Machine machine = new Machine() {
            public ResourceLocation registryName() { return original.registryName(); }
            public BlockArray pattern() { return original.pattern(); }
            public MachineControllerSpec controller() { return original.controller(); }
            public RecipeBehavior behavior() { return behavior; }
        };
        controller.setMachine(machine);
        Field runtimeField = MachineControllerBlockEntity.class.getDeclaredField("runtime");
        runtimeField.setAccessible(true);
        Method publish = MachineControllerRuntime.class.getDeclaredMethod("publishFormationState",
                Machine.class, BlockArray.class, CompiledMachinePattern.class, Direction.class, Direction.class, int.class);
        publish.setAccessible(true);
        var structure = controller.currentStructureSnapshot();
        publish.invoke(runtimeField.get(controller), machine, structure.pattern(), MachinePatternCompiler.compile(machine),
                structure.facing(), structure.rollFacing(), structure.matchedStage());
        RuntimeTestFixtures.republish(controller);
        assertThat(controller.currentStructureSnapshot().machine().behavior()).isSameAs(behavior);
    }

    /** Records real pending start terminal dispatches. @author howxu <dev@howxu.cn> */
    private static final class RecordingRecipeThread extends RecipeThread {
        private final List<StartResult> lastResults = new ArrayList<>();
        private AtomicBoolean eligibility = new AtomicBoolean(true);
        private Runnable finishedAttempt = () -> { };
        private int finishedNotifications;

        private RecordingRecipeThread(MachineControllerBlockEntity controller) { super(controller); }
        @Override protected void onStarted() { }
        @Override protected void onFinished() { finishedNotifications++; }
        @Override protected void onRecipeFinished() { finishedAttempt.run(); }
        @Override protected void onLastRecipeStartResult(StartResult result) { lastResults.add(result); }
        @Override protected boolean isStartCandidateEligible(MachineRecipe recipe) {
            return eligibility.get() && super.isStartCandidateEligible(recipe);
        }
    }

    private static void assertReceiptWorkersOnly(MachineAsyncCoordinator coordinator, ArrayDeque<Runnable> workers)
            throws ReflectiveOperationException {
        Field tasksField = MachineAsyncCoordinator.class.getDeclaredField("tasks");
        tasksField.setAccessible(true);
        assertThat((Map<?, ?>) tasksField.get(coordinator)).isEmpty();
        Field drainsField = MachineAsyncCoordinator.class.getDeclaredField("receiptDrains");
        drainsField.setAccessible(true);
        int queued = 0;
        for (Object drain : ((Map<?, ?>) drainsField.get(coordinator)).values()) {
            Field queuedField = drain.getClass().getDeclaredField("queued");
            queuedField.setAccessible(true);
            if (((AtomicBoolean) queuedField.get(drain)).get()) queued++;
        }
        assertThat(workers).hasSize(queued);
    }

    private static void attachFactoryScheduler(MachineControllerBlockEntity controller) {
        var scheduler = new FactorySchedulerBlockEntity(new BlockPos(4, 0, 0),
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.replaceBlockEntity(controller, scheduler);
        var members = new ArrayList<>(controller.componentRuntime().components());
        members.add(new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), scheduler.getBlockPos(), (String) null));
        controller.componentRuntime().replaceComponents(members);
        RuntimeTestFixtures.republish(controller);
    }

    @ParameterizedTest
    @EnumSource(value = MachineWorkMode.class, names = {"ASYNC", "SEMI_SYNC"})
    void worker_free_ticks_wait_for_level_post_tick_and_ignore_duplicate_tickers(MachineWorkMode mode) throws Exception {
        ConfigTestSupport.setMachineWorkMode(mode);
        MachineControllerBlockEntity controller = controllerWithPorts();
        ServerLevel level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(command -> {
            throw new AssertionError("worker-free ticks must not dispatch a worker");
        }));
        try {
            FactoryRecipeThread thread = attachedBaseLane(controller);
            MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("worker_free_tick_timing"), MMCR.id("test_cube"),
                    20, List.of(), List.of());
            assertThat(thread.runtime().start(recipe, 1).isCrafting()).isTrue();

            for (int tick = 1; tick <= 2; tick++) {
                for (int duplicate = 0; duplicate < 8; duplicate++) thread.tick();
                assertThat(thread.runtime().tickCount()).isEqualTo(tick - 1);
                assertThat(thread.tickPendingForTesting()).isTrue();

                SharedIoEvents.completeLevelTick(level);
                assertThat(thread.runtime().tickCount()).isEqualTo(tick);
                assertThat(thread.tickPendingForTesting()).isFalse();

                for (int duplicate = 0; duplicate < 8; duplicate++) thread.tick();
                assertThat(thread.tickPendingForTesting()).isFalse();
                SharedIoEvents.completeLevelTick(level);
                assertThat(thread.runtime().tickCount()).isEqualTo(tick);
                RuntimeTestFixtures.advanceGameTime(level);
            }
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    @Test
    void energy_tick_rechecks_supply_at_arbitration_and_recovers_without_duplicate_consumption() throws Exception {
        EnergyInputHatchBlockEntity energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = controllerWithPorts(energy);
        ServerLevel level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(command -> {
            throw new AssertionError("energy-only ticks must not dispatch a worker");
        }));
        try {
            energy.nativeEnergyStorage().setAmount(100L);
            MachineRecipeThread thread = new MachineRecipeThread(controller);
            MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("energy_tick_live_supply"), MMCR.id("test_cube"),
                    20, List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 20L)), List.of());
            assertThat(thread.runtime().start(recipe, 1).isCrafting()).isTrue();
            thread.tick();
            energy.nativeEnergyStorage().setAmount(0L);
            SharedIoEvents.completeLevelTick(level);

            assertThat(thread.runtime().tickCount()).isZero();
            assertThat(thread.runtime().failure()).isNotNull();
            assertThat(energy.nativeEnergyStorage().getAmountAsLong()).isZero();
            assertThat(thread.tickPendingForTesting()).isFalse();

            RuntimeTestFixtures.advanceGameTime(level);
            energy.nativeEnergyStorage().setAmount(100L);
            thread.tick();
            SharedIoEvents.completeLevelTick(level);

            assertThat(thread.runtime().tickCount()).isEqualTo(1);
            assertThat(thread.runtime().failure()).isNull();
            assertThat(energy.nativeEnergyStorage().getAmountAsLong()).isEqualTo(80L);
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    @Test
    void mixed_per_tick_fallback_does_not_dispatch_discarded_worker_planning() throws Exception {
        EnergyInputHatchBlockEntity energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        ItemInputBusBlockEntity catalyst = RuntimeTestFixtures.itemInput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = controllerWithPorts(energy, catalyst);
        ServerLevel level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        installCoordinator(level, MachineAsyncCoordinator.forTesting(command -> {
            throw new AssertionError("known fallback must not dispatch discarded worker planning");
        }));
        try {
            energy.nativeEnergyStorage().setAmount(100L);
            catalyst.itemHandler().setContents(0, new ItemStack(Items.IRON_INGOT), 1L);
            MachineRecipeThread thread = new MachineRecipeThread(controller);
            MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("mixed_energy_tick"), MMCR.id("test_cube"),
                    20, List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 20L),
                            new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                                    ItemStack.EMPTY, 1F, List.of(), DataComponentPredicateSet.EMPTY, 0F)), List.of());
            assertThat(thread.runtime().start(recipe, 1).isCrafting()).isTrue();
            long energyAfterStart = energy.nativeEnergyStorage().getAmountAsLong();
            thread.tick();
            catalyst.itemHandler().setContents(0, ItemStack.EMPTY, 0L);
            SharedIoEvents.completeLevelTick(level);

            assertThat(thread.runtime().tickCount()).isZero();
            assertThat(thread.runtime().failure()).isNotNull();
            assertThat(energy.nativeEnergyStorage().getAmountAsLong()).isEqualTo(energyAfterStart);
            assertThat(thread.tickPendingForTesting()).isFalse();

            RuntimeTestFixtures.advanceGameTime(level);
            catalyst.itemHandler().setContents(0, new ItemStack(Items.IRON_INGOT), 1L);
            thread.tick();
            SharedIoEvents.completeLevelTick(level);
            assertThat(thread.runtime().tickCount()).isEqualTo(1);
            assertThat(catalyst.itemHandler().amount(0)).isEqualTo(1L);
            assertThat(energy.nativeEnergyStorage().getAmountAsLong()).isEqualTo(energyAfterStart - 20L);
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void heat_temperature_and_energy_ticks_use_live_main_thread_arbitration(boolean includeEnergy) throws Exception {
        try (var requirementScope = RequirementHandlerRegistry.openTestScope();
             var outputScope = OutputRegistry.openTestScope()) {
            var bridge = MekanismBridgeBootstrap.selectForTesting(true);
            MekanismBridgeBootstrap.installForTesting(bridge);
            bridge.registerRecipeTypes(MekanismRecipeTypes.CHEMICAL, MekanismRecipeTypes.HEAT_TEMPERATURE,
                    MekanismRecipeTypes.HEAT);
            HeatProbePort input = new HeatProbePort(new BlockPos(1, 0, 0), IOType.INPUT);
            HeatProbePort output = new HeatProbePort(new BlockPos(2, 0, 0), IOType.OUTPUT);
            EnergyInputHatchBlockEntity energy = RuntimeTestFixtures.energyInput(new BlockPos(3, 0, 0));
            MachineControllerBlockEntity controller = controllerWithPorts(input, output, energy);
            ServerLevel level = (ServerLevel) controller.getLevel();
            assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
            installCoordinator(level, MachineAsyncCoordinator.forTesting(command -> {
                throw new AssertionError("scalar heat/energy ticks must not dispatch a worker");
            }));
            try {
                input.capacitor.setHeat(6_000D);
                energy.nativeEnergyStorage().setAmount(1_000L);
                List<MachineRequirement> requirements = new ArrayList<>(List.of(
                        LoadedHeatRequirement.minimumTemperature(10D), LoadedHeatRequirement.outputHeat(5D)));
                if (includeEnergy) requirements.add(new EnergyRequirement(RecipeModifier.IOType.INPUT, 20L));
                MachineRecipeThread thread = new MachineRecipeThread(controller);
                MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("scalar_heat_tick"), controller.currentRecipePoolId(),
                        20, requirements, List.of());
                assertThat(thread.runtime().start(recipe, 1).isCrafting())
                        .as("start failure: %s", thread.runtime().failure()).isTrue();
                long energyAfterStart = energy.nativeEnergyStorage().getAmountAsLong();

                thread.tick();
                input.capacitor.setHeat(0D);
                SharedIoEvents.completeLevelTick(level);
                output.capacitor.update();
                assertThat(thread.runtime().tickCount()).isZero();
                assertThat(thread.runtime().failure().reason()).isEqualTo(MekanismFailureReasons.HEAT_TEMPERATURE_INSUFFICIENT);
                assertThat(output.capacitor.getHeat()).isZero();
                assertThat(energy.nativeEnergyStorage().getAmountAsLong()).isEqualTo(energyAfterStart);

                input.capacitor.setHeat(6_000D);
                for (int tick = 1; tick <= 3; tick++) {
                    RuntimeTestFixtures.advanceGameTime(level);
                    thread.tick();
                    SharedIoEvents.completeLevelTick(level);
                    output.capacitor.update();
                    assertThat(thread.runtime().tickCount()).isEqualTo(tick);
                    assertThat(thread.tickPendingForTesting()).isFalse();
                    assertThat(output.capacitor.getHeat()).isEqualTo(tick * 5D);
                    assertThat(energy.nativeEnergyStorage().getAmountAsLong())
                            .isEqualTo(energyAfterStart - (includeEnergy ? tick * 20L : 0L));
                }
            } finally {
                MachineAsyncCoordinator.discard(level);
                SharedIoCoordinator.discard(level);
                StructureClaimRegistry.discard(level);
            }
        } finally {
            MekanismBridgeBootstrap.resetForTesting();
        }
    }

    @ParameterizedTest
    @EnumSource(value = MachineWorkMode.class, names = {"ASYNC", "SEMI_SYNC"})
    void late_worker_workset_does_not_clear_a_replacement_tick(MachineWorkMode mode) throws Exception {
        assertWorkerWorksetInvalidation(mode, true);
    }

    @ParameterizedTest
    @EnumSource(value = MachineWorkMode.class, names = {"ASYNC", "SEMI_SYNC"})
    void worker_workset_rechecks_state_version_after_planning_and_can_retry(MachineWorkMode mode) throws Exception {
        assertWorkerWorksetInvalidation(mode, false);
    }

    private void assertWorkerWorksetInvalidation(MachineWorkMode mode, boolean replacementTick) throws Exception {
        ConfigTestSupport.setMachineWorkMode(mode);
        EnergyInputHatchBlockEntity input = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = controllerWithPorts(input);
        ServerLevel level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        installCoordinator(level, coordinator);
        try {
            input.nativeEnergyStorage().setAmount(1_000L);
            FactoryRecipeThread lane = attachedBaseLane(controller);
            MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("worker_workset_invalidation"), controller.currentRecipePoolId(),
                    20, List.of(new EnergyRequirement(20L)), List.of());
            assertThat(lane.runtime().start(recipe, 1L).isCrafting())
                    .as("workset recipe start failure: %s", lane.runtime().failure()).isTrue();
            long before = input.nativeEnergyStorage().getAmountAsLong();
            lane.tick();
            Field tokenField = RecipeThread.class.getDeclaredField("pendingTickToken");
            tokenField.setAccessible(true);
            long oldToken = tokenField.getLong(lane);
            var captured = controller.currentRuntimeSnapshot();
            var domain = controller.resourceDomain();
            long lifecycleEpoch = controller.lifecycleEpoch();
            long catalogVersion = RecipeRegistry.catalogForPool(controller.currentRecipePoolId()).version();
            Method commit = RecipeThread.class.getDeclaredMethod("commitTickWorksetIntent", long.class,
                    StructureClaimRegistry.ResourceDomain.class, long.class, long.class, long.class, long.class,
                    AsyncRequirementPlanner.PlanResult.class);
            commit.setAccessible(true);
            var result = new AsyncRequirementPlanner.PlanResult(List.of(), List.of(0));

            // Deliver the captured workset result only after its lane has changed.
            RuntimeTestFixtures.advanceGameTime(level);
            if (replacementTick) {
                lane.cancelAsyncState();
                lane.tick();
                assertThat(lane.tickPendingForTesting()).isTrue();
                assertThat(tokenField.getLong(lane)).isNotEqualTo(oldToken);
            } else {
                long capabilityVersion = controller.componentRuntime().capabilityVersion();
                long structureVersion = controller.currentStructureSnapshot().version();
                assertThat(controller.componentRuntime().replaceLinkedPortPositions(Set.of(new BlockPos(99, 0, 0))))
                        .isTrue();
                assertThat(controller.componentRuntime().capabilityVersion()).isEqualTo(capabilityVersion);
                assertThat(controller.currentStructureSnapshot().version()).isEqualTo(structureVersion);
                assertThat(lane.runtime().versionsCurrent()).isTrue();
            }
            assertThat(commit.invoke(lane, oldToken, domain, lifecycleEpoch, captured.structure().version(),
                    captured.stateVersion(), catalogVersion, result)).isEqualTo(false);

            assertThat(lane.runtime().tickCount()).isZero();
            assertThat(input.nativeEnergyStorage().getAmountAsLong()).isEqualTo(before);
            assertThat(lane.tickPendingForTesting()).isEqualTo(replacementTick);

            if (!replacementTick) lane.tick();
            SharedIoEvents.completeLevelTick(level);
            assertThat(lane.runtime().tickCount()).isEqualTo(1);
            assertThat(input.nativeEnergyStorage().getAmountAsLong()).isEqualTo(before - 20L);
            assertThat(lane.tickPendingForTesting()).isFalse();
            SharedIoEvents.completeLevelTick(level);
            assertThat(lane.runtime().tickCount()).isEqualTo(1);
            assertThat(input.nativeEnergyStorage().getAmountAsLong()).isEqualTo(before - 20L);
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
            MachineControllerBlockEntity.clearFormedControllerIndex(level);
        }
    }

    /** Test port with a real Mekanism heat capability and a deterministic ambient temperature.
     * @author howxu <dev@howxu.cn>
     */
    private static final class HeatProbePort extends IOPortBlockEntity {
        private final IOType direction;
        private final BasicHeatCapacitor capacitor = BasicHeatCapacitor.create(300D, () -> 0D, () -> { });

        private HeatProbePort(BlockPos pos, IOType direction) {
            super(ModBlockEntities.BES.get(direction == IOType.INPUT ? "item_input_bus" : "item_output_bus").get(), pos,
                    ModBlocks.BLOCKS.get(direction == IOType.INPUT ? "item_input_bus" : "item_output_bus").get().defaultBlockState());
            this.direction = direction;
        }

        @Override
        public IOType ioType() { return direction; }

        @Override
        public IOPortKind kind() { return direction == IOType.INPUT ? PortKinds.ITEM_INPUT : PortKinds.ITEM_OUTPUT; }

        @Override
        public CapabilitySnapshot capabilitySnapshot() {
            return new CapabilitySnapshot(List.of(new HeatPortCapability(capacitor, direction)));
        }
    }

    @SuppressWarnings("unchecked")
    private void installCoordinator(ServerLevel level, MachineAsyncCoordinator coordinator) throws Exception {
        Field field = MachineAsyncCoordinator.class.getDeclaredField("COORDINATORS");
        field.setAccessible(true);
        ((Map<ServerLevel, MachineAsyncCoordinator>) field.get(null)).put(level, coordinator);
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        MinecraftServer server = (MinecraftServer) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(DedicatedServer.class);
        Field serverThread = MinecraftServer.class.getDeclaredField("serverThread");
        serverThread.setAccessible(true);
        serverThread.set(server, Thread.currentThread());
        Field currentServer = ServerLifecycleHooks.class.getDeclaredField("currentServer");
        currentServer.setAccessible(true);
        previousServer = (MinecraftServer) currentServer.get(null);
        serverInstalled = true;
        currentServer.set(null, server);
    }

    @SuppressWarnings("unchecked")
    private static FactoryRecipeThread attachedBaseLane(MachineControllerBlockEntity controller) throws Exception {
        Field runtimeField = MachineControllerBlockEntity.class.getDeclaredField("runtime");
        runtimeField.setAccessible(true);
        FactoryRuntime factory = ((MachineControllerRuntime) runtimeField.get(controller)).factoryRuntime();
        factory.ensureBaseLane(controller);
        Field lanesField = FactoryRuntime.class.getDeclaredField("lanes");
        lanesField.setAccessible(true);
        FactoryRecipeThread base = ((List<FactoryRecipeThread>) lanesField.get(factory)).getFirst();
        assertThat(base.runtime()).isNotSameAs(RuntimeTestFixtures.craftingRuntime(controller));
        return base;
    }

    private static MachineControllerBlockEntity controllerWithPorts(IOPortBlockEntity... ports) {
        MachineControllerBlockEntity controller = normalController();
        for (IOPortBlockEntity port : ports) RuntimeTestFixtures.replaceBlockEntity(controller, port);
        controller.componentRuntime().replaceComponents(Arrays.stream(ports).map(port -> new ProcessingComponent(
                new MachineComponent(port.kind(), port.ioType()), port,
                port.getBlockPos(), port.getBlockPos(), (String) null)).toList());
        RuntimeTestFixtures.republish(controller);
        return controller;
    }

    @Test
    void ordinary_search_uses_the_controller_selected_recipe_pool() {
        ResourceLocation machineId = MMCR.id("ordinary_selected_pool_machine");
        ResourceLocation firstPool = MMCR.id("ordinary_selected_pool_first");
        ResourceLocation secondPool = MMCR.id("ordinary_selected_pool_second");
        MachineControllerBlockEntity controller = multiPoolController(machineId, firstPool, secondPool);
        assertThat(controller.selectRecipePool(secondPool)).isTrue();
        MachineRecipe first = RecipeTestSupport.create(MMCR.id("ordinary_selected_pool_first_recipe"), firstPool,
                20, List.of(), List.of());
        MachineRecipe second = RecipeTestSupport.create(MMCR.id("ordinary_selected_pool_second_recipe"), secondPool,
                20, List.of(), List.of());
        MachineRecipeThread thread = new MachineRecipeThread(controller);

        assertThat(thread.searchAndStartRecipe(List.of(first, second), 1,
                controller.runtimeSnapshot().structure().version())).isEqualTo(RecipeThread.StartResult.STARTED);
        assertThat(thread.runtime().recipe()).isEqualTo(second);
    }

    @Test
    void async_ordinary_search_ignores_a_recipe_from_a_different_pool() {
        MachineControllerBlockEntity controller = normalController();
        MachineRecipe foreign = RecipeTestSupport.create(MMCR.id("async_ordinary_foreign_pool_recipe"),
                MMCR.id("async_ordinary_foreign_pool"), 20, List.of(), List.of());
        MachineRecipe valid = RecipeTestSupport.create(MMCR.id("async_ordinary_valid_pool_recipe"),
                MMCR.id("test_cube"), 20, List.of(), List.of());
        MachineRecipeThread thread = new MachineRecipeThread(controller);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);

        assertThat(thread.searchAndStartAsyncRecipe(List.of(foreign, valid), 1,
                controller.runtimeSnapshot().structure().version())).isEqualTo(RecipeThread.StartResult.PENDING);

        completeAsyncLevelTick((ServerLevel) controller.getLevel());

        assertThat(thread.runtime().active()).isTrue();
        assertThat(thread.runtime().recipe()).isEqualTo(valid);
    }

    @Test
    void switching_pools_discards_an_outstanding_async_search_result() {
        ResourceLocation machineId = MMCR.id("test_cube");
        ResourceLocation firstPool = MMCR.id("async_selected_pool_first");
        ResourceLocation secondPool = MMCR.id("async_selected_pool_second");
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(machineId)
                .recipePoolIds(List.of(firstPool, secondPool)).build());
        MachineControllerBlockEntity controller = normalController();
        assertThat(controller.selectRecipePool(secondPool)).isTrue();
        MachineRecipe second = RecipeTestSupport.create(MMCR.id("async_selected_pool_recipe"), secondPool,
                20, List.of(), List.of());
        MachineRecipeThread thread = new MachineRecipeThread(controller);

        assertThat(thread.searchAndStartAsyncRecipe(List.of(second), 1,
                controller.runtimeSnapshot().structure().version())).isEqualTo(RecipeThread.StartResult.PENDING);
        assertThat(controller.selectRecipePool(firstPool)).isTrue();
        thread.requestFullSearch();
        completeAsyncLevelTick((ServerLevel) controller.getLevel());

        assertThat(thread.runtime().active()).isFalse();
        assertThat(thread.hasPendingAsyncSearch()).isFalse();
        assertThat(thread.hasFullSearchPending()).isTrue();
    }

    @Test
    void async_search_without_a_server_level_keeps_the_search_pending() {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        assertThat(controller.getLevel()).isNotInstanceOf(ServerLevel.class);
        MachineRecipeThread thread = new MachineRecipeThread(controller);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("async_search_without_level"), MMCR.id("test_cube"),
                20, List.of(), List.of());
        thread.requestFullSearch();

        assertThat(thread.searchAndStartAsyncRecipe(List.of(recipe), 1L,
                controller.runtimeSnapshot().structure().version())).isEqualTo(RecipeThread.StartResult.DEFERRED);
        assertThat(thread.hasFullSearchPending()).isTrue();
        assertThat(thread.runtime().active()).isFalse();
        assertThat(thread.runtime().failure()).isNull();
    }

    @Test
    void async_normal_controller_waits_for_worker_search_before_starting() {
        MachineControllerBlockEntity controller = normalController();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("async_normal_search"), MMCR.id("test_cube"), 20,
                List.of(), List.of());
        RecipeRegistry.registerStatic(recipe);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);

        controller.serverTick();

        assertThat(controller.isRuntimeActive()).isFalse();

        completeAsyncLevelTick((ServerLevel) controller.getLevel());

        assertThat(controller.isRuntimeActive()).isTrue();
    }

    @Test
    void async_normal_controller_discards_a_structure_changed_search_and_retries() {
        MachineControllerBlockEntity controller = normalController();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("async_normal_stale_search"), MMCR.id("test_cube"), 20,
                List.of(), List.of());
        RecipeRegistry.registerStatic(recipe);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        ServerLevel level = (ServerLevel) controller.getLevel();

        controller.serverTick();
        controller.setFormed(false);
        completeAsyncLevelTick(level);

        assertThat(controller.isRuntimeActive()).isFalse();

        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        RuntimeTestFixtures.advanceGameTime(level);
        controller.serverTick();
        completeAsyncLevelTick(level);

        assertThat(controller.isRuntimeActive()).isTrue();
    }

    @Test
    void valid_last_recipe_restarts_without_a_normal_candidate_search() {
        MachineControllerBlockEntity controller = normalController();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("async_normal_last_recipe"), MMCR.id("test_cube"), 20,
                List.of(), List.of());
        MachineRecipeThread thread = new MachineRecipeThread(controller);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);

        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1,
                controller.runtimeSnapshot().structure().version())).isEqualTo(RecipeThread.StartResult.STARTED);
        thread.markRecipeFinished();
        thread.runtime().invalidate();

        assertThat(thread.tryRestartLastRecipe(List.of(recipe), 1,
                controller.runtimeSnapshot().structure().version())).isEqualTo(RecipeThread.StartResult.STARTED);
        assertThat(thread.hasPendingAsyncSearch()).isFalse();
        assertThat(thread.runtime().active()).isTrue();
    }

    @Test
    void recipe_pool_change_discards_an_active_recipe_and_its_restart_memory() {
        MachineControllerBlockEntity controller = normalController();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("recipe_pool_active_discard"), MMCR.id("test_cube"),
                20, List.of(), List.of());
        MachineRecipeThread thread = new MachineRecipeThread(controller);
        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1,
                controller.runtimeSnapshot().structure().version())).isEqualTo(RecipeThread.StartResult.STARTED);
        thread.markRecipeFinished();

        thread.discardForRecipePoolChange();

        assertThat(thread.runtime().active()).isFalse();
        assertThat(thread.tryRestartLastRecipe(List.of(recipe), 1,
                controller.runtimeSnapshot().structure().version())).isEqualTo(RecipeThread.StartResult.NO_MATCH);
    }

    @Test
    void recipe_pool_change_discards_a_pending_recipe_start() {
        MachineControllerBlockEntity controller = normalController();
        ServerLevel level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("recipe_pool_pending_discard"), MMCR.id("test_cube"),
                20, List.of(), List.of());
        MachineRecipeThread thread = new MachineRecipeThread(controller);
        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1,
                controller.runtimeSnapshot().structure().version())).isEqualTo(RecipeThread.StartResult.PENDING);
        assertThat(thread.isStartPending()).isTrue();

        thread.requestFullSearch();
        thread.discardForRecipePoolChange();
        completeAsyncLevelTick(level);

        assertThat(thread.isStartPending()).isFalse();
        assertThat(thread.runtime().active()).isFalse();
        assertThat(thread.hasFullSearchPending()).isFalse();
    }

    private static MachineControllerBlockEntity normalController() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller,
                new DynamicMachine(MMCR.id("test_cube"), "async normal", new BlockArray(Map.of())));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        StructureClaimRegistry.get((ServerLevel) controller.getLevel()).release(controller.getBlockPos());
        return controller;
    }

    private static MachineControllerBlockEntity multiPoolController(ResourceLocation machineId, ResourceLocation... pools) {
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(machineId).recipePoolIds(List.of(pools)).build());
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        controller.setMachine(new DynamicMachine(machineId, "multi pool", new BlockArray(Map.of())));
        return controller;
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
}
