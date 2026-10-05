package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.RecipeEnergyPrefetchFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.compat.pneumaticcraft.AirState;
import cn.howxu.mmcr.api.compat.pneumaticcraft.PneumaticAirFacet;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.compat.pneumaticcraft.AirFailureReasons;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticCraftBridge;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticCraftBridgeBootstrap;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticIds;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticRecipeTypes;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises production air planning and recipe lifecycle with neutral, counted storage facets.
 *
 * @author howxu <dev@howxu.cn>
 */
class CraftingRuntimeAirTest {
    private RequirementHandlerRegistry.TestScope requirementScope;
    private Field currentServerField;
    private MinecraftServer previousServer;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void registerAir() throws ReflectiveOperationException {
        requirementScope = RequirementHandlerRegistry.openTestScope();
        PneumaticRecipeTypes.register();
        PneumaticCraftBridgeBootstrap.installForTesting(new AvailableBridge());
        currentServerField = ServerLifecycleHooks.class.getDeclaredField("currentServer");
        currentServerField.setAccessible(true);
        previousServer = (MinecraftServer) currentServerField.get(null);
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
            RecipeRegistry.clearForTesting();
            PneumaticCraftBridgeBootstrap.resetForTesting();
            requirementScope.close();
        } finally {
            if (currentServerField != null) currentServerField.set(null, previousServer);
        }
    }

    @Test
    void startupAndFinishDoNotMoveAirAndEverySuccessfulTickMovesExactlyOneBatch() {
        for (int duration : List.of(1, 3)) {
            AirCapability input = new AirCapability(IOType.INPUT, 480);
            AirCapability output = new AirCapability(IOType.OUTPUT, 0);
            CraftingRuntime runtime = runtime(controller(input, output));
            MachineRecipe recipe = recipe("exact_" + duration, duration,
                    List.of(AirRequirement.input(40, 4F), AirRequirement.output(80)));

            assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
            assertThat(input.air).isEqualTo(480);
            assertThat(output.air).isZero();
            assertThat(input.applyCalls).isZero();
            assertThat(input.validateCalls).isPositive();
            assertThat(runtime.activeRecipe().inputConsumptionPlan().consumedInputBatches()).containsExactly(0, 0);
            for (int step = 1; step <= duration; step++) {
                assertThat(runtime.tick().isCrafting()).isTrue();
                assertThat(input.air).isEqualTo(480 - step * 40);
                assertThat(output.air).isEqualTo(step * 80);
            }
            // The final debit crosses below 4 bar in the duration=3 case.
            assertThat(runtime.finishPending()).isTrue();
            int validations = input.validateCalls;
            runtime.tick();
            runtime.finish();
            runtime.finish();
            runtime.tick();
            assertThat(runtime.active()).isFalse();
            assertThat(input.validateCalls).isEqualTo(validations);
            assertThat(input.applyCalls).isEqualTo(duration);
            assertThat(output.applyCalls).isEqualTo(duration);
            assertThat(input.air).isEqualTo(480 - duration * 40);
            assertThat(output.air).isEqualTo(duration * 80);
        }
    }

    @Test
    void pressureWaitPreservesProgressAndBothStoresUntilRefilled() {
        AirCapability input = new AirCapability(IOType.INPUT, 400);
        AirCapability output = new AirCapability(IOType.OUTPUT, 0);
        CraftingRuntime runtime = runtime(controller(input, output));
        assertThat(runtime.start(recipe("pressure_wait", 3,
                List.of(AirRequirement.input(40, 4F), AirRequirement.output(80))), 1).isCrafting()).isTrue();
        runtime.tick();
        runtime.tick();
        assertThat(runtime.failure().reason()).isEqualTo(AirFailureReasons.INSUFFICIENT_PRESSURE);
        assertThat(runtime.tickCount()).isEqualTo(1);
        assertThat(input.air).isEqualTo(360);
        assertThat(output.air).isEqualTo(80);
        input.air = 440;
        runtime.tick();
        runtime.tick();
        runtime.finish();
        assertThat(runtime.active()).isFalse();
        assertThat(input.air).isEqualTo(360);
        assertThat(output.air).isEqualTo(240);
        assertThat(input.applyCalls).isEqualTo(3);
    }

    @Test
    void pressureOnlyInputIsContinuousAndRevalidatedAtCommitEvenWithZeroRate() {
        AirCapability input = new AirCapability(IOType.INPUT, 400);
        AirCapability output = new AirCapability(IOType.OUTPUT, 0);
        PrefetchCapability prefetch = new PrefetchCapability(6);
        CraftingRuntime runtime = runtime(controller(prefetch, input, output));
        assertThat(runtime.start(recipe("pressure_only", 3,
                List.of(new EnergyRequirement(2), AirRequirement.input(0, 4F), AirRequirement.output(80))), 1)
                .isCrafting()).isTrue();
        runtime.tick();
        assertThat(input.air).isEqualTo(400);
        assertThat(output.air).isEqualTo(80);
        // Production runtime consumes prefetch after simulation and before committing the air operation.
        prefetch.onConsume = () -> input.air = 399;
        runtime.tick();
        assertThat(runtime.failure().reason()).isEqualTo(AirFailureReasons.INSUFFICIENT_PRESSURE);
        assertThat(runtime.tickCount()).isEqualTo(1);
        assertThat(output.air).isEqualTo(80);
        runtime.tick();
        assertThat(runtime.tickCount()).isEqualTo(1);
        assertThat(output.air).isEqualTo(80);
        prefetch.onConsume = () -> { };
        input.air = 400;
        runtime.tick();
        runtime.tick();
        runtime.finish();
        assertThat(runtime.active()).isFalse();
        assertThat(input.air).isEqualTo(400);
        assertThat(output.air).isEqualTo(240);
        assertThat(prefetch.consumed).isEqualTo(6);
    }

    @Test
    void startupExclusionUsesOriginalIndexesWithItemAndOrdinaryEnergy() {
        var items = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemStack stack = new ItemStack(Items.IRON_INGOT, 2);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        items.itemHandler().setContents(0, stack, 2);
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(2, 0, 0));
        energy.energyStorage().setAmount(100);
        AirCapability input = new AirCapability(IOType.INPUT, 480);
        AirCapability output = new AirCapability(IOType.OUTPUT, 0);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), items, energy);
        attach(controller, input, output);
        CraftingRuntime runtime = runtime(controller);
        // Output index 0 is omitted by startup input planning; air's original index is 3, not 2.
        assertThat(runtime.start(recipe("mixed_fe", 3, List.of(AirRequirement.output(80), itemInput(),
                new EnergyRequirement(2), AirRequirement.input(40, 4F))), 1).isCrafting()).isTrue();
        assertThat(items.itemHandler().amount(0)).isEqualTo(1);
        assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(98);
        assertThat(input.air).isEqualTo(480);
        assertThat(runtime.activeRecipe().inputConsumptionPlan().consumedInputBatches()).containsExactly(0, 1, 0, 0);
        for (int tick = 0; tick < 3; tick++) runtime.tick();
        runtime.finish();
        assertThat(runtime.active()).isFalse();
        assertThat(items.itemHandler().amount(0)).isEqualTo(1);
        assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(92);
        assertThat(input.air).isEqualTo(360);
        assertThat(output.air).isEqualTo(240);
    }

    @Test
    void prefetchAndPatternStartKeepOriginalAirAndItemIndexes() {
        var items = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemStack stack = new ItemStack(Items.IRON_INGOT, 2);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        items.itemHandler().setContents(0, stack, 2);
        PrefetchCapability prefetch = new PrefetchCapability(6);
        AirCapability input = new AirCapability(IOType.INPUT, 480);
        AirCapability output = new AirCapability(IOType.OUTPUT, 0);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), items);
        attach(controller, prefetch, input, output);
        CraftingRuntime runtime = runtime(controller);
        MachineRecipe recipe = recipe("mixed_prefetch", 3, List.of(new EnergyRequirement(2),
                AirRequirement.output(80), itemInput(), AirRequirement.input(40, 4F)));
        CraftingRuntime.PreparedStart prepared = runtime.preparePatternStart(recipe, 1, List.of());
        assertThat(prepared).isNotNull();
        assertThat(prepared.plan().requirements()).extracting(plan -> plan.requirementIndex()).containsExactly(2, 3);
        assertThat(input.air).isEqualTo(480);
        assertThat(items.itemHandler().amount(0)).isEqualTo(2);
        assertThat(runtime.reservePatternStart()).isTrue();
        assertThat(runtime.commitPatternStart(prepared)).isTrue();
        assertThat(prefetch.reserved).isEqualTo(6);
        assertThat(prefetch.consumed).isZero();
        assertThat(items.itemHandler().amount(0)).isEqualTo(1);
        assertThat(input.air).isEqualTo(480);
        assertThat(runtime.activeRecipe().inputConsumptionPlan().consumedInputBatches()).containsExactly(0, 0, 1, 0);
        for (int tick = 0; tick < 3; tick++) runtime.tick();
        runtime.finish();
        assertThat(runtime.active()).isFalse();
        assertThat(prefetch.reserved).isZero();
        assertThat(prefetch.consumed).isEqualTo(6);
        assertThat(items.itemHandler().amount(0)).isEqualTo(1);
        assertThat(input.air).isEqualTo(360);
        assertThat(output.air).isEqualTo(240);
    }

    @Test
    void patternPrepareThenLivePressureOrAmountLossRejectsBeforeOrdinaryInputsCommit() {
        for (AirRequirement requirement : List.of(AirRequirement.input(0, 4F),
                AirRequirement.input(40, 4F), AirRequirement.input(40, 0F))) {
            var items = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
            ItemStack stack = new ItemStack(Items.IRON_INGOT, 2);
            stack.set(DataComponents.MAX_STACK_SIZE, 64);
            items.itemHandler().setContents(0, stack, 2);
            var energy = RuntimeTestFixtures.energyInput(new BlockPos(2, 0, 0));
            energy.energyStorage().setAmount(100);
            AirCapability input = new AirCapability(IOType.INPUT, 480);
            AirCapability output = new AirCapability(IOType.OUTPUT, 0);
            MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), items, energy);
            attach(controller, input, output);
            CraftingRuntime runtime = runtime(controller);
            var prepared = runtime.preparePatternStart(recipe("stale_pattern_" + requirement.airPerTick()
                    + "_" + requirement.minPressure(), 3,
                    List.of(AirRequirement.output(80), itemInput(), new EnergyRequirement(2), requirement)),
                    1, List.of());
            assertThat(prepared).isNotNull();
            assertThat(runtime.reservePatternStart()).isTrue();
            input.air = requirement.minPressure() == 0F ? 39 : 399;
            assertThat(runtime.commitPatternStart(prepared)).isFalse();
            assertThat(runtime.active()).isFalse();
            assertThat(runtime.pendingPatternRecipeId()).isNull();
            assertThat(runtime.failure().reason()).isEqualTo(requirement.minPressure() == 0F
                    ? AirFailureReasons.INSUFFICIENT_AIR : AirFailureReasons.INSUFFICIENT_PRESSURE);
            assertThat(runtime.failure().failure().trace().frames().getFirst().requirementIndex()).isEqualTo(3);
            assertThat(items.itemHandler().amount(0)).isEqualTo(2);
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(100);
            assertThat(input.air).isEqualTo(requirement.minPressure() == 0F ? 39 : 399);
            assertThat(input.applyCalls).isZero();
            assertThat(output.air).isZero();
            assertThat(output.applyCalls).isZero();
        }
    }

    @Test
    void startupRevalidatesAfterPrefetchSettlementAndReleasesFailedReservation() {
        for (boolean pattern : List.of(false, true)) {
            for (AirRequirement requirement : List.of(AirRequirement.input(0, 4F), AirRequirement.input(40, 0F))) {
                var items = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
                ItemStack stack = new ItemStack(Items.IRON_INGOT, 2);
                stack.set(DataComponents.MAX_STACK_SIZE, 64);
                items.itemHandler().setContents(0, stack, 2);
                PrefetchCapability prefetch = new PrefetchCapability(6);
                AirCapability input = new AirCapability(IOType.INPUT, 480);
                MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), items);
                attach(controller, prefetch, input);
                CraftingRuntime runtime = runtime(controller);
                MachineRecipe recipe = recipe("stale_prefetch_" + pattern + "_" + requirement.airPerTick(), 3,
                        List.of(new EnergyRequirement(2), itemInput(), requirement));
                prefetch.onReserve = () -> input.air = requirement.airPerTick() == 0 ? 399 : 39;
                if (pattern) {
                    var prepared = runtime.preparePatternStart(recipe, 1, List.of());
                    assertThat(prepared).isNotNull();
                    assertThat(input.air).isEqualTo(480);
                    assertThat(runtime.reservePatternStart()).isTrue();
                    assertThat(runtime.commitPatternStart(prepared)).isFalse();
                } else {
                    assertThat(runtime.start(recipe, 1).isCrafting()).isFalse();
                }
                assertThat(runtime.failure().reason()).isEqualTo(requirement.airPerTick() == 0
                        ? AirFailureReasons.INSUFFICIENT_PRESSURE : AirFailureReasons.INSUFFICIENT_AIR);
                assertThat(runtime.failure().failure().trace().frames().getFirst().requirementIndex()).isEqualTo(2);
                assertThat(runtime.active()).isFalse();
                assertThat(items.itemHandler().amount(0)).isEqualTo(2);
                assertThat(input.air).isEqualTo(requirement.airPerTick() == 0 ? 399 : 39);
                assertThat(input.applyCalls).isZero();
                assertThat(prefetch.reserved).isZero();
                assertThat(prefetch.available).isEqualTo(6);
                assertThat(prefetch.consumed).isZero();
            }
        }
    }

    @Test
    void stalePatternSplitCannotBorrowPressureFromAnotherPortOrConsumeStartupItem() {
        var items = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemStack stack = new ItemStack(Items.IRON_INGOT, 2);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        items.itemHandler().setContents(0, stack, 2);
        AirCapability first = new AirCapability(IOType.INPUT, 450);
        AirCapability second = new AirCapability(IOType.INPUT, 450);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), items);
        attach(controller, first, second);
        CraftingRuntime runtime = runtime(controller);
        var prepared = runtime.preparePatternStart(recipe("stale_split", 3,
                List.of(itemInput(), AirRequirement.input(800, 4F))), 1, List.of());
        assertThat(prepared).isNotNull();
        assertThat(runtime.reservePatternStart()).isTrue();
        second.air = 399;
        assertThat(runtime.commitPatternStart(prepared)).isFalse();
        assertThat(runtime.failure().reason()).isEqualTo(AirFailureReasons.INSUFFICIENT_AIR);
        assertThat(runtime.active()).isFalse();
        assertThat(items.itemHandler().amount(0)).isEqualTo(2);
        assertThat(first.air).isEqualTo(450);
        assertThat(second.air).isEqualTo(399);
        assertThat(first.applyCalls + second.applyCalls).isZero();
    }

    @Test
    void startupSplitRunsLiveFacetValidationBeforeCommittingTheItem() {
        var items = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemStack stack = new ItemStack(Items.IRON_INGOT, 2);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        items.itemHandler().setContents(0, stack, 2);
        AirCapability first = new AirCapability(IOType.INPUT, 450);
        AirCapability second = new AirCapability(IOType.INPUT, 450);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), items);
        attach(controller, first, second);
        CraftingRuntime runtime = runtime(controller);
        // Snapshots permit the split; the second live facet loses pressure during preflight itself.
        second.onValidate = () -> second.air = 399;
        assertThat(runtime.start(recipe("start_split_preflight", 3,
                List.of(itemInput(), AirRequirement.input(800, 4F))), 1).isCrafting()).isFalse();
        assertThat(runtime.failure().reason()).isEqualTo(AirFailureReasons.INSUFFICIENT_PRESSURE);
        assertThat(runtime.active()).isFalse();
        assertThat(items.itemHandler().amount(0)).isEqualTo(2);
        assertThat(first.air).isEqualTo(450);
        assertThat(second.air).isEqualTo(399);
        assertThat(first.validateCalls).isEqualTo(1);
        assertThat(second.validateCalls).isEqualTo(1);
        assertThat(first.applyCalls + second.applyCalls).isZero();
    }

    @Test
    void patternStartChecksLaterRequirementsAgainstLiveVirtualDrainWithoutApplyingEarlierOnes() {
        for (long laterRate : List.of(0L, 20L)) {
            var items = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
            ItemStack stack = new ItemStack(Items.IRON_INGOT, 2);
            stack.set(DataComponents.MAX_STACK_SIZE, 64);
            items.itemHandler().setContents(0, stack, 2);
            AirCapability input = new AirCapability(IOType.INPUT, 500);
            MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), items);
            attach(controller, input);
            CraftingRuntime runtime = runtime(controller);
            var prepared = runtime.preparePatternStart(recipe("stale_virtual_" + laterRate, 3,
                    List.of(itemInput(), AirRequirement.input(100, 4F), AirRequirement.input(laterRate, 4F))),
                    1, List.of());
            assertThat(prepared).isNotNull();
            assertThat(runtime.reservePatternStart()).isTrue();
            input.air = 499;
            assertThat(runtime.commitPatternStart(prepared)).isFalse();
            assertThat(runtime.failure().reason()).isEqualTo(AirFailureReasons.INSUFFICIENT_PRESSURE);
            assertThat(runtime.failure().failure().trace().frames().getFirst().requirementIndex()).isEqualTo(2);
            assertThat(runtime.active()).isFalse();
            assertThat(items.itemHandler().amount(0)).isEqualTo(2);
            assertThat(input.air).isEqualTo(499);
            assertThat(input.validateCalls).isEqualTo(1);
            assertThat(input.applyCalls).isZero();
        }
    }

    @Test
    void fullOutputBlocksWholeTickAndPartialPolicySettlesOnlyAvailableAir() {
        for (boolean partial : List.of(false, true)) {
            AirCapability input = new AirCapability(IOType.INPUT, 480);
            AirCapability output = new AirCapability(IOType.OUTPUT, 2_000);
            CraftingRuntime runtime = runtime(controller(input, output));
            MachineRecipe recipe = recipe("output_" + partial, 1,
                    List.of(AirRequirement.output(80), AirRequirement.input(40, 4F)), false, partial);
            assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
            runtime.tick();
            assertThat(runtime.failure().reason()).isEqualTo(AirFailureReasons.OUTPUT_BLOCKED);
            assertThat(runtime.tickCount()).isZero();
            assertThat(input.air).isEqualTo(480);
            assertThat(output.air).isEqualTo(2_000);
            output.air -= 30;
            runtime.tick();
            if (!partial) {
                assertThat(runtime.finishPending()).isFalse();
                assertThat(input.air).isEqualTo(480);
                assertThat(output.air).isEqualTo(1_970);
                output.air -= 50;
                runtime.tick();
            }
            assertThat(runtime.finishPending()).isTrue();
            runtime.finish();
            assertThat(runtime.active()).isFalse();
            assertThat(input.air).isEqualTo(440);
            assertThat(output.air).isEqualTo(2_000);
            assertThat(output.moved).isEqualTo(partial ? 30 : 80);
            assertThat(output.applyCalls).isEqualTo(1);
        }
    }

    @Test
    void controllersSharingFiniteAirCannotSpendTheSameBatch() {
        AirCapability input = new AirCapability(IOType.INPUT, 40);
        CraftingRuntime first = runtime(controller(input));
        CraftingRuntime second = runtime(controller(input));
        MachineRecipe recipe = recipe("shared", 1, List.of(AirRequirement.input(40, 0F)));
        assertThat(first.start(recipe, 1).isCrafting()).isTrue();
        assertThat(second.start(recipe, 1).isCrafting()).isTrue();
        assertThat(input.air).isEqualTo(40);
        first.tick();
        second.tick();
        assertThat(first.finishPending()).isTrue();
        assertThat(second.finishPending()).isFalse();
        assertThat(second.failure().reason()).isEqualTo(AirFailureReasons.INSUFFICIENT_AIR);
        assertThat(input.air).isZero();
        assertThat(input.applyCalls).isEqualTo(1);
        input.air = 40;
        second.tick();
        first.finish();
        second.finish();
        assertThat(first.active()).isFalse();
        assertThat(second.active()).isFalse();
        assertThat(input.air).isZero();
        assertThat(input.applyCalls).isEqualTo(2);
    }

    @Test
    void pauseResumeSerializedRestoreAndFinishPendingRestoreNeverReplayTicks() {
        AirCapability input = new AirCapability(IOType.INPUT, 480);
        AirCapability output = new AirCapability(IOType.OUTPUT, 0);
        MachineControllerBlockEntity controller = controller(input, output);
        CraftingRuntime runtime = runtime(controller);
        MachineRecipe recipe = recipe("restore", 3, List.of(AirRequirement.input(40, 4F), AirRequirement.output(80)));
        RecipeRegistry.registerStatic(recipe);
        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        runtime.tick();
        runtime.pause();
        runtime.tick();
        runtime.finish();
        assertThat(runtime.tickCount()).isEqualTo(1);
        assertThat(input.air).isEqualTo(440);
        assertThat(output.air).isEqualTo(80);
        runtime.resume();
        CraftingRuntime restored = reload(runtime, controller, input, output);
        assertThat(restored.tickCount()).isEqualTo(1);
        assertThat(restored.activeRecipe().inputConsumptionPlan().consumedInputBatches()).containsExactly(0, 0);
        restored.tick();
        restored.tick();
        assertThat(restored.finishPending()).isTrue();
        assertThat(input.air).isEqualTo(360);
        assertThat(output.air).isEqualTo(240);
        restored = reload(restored, controller, input, output);
        assertThat(restored.finishPending()).isTrue();
        restored.tick();
        restored.finish();
        restored.finish();
        assertThat(restored.active()).isFalse();
        assertThat(input.air).isEqualTo(360);
        assertThat(output.air).isEqualTo(240);
        assertThat(input.applyCalls).isEqualTo(3);
        assertThat(output.applyCalls).isEqualTo(3);
    }

    @Test
    void cancellationAndUnloadDoNotRefundInputOrCreateOutput() {
        for (boolean cancelOnFailure : List.of(false, true)) {
            AirCapability input = new AirCapability(IOType.INPUT, 400);
            AirCapability output = new AirCapability(IOType.OUTPUT, 0);
            CraftingRuntime runtime = runtime(controller(input, output));
            assertThat(runtime.start(recipe("cancel_" + cancelOnFailure, 3,
                    List.of(AirRequirement.input(40, 4F), AirRequirement.output(80)), cancelOnFailure, false), 1)
                    .isCrafting()).isTrue();
            runtime.tick();
            runtime.tick();
            if (cancelOnFailure) assertThat(runtime.active()).isFalse();
            else assertThat(runtime.active()).isTrue();
            runtime.invalidate();
            runtime.invalidate();
            runtime.tick();
            runtime.finish();
            assertThat(input.air).isEqualTo(360);
            assertThat(output.air).isEqualTo(80);
            assertThat(input.applyCalls).isEqualTo(1);
            assertThat(output.applyCalls).isEqualTo(1);
        }
    }

    @Test
    void unsupportedAsyncPlanningFallsBackToLivePressureChecksAndMatchesSyncTotals() {
        for (boolean async : List.of(false, true)) {
            AirCapability input = new AirCapability(IOType.INPUT, 400);
            AirCapability output = new AirCapability(IOType.OUTPUT, 0);
            var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
            energy.energyStorage().setAmount(100);
            MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), energy);
            attach(controller, input, output);
            CraftingRuntime runtime = runtime(controller);
            List<MachineRequirement> requirements = List.of(new EnergyRequirement(2),
                    AirRequirement.input(40, 4F), AirRequirement.output(80));
            var unsupported = new CraftingContext(new CapabilitySnapshot(controller.componentRuntime().capabilities()))
                    .planAsync(requirements, 1);
            assertThat(unsupported.initialMainThreadRequirements()).containsExactly(1, 2);
            assertThat(input.applyCalls).isZero();
            assertThat(runtime.start(recipe("fallback_" + async, 3, requirements), 1).isCrafting()).isTrue();
            assertThat(runTick(runtime, controller, async)).isTrue();
            assertThat(runTick(runtime, controller, async)).isFalse();
            assertThat(runtime.failure().reason()).isEqualTo(AirFailureReasons.INSUFFICIENT_PRESSURE);
            assertThat(runtime.tickCount()).isEqualTo(1);
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(96);
            input.air = 440;
            assertThat(runTick(runtime, controller, async)).isTrue();
            assertThat(runTick(runtime, controller, async)).isTrue();
            runtime.finish();
            assertThat(runtime.active()).isFalse();
            assertThat(input.air).isEqualTo(360);
            assertThat(output.air).isEqualTo(240);
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(92);
            assertThat(input.applyCalls).isEqualTo(3);
            assertThat(output.applyCalls).isEqualTo(3);
        }
    }

    @Test
    void asyncFallbackRechecksPressureAfterWorkerPlanningInsteadOfTrustingCapture() {
        AirCapability input = new AirCapability(IOType.INPUT, 400);
        AirCapability output = new AirCapability(IOType.OUTPUT, 0);
        MachineControllerBlockEntity controller = controller(input, output);
        CraftingRuntime runtime = runtime(controller);
        assertThat(runtime.start(recipe("fallback_live", 1,
                List.of(AirRequirement.input(0, 4F), AirRequirement.output(80))), 1).isCrafting()).isTrue();
        var prepared = runtime.prepareAsyncTickPlan(controller.currentRuntimeSnapshot());
        assertThat(prepared.initialMainThreadRequirements()).containsExactly(0, 1);
        var planned = prepared.plan();
        input.air = 399;
        assertThat(runtime.commitAsyncTick(planned)).isFalse();
        runtime.discardAsyncTickPreparation();
        assertThat(runtime.failure().reason()).isEqualTo(AirFailureReasons.INSUFFICIENT_PRESSURE);
        assertThat(runtime.finishPending()).isFalse();
        assertThat(output.air).isZero();
        input.air = 400;
        assertThat(runTick(runtime, controller, true)).isTrue();
        runtime.finish();
        assertThat(runtime.active()).isFalse();
        assertThat(input.air).isEqualTo(400);
        assertThat(output.air).isEqualTo(80);
    }

    private static boolean runTick(CraftingRuntime runtime, MachineControllerBlockEntity controller, boolean async) {
        if (!async) return runtime.tick().isCrafting();
        var prepared = runtime.prepareAsyncTickPlan(controller.currentRuntimeSnapshot());
        assertThat(prepared).isNotNull();
        assertThat(prepared.initialMainThreadRequirements()).isNotEmpty();
        var planned = prepared.plan();
        assertThat(planned.operations()).isEmpty();
        if (!runtime.commitAsyncTick(planned)) {
            runtime.discardAsyncTickPreparation();
            return false;
        }
        assertThat(runtime.completeAsyncTickAfterInputs()).isTrue();
        return runtime.completeAsyncTickAfterRecipe().isCrafting();
    }

    private static CraftingRuntime reload(CraftingRuntime runtime, MachineControllerBlockEntity controller,
                                          AirCapability input, AirCapability output) {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        CompoundTag saved = new CompoundTag();
        runtime.save(saved, registries);
        CompoundTag inputSaved = input.save();
        CompoundTag outputSaved = output.save();
        runtime.invalidate();
        input.load(inputSaved);
        output.load(outputSaved);
        CraftingRuntime restored = runtime(controller);
        restored.load(saved, controller.resourceDomain(), registries);
        assertThat(restored.active()).isTrue();
        assertThat(restored.failure()).isNull();
        return restored;
    }

    private static ItemRequirement itemInput() {
        return new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY);
    }

    private static MachineRecipe recipe(String id, int duration, List<MachineRequirement> requirements) {
        return recipe(id, duration, requirements, false, false);
    }

    private static MachineRecipe recipe(String id, int duration, List<MachineRequirement> requirements,
                                         boolean cancel, boolean partial) {
        return new MachineRecipe(MMCR.id("runtime_air_" + id), MMCR.id("test_cube"), duration,
                requirements, List.of(), List.of(), 0, 1, cancel, false, partial, Set.of());
    }

    private static CraftingRuntime runtime(MachineControllerBlockEntity controller) {
        return new CraftingRuntime(controller, controller.componentRuntime());
    }

    private static MachineControllerBlockEntity controller(MachineCapability... capabilities) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        attach(controller, capabilities);
        return controller;
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
        return ExecutionStatus.blocked(MMCR.id("air_runtime_test"), PneumaticIds.AIR,
                FailureOccurrence.at(reason, PneumaticIds.AIR, FailurePhase.CAPABILITY_COMMIT, null, null, Map.of()));
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class AvailableBridge implements PneumaticCraftBridge {
        @Override public boolean available() { return true; }
        @Override public List<IOPortKind> portKinds() { return List.of(); }
        @Override public void registerPorts(IEventBus modBus) { }
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

    /** Only storage is emulated; the requirement handler, reservations and lifecycle are production code.
     * @author howxu <dev@howxu.cn>
     */
    private static final class AirCapability implements MachineCapability, CapabilityView, PneumaticAirFacet {
        private final IOType direction;
        private int air;
        private int applyCalls;
        private int validateCalls;
        private long moved;
        private Runnable onValidate = () -> { };
        private AirCapability(IOType direction, int air) { this.direction = direction; this.air = air; }
        @Override public CapabilityType type() { return new CapabilityType(PneumaticIds.AIR); }
        @Override public CapabilityDirections directions() { return CapabilityDirections.of(direction); }
        @Override public IOType ioType() { return direction; }
        @Override public CapabilityView view() { return this; }
        @Override public Set<Class<? extends CapabilityFacet>> facets() { return Set.of(PneumaticAirFacet.class); }
        @Override public CapabilityOperation prepare(CapabilityRequest request) { throw new UnsupportedOperationException(); }
        @Override public Object queryIdentity() { return this; }
        @Override public AirState state() { return new AirState(BlockPos.ZERO, air, 100, 20F, 25F); }
        @Override public CapabilityResult validate(long amount, boolean insert, float minPressure) {
            validateCalls++;
            onValidate.run();
            if (insert != (direction == IOType.OUTPUT)) return CapabilityResult.failure(blocked(AirFailureReasons.MISSING_INTERFACE));
            if (!insert && state().pressure() < minPressure) return CapabilityResult.failure(blocked(AirFailureReasons.INSUFFICIENT_PRESSURE));
            if (amount > (insert ? state().outputCapacity() : Math.max(0L, air))) {
                return CapabilityResult.failure(blocked(insert ? AirFailureReasons.OUTPUT_BLOCKED : AirFailureReasons.INSUFFICIENT_AIR));
            }
            return CapabilityResult.successful();
        }
        @Override public CapabilityResult apply(long amount, boolean insert, float minPressure) {
            CapabilityResult result = validate(amount, insert, minPressure);
            if (!result.success()) return result;
            air += (int) (insert ? amount : -amount);
            moved += amount;
            applyCalls++;
            return CapabilityResult.successful();
        }
        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("air", air);
            return tag;
        }
        private void load(CompoundTag tag) { air = tag.getInt("air"); }
    }

    /** Counts actual reserved FE consumption and can change pressure between planning and commit.
     * @author howxu <dev@howxu.cn>
     */
    private static final class PrefetchCapability implements MachineCapability, CapabilityView, RecipeEnergyPrefetchFacet {
        private long available;
        private long reserved;
        private long consumed;
        private Runnable onReserve = () -> { };
        private Runnable onConsume = () -> { };
        private PrefetchCapability(long available) { this.available = available; }
        @Override public CapabilityType type() { return new CapabilityType(MMCR.id("air_test_prefetch")); }
        @Override public CapabilityDirections directions() { return CapabilityDirections.input(); }
        @Override public IOType ioType() { return IOType.INPUT; }
        @Override public CapabilityView view() { return this; }
        @Override public Set<Class<? extends CapabilityFacet>> facets() { return Set.of(RecipeEnergyPrefetchFacet.class); }
        @Override public CapabilityOperation prepare(CapabilityRequest request) { throw new UnsupportedOperationException(); }
        @Override public String reservationKey() { return "air_test:prefetch"; }
        @Override public Optional<PrefetchPlan> planPrefetch(long amount) {
            if (amount > available) return Optional.empty();
            return Optional.of(new PrefetchPlan(amount, () -> {
                if (amount > available) return CapabilityResult.failure(blocked(BuiltinFailureReasons.MISSING_ENERGY));
                available -= amount;
                reserved += amount;
                onReserve.run();
                return CapabilityResult.successful();
            }));
        }
        @Override public void restoreReservation(long amount) { reserved += amount; }
        @Override public long releaseReservation(long amount) {
            long released = Math.min(amount, reserved);
            reserved -= released;
            available += released;
            return released;
        }
        @Override public CapabilityResult consumeReservation(long amount) {
            if (amount > reserved) return CapabilityResult.failure(blocked(BuiltinFailureReasons.MISSING_ENERGY));
            reserved -= amount;
            consumed += amount;
            onConsume.run();
            return CapabilityResult.successful();
        }
    }
}
