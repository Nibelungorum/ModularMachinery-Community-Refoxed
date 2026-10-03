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
import cn.howxu.mmcr.api.capability.facet.RecipeEnergyPrefetchFacet;
import cn.howxu.mmcr.api.capability.facet.TickFacet;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickContext;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickResult;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.compat.create.CreateFailureReasons;
import cn.howxu.mmcr.api.compat.create.StressFacet;
import cn.howxu.mmcr.api.compat.create.StressState;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.compat.create.CreateBridgeBootstrap;
import cn.howxu.mmcr.compat.create.StressRequirement;
import cn.howxu.mmcr.compat.create.StressSession;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.recipe.AsyncRequirementPlanner;
import cn.howxu.mmcr.internal.recipe.MachineRecipeThread;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import com.mojang.serialization.Codec;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.fml.config.IConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises real recipe runtimes and planning with in-memory native stress facets.
 *
 * @author howxu <dev@howxu.cn>
 */
class CraftingRuntimeStressTest {
    private RequirementHandlerRegistry.TestScope requirements;
    private Field currentServerField;
    private MinecraftServer previousServer;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        CommentedConfig config = CommentedConfig.inMemory();
        ServerConfig.SPEC.correct(config);
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        ServerConfig.SPEC.acceptConfig((IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null));
    }

    @BeforeEach
    void registerStress() throws ReflectiveOperationException {
        requirements = RequirementHandlerRegistry.openTestScope();
        RequirementHandlerRegistry.register(StressRequirement.TYPE);
        CreateBridgeBootstrap.installForTesting(() -> true);
        currentServerField = ServerLifecycleHooks.class.getDeclaredField("currentServer");
        currentServerField.setAccessible(true);
        previousServer = (MinecraftServer) currentServerField.get(null);
        // Same server identity/thread setup used by RecipeThreadTest; no server is started.
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        MinecraftServer server = (MinecraftServer) ((Unsafe) unsafeField.get(null)).allocateInstance(DedicatedServer.class);
        Field serverThread = MinecraftServer.class.getDeclaredField("serverThread");
        serverThread.setAccessible(true);
        serverThread.set(server, Thread.currentThread());
        currentServerField.set(null, server);
    }

    @AfterEach
    void cleanup() throws ReflectiveOperationException {
        try {
            requirements.close();
            CreateBridgeBootstrap.resetForTesting();
            RecipeRegistry.clearForTesting();
        } finally {
            if (currentServerField != null) currentServerField.set(null, previousServer);
        }
    }

    @Test
    void lastRecipeRestartDoesNotClearThePreviousTicksGeneration() {
        StressCapability input = new StressCapability(IOType.INPUT);
        StressCapability output = new StressCapability(IOType.OUTPUT);
        MachineControllerBlockEntity controller = asyncController(List.of(input, output));
        MachineRecipe recipe = recipe(2, List.of(stressInput(), stressOutput()));
        RecipeRegistry.registerStatic(recipe);
        controller.setFormed(true);
        MachineRecipeThread thread = new MachineRecipeThread(controller);
        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1,
                controller.currentRuntimeSnapshot().structure().version())).isTrue();
        completeAsyncTick(controller);
        thread.tick();
        completeAsyncTick(controller);
        StressSession owner = output.onlyOwner();
        for (int tick = 0; tick < 6; tick++) {
            RuntimeTestFixtures.advanceGameTime(controller.getLevel());
            thread.tick();
            completeAsyncTick(controller);
            assertThat(thread.runtime().active()).as("tick=%s failure=%s", tick, thread.runtime().failure()).isTrue();
            assertThat(output.onlyOwner()).isSameAs(owner);
            assertThat(output.baseStress()).isEqualTo(16D);
            assertThat(output.releases).isZero();
        }
        input.actualRpm = 0;
        RuntimeTestFixtures.advanceGameTime(controller.getLevel());
        thread.tick();
        completeAsyncTick(controller);
        assertThat(output.baseStress()).isZero();
    }

    @Test
    void exhaustedLastRecipeReleasesGenerationAtTheFinishDecision() {
        ItemInputBusBlockEntity items = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        items.itemHandler().setContents(0, new ItemStack(Items.IRON_INGOT), 1);
        StressCapability output = new StressCapability(IOType.OUTPUT);
        MachineControllerBlockEntity controller = asyncController(List.of(output), items);
        MachineRecipe recipe = recipe(2, List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY),
                stressOutput()));
        RecipeRegistry.registerStatic(recipe);
        controller.setFormed(true);
        MachineRecipeThread thread = new MachineRecipeThread(controller);
        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1,
                controller.currentRuntimeSnapshot().structure().version())).isTrue();
        completeAsyncTick(controller);
        thread.tick();
        completeAsyncTick(controller);
        assertThat(output.baseStress()).isEqualTo(16D);
        RuntimeTestFixtures.advanceGameTime(controller.getLevel());
        thread.tick();
        completeAsyncTick(controller);
        RuntimeTestFixtures.advanceGameTime(controller.getLevel());
        thread.tick();
        completeAsyncTick(controller);
        assertThat(thread.runtime().active()).isFalse();
        assertThat(output.baseStress()).isZero();
    }

    @Test
    void sameTickAsyncHandoffKeepsGenerationButPendingHandoffExpires() {
        for (boolean restart : List.of(false, true)) {
            StressCapability output = new StressCapability(IOType.OUTPUT);
            MachineControllerBlockEntity controller = controller(output);
            CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
            MachineRecipe recipe = recipe(1, List.of(stressOutput()));
            assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
            runtime.beginStressHandoff();
            assertThat(runTick(runtime, controller, true)).isTrue();
            assertThat(runtime.prepareAsyncFinish()).isTrue();
            runtime.finish();
            runtime.completeStressHandoff(true);
            assertThat(output.baseStress()).isEqualTo(16D);
            if (restart) {
                assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
                assertThat(output.releases).isZero();
            }
            LevelStub.setGameTime(controller.getLevel(), controller.getLevel().getGameTime() + 1);
            runtime.expireStressHandoff();
            assertThat(output.baseStress()).isEqualTo(restart ? 16D : 0D);
            runtime.invalidate();
        }
    }

    @Test
    void stalledAsyncFinishCannotRetainGenerationIndefinitely() {
        StressCapability output = new StressCapability(IOType.OUTPUT);
        MachineControllerBlockEntity controller = controller(output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(runtime.start(recipe(1, List.of(stressOutput())), 1).isCrafting()).isTrue();
        runtime.beginStressHandoff();
        assertThat(runTick(runtime, controller, true)).isTrue();
        assertThat(runtime.finishPending()).isTrue();
        LevelStub.setGameTime(controller.getLevel(), controller.getLevel().getGameTime() + 1);
        runtime.expireStressHandoff();
        assertThat(output.baseStress()).isEqualTo(16D);
        LevelStub.setGameTime(controller.getLevel(), controller.getLevel().getGameTime() + 1);
        runtime.expireStressHandoff();
        assertThat(output.baseStress()).isZero();
        assertThat(runtime.finishPending()).isTrue();
    }

    @Test
    void changedStressRequirementsCannotInheritPreviousGeneration() {
        StressCapability output = new StressCapability(IOType.OUTPUT);
        MachineControllerBlockEntity controller = controller(output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(runtime.start(recipe(1, List.of(stressOutput())), 1).isCrafting()).isTrue();
        runtime.beginStressHandoff();
        runtime.tick();
        runtime.finish();
        runtime.completeStressHandoff(true);
        assertThat(output.baseStress()).isEqualTo(16D);
        assertThat(runtime.start(recipe(20, List.of(StressRequirement.output(4, -32, List.of()))), 1)
                .isCrafting()).isTrue();
        assertThat(output.baseStress()).isZero();
        runtime.tick();
        assertThat(output.baseStress()).isEqualTo(4D);
    }

    @Test
    void consumedItemDoesNotRenumberPersistentStressOnSyncOrAsyncFallback() {
        ItemInputBusBlockEntity items = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemStack stack = new ItemStack(Items.IRON_INGOT);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        items.itemHandler().setContents(0, stack, 1);
        StressCapability input = new StressCapability(IOType.INPUT);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), items);
        attach(controller, input);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe(20, List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY),
                stressInput()));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        StressSession owner = input.onlyOwner();
        assertThat(input.indexes(owner)).containsExactly(1);
        runtime.tick();
        runtime.tick();
        assertThat(items.itemHandler().amount(0)).isZero();
        assertThat(input.indexes(owner)).containsExactly(1);
        assertThat(input.baseStress()).isEqualTo(8D);

        var prepared = runtime.prepareAsyncTickPlan(controller.currentRuntimeSnapshot());
        assertThat(prepared).isNotNull();
        assertThat(prepared.initialMainThreadRequirements()).contains(1);
        assertThat(runtime.commitAsyncTick(null)).isTrue();
        assertThat(runtime.completeAsyncTickAfterInputs()).isTrue();
        runtime.completeAsyncTickAfterRecipe();
        assertThat(input.indexes(owner)).containsExactly(1);
        assertThat(input.baseStress()).isEqualTo(8D);
    }

    @Test
    void lanesSharingPortsHaveIndependentOwnersAndCancellation() {
        StressCapability input = new StressCapability(IOType.INPUT);
        MachineControllerBlockEntity controller = controller(input);
        CraftingRuntime first = new CraftingRuntime(controller, controller.componentRuntime());
        CraftingRuntime second = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe(20, List.of(stressInput()));
        assertThat(first.start(recipe, 1).isCrafting()).isTrue();
        assertThat(second.start(recipe, 1).isCrafting()).isTrue();
        assertThat(input.contributions).hasSize(2);
        assertThat(input.baseStress()).isEqualTo(16D);

        first.invalidate();
        assertThat(input.contributions).hasSize(1);
        second.tick();
        assertThat(input.baseStress()).isEqualTo(8D);
        second.invalidate();
        assertThat(input.baseStress()).isZero();
    }

    @Test
    void outputListedFirstCannotSupplyPowerUntilEveryTickInputCommits() {
        StressCapability input = new StressCapability(IOType.INPUT);
        StressCapability output = new StressCapability(IOType.OUTPUT);
        EnergyInputHatchBlockEntity energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        energy.energyStorage().setAmount(100);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), energy);
        attach(controller, input, output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(runtime.start(recipe(20, List.of(stressOutput(), stressInput(), new EnergyRequirement(2))), 1)
                .isCrafting()).isTrue();
        assertThat(output.baseStress()).isZero();
        input.onApply = () -> energy.energyStorage().setAmount(0);

        runtime.tick();

        assertThat(runtime.failure()).isNotNull();
        assertThat(output.applyCalls).isZero();
        assertThat(output.baseStress()).isZero();
        assertThat(input.baseStress()).isZero();
    }

    @Test
    void successfulSyncAndAsyncTicksActivateOutputButPowerWaitRemovesIt() {
        StressCapability input = new StressCapability(IOType.INPUT);
        StressCapability output = new StressCapability(IOType.OUTPUT);
        MachineControllerBlockEntity controller = controller(input, output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(runtime.start(recipe(20, List.of(stressOutput(), stressInput())), 1).isCrafting()).isTrue();
        assertThat(output.baseStress()).isZero();
        runtime.tick();
        assertThat(output.baseStress()).isEqualTo(16D);
        input.actualRpm = 0;
        runtime.tick();
        assertThat(output.baseStress()).isZero();
        assertThat(input.baseStress()).isEqualTo(8D);

        input.actualRpm = 64;
        assertThat(runtime.prepareAsyncTickPlan(controller.currentRuntimeSnapshot())).isNotNull();
        assertThat(runtime.commitAsyncTick(null)).isTrue();
        assertThat(runtime.completeAsyncTickAfterInputs()).isTrue();
        runtime.completeAsyncTickAfterRecipe();
        assertThat(output.baseStress()).isEqualTo(16D);
        input.actualRpm = 16;
        runtime.tick();
        assertThat(runtime.failure().reason()).isEqualTo(CreateFailureReasons.INSUFFICIENT_RPM);
        assertThat(output.baseStress()).isZero();
        assertThat(input.baseStress()).isEqualTo(8D);
        input.actualRpm = 64;
        input.overstressed = true;
        assertThat(runtime.prepareAsyncTickPlan(controller.currentRuntimeSnapshot())).isNotNull();
        assertThat(runtime.commitAsyncTick(null)).isFalse();
        assertThat(output.baseStress()).isZero();
        assertThat(input.baseStress()).isEqualTo(8D);
    }

    @Test
    void createPlanningPowerWaitsReleaseOutputButKeepOldInput() {
        for (FailureReason reason : List.of(CreateFailureReasons.INSUFFICIENT_RPM,
                CreateFailureReasons.INSUFFICIENT_STRESS, CreateFailureReasons.MISSING_ROTATION)) {
            StressCapability input = new StressCapability(IOType.INPUT);
            StressCapability output = new StressCapability(IOType.OUTPUT);
            MachineControllerBlockEntity controller = controller(input, output);
            CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
            assertThat(runtime.start(recipe(20, List.of(stressInput(), stressOutput())), 1).isCrafting()).isTrue();
            runtime.tick();
            if (reason == CreateFailureReasons.INSUFFICIENT_RPM) input.actualRpm = 16;
            else if (reason == CreateFailureReasons.INSUFFICIENT_STRESS) input.overstressed = true;
            else input.actualRpm = 0;
            runtime.tick();
            assertThat(runtime.failure().reason()).isEqualTo(reason);
            assertThat(output.baseStress()).isZero();
            assertThat(input.baseStress()).isEqualTo(8D);
            runtime.invalidate();
        }
    }

    @Test
    void nonPowerCallbackWaitReleasesAllAndSuccessfulRetryReacquires() {
        StressCapability input = new StressCapability(IOType.INPUT);
        StressCapability output = new StressCapability(IOType.OUTPUT);
        MachineControllerBlockEntity controller = controller(input, output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(runtime.start(recipe(20, List.of(stressInput(), stressOutput())), 1).isCrafting()).isTrue();
        runtime.tick();
        input.tickFailure = BuiltinFailureReasons.RECIPE_BEHAVIOR;
        runtime.tick();
        assertThat(input.baseStress()).isZero();
        assertThat(output.baseStress()).isZero();
        input.tickFailure = null;
        runtime.tick();
        assertThat(input.baseStress()).isEqualTo(8D);
        assertThat(output.baseStress()).isEqualTo(16D);
    }

    @Test
    void pauseAndExplicitControllerCleanupReleaseAllWithoutCancellingRecipe() {
        StressCapability input = new StressCapability(IOType.INPUT);
        StressCapability output = new StressCapability(IOType.OUTPUT);
        MachineControllerBlockEntity controller = controller(input, output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(runtime.start(recipe(20, List.of(stressInput(), stressOutput())), 1).isCrafting()).isTrue();
        runtime.tick();
        runtime.pause();
        runtime.tick();
        assertThat(input.baseStress()).isZero();
        assertThat(output.baseStress()).isZero();
        assertThat(runtime.active()).isTrue();
        runtime.resume();
        runtime.tick();
        assertThat(output.baseStress()).isEqualTo(16D);
        runtime.releaseStressContributions();
        runtime.releaseStressContributions();
        assertThat(input.baseStress()).isZero();
        assertThat(output.baseStress()).isZero();
    }

    @Test
    void finishPendingAndBlockedPhysicalOutputsNeverKeepStressActive() {
        StressCapability input = new StressCapability(IOType.INPUT);
        StressCapability output = new StressCapability(IOType.OUTPUT);
        ItemOutputBusBlockEntity items = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), items);
        attach(controller, input, output);
        for (int slot = 0; slot < items.itemHandler().size(); slot++) {
            items.itemHandler().setContents(slot, new ItemStack(Items.COBBLESTONE, 64), 64);
        }
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("runtime_stress_blocked_output"), MMCR.id("test_cube"), 1,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(stressInput(), stressOutput(),
                        new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(Items.IRON_NUGGET))));
        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        runtime.tick();
        assertThat(runtime.finishPending()).isTrue();
        assertThat(input.baseStress()).isZero();
        assertThat(output.baseStress()).isZero();
        runtime.finish();
        assertThat(runtime.failure()).isNotNull();
        assertThat(runtime.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT);
        assertThat(runtime.active()).isTrue();
        assertThat(runtime.finishPending()).isTrue();
        assertThat(input.baseStress()).isZero();
        assertThat(output.baseStress()).isZero();
        items.itemHandler().setContents(0, ItemStack.EMPTY, 0);
        LevelStub.setGameTime(controller.getLevel(), controller.getLevel().getGameTime() + 20);
        assertThat(runtime.shouldRetryFinish()).isTrue();
        runtime.finish();
        assertThat(runtime.active()).isFalse();
        assertThat(runtime.failure()).isNull();
        assertThat(items.itemHandler().getStackInSlot(0).is(Items.IRON_NUGGET)).isTrue();
        assertThat(items.itemHandler().amount(0)).isEqualTo(1);
        assertThat(input.baseStress()).isZero();
        assertThat(output.baseStress()).isZero();
    }

    @Test
    void asyncFinishTransitionAndCompletionReleaseAll() {
        StressCapability input = new StressCapability(IOType.INPUT);
        StressCapability output = new StressCapability(IOType.OUTPUT);
        MachineControllerBlockEntity controller = controller(input, output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(runtime.start(recipe(1, List.of(stressInput(), stressOutput())), 1).isCrafting()).isTrue();
        assertThat(runtime.prepareAsyncTickPlan(controller.currentRuntimeSnapshot())).isNotNull();
        assertThat(runtime.commitAsyncTick(null)).isTrue();
        assertThat(runtime.completeAsyncTickAfterInputs()).isTrue();
        runtime.completeAsyncTickAfterRecipe();
        assertThat(runtime.finishPending()).isTrue();
        assertThat(input.baseStress()).isZero();
        assertThat(output.baseStress()).isZero();
        runtime.finish();
        assertThat(runtime.active()).isFalse();
        assertThat(output.applyCalls).isEqualTo(1);
    }

    @Test
    void componentReplacementReleasesTrackedOldFacetsOnInvalidationAndRebind() {
        for (boolean rebind : List.of(false, true)) {
            StressCapability oldInput = new StressCapability(IOType.INPUT);
            StressCapability oldOutput = new StressCapability(IOType.OUTPUT);
            MachineControllerBlockEntity controller = controller(oldInput, oldOutput);
            CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
            assertThat(runtime.start(recipe(20, List.of(stressInput(), stressOutput())), 1).isCrafting()).isTrue();
            runtime.tick();
            controller.componentRuntime().replaceComponents(List.of());
            RuntimeTestFixtures.republish(controller);
            if (rebind) runtime.rebindCurrentVersions();
            else runtime.tick();
            assertThat(oldInput.baseStress()).isZero();
            assertThat(oldOutput.baseStress()).isZero();
        }
    }

    @Test
    void failedPartialStartAndOutputCommitLeaveNoContributions() {
        StressCapability input = new StressCapability(IOType.INPUT);
        MachineControllerBlockEntity controller = controller(input);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        input.failOnApply = 2;
        assertThat(runtime.start(recipe(20, List.of(stressInput(), stressInput())), 1).isCrafting()).isFalse();
        assertThat(runtime.active()).isFalse();
        assertThat(input.baseStress()).isZero();

        StressCapability output = new StressCapability(IOType.OUTPUT);
        controller = controller(output);
        runtime = new CraftingRuntime(controller, controller.componentRuntime());
        output.failOnApply = 2;
        assertThat(runtime.start(recipe(20, List.of(stressOutput(), stressOutput())), 1).isCrafting()).isTrue();
        runtime.tick();
        assertThat(runtime.failure()).isNotNull();
        assertThat(output.baseStress()).isZero();
    }

    @Test
    void prefetchFilteringPreservesOriginalStressIndexAndFailureCannotActivateOutput() {
        StressCapability input = new StressCapability(IOType.INPUT);
        StressCapability output = new StressCapability(IOType.OUTPUT);
        PrefetchCapability prefetch = new PrefetchCapability();
        MachineControllerBlockEntity controller = controller(prefetch, input, output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe(20, List.of(new EnergyRequirement(2), stressInput(), stressOutput()));
        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        StressSession owner = input.onlyOwner();
        assertThat(input.indexes(owner)).containsExactly(1);
        runtime.tick();
        assertThat(input.indexes(owner)).containsExactly(1);
        assertThat(output.indexes(owner)).containsExactly(2);
        runtime.invalidate();
        prefetch.failCommit = true;
        assertThat(runtime.start(recipe, 1).isCrafting()).isFalse();
        assertThat(input.baseStress()).isZero();
        assertThat(output.baseStress()).isZero();
    }

    @Test
    void ownerBoundContextKeepsIndexesAndAsyncDescriptorsNeverTouchNativeState() {
        StressCapability input = new StressCapability(IOType.INPUT);
        StressSession owner = new StressSession();
        CraftingContext context = new CraftingContext(new CapabilitySnapshot(List.of(input)))
                .withReservationOwner(owner);
        var prepared = context.planAsync(List.of(stressInput()), 1, List.of(3));
        assertThat(prepared.initialMainThreadRequirements()).containsExactly(3);
        assertThat(input.applyCalls).isZero();
        var plan = context.planRequirements(List.of(stressInput()), 1, Map.of(), List.of(3));
        assertThat(plan.successful()).isTrue();
        assertThat(plan.plan().commit()).isTrue();
        assertThat(input.indexes(owner)).containsExactly(3);
        owner.releaseAll();
    }

    @Test
    void factoryPauseCancelAndClearReleaseEveryRealLane() {
        StressCapability input = new StressCapability(IOType.INPUT);
        StressCapability output = new StressCapability(IOType.OUTPUT);
        MachineControllerBlockEntity controller = controller(input, output);
        FactoryRuntime factory = new FactoryRuntime();
        factory.ensureBaseLane(controller);
        factory.setLaneLimit(2);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("runtime_stress_factory"), MMCR.id("test_cube"), 20,
                List.of(stressInput(), stressOutput()), List.of(), List.of(), 0, 2, false, false, false, Set.of());
        List<FactoryRuntime.PatternLane> lanes = factory.reservePatternStarts(recipe, 2, List.of());
        assertThat(lanes).hasSize(2);
        for (FactoryRuntime.PatternLane lane : lanes) {
            assertThat(lane.runtime().commitPatternStart(lane.preparedStart())).isTrue();
            lane.runtime().tick();
        }
        assertThat(output.baseStress()).isEqualTo(32D);
        factory.pause();
        assertThat(input.baseStress()).isZero();
        assertThat(output.baseStress()).isZero();
        factory.resume();
        factory.activeRuntimes().forEach(CraftingRuntime::tick);
        assertThat(output.baseStress()).isEqualTo(32D);
        factory.cancelAsyncState();
        assertThat(input.baseStress()).isZero();
        assertThat(output.baseStress()).isZero();
        factory.activeRuntimes().forEach(CraftingRuntime::tick);
        factory.releaseStressContributions();
        assertThat(input.baseStress()).isZero();
        assertThat(output.baseStress()).isZero();
        factory.activeRuntimes().forEach(CraftingRuntime::tick);
        factory.clear();
        assertThat(input.baseStress()).isZero();
        assertThat(output.baseStress()).isZero();
        assertThat(factory.activeRuntimes()).isEmpty();
    }

    @Test
    void discardedAsyncPreparationCleansCancellationButKeepsPowerWaitInput() {
        StressCapability input = new StressCapability(IOType.INPUT);
        StressCapability output = new StressCapability(IOType.OUTPUT);
        MachineControllerBlockEntity controller = controller(input, output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(runtime.start(recipe(20, List.of(stressInput(), stressOutput())), 1).isCrafting()).isTrue();
        runtime.tick();
        assertThat(runtime.prepareAsyncTickPlan(controller.currentRuntimeSnapshot())).isNotNull();
        runtime.discardAsyncTickPreparation();
        assertThat(input.baseStress()).isZero();
        assertThat(output.baseStress()).isZero();
        runtime.tick();
        input.actualRpm = 0;
        assertThat(runtime.prepareAsyncTickPlan(controller.currentRuntimeSnapshot())).isNotNull();
        assertThat(runtime.commitAsyncTick(null)).isFalse();
        runtime.discardAsyncTickPreparation();
        assertThat(input.baseStress()).isEqualTo(8D);
        assertThat(output.baseStress()).isZero();
        input.actualRpm = 64;
        assertThat(runtime.prepareAsyncTickPlan(controller.currentRuntimeSnapshot())).isNotNull();
        runtime.discardAsyncTickPreparation();
        assertThat(input.baseStress()).isZero();
    }

    @Test
    void pooledContextResetRemovesPreviousLaneOwner() throws Exception {
        StressCapability input = new StressCapability(IOType.INPUT);
        CraftingContext context = new CraftingContext(new CapabilitySnapshot(List.of(input)))
                .withReservationOwner(new StressSession());
        var reset = CraftingContext.class.getDeclaredMethod("resetFor", CapabilitySnapshot.class, List.class);
        reset.setAccessible(true);
        reset.invoke(context, new CapabilitySnapshot(List.of(input)), List.of());
        var result = context.planRequirements(List.of(stressInput()), 1, Map.of());
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().commit()).isFalse();
        assertThat(result.plan().failure().reason()).isEqualTo(CreateFailureReasons.MISSING_RECIPE_SCOPE);
        assertThat(input.baseStress()).isZero();
    }

    @Test
    void cancellingRecipeOnPowerFailureReleasesInputAsWell() {
        StressCapability input = new StressCapability(IOType.INPUT);
        StressCapability output = new StressCapability(IOType.OUTPUT);
        MachineControllerBlockEntity controller = controller(input, output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = new MachineRecipe(MMCR.id("runtime_stress_cancel"), MMCR.id("test_cube"), 20,
                List.of(stressInput(), stressOutput()), List.of(), List.of(), 0, 1, true, false, false, Set.of());
        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        runtime.tick();
        input.actualRpm = 0;
        runtime.tick();
        assertThat(runtime.active()).isFalse();
        assertThat(input.baseStress()).isZero();
        assertThat(output.baseStress()).isZero();
    }

    @Test
    void restoringAnActiveRecipeReleasesOldContributionsUntilTheNextRunnableTick() {
        StressCapability input = new StressCapability(IOType.INPUT);
        StressCapability output = new StressCapability(IOType.OUTPUT);
        MachineControllerBlockEntity controller = controller(input, output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(runtime.start(recipe(20, List.of(stressInput(), stressOutput())), 1).isCrafting()).isTrue();
        runtime.tick();
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        runtime.restore(runtime.activeRecipe(), null, snapshot.structure().version(), snapshot.capabilityVersion(),
                snapshot.modifierVersion(), snapshot.stateVersion());
        assertThat(runtime.active()).isTrue();
        assertThat(input.baseStress()).isZero();
        assertThat(output.baseStress()).isZero();
        runtime.tick();
        assertThat(input.baseStress()).isEqualTo(8D);
        assertThat(output.baseStress()).isEqualTo(16D);
    }

    @Test
    void throwingOutputCommitCleansEvenTheContributionAppliedBeforeTheException() {
        StressCapability input = new StressCapability(IOType.INPUT);
        StressCapability output = new StressCapability(IOType.OUTPUT);
        MachineControllerBlockEntity controller = controller(input, output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(runtime.start(recipe(20, List.of(stressInput(), stressOutput())), 1).isCrafting()).isTrue();
        output.onApply = () -> { throw new IllegalStateException("expected native commit failure"); };
        runtime.tick();
        assertThat(runtime.failure()).isNotNull();
        assertThat(input.baseStress()).isZero();
        assertThat(output.baseStress()).isZero();
    }

    @Test
    void realFeShortageReleasesAllStressAndRecoveryReacquiresOnSyncAndAsyncFallback() {
        for (boolean async : List.of(false, true)) {
            StressCapability input = new StressCapability(IOType.INPUT);
            StressCapability output = new StressCapability(IOType.OUTPUT);
            EnergyInputHatchBlockEntity energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
            energy.energyStorage().setAmount(100);
            MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), energy);
            attach(controller, input, output);
            CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
            assertThat(runtime.start(recipe(20, List.of(stressInput(), new EnergyRequirement(2), stressOutput())), 1)
                    .isCrafting()).isTrue();
            assertThat(runTick(runtime, controller, async)).isTrue();
            assertThat(output.baseStress()).isEqualTo(16D);
            energy.energyStorage().setAmount(0);
            assertThat(runTick(runtime, controller, async)).isFalse();
            assertThat(runtime.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_ENERGY);
            assertThat(input.baseStress()).isZero();
            assertThat(output.baseStress()).isZero();
            assertThat(runtime.active()).isTrue();
            energy.energyStorage().setAmount(100);
            assertThat(runTick(runtime, controller, async)).isTrue();
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(98);
            assertThat(input.baseStress()).isEqualTo(8D);
            assertThat(output.baseStress()).isEqualTo(16D);
            runtime.invalidate();
        }
    }

    @Test
    void candidatePrefetchFilteringPreservesStressFailureAndOutputFeasibilityIndexes() {
        StressCapability input = new StressCapability(IOType.INPUT);
        StressCapability output = new StressCapability(IOType.OUTPUT);
        PrefetchCapability prefetch = new PrefetchCapability();
        CraftingContext context = new CraftingContext(new CapabilitySnapshot(List.of(prefetch, input, output)));
        MachineRecipe recipe = recipe(20, List.of(new EnergyRequirement(2), stressInput(), stressOutput()));
        input.actualRpm = 16;
        var result = context.planStartResult(recipe, 1);
        assertThat(result.successful()).isFalse();
        assertThat(result.failureRequirementIndex()).isEqualTo(1);
        assertThat(result.failure().failure().trace().frames().getFirst().requirementIndex()).isEqualTo(1);
        assertThat(result.failure().reason()).isEqualTo(CreateFailureReasons.INSUFFICIENT_RPM);
        input.actualRpm = 64;
        result = context.planStartResult(recipe, 1);
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().requirements()).extracting(RequirementPlan::requirementIndex).containsExactly(1, 2);
        assertThat(input.baseStress()).isZero();
        assertThat(output.baseStress()).isZero();
        MachineRecipe blockedOutput = recipe(20, List.of(new EnergyRequirement(2), stressInput(),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(Items.IRON_NUGGET))));
        result = context.planStartResult(blockedOutput, 1);
        assertThat(result.successful()).isFalse();
        assertThat(result.failureRequirementIndex()).isEqualTo(2);
        assertThat(result.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT);
        MachineRecipe consumedItem = recipe(20, List.of(new EnergyRequirement(2),
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY),
                stressInput()));
        result = context.planInputs(consumedItem, 1, Set.of(1), Set.of());
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().requirements()).extracting(RequirementPlan::requirementIndex).containsExactly(2);
        prefetch.unavailable = true;
        result = context.planStartResult(recipe, 1);
        assertThat(result.successful()).isFalse();
        assertThat(result.failureRequirementIndex()).isZero();
    }

    @Test
    void resumedTickPartialInputCommitIsClearedWithoutAffectingAnotherLane() {
        for (boolean async : List.of(false, true)) assertPartialInputCommitCleanup(async, false);
    }

    @Test
    void migratedTickPartialInputCommitIsClearedWithoutAffectingAnotherLane() {
        for (boolean async : List.of(false, true)) assertPartialInputCommitCleanup(async, true);
    }

    private static void assertPartialInputCommitCleanup(boolean async, boolean migrate) {
        if (RequirementHandlerRegistry.typeFor(CommitHookRequirement.TYPE.id()) == null) {
            RequirementHandlerRegistry.register(CommitHookRequirement.TYPE);
        }
        StressCapability oldInput = new StressCapability(IOType.INPUT, List.of("first"));
        StressCapability newInput = new StressCapability(IOType.INPUT, List.of("first"));
        StressCapability secondInput = new StressCapability(IOType.INPUT, List.of("second"));
        StressCapability output = new StressCapability(IOType.OUTPUT);
        newInput.actualRpm = 0;
        CommitHookCapability hook = new CommitHookCapability();
        ItemInputBusBlockEntity items = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemStack stack = new ItemStack(Items.IRON_INGOT, 2);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        items.itemHandler().setContents(0, stack, 2);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), items);
        attach(controller, oldInput, newInput, secondInput, output, hook);
        StressRequirement first = StressRequirement.input(8, 32, List.of("first"));
        StressRequirement second = StressRequirement.input(8, 32, List.of("second"));
        CraftingRuntime otherLane = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(otherLane.start(recipe(20, List.of(first)), 1).isCrafting()).isTrue();
        StressSession otherOwner = oldInput.onlyOwner();
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe(20, List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY),
                first, new CommitHookRequirement(RecipeModifier.IOType.INPUT), second, stressOutput()));
        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        StressSession owner = secondInput.onlyOwner();
        assertThat(runTick(runtime, controller, async)).isTrue();
        if (migrate) {
            oldInput.actualRpm = 0;
            newInput.actualRpm = 64;
        } else {
            runtime.pause();
            runtime.resume();
        }
        int firstApplies = (migrate ? newInput : oldInput).applyCalls;
        hook.onCommit = () -> {
            items.itemHandler().extractItem(0, 1, false);
            secondInput.actualRpm = 0;
        };
        assertThat(runTick(runtime, controller, async)).isFalse();
        assertThat(runtime.failure().reason()).isEqualTo(CreateFailureReasons.MISSING_ROTATION);
        assertThat((migrate ? newInput : oldInput).applyCalls).isEqualTo(firstApplies + 1);
        assertThat(oldInput.indexes(owner)).isEmpty();
        assertThat(newInput.indexes(owner)).isEmpty();
        assertThat(secondInput.indexes(owner)).isEmpty();
        assertThat(output.indexes(owner)).isEmpty();
        assertThat(oldInput.indexes(otherOwner)).containsExactly(0);
        assertThat(oldInput.baseStress()).isEqualTo(8D);
        assertThat(newInput.baseStress()).isZero();
        assertThat(secondInput.baseStress()).isZero();
        assertThat(output.baseStress()).isZero();
        assertThat(items.itemHandler().amount(0)).isZero();
        hook.onCommit = () -> { };
        oldInput.actualRpm = 64;
        newInput.actualRpm = 0;
        secondInput.actualRpm = 64;
        assertThat(runTick(runtime, controller, async)).isTrue();
        assertThat(oldInput.indexes(owner)).containsExactly(1);
        assertThat(secondInput.indexes(owner)).containsExactly(3);
        assertThat(output.indexes(owner)).containsExactly(4);
        assertThat(oldInput.indexes(otherOwner)).containsExactly(0);
        assertThat(oldInput.baseStress()).isEqualTo(16D);
        runtime.invalidate();
        otherLane.invalidate();
    }

    private static boolean runTick(CraftingRuntime runtime, MachineControllerBlockEntity controller, boolean async) {
        if (!async) return runtime.tick().isCrafting();
        var prepared = runtime.prepareAsyncTickPlan(controller.currentRuntimeSnapshot());
        assertThat(prepared).isNotNull();
        var planned = new AsyncRequirementPlanner.PlanResult(List.of(), prepared.initialMainThreadRequirements());
        if (!runtime.commitAsyncTick(planned)) {
            runtime.discardAsyncTickPreparation();
            return false;
        }
        if (!runtime.completeAsyncTickAfterInputs()) return false;
        return runtime.completeAsyncTickAfterRecipe().isCrafting();
    }

    private static StressRequirement stressInput() {
        return StressRequirement.input(8, 32, List.of());
    }

    private static StressRequirement stressOutput() {
        return StressRequirement.output(16, 64, List.of());
    }

    private static MachineRecipe recipe(int duration, List<MachineRequirement> requirements) {
        return new MachineRecipe(MMCR.id("runtime_stress"), MMCR.id("test_cube"), duration,
                requirements, List.of(), List.of(), 0, 1, false, false, false, Set.of());
    }

    private static MachineControllerBlockEntity controller(MachineCapability... capabilities) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        attach(controller, capabilities);
        return controller;
    }

    private static MachineControllerBlockEntity asyncController(List<MachineCapability> capabilities,
                                                                IOPortBlockEntity... ports) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructureWithComponents(controller,
                new DynamicMachine(MMCR.id("test_cube"), "async stress", new BlockArray(Map.of())), ports);
        List<ProcessingComponent> components = new ArrayList<>(controller.componentRuntime().components());
        for (IOPortBlockEntity port : ports) {
            components.add(new ProcessingComponent(port.provideComponent(), port,
                    port.getBlockPos(), port.getBlockPos(), (String) null));
        }
        controller.componentRuntime().replaceComponents(components);
        attach(controller, capabilities.toArray(MachineCapability[]::new));
        ServerLevel level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        assertThat(controller.activeWorkMode()).isEqualTo(MachineWorkMode.ASYNC);
        return controller;
    }

    private static void completeAsyncTick(MachineControllerBlockEntity controller) {
        ServerLevel level = (ServerLevel) controller.getLevel();
        SharedIoCoordinator sharedIo = SharedIoCoordinator.get(level);
        MachineAsyncCoordinator async = MachineAsyncCoordinator.get(level);
        sharedIo.beginLevelTick(level.getGameTime());
        async.beginLevelTick(level.getGameTime());
        sharedIo.resolve(level);
        async.completeUntilIdleForTesting(() -> sharedIo.resolve(level));
        MachineControllerBlockEntity.flushQueuedAsyncRuntimeState(level);
    }

    private static void attach(MachineControllerBlockEntity controller, MachineCapability... capabilities) {
        List<ProcessingComponent> components = new ArrayList<>(controller.componentRuntime().components());
        for (MachineCapability capability : capabilities) {
            components.add(new ProcessingComponent(null, new CapabilityOwner(capability), BlockPos.ZERO,
                    BlockPos.ZERO, (String) null));
        }
        controller.componentRuntime().replaceComponents(components);
        RuntimeTestFixtures.republish(controller);
    }

    private static ExecutionStatus blocked(FailureReason reason) {
        return ExecutionStatus.blocked(MMCR.id("stress_test"), StressRequirement.TYPE.id(),
                FailureOccurrence.at(reason, StressRequirement.TYPE.id(), FailurePhase.PER_TICK,
                        null, null, Map.of()));
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class CapabilityOwner extends BlockEntity implements CapabilityHost {
        private final CapabilitySnapshot snapshot;

        private CapabilityOwner(MachineCapability capability) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), BlockPos.ZERO,
                    ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
            snapshot = new CapabilitySnapshot(List.of(capability));
        }

        @Override public CapabilitySnapshot capabilitySnapshot() { return snapshot; }
    }

    /** @author howxu <dev@howxu.cn> */
    private record Contribution(double baseStress, double rpm) { }

    /** Native-side memory fixture; lifecycle and planner remain production implementations.
     * @author howxu <dev@howxu.cn>
     */
    private static final class StressCapability implements MachineCapability, CapabilityView, StressFacet, TickFacet {
        private final CapabilityDirections directions;
        private final List<String> tags;
        private final Map<StressSession, Map<Integer, Contribution>> contributions = new IdentityHashMap<>();
        private double actualRpm = 64;
        private boolean overstressed;
        private int applyCalls;
        private int releases;
        private int failOnApply = -1;
        private Runnable onApply = () -> { };
        private FailureReason tickFailure;

        private StressCapability(IOType direction) { this(direction, List.of()); }
        private StressCapability(IOType direction, List<String> tags) {
            directions = CapabilityDirections.of(direction);
            this.tags = List.copyOf(tags);
        }
        @Override public CapabilityType type() { return new CapabilityType(StressRequirement.TYPE.id()); }
        @Override public CapabilityDirections directions() { return directions; }
        @Override public IOType ioType() { return directions.values().iterator().next(); }
        @Override public List<String> tags() { return tags; }
        @Override public CapabilityView view() { return this; }
        @Override public Set<Class<? extends CapabilityFacet>> facets() { return Set.of(StressFacet.class, TickFacet.class); }
        @Override public CapabilityTickResult plan(CapabilityTickContext context) {
            return tickFailure == null ? CapabilityTickResult.empty()
                    : new CapabilityTickResult(List.of(), blocked(tickFailure), false);
        }
        @Override public CapabilityOperation prepare(CapabilityRequest request) { throw new UnsupportedOperationException(); }
        @Override public Object networkIdentity() { return this; }
        @Override public StressState state() {
            return new StressState(BlockPos.ZERO, actualRpm, 64, 0, baseStress(),
                    baseStress() * 64, 1_000_000, baseStress() * 64, true, overstressed);
        }
        @Override public boolean stressEnabled() { return true; }
        @Override public boolean acceptsGeneratedRpm(StressSession session, int index, double rpm) { return true; }
        @Override public double ownedActualStress(StressSession session, int index) {
            Contribution contribution = contributions.getOrDefault(session, Map.of()).get(index);
            return contribution == null ? 0 : contribution.baseStress() * 64;
        }
        @Override public CapabilityResult apply(StressSession session, int index, double baseStress, double rpm) {
            applyCalls++;
            if (applyCalls == failOnApply) return CapabilityResult.failure(blocked(BuiltinFailureReasons.PER_TICK));
            contributions.computeIfAbsent(session, ignored -> new LinkedHashMap<>())
                    .put(index, new Contribution(baseStress, rpm));
            onApply.run();
            return CapabilityResult.successful();
        }
        @Override public void release(StressSession session) {
            if (contributions.remove(session) != null) releases++;
        }
        @Override public void release(StressSession session, int index) {
            Map<Integer, Contribution> owned = contributions.get(session);
            if (owned == null) return;
            if (owned.remove(index) != null) releases++;
            if (owned.isEmpty()) contributions.remove(session);
        }
        private double baseStress() {
            return contributions.values().stream().flatMap(values -> values.values().stream())
                    .mapToDouble(Contribution::baseStress).sum();
        }
        private StressSession onlyOwner() { return contributions.keySet().iterator().next(); }
        private Set<Integer> indexes(StressSession owner) { return contributions.getOrDefault(owner, Map.of()).keySet(); }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class PrefetchCapability implements MachineCapability, CapabilityView, RecipeEnergyPrefetchFacet {
        private boolean failCommit;
        private boolean unavailable;
        @Override public CapabilityType type() { return new CapabilityType(MMCR.id("stress_test_prefetch")); }
        @Override public CapabilityDirections directions() { return CapabilityDirections.input(); }
        @Override public IOType ioType() { return IOType.INPUT; }
        @Override public CapabilityView view() { return this; }
        @Override public Set<Class<? extends CapabilityFacet>> facets() { return Set.of(RecipeEnergyPrefetchFacet.class); }
        @Override public CapabilityOperation prepare(CapabilityRequest request) { throw new UnsupportedOperationException(); }
        @Override public String reservationKey() { return "stress_test:prefetch"; }
        @Override public Optional<PrefetchPlan> planPrefetch(long amount) {
            if (unavailable) return Optional.empty();
            return Optional.of(new PrefetchPlan(amount, () -> failCommit
                    ? CapabilityResult.failure(blocked(BuiltinFailureReasons.MISSING_ENERGY))
                    : CapabilityResult.successful()));
        }
        @Override public void restoreReservation(long amount) { }
        @Override public long releaseReservation(long amount) { return amount; }
    }

    /** A real extension operation runs between two stress requirements.
     * @author howxu <dev@howxu.cn>
     */
    private record CommitHookRequirement(RecipeModifier.IOType io) implements MachineRequirement {
        private static final MapCodec<CommitHookRequirement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(value -> "mmcr_test:stress_commit_hook"),
                RecipeModifier.IO_TYPE_CODEC.fieldOf("io").forGetter(CommitHookRequirement::io)
        ).apply(instance, (ignored, io) -> new CommitHookRequirement(io)));
        private static final RequirementType<CommitHookRequirement> TYPE = new RequirementType.Definition<>(
                ResourceLocation.fromNamespaceAndPath("mmcr_test", "stress_commit_hook"), CODEC,
                (requirement, capabilities, context) -> {
                    CommitHookCapability capability = (CommitHookCapability) capabilities.getFirst();
                    return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(),
                            List.of(() -> {
                                capability.onCommit.run();
                                return CapabilityResult.successful();
                            }), null);
                });
        @Override public RequirementType<CommitHookRequirement> type() { return TYPE; }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class CommitHookCapability implements MachineCapability {
        private Runnable onCommit = () -> { };
        @Override public CapabilityType type() { return new CapabilityType(CommitHookRequirement.TYPE.id()); }
        @Override public CapabilityDirections directions() { return CapabilityDirections.input(); }
        @Override public CapabilityView view() {
            return new CapabilityView() {
                @Override public CapabilityType type() { return CommitHookCapability.this.type(); }
                @Override public CapabilityDirections directions() { return CapabilityDirections.input(); }
            };
        }
        @Override public CapabilityOperation prepare(CapabilityRequest request) { throw new UnsupportedOperationException(); }
    }
}
