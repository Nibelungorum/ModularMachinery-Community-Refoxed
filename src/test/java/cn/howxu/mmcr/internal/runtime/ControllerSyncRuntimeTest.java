package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.publicapi.machine.TickBehavior;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.network.PktFactoryControllerStatePayload;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Immutable controller sync and final payload tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class ControllerSyncRuntimeTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void clearRecipes() {
        RecipeRegistry.clearForTesting();
    }

    @Test
    void machineProjectionContainsFactoryProgressModuleStateLevelsAndFailure() {
        ControllerRuntimeSnapshot runtime = runtimeSnapshot();

        MachineStateSnapshot state = new ControllerSyncRuntime().machineState(runtime);

        assertThat(state.formed()).isTrue();
        assertThat(state.active()).isTrue();
        assertThat(state.activeRecipe()).isEqualTo("mmcr:factory_recipe");
        assertThat(state.tick()).isEqualTo(4);
        assertThat(state.totalTick()).isEqualTo(20);
        assertThat(state.parallelism()).isEqualTo(6);
        assertThat(state.maxParallelism()).isEqualTo(8);
        assertThat(state.factoryThreadCount()).isEqualTo(2);
        assertThat(state.activeFactoryThreadCount()).isEqualTo(1);
        assertThat(state.moduleConnected()).isTrue();
        assertThat(state.installedModuleCount()).isEqualTo(2);
        assertThat(state.foundLevelIds()).containsExactly("mmcr:steel");
        assertThat(state.failure().details()).containsEntry("reason", "insufficient_energy");
    }

    @Test
    void factoryProjectionUsesImmutableLaneSnapshotsAndPerLaneParallelism() {
        FactorySnapshot factory = new ControllerSyncRuntime().factoryState(runtimeSnapshot());

        assertThat(factory.active()).isTrue();
        assertThat(factory.maxParallelism()).isEqualTo(8L);
        assertThat(factory.presentationLanes().getFirst().parallelism()).isEqualTo(6L);
        assertThat(factory.laneLimit()).isEqualTo(2);
        assertThat(factory.presentationLanes()).hasSize(2).isUnmodifiable();
        assertThat(factory.foundLevelIds()).containsExactly("mmcr:steel");
        assertThat(factory.lanes()).isUnmodifiable();
    }

    @Test
    void sync_runtime_consumes_a_published_controller_snapshot() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        var machine = controller.runtimeSnapshot().structure().configuredMachine();
        RuntimeTestFixtures.publishStructure(controller, machine, true);

        MachineStateSnapshot state = new ControllerSyncRuntime().machineState(controller.runtimeSnapshot());

        assertThat(state.formed()).isTrue();
        assertThat(state.machineId()).isEqualTo("mmcr:test_cube");
        assertThat(state.moduleConnected()).isFalse();
    }

    @Test
    void tick_behavior_projects_as_active_without_recipe_or_factory_work() {
        Identifier machineId = MMCR.id("sync_tick_machine");
        DynamicMachine machine = new DynamicMachine(machineId, "Sync Tick", new BlockArray(Map.of()),
                MachineControllerSpec.defaultsFor(machineId),
                cn.howxu.mmcr.api.machine.MachineAppearanceSpec.defaults(), PortRequirementSpec.none(),
                cn.howxu.mmcr.api.machine.PortTierRequirementSpec.none(), List.of(), Map.of(), 1, false, false, 1,
                List.of(), MachineRole.NORMAL, Set.of(), List.of(), RecipeFailureActions.getDefaultAction(),
                TickBehavior.builder().build());
        StructureSnapshot structure = new StructureSnapshot(machine, machine, new BlockArray(Map.of()),
                null, Direction.SOUTH, Direction.SOUTH, 1, true, 1L, null, null,
                null, false, true, Set.of());
        ControllerRuntimeSnapshot runtime = new ControllerRuntimeSnapshot(structure, 0L, 0L, 0L,
                Map.of(), Map.of(), Set.of(), ModuleConnectionStatus.notRequired(), 0,
                new ComponentRuntime.CapabilityAggregate(0L, 0L, null, null),
                CraftingStateSnapshot.empty(1L, 0L, 0L), FactorySnapshot.empty(),
                List.of(), List.of(), List.of(), machineId.toString(), "Sync Tick", 0,
                false, false, 0, 0, 1, Map.of());

        MachineStateSnapshot state = new ControllerSyncRuntime().machineState(runtime);

        assertThat(state.active()).isTrue();
        assertThat(state.activeRecipe()).isEmpty();
    }

    @Test
    void controller_snapshot_publishes_data_storage_values_immutably() {
        Map<String, DataValue> values = new LinkedHashMap<>();
        values.put("mode", DataValue.of("active"));
        ControllerRuntimeSnapshot snapshot = new ControllerRuntimeSnapshot(
                StructureSnapshot.empty(), 0L, 0L, 0L, Map.of(), Map.of(), Set.of(),
                ModuleConnectionStatus.disconnected(), 0,
                new ComponentRuntime.CapabilityAggregate(0L, 0L, null, null),
                CraftingStateSnapshot.empty(0L, 0L, 0L), FactorySnapshot.empty(),
                List.of(), List.of(), List.of(), "", "", 0, false, false, 0, 0, 1, values);

        assertEquals(values, snapshot.dataStorageValues());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.dataStorageValues().put("mode", DataValue.of("changed")));
    }

    @Test
    void factory_progress_is_published_through_a_real_controller_runtime_boundary() {
        Identifier machineId = MMCR.id("sync_factory_machine");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos schedulerPos = controller.getBlockPos().offset(-1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));
        DynamicMachine machine = new DynamicMachine(machineId, "Sync Factory",
                pattern,
                MachineControllerSpec.defaultsFor(machineId),
                PortRequirementSpec.none(), List.of(), Map.of(), 1, false, true, 1);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null)));
        controller.setFormed(true);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("sync_factory_recipe"), machineId, 20,
                List.of(), List.of());
        RecipeRegistry.register(recipe);

        controller.serverTick();
        resolveSharedRequests(controller);
        MachineStateSnapshot started = new ControllerSyncRuntime().machineState(controller.runtimeSnapshot());
        RuntimeTestFixtures.advanceGameTime(controller.getLevel());
        controller.serverTick();
        resolveSharedRequests(controller);
        RuntimeTestFixtures.advanceGameTime(controller.getLevel());
        controller.serverTick();
        resolveSharedRequests(controller);
        MachineStateSnapshot progressed = new ControllerSyncRuntime().machineState(controller.runtimeSnapshot());

        assertThat(started.activeRecipe()).isEqualTo(recipe.id().toString());
        assertThat(started.factoryControllerPresent()).isTrue();
        assertThat(started.factoryThreadCount()).isEqualTo(1);
        assertThat(started.activeFactoryThreadCount()).isEqualTo(1);
        assertThat(progressed.active()).isTrue();
        assertThat(progressed.tick()).isGreaterThan(started.tick());
    }

    @Test
    void factory_shared_tick_publishes_progress_in_the_same_resolve_pass() {
        Identifier machineId = MMCR.id("sync_factory_immediate_progress");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos schedulerPos = controller.getBlockPos().offset(-1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));
        DynamicMachine machine = new DynamicMachine(machineId, "Sync Factory Immediate Progress", pattern,
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null)));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("sync_factory_immediate_recipe"), machineId, 20,
                List.of(), List.of());
        RecipeRegistry.register(recipe);

        controller.serverTick();
        resolveSharedRequests(controller);
        int startedTick = controller.runtimeSnapshot().factory().presentationLanes().getFirst().tick();

        RuntimeTestFixtures.advanceGameTime(controller.getLevel());
        controller.serverTick();
        resolveSharedRequests(controller);

        assertThat(controller.runtimeSnapshot().factory().presentationLanes().getFirst().tick())
                .isGreaterThan(startedTick);
    }

    @Test
    void factory_uses_machine_level_parallelism_bonus() {
        Identifier machineId = MMCR.id("sync_factory_level_parallelism");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos schedulerPos = controller.getBlockPos().offset(-1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));
        DynamicMachine machine = new DynamicMachine(machineId, "Sync Factory Level Parallelism", pattern,
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                8, true, true, 1);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null)));
        controller.componentRuntime().replaceLevels(Map.of(MMCR.id("sync_level"), new MachineLevel(
                MMCR.id("sync_level"), MMCR.id("sync_level_type"), 1, new BlockPredicate.Any(),
                ItemStack.EMPTY, new LevelModifier(1D, 1D, 1D, 2, 0))));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("sync_factory_level_recipe"), machineId, 20,
                List.of(), List.of());
        RecipeRegistry.register(recipe);

        controller.serverTick();
        resolveSharedRequests(controller);

        FactorySnapshot snapshot = controller.runtimeSnapshot().factory();
        assertThat(snapshot.maxParallelism()).isEqualTo(3);
        assertThat(snapshot.presentationLanes().getFirst().parallelism()).isEqualTo(3);
    }

    @Test
    void machine_projection_reports_live_parallel_capacity_while_active_recipe_keeps_its_parallelism() {
        ControllerRuntimeSnapshot base = runtimeSnapshot();
        CraftingStateSnapshot crafting = new CraftingStateSnapshot(MMCR.id("sync_live_parallel_recipe"),
                CraftingStatus.working(), null, 1L, 20L, 1L, 4, 20, 7, 7, false, "");
        ControllerRuntimeSnapshot runtime = new ControllerRuntimeSnapshot(base.structure(), base.capabilityVersion(),
                base.modifierVersion(), base.stateVersion(), base.foundModifiers(), base.foundLevels(),
                base.linkedPortPositions(), base.moduleConnectionStatus(), base.installedModuleCount(),
                base.capabilityAggregate(), crafting, FactorySnapshot.empty(), base.componentPresentations(),
                base.capabilityPresentations(), base.foundLevelIds(), base.machineId(), base.machineName(),
                base.controllerRole(), false, false, 0, 32, 32, base.dataStorageValues());

        MachineStateSnapshot state = new ControllerSyncRuntime().machineState(runtime);
        assertThat(state.parallelism()).isEqualTo(7);
        assertThat(state.maxParallelism()).isEqualTo(32);
    }

    @Test
    void factory_projection_reports_live_parallel_capacity_when_factory_runtime_is_stale() {
        ControllerRuntimeSnapshot base = runtimeSnapshot();
        ControllerRuntimeSnapshot runtime = new ControllerRuntimeSnapshot(base.structure(), base.capabilityVersion(),
                base.modifierVersion(), base.stateVersion(), base.foundModifiers(), base.foundLevels(),
                base.linkedPortPositions(), base.moduleConnectionStatus(), base.installedModuleCount(),
                base.capabilityAggregate(), base.crafting(), base.factory(), base.componentPresentations(),
                base.capabilityPresentations(), base.foundLevelIds(), base.machineId(), base.machineName(),
                base.controllerRole(), true, true, base.parallelControllerCount(), base.maxParallelControllerCount(), 32,
                base.dataStorageValues());

        assertThat(new ControllerSyncRuntime().machineState(runtime).maxParallelism()).isEqualTo(32);
        assertThat(new ControllerSyncRuntime().factoryState(runtime).maxParallelism()).isEqualTo(32);
    }

    @Test
    void factory_starts_all_allowed_threads_on_the_first_tick() {
        Identifier machineId = MMCR.id("sync_factory_initial_threads");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos schedulerPos = controller.getBlockPos().offset(-1, 0, 0);
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(2, 0, 0));
        ItemStack inputStack = new ItemStack(Items.IRON_INGOT, 4);
        inputStack.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64);
        input.getItemStackHandler(null).setStackInSlot(0, inputStack);
        BlockArray pattern = new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));
        DynamicMachine machine = new DynamicMachine(machineId, "Sync Factory Initial Threads", pattern,
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 4);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler, input);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null),
                new ProcessingComponent(null, input, input.getBlockPos(), BlockPos.ZERO, (String) null)));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("sync_factory_initial_recipe"), machineId, 20,
                List.of(), List.of(), List.of(), 0, 4, false, List.of(), List.of(
                        new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                                ItemStack.EMPTY)));
        RecipeRegistry.register(recipe);

        controller.serverTick();
        resolveSharedRequests(controller);

        assertThat(controller.runtimeSnapshot().factory().activeLaneCount()).isEqualTo(4);
    }

    @Test
    void reforming_a_replaced_factory_component_clears_the_stale_active_lane() {
        Identifier machineId = MMCR.id("sync_factory_reform_machine");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos schedulerPos = controller.getBlockPos().offset(-1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));
        DynamicMachine machine = new DynamicMachine(machineId, "Sync Factory Reform",
                pattern,
                MachineControllerSpec.defaultsFor(machineId),
                PortRequirementSpec.none(), List.of(), Map.of(), 1, false, true, 1);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null)));
        controller.setFormed(true);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("sync_factory_reform_recipe"), machineId, 20,
                List.of(), List.of());
        RecipeRegistry.register(recipe);

        controller.serverTick();
        resolveSharedRequests(controller);
        assertThat(controller.runtimeSnapshot().factory().active()).isTrue();

        FactorySchedulerBlockEntity replacement = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.replaceBlockEntity(controller, replacement);
        controller.onStructureBlockChanged(schedulerPos);
        for (int tick = 0; tick < 32 && controller.structureSnapshot().dirty(); tick++) {
            RuntimeTestFixtures.advanceGameTime(controller.getLevel());
            controller.tickStructure((ServerLevel) controller.getLevel(), controller.getBlockPos());
        }

        MachineStateSnapshot reformed = new ControllerSyncRuntime().machineState(controller.runtimeSnapshot());
        assertThat(reformed.active()).isFalse();
        assertThat(reformed.activeFactoryThreadCount()).isZero();
    }

    @Test
    void recipe_failure_is_projected_from_a_real_controller_start_attempt() {
        Identifier machineId = MMCR.id("sync_failure_machine");
        DynamicMachine machine = new DynamicMachine(machineId, "Sync Failure",
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))),
                MachineControllerSpec.defaultsFor(machineId));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, machine);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("sync_failure_recipe"), machineId, 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                        ItemStack.EMPTY)));
        RecipeRegistry.register(recipe);

        controller.serverTick();
        MachineStateSnapshot state = new ControllerSyncRuntime().machineState(controller.runtimeSnapshot());

        assertThat(state.active()).isFalse();
        assertThat(state.failure()).isNotNull();
        assertThat(state.failure().details()).containsEntry("reason", "insufficient_resource");
        assertThat(state.craftingStatus()).isEqualTo(CraftingStatus.Status.NO_RECIPE);
    }

    @Test
    void published_controller_module_state_and_levels_reach_the_sync_projection() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        DynamicMachine machine = new DynamicMachine(MMCR.id("sync_module_machine"), "Sync Module",
                new BlockArray(Map.of()), MachineControllerSpec.defaultsFor(MMCR.id("sync_module_machine")));
        RuntimeTestFixtures.publishStructure(controller, machine, true);
        Identifier hostId = MMCR.id("sync_host");
        Identifier levelId = MMCR.id("sync_steel");
        MachineLevel level = new MachineLevel(levelId, MMCR.id("sync_level_type"), 1,
                new BlockPredicate.Any(), ItemStack.EMPTY, LevelModifier.IDENTITY);
        controller.componentRuntime().replaceModuleConnectionState(ModuleConnectionStatus.connected(hostId), 2);
        controller.componentRuntime().replaceLevels(Map.of(levelId, level));
        RuntimeTestFixtures.republish(controller);

        MachineStateSnapshot state = new ControllerSyncRuntime().machineState(controller.runtimeSnapshot());

        assertThat(state.moduleConnected()).isTrue();
        assertThat(state.connectedHostId()).isEqualTo(hostId.toString());
        assertThat(state.installedModuleCount()).isEqualTo(2);
        assertThat(state.foundLevelIds()).containsExactly(levelId.toString());
    }

    @Test
    void clientPayloadSnapshotsDoNotRetainMutableFluidValuesOrRequireAnOwner() {
        ControllerRuntimeSnapshot runtime = runtimeSnapshot();
        PktMachineStatePayload payload = PktMachineStatePayload.from(new BlockPos(3, 4, 5), runtime);
        FluidStack fluid = payload.primaryFluid();
        fluid.setAmount(1);

        assertThat(payload.primaryFluid().getAmount()).isEqualTo(250);
        assertThat(payload.pos()).isEqualTo(new BlockPos(3, 4, 5));
        assertThat(payload.factoryThreadCount()).isEqualTo(2);
    }

    @Test
    void finalFactoryPayloadRoundTripsAllLaneAndLevelStateWithoutAnOwnerBlockEntity() {
        PktFactoryControllerStatePayload payload = new PktFactoryControllerStatePayload(BlockPos.ZERO,
                new ControllerSyncRuntime().factoryState(runtimeSnapshot()));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);

        PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer, payload);
        PktFactoryControllerStatePayload decoded = PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer);

        assertThat(decoded.controllerPos()).isEqualTo(BlockPos.ZERO);
        assertThat(decoded.snapshot()).isEqualTo(payload.snapshot());
        assertThat(decoded.snapshot().presentationLanes()).hasSize(2);
    }

    private static ControllerRuntimeSnapshot runtimeSnapshot() {
        ExecutionStatus failure = new ExecutionStatus(MMCR.id("runtime_failure"), StatusSeverity.BLOCKED,
                MMCR.id("crafting_runtime"), Map.of("reason", "insufficient_energy"));
        FactoryRuntime.ThreadSnapshot activeLane = new FactoryRuntime.ThreadSnapshot(0, true, false, true,
                "mmcr:factory_recipe", 4, 20, 6, "", true, "mmcr:factory_recipe");
        FactoryRuntime.ThreadSnapshot idleLane = new FactoryRuntime.ThreadSnapshot(1, false, false, false,
                "", 0, 0, 1, "", false, "");
        CraftingStateSnapshot crafting = new CraftingStateSnapshot(MMCR.id("crafting_recipe"),
                CraftingStatus.working(), null, 7L, 8L, 9L, 3, 20, 2, 8, true, "mmcr:crafting_recipe");
        FactorySnapshot factory = new FactorySnapshot(true, true, List.of(crafting), 2, 1, 8L,
                false, List.of(activeLane, idleLane), "factory", 3, failure, List.of("mmcr:steel"));
        StructureSnapshot structure = new StructureSnapshot(null, null, null, null, null,
                net.minecraft.core.Direction.SOUTH, 1, true, 7L, null, null, null, false, true, Set.of());
        return new ControllerRuntimeSnapshot(structure, 8L, 9L, 10L, Map.of(), Map.of(), Set.of(),
                ModuleConnectionStatus.connected(MMCR.id("host")), 2,
                new ComponentRuntime.CapabilityAggregate(250L, 1000L,
                        new FluidStack(Fluids.WATER, 250), new FluidStack(Fluids.LAVA, 100)),
                crafting, factory, List.of(), List.of(), List.of("mmcr:steel"), "mmcr:machine", "machine", 1,
                true, true, 1, 2, 8, Map.of());
    }

    private static void resolveSharedRequests(MachineControllerBlockEntity controller) {
        if (controller.resourceDomain() != null) {
            SharedIoCoordinator.get((ServerLevel) controller.getLevel()).resolve(controller.resourceDomain());
        }
    }
}
