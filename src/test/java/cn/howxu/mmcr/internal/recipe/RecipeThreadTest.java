package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.compat.mekanism.MekanismBridgeBootstrap;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.compat.mekanism.loaded.HeatPortCapability;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.event.SharedIoEvents;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.EnergyOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.fml.config.IConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
                controller.runtimeSnapshot().structure().version())).isFalse();
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
            energy.energyStorage().setAmount(1_000L);
            FactoryRecipeThread thread = attachedBaseLane(controller);
            MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("energy_tick_timing"), MMCR.id("test_cube"),
                    20, List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 20L),
                            new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 10L)), List.of());
            assertThat(thread.runtime().start(recipe, 1).isCrafting()).isTrue();
            long energyAfterStart = energy.energyStorage().getAmountAsLong();

            for (int tick = 1; tick <= 4; tick++) {
                thread.tick();
                assertThat(thread.tickPendingForTesting()).isTrue();
                SharedIoEvents.completeLevelTick(level);

                assertThat(thread.tickPendingForTesting()).isFalse();
                assertThat(thread.runtime().tickCount()).isEqualTo(tick);
                assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(energyAfterStart - tick * 20L);
                assertThat(output.energyStorage().getAmountAsLong()).isEqualTo(tick * 10L);
                RuntimeTestFixtures.advanceGameTime(level);
            }
        } finally {
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
        }
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
            energy.energyStorage().setAmount(100L);
            MachineRecipeThread thread = new MachineRecipeThread(controller);
            MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("energy_tick_live_supply"), MMCR.id("test_cube"),
                    20, List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 20L)), List.of());
            assertThat(thread.runtime().start(recipe, 1).isCrafting()).isTrue();
            thread.tick();
            energy.energyStorage().setAmount(0L);
            SharedIoEvents.completeLevelTick(level);

            assertThat(thread.runtime().tickCount()).isZero();
            assertThat(thread.runtime().failure()).isNotNull();
            assertThat(energy.energyStorage().getAmountAsLong()).isZero();
            assertThat(thread.tickPendingForTesting()).isFalse();

            RuntimeTestFixtures.advanceGameTime(level);
            energy.energyStorage().setAmount(100L);
            thread.tick();
            SharedIoEvents.completeLevelTick(level);

            assertThat(thread.runtime().tickCount()).isEqualTo(1);
            assertThat(thread.runtime().failure()).isNull();
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(80L);
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
            energy.energyStorage().setAmount(100L);
            try (Transaction transaction = Transaction.openRoot()) {
                catalyst.itemStorage().insert(0, ItemResource.of(Items.IRON_INGOT), 1L, transaction);
                transaction.commit();
            }
            MachineRecipeThread thread = new MachineRecipeThread(controller);
            MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("mixed_energy_tick"), MMCR.id("test_cube"),
                    20, List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 20L),
                            new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                                    ItemStack.EMPTY, 1F, List.of(), DataComponentPredicateSet.EMPTY, 0F)), List.of());
            assertThat(thread.runtime().start(recipe, 1).isCrafting()).isTrue();
            long energyAfterStart = energy.energyStorage().getAmountAsLong();
            thread.tick();
            try (Transaction transaction = Transaction.openRoot()) {
                catalyst.itemStorage().extract(0, ItemResource.of(Items.IRON_INGOT), 1L, transaction);
                transaction.commit();
            }
            SharedIoEvents.completeLevelTick(level);

            assertThat(thread.runtime().tickCount()).isZero();
            assertThat(thread.runtime().failure()).isNotNull();
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(energyAfterStart);
            assertThat(thread.tickPendingForTesting()).isFalse();

            RuntimeTestFixtures.advanceGameTime(level);
            try (Transaction transaction = Transaction.openRoot()) {
                catalyst.itemStorage().insert(0, ItemResource.of(Items.IRON_INGOT), 1L, transaction);
                transaction.commit();
            }
            thread.tick();
            SharedIoEvents.completeLevelTick(level);
            assertThat(thread.runtime().tickCount()).isEqualTo(1);
            assertThat(catalyst.itemStorage().amount(0)).isEqualTo(1L);
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(energyAfterStart - 20L);
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
                input.setHeat(6_000D);
                energy.energyStorage().setAmount(1_000L);
                List<MachineRequirement> requirements = new ArrayList<>(List.of(
                        LoadedHeatRequirement.minimumTemperature(10D), LoadedHeatRequirement.outputHeat(5D)));
                if (includeEnergy) requirements.add(new EnergyRequirement(RecipeModifier.IOType.INPUT, 20L));
                MachineRecipeThread thread = new MachineRecipeThread(controller);
                MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("scalar_heat_tick"), controller.currentRecipePoolId(),
                        20, requirements, List.of());
                assertThat(thread.runtime().start(recipe, 1).isCrafting())
                        .as("start failure: %s", thread.runtime().failure()).isTrue();
                long energyAfterStart = energy.energyStorage().getAmountAsLong();

                thread.tick();
                input.setHeat(0D);
                SharedIoEvents.completeLevelTick(level);
                assertThat(thread.runtime().tickCount()).isZero();
                assertThat(thread.runtime().failure().reason()).isEqualTo(MekanismFailureReasons.HEAT_TEMPERATURE_INSUFFICIENT);
                assertThat(output.capacitor.getHeat()).isZero();
                assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(energyAfterStart);

                input.setHeat(6_000D);
                for (int tick = 1; tick <= 3; tick++) {
                    RuntimeTestFixtures.advanceGameTime(level);
                    thread.tick();
                    SharedIoEvents.completeLevelTick(level);
                    assertThat(thread.runtime().tickCount()).isEqualTo(tick);
                    assertThat(thread.tickPendingForTesting()).isFalse();
                    assertThat(output.capacitor.getHeat()).isEqualTo(tick * 5D);
                    assertThat(energy.energyStorage().getAmountAsLong())
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
            input.energyStorage().setAmount(1_000L);
            FactoryRecipeThread lane = attachedBaseLane(controller);
            MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("worker_workset_invalidation"), controller.currentRecipePoolId(),
                    20, List.of(new EnergyRequirement(20L)), List.of());
            assertThat(lane.runtime().start(recipe, 1L).isCrafting())
                    .as("workset recipe start failure: %s", lane.runtime().failure()).isTrue();
            long before = input.energyStorage().getAmountAsLong();
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
            assertThat(input.energyStorage().getAmountAsLong()).isEqualTo(before);
            assertThat(lane.tickPendingForTesting()).isEqualTo(replacementTick);

            if (!replacementTick) lane.tick();
            SharedIoEvents.completeLevelTick(level);
            assertThat(lane.runtime().tickCount()).isEqualTo(1);
            assertThat(input.energyStorage().getAmountAsLong()).isEqualTo(before - 20L);
            assertThat(lane.tickPendingForTesting()).isFalse();
            SharedIoEvents.completeLevelTick(level);
            assertThat(lane.runtime().tickCount()).isEqualTo(1);
            assertThat(input.energyStorage().getAmountAsLong()).isEqualTo(before - 20L);
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

        private void setHeat(double heat) {
            try (Transaction transaction = Transaction.openRoot()) {
                capacitor.setHeat(heat, transaction);
                transaction.commit();
            }
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
        return ((List<FactoryRecipeThread>) lanesField.get(factory)).getFirst();
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
        Identifier machineId = MMCR.id("ordinary_selected_pool_machine");
        Identifier firstPool = MMCR.id("ordinary_selected_pool_first");
        Identifier secondPool = MMCR.id("ordinary_selected_pool_second");
        MachineControllerBlockEntity controller = multiPoolController(machineId, firstPool, secondPool);
        assertThat(controller.selectRecipePool(secondPool)).isTrue();
        MachineRecipe first = RecipeTestSupport.create(MMCR.id("ordinary_selected_pool_first_recipe"), firstPool,
                20, List.of(), List.of());
        MachineRecipe second = RecipeTestSupport.create(MMCR.id("ordinary_selected_pool_second_recipe"), secondPool,
                20, List.of(), List.of());
        MachineRecipeThread thread = new MachineRecipeThread(controller);

        assertThat(thread.searchAndStartRecipe(List.of(first, second), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
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
                controller.runtimeSnapshot().structure().version())).isTrue();

        completeAsyncLevelTick((ServerLevel) controller.getLevel());

        assertThat(thread.runtime().active()).isTrue();
        assertThat(thread.runtime().recipe()).isEqualTo(valid);
    }

    @Test
    void switching_pools_discards_an_outstanding_async_search_result() {
        Identifier machineId = MMCR.id("test_cube");
        Identifier firstPool = MMCR.id("async_selected_pool_first");
        Identifier secondPool = MMCR.id("async_selected_pool_second");
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(machineId)
                .recipePoolIds(List.of(firstPool, secondPool)).build());
        MachineControllerBlockEntity controller = normalController();
        assertThat(controller.selectRecipePool(secondPool)).isTrue();
        MachineRecipe second = RecipeTestSupport.create(MMCR.id("async_selected_pool_recipe"), secondPool,
                20, List.of(), List.of());
        MachineRecipeThread thread = new MachineRecipeThread(controller);

        assertThat(thread.searchAndStartAsyncRecipe(List.of(second), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
        assertThat(controller.selectRecipePool(firstPool)).isTrue();
        completeAsyncLevelTick((ServerLevel) controller.getLevel());

        assertThat(thread.runtime().active()).isFalse();
        assertThat(thread.hasPendingAsyncSearch()).isFalse();
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
                controller.runtimeSnapshot().structure().version())).isTrue();
        thread.markRecipeFinished();
        thread.runtime().invalidate();

        assertThat(thread.tryRestartLastRecipe(List.of(recipe), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
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
                controller.runtimeSnapshot().structure().version())).isTrue();
        thread.markRecipeFinished();

        thread.discardForRecipePoolChange();

        assertThat(thread.runtime().active()).isFalse();
        assertThat(thread.tryRestartLastRecipe(List.of(recipe), 1,
                controller.runtimeSnapshot().structure().version())).isFalse();
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
                controller.runtimeSnapshot().structure().version())).isTrue();
        assertThat(thread.isStartPending()).isTrue();

        thread.discardForRecipePoolChange();
        completeAsyncLevelTick(level);

        assertThat(thread.isStartPending()).isFalse();
        assertThat(thread.runtime().active()).isFalse();
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

    private static MachineControllerBlockEntity multiPoolController(Identifier machineId, Identifier... pools) {
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
