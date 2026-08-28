package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.CompiledMachinePattern;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.ControllerSyncRuntime;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.MachineStateSnapshot;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Final controller structure and preview behavior tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineControllerBlockEntityTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.FACTORY_CONTROLLER,
                new MenuType<>((containerId, inventory) -> FactoryControllerMenu.clientOpen(containerId, inventory),
                        FeatureFlags.VANILLA_SET));
    }

    @Test
    void structure_stage_and_preview_use_the_public_controller_boundaries() {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        DynamicMachine machine = new DynamicMachine(MMCR.id("controller_boundary"), "Controller Boundary",
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))),
                MachineControllerSpec.defaultsFor(MMCR.id("controller_boundary")));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        controller.setMachine(machine);
        controller.setLevel(LevelStub.create(Map.of(
                controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get()), List.of(controller)));

        assertThat(controller.assemblyPattern(machine, 1).pattern()).containsKey(new BlockPos(-1, 0, 0));
        assertThat(controller.createStructurePreviewSnapshot(16)).isPresent();
        assertThat(controller.structureSnapshot().formed()).isFalse();

        controller.setFormed(true);

        assertThat(controller.structureSnapshot().formed()).isTrue();
    }

    @Test
    void idle_runtime_work_reuses_the_published_snapshot() {
        Identifier machineId = MMCR.id("idle_runtime_snapshot");
        DynamicMachine machine = new DynamicMachine(machineId, "Idle Runtime Snapshot",
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.Any())),
                MachineControllerSpec.defaultsFor(machineId));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, machine);
        ServerLevel level = (ServerLevel) controller.getLevel();

        controller.tickRuntimeWork(level, controller.getBlockPos());
        var published = controller.runtimeSnapshot();
        controller.tickRuntimeWork(level, controller.getBlockPos());

        assertThat(controller.runtimeSnapshot()).isSameAs(published);
    }

    @Test
    void no_op_runtime_update_reuses_the_published_snapshot() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        var published = controller.runtimeSnapshot();
        int buildCount = controller.snapshotBuildCountForTesting();

        controller.setFormed(published.structure().formed());

        assertThat(controller.runtimeSnapshot()).isEqualTo(published);
        assertThat(controller.snapshotBuildCountForTesting()).isEqualTo(buildCount);
        assertThat(controller.runtimeSnapshot()).isSameAs(published);
    }

    @Test
    void update_batch_flushes_structure_component_and_factory_changes_once() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        MachineControllerRuntime runtime = new MachineControllerRuntime(controller);
        DynamicMachine machine = new DynamicMachine(MMCR.id("batched_snapshot"), "Batched Snapshot",
                new BlockArray(Map.of()), MachineControllerSpec.defaultsFor(MMCR.id("batched_snapshot")));

        runtime.beginUpdateBatch();
        try {
            runtime.publishStructureState(true, true, machine, 1);
            runtime.publishComponentState(List.of(new ProcessingComponent(null, "component", BlockPos.ZERO)),
                    Map.of(), Map.of(), Set.of());
            runtime.factoryRuntime().setLaneLimit(2);
        } finally {
            runtime.endUpdateBatch();
        }

        var published = runtime.snapshot();
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(1);
        assertThat(published.structure().configuredMachine()).isEqualTo(machine);
        assertThat(published.structure().formed()).isTrue();
        assertThat(published.componentPresentations()).hasSize(1);
        assertThat(published.factory().laneLimit()).isEqualTo(2);
    }

    @Test
    void server_tick_runs_factory_work_when_structure_forms_earlier_in_the_same_batch() {
        Identifier machineId = MMCR.id("same_tick_factory_formation");
        DynamicMachine machine = new DynamicMachine(machineId, "Same Tick Factory Formation",
                new BlockArray(Map.of(new BlockPos(1, 0, 0),
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()))),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(new BlockPos(-1, 0, 0),
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler);
        controller.invalidateFormedStructure();
        RecipeRegistry.register(new MachineRecipe(MMCR.id("same_tick_factory_recipe"), machineId, 20,
                List.of(), List.of()));

        controller.serverTick();
        resolveSharedRequests(controller);

        assertThat(controller.structureSnapshot().formed()).isTrue();
        assertThat(controller.runtimeSnapshot().factory().active())
                .as("factory snapshot=%s", controller.runtimeSnapshot().factory()).isTrue();
    }

    @Test
    void server_tick_does_not_run_factory_after_structure_resets_earlier_in_the_same_batch() {
        Identifier machineId = MMCR.id("same_tick_factory_reset");
        DynamicMachine machine = new DynamicMachine(machineId, "Same Tick Factory Reset",
                new BlockArray(Map.of(new BlockPos(1, 0, 0),
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()))),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos schedulerPos = new BlockPos(-1, 0, 0);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler);
        RuntimeTestFixtures.republish(controller);
        RecipeRegistry.register(new MachineRecipe(MMCR.id("same_tick_reset_recipe"), machineId, 20,
                List.of(), List.of()));
        controller.serverTick();
        resolveSharedRequests(controller);
        assertThat(controller.runtimeSnapshot().factory().active())
                .as("factory snapshot=%s", controller.runtimeSnapshot().factory()).isTrue();

        RuntimeTestFixtures.replaceBlockEntity(controller, RuntimeTestFixtures.itemInput(schedulerPos));
        controller.onStructureBlockChanged(schedulerPos);
        controller.serverTick();
        resolveSharedRequests(controller);

        assertThat(controller.runtimeSnapshot().structure().formed()).isTrue();
        assertThat(controller.runtimeSnapshot().factoryControllerPresent()).isFalse();
        assertThat(controller.runtimeSnapshot().factory().active()).isFalse();
    }

    @Test
    void module_refresh_in_a_batch_notifies_from_current_component_state_once() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        DynamicMachine machine = new DynamicMachine(MMCR.id("module_refresh_batch"), "Module Refresh Batch",
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))),
                MachineControllerSpec.defaultsFor(MMCR.id("module_refresh_batch")));
        RuntimeTestFixtures.formStructure(controller, machine);
        controller.componentRuntime().replaceModuleConnectionState(
                ModuleConnectionStatus.connected(MMCR.id("module_host")), 2);
        RuntimeTestFixtures.republish(controller);
        long beforeEpoch = controller.resourceAvailabilityEpoch();
        controller.setStructureCheckCallbackForTesting(controller::refreshModuleConnectionState);
        controller.requestImmediateStructureCheck();

        controller.serverTick();

        assertThat(controller.componentRuntime().moduleConnectionStatus()).isEqualTo(ModuleConnectionStatus.notRequired());
        assertThat(controller.componentRuntime().installedModuleCount()).isZero();
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeEpoch + 1);
        controller.refreshModuleConnectionState();
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeEpoch + 1);
    }

    @Test
    void formation_batch_sends_the_final_factory_snapshot_to_an_open_menu() throws Exception {
        Identifier firstMachineId = MMCR.id("factory_menu_first");
        Identifier secondMachineId = MMCR.id("factory_menu_second");
        BlockArray pattern = new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));
        DynamicMachine firstMachine = new DynamicMachine(firstMachineId, "Factory Menu First", pattern,
                MachineControllerSpec.defaultsFor(firstMachineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        DynamicMachine secondMachine = new DynamicMachine(secondMachineId, "Factory Menu Second", pattern,
                MachineControllerSpec.defaultsFor(secondMachineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(new BlockPos(-1, 0, 0),
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, firstMachine, scheduler);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null)));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);

        ServerLevel level = (ServerLevel) controller.getLevel();
        ServerPlayer player = testPlayer(level, controller.getBlockPos());
        FactoryControllerMenu menu = new FactoryControllerMenu(1, new Inventory(player, null), controller, player);
        player.containerMenu = menu;
        setField(ServerLevel.class, level, "players", List.of(player));
        assertThat(menu.machineName()).isEqualTo(firstMachine.displayNameKey());

        controller.invalidateFormedStructure();
        controller.setMachine(secondMachine);
        controller.serverTick();

        assertThat(controller.structureSnapshot().formed()).isTrue();
        assertThat(menu.isFormed()).isTrue();
        assertThat(menu.machineName()).isEqualTo(secondMachine.displayNameKey());
    }

    @Test
    void structure_and_factory_epochs_change_only_for_real_state_changes() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        StructureRuntime structure = new StructureRuntime(controller);
        DynamicMachine machine = new DynamicMachine(MMCR.id("epoch_machine"), "Epoch Machine",
                new BlockArray(Map.of()), MachineControllerSpec.defaultsFor(MMCR.id("epoch_machine")));
        long structureVersion = structure.version();
        FactoryRuntime factory = new FactoryRuntime();
        long factoryEpoch = factory.stateEpoch();

        assertThat(structure.setMachine(null)).isFalse();
        assertThat(structure.setMachine(machine)).isTrue();
        assertThat(structure.setMachine(machine)).isFalse();
        assertThat(structure.version()).isGreaterThan(structureVersion);
        assertThat(structure.setCriticalChunks(Set.of())).isFalse();
        assertThat(structure.setCriticalChunks(Set.of(new ChunkPos(1, 1)))).isTrue();
        assertThat(structure.setCriticalChunks(Set.of(new ChunkPos(1, 1)))).isFalse();

        assertThat(factory.setLaneLimit(1)).isFalse();
        assertThat(factory.stateEpoch()).isEqualTo(factoryEpoch);
        assertThat(factory.setLaneLimit(2)).isTrue();
        assertThat(factory.stateEpoch()).isGreaterThan(factoryEpoch);
    }

    @Test
    void structure_version_changes_only_for_a_block_inside_the_formed_bounds() {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        DynamicMachine machine = new DynamicMachine(MMCR.id("controller_version"), "Controller Version",
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))),
                MachineControllerSpec.defaultsFor(MMCR.id("controller_version")));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        RuntimeTestFixtures.formStructure(controller, machine);
        long formedVersion = controller.structureSnapshot().version();

        controller.handleStructureBlockChanged(controllerPos.offset(10, 0, 0));
        assertThat(controller.structureSnapshot().version()).isEqualTo(formedVersion);

        controller.handleStructureBlockChanged(controllerPos.offset(-1, 0, 0));
        assertThat(controller.structureSnapshot().version()).isGreaterThan(formedVersion);
        assertThat(controller.structureSnapshot().dirty()).isTrue();
    }

    @Test
    void unformed_structure_mismatch_waits_for_the_next_check_interval() {
        BlockPos controllerPos = BlockPos.ZERO;
        Identifier machineId = MMCR.id("controller_mismatch_interval");
        DynamicMachine machine = new DynamicMachine(machineId, "Controller Mismatch Interval",
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))),
                MachineControllerSpec.defaultsFor(machineId));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        RuntimeTestFixtures.formStructure(controller, machine);
        BlockPos structurePos = controller.getBlockPos().offset(controller.assemblyPattern(machine).pattern().keySet().iterator().next());
        controller.getLevel().setBlock(structurePos, Blocks.AIR.defaultBlockState(), 3);
        controller.setStructureCheckIntervalForTesting(40);
        controller.invalidateFormedStructure();

        controller.tickStructure((ServerLevel) controller.getLevel(), controllerPos);
        int invocationsAfterFirstCheck = controller.matcherInvocationCountForTesting();

        RuntimeTestFixtures.advanceGameTime(controller.getLevel());
        controller.tickStructure((ServerLevel) controller.getLevel(), controllerPos);

        assertThat(invocationsAfterFirstCheck).isGreaterThan(0);
        assertThat(controller.matcherInvocationCountForTesting()).isEqualTo(invocationsAfterFirstCheck);
    }

    @Test
    void formed_structure_uses_the_120_tick_safety_interval() {
        TestBootstrap.registerRuntimeBuiltins();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, MachineRegistry.getMachine(MMCR.id("test_cube")));
        ServerLevel level = (ServerLevel) controller.getLevel();
        int safetyChecks = controller.structureSafetyCheckCountForTesting();

        for (int tick = 0; tick < 119; tick++) {
            RuntimeTestFixtures.advanceGameTime(level);
            controller.tickStructure(level, controller.getBlockPos());
        }

        assertThat(controller.structureSafetyCheckCountForTesting()).isEqualTo(safetyChecks);

        RuntimeTestFixtures.advanceGameTime(level);
        controller.tickStructure(level, controller.getBlockPos());

        assertThat(controller.structureSafetyCheckCountForTesting()).isEqualTo(safetyChecks + 1);
    }

    @Test
    void stable_safety_check_keeps_the_published_runtime_snapshot() {
        TestBootstrap.registerRuntimeBuiltins();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, MachineRegistry.getMachine(MMCR.id("test_cube")));
        ServerLevel level = (ServerLevel) controller.getLevel();
        var published = controller.runtimeSnapshot();

        for (int tick = 0; tick < 120; tick++) {
            RuntimeTestFixtures.advanceGameTime(level);
            controller.tickStructure(level, controller.getBlockPos());
        }

        assertThat(controller.runtimeSnapshot()).isSameAs(published);
    }

    @Test
    void structure_work_records_dirty_component_and_chunk_state_transitions() {
        BlockPos controllerPos = BlockPos.ZERO;
        BlockPos inputPos = controllerPos.offset(-1, 0, 0);
        var input = RuntimeTestFixtures.itemInput(inputPos);
        Identifier machineId = MMCR.id("task7_component_transition");
        DynamicMachine machine = new DynamicMachine(machineId, "Task 7 Component Transition",
                new BlockArray(Map.of(new BlockPos(1, 0, 0),
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()))),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(), 1, false, false, 1);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, input);
        long chunkEpoch = controller.structureWorkSnapshotForTesting().chunkStateEpoch();
        BlockPos changedPos = controller.getBlockPos().offset(
                controller.structureSnapshot().pattern().pattern().keySet().iterator().next());

        controller.handleStructureBlockChanged(changedPos);

        assertThat(controller.structureWorkSnapshotForTesting().checkReason())
                .isEqualTo(StructureRuntime.CheckReason.DIRTY_EVENT);
        assertThat(controller.structureWorkSnapshotForTesting().componentRefreshRequired()).isTrue();

        controller.handleStructureChunkChanged((ServerLevel) controller.getLevel(), controller.getBlockPos());

        assertThat(controller.structureWorkSnapshotForTesting().chunkStateEpoch()).isEqualTo(chunkEpoch + 1);
    }

    @Test
    void formed_structure_block_change_starts_incremental_scan_immediately() {
        TestBootstrap.registerRuntimeBuiltins();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, MachineRegistry.getMachine(MMCR.id("test_cube")));
        ServerLevel level = (ServerLevel) controller.getLevel();
        controller.setStructureCheckIntervalForTesting(1000);
        controller.setStructureScanBatchesForTesting(2);
        BlockPos changedPos = controller.getBlockPos().offset(1, 0, 0);

        controller.handleStructureBlockChanged(changedPos);
        controller.tickStructure(level, controller.getBlockPos());

        assertThat(controller.structureScanCursorForTesting())
                .as("dirty event should start or advance a scan")
                .isGreaterThanOrEqualTo(0);
    }

    @Test
    void late_scan_mismatch_is_reused_by_diagnostic_without_a_second_full_matcher() throws Exception {
        TestBootstrap.registerRuntimeBuiltins();
        Identifier machineId = MMCR.id("late_scan_mismatch");
        Map<BlockPos, BlockPredicate> entries = new java.util.LinkedHashMap<>();
        Map<BlockPos, net.minecraft.world.level.block.Block> blocks = new java.util.LinkedHashMap<>();
        for (int index = 0; index < 10; index++) {
            BlockPos position = new BlockPos(index + 1, 0, 0);
            entries.put(position, new BlockPredicate.OfBlock(Blocks.STONE));
            blocks.put(position, index == 9 ? Blocks.DIRT : Blocks.STONE);
        }
        DynamicMachine machine = new DynamicMachine(machineId, "Late Scan Mismatch", new BlockArray(entries),
                MachineControllerSpec.defaultsFor(machineId));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, MachineRegistry.getMachine(MMCR.id("test_cube")));
        controller.setMachine(machine);
        for (var entry : blocks.entrySet()) {
            controller.getLevel().setBlock(entry.getKey(), entry.getValue().defaultBlockState(), 3);
        }
        controller.invalidateFormedStructure();
        int matcherInvocationsBeforeScan = controller.matcherInvocationCountForTesting();
        controller.setStructureScanBatchesForTesting(5);
        controller.setStructureCheckIntervalForTesting(1);
        int[] diagnostics = {0};
        controller.setStructureDiagnosticCallbackForTesting(() -> diagnostics[0]++);
        controller.requestImmediateStructureCheck(testPlayer((ServerLevel) controller.getLevel(), BlockPos.ZERO));

        for (int tick = 0; tick < 16 && diagnostics[0] == 0; tick++) {
            controller.tickStructure((ServerLevel) controller.getLevel(), BlockPos.ZERO);
            RuntimeTestFixtures.advanceGameTime(controller.getLevel());
        }

        assertThat(diagnostics[0]).isEqualTo(1);
        assertThat(controller.matcherInvocationCountForTesting()).isEqualTo(matcherInvocationsBeforeScan);
    }

    @Test
    void continuation_mismatch_resets_a_formed_structure_before_runtime_work() {
        TestBootstrap.registerRuntimeBuiltins();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, MachineRegistry.getMachine(MMCR.id("test_cube")));
        ServerLevel level = (ServerLevel) controller.getLevel();
        BlockPos changedRelative = null;
        int entryIndex = 0;
        for (BlockPos relative : controller.structureSnapshot().pattern().pattern().keySet()) {
            if (entryIndex++ == 1) changedRelative = relative;
        }
        BlockPos changedPos = controller.getBlockPos().offset(changedRelative);
        level.setBlock(changedPos, Blocks.DIRT.defaultBlockState(), 3);
        controller.setStructureScanBatchesForTesting(5);
        controller.handleStructureBlockChanged(changedPos);

        for (int tick = 0; tick < 20 && controller.structureSnapshot().formed(); tick++) {
            controller.tickStructure(level, controller.getBlockPos());
            RuntimeTestFixtures.advanceGameTime(level);
        }

        assertThat(controller.scanBatchCountForTesting()).isGreaterThan(1);
        assertThat(controller.structureSnapshot().formed()).isFalse();
    }

    @Test
    void every_structure_chunk_is_tracked_for_loaded_area_invalidation() {
        BlockPos controllerPos = new BlockPos(0, 1, 1);
        Identifier machineId = MMCR.id("controller_structure_chunk");
        DynamicMachine machine = new DynamicMachine(machineId, "Controller Structure Chunk",
                new BlockArray(Map.of(new BlockPos(20, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE))),
                MachineControllerSpec.defaultsFor(machineId));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        RuntimeTestFixtures.formStructure(controller, machine);
        BlockPos relative = controller.structureSnapshot().pattern().pattern().keySet().iterator().next();
        ChunkPos expectedChunk = new ChunkPos((controllerPos.getX() + relative.getX()) >> 4,
                (controllerPos.getZ() + relative.getZ()) >> 4);

        assertThat(controller.structureSnapshot().criticalChunks()).contains(expectedChunk);
    }

    @Test
    void unloading_a_critical_component_chunk_marks_the_formed_structure_unloaded() {
        BlockPos controllerPos = new BlockPos(0, 1, 1);
        BlockPos inputPos = controllerPos.offset(-1, 0, 0);
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(inputPos);
        DynamicMachine machine = new DynamicMachine(MMCR.id("controller_chunk"), "Controller Chunk",
                new BlockArray(Map.of(new BlockPos(1, 0, 0),
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()))),
                MachineControllerSpec.defaultsFor(MMCR.id("controller_chunk")),
                PortRequirementSpec.none(), List.of(), Map.of(), 1, false, false, 1);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, input);
        assertThat(controller.structureSnapshot().criticalChunks()).contains(new ChunkPos(-1, 0));
        long formedVersion = controller.structureSnapshot().version();

        RuntimeTestFixtures.setLoadedChunks(controller.getLevel(), Set.of(
                LevelStub.chunkKey(controllerPos.getX() >> 4, controllerPos.getZ() >> 4)));
        controller.handleStructureChunkChanged((net.minecraft.server.level.ServerLevel) controller.getLevel(), controllerPos);

        assertThat(controller.structureSnapshot().structureAreaLoaded()).isFalse();
        assertThat(controller.structureSnapshot().version()).isGreaterThan(formedVersion);
    }

    @Test
    void structure_runtime_version_round_trips_before_the_load_recheck() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        controller.setFormed(true);
        long savedVersion = controller.structureSnapshot().version();

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        controller.saveAdditional(output);

        MachineControllerBlockEntity restored = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));

        assertThat(restored.structureSnapshot().version()).isEqualTo(savedVersion);
        assertThat(restored.structureSnapshot().dirty()).isTrue();
    }

    @Test
    void negative_structure_runtime_version_loads_as_zero() {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        output.putLong("structure_runtime_version", -1L);

        MachineControllerBlockEntity restored = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));

        assertThat(restored.structureSnapshot().version()).isZero();
        assertThat(restored.structureSnapshot().dirty()).isTrue();
    }

    @Test
    void factory_runtime_survives_initial_structure_recheck_after_load() {
        Identifier machineId = MMCR.id("controller_factory_persistence");
        DynamicMachine machine = new DynamicMachine(machineId, "Factory Persistence",
                new BlockArray(Map.of(new BlockPos(1, 0, 0),
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()))),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(new BlockPos(1, 0, 0),
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null)));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("controller_factory_persistence_recipe"), machineId, 20,
                List.of(), List.of());
        RecipeRegistry.register(recipe);

        controller.serverTick();
        resolveSharedRequests(controller);
        assertThat(controller.runtimeSnapshot().factory().active()).isTrue();

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        controller.saveAdditional(output);

        MachineControllerBlockEntity restored = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        FactorySchedulerBlockEntity restoredScheduler = new FactorySchedulerBlockEntity(new BlockPos(1, 0, 0),
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(restored, machine, restoredScheduler);
        restored.invalidateFormedStructure();
        restored.setMachine(null);
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));
        restored.setMachine(machine);

        restored.tickStructure((ServerLevel) restored.getLevel(), restored.getBlockPos());

        assertThat(restored.runtimeSnapshot().factory().active()).isTrue();
        assertThat(restored.runtimeSnapshot().factory().presentationLanes().getFirst().recipeId())
                .isEqualTo(recipe.id().toString());
    }

    @Test
    void restored_factory_runtime_is_cleared_when_the_factory_component_is_removed_before_recheck() {
        Identifier machineId = MMCR.id("controller_factory_removed_after_load");
        BlockPos controllerPos = BlockPos.ZERO;
        BlockPos schedulerPos = controllerPos.offset(-1, 0, 0);
        DynamicMachine machine = new DynamicMachine(machineId, "Factory Removed After Load",
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.Any())),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, schedulerPos, BlockPos.ZERO, (String) null)));
        controller.setFormed(true);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("controller_factory_removed_after_load_recipe"), machineId, 20,
                List.of(), List.of());
        RecipeRegistry.register(recipe);
        controller.serverTick();
        resolveSharedRequests(controller);
        assertThat(controller.runtimeSnapshot().factory().active()).isTrue();
        RuntimeTestFixtures.setDirectSignal(controller.getLevel(), controllerPos, 15);
        controller.serverTick();

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        controller.saveAdditional(output);

        MachineControllerBlockEntity restored = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        FactorySchedulerBlockEntity restoredScheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(restored, machine, restoredScheduler);
        restored.invalidateFormedStructure();
        restored.setMachine(null);
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));
        restored.setMachine(machine);
        assertThat(restored.runtimeSnapshot().factory().presentationLanes())
                .anyMatch(thread -> thread.recipeId().equals(recipe.id().toString()));
        RuntimeTestFixtures.setDirectSignal(restored.getLevel(), controllerPos, 15);
        ItemInputBusBlockEntity replacement = RuntimeTestFixtures.itemInput(schedulerPos);
        RuntimeTestFixtures.replaceBlockEntity(restored, replacement);
        restored.onStructureBlockChanged(schedulerPos);
        for (int tick = 0; tick < 32 && !restored.structureSnapshot().formed(); tick++) {
            RuntimeTestFixtures.advanceGameTime(restored.getLevel());
            restored.tickStructure((ServerLevel) restored.getLevel(), controllerPos);
        }

        assertThat(restored.structureSnapshot().formed()).isTrue();
        assertThat(restored.runtimeSnapshot().factory().presentationLanes())
                .noneMatch(thread -> thread.recipeId().equals(recipe.id().toString()));
        MachineStateSnapshot state = new ControllerSyncRuntime().machineState(restored.runtimeSnapshot());
        assertThat(state.factoryControllerPresent()).isFalse();
        assertThat(state.active()).isFalse();
        assertThat(state.activeFactoryThreadCount()).isZero();
    }

    @Test
    void reforming_same_structure_refreshes_a_replaced_same_type_component_reference() {
        BlockPos controllerPos = BlockPos.ZERO;
        BlockPos componentPos = controllerPos.offset(-1, 0, 0);
        DynamicMachine machine = new DynamicMachine(MMCR.id("controller_component_replacement"),
                "Controller Component Replacement",
                new BlockArray(Map.of(new BlockPos(1, 0, 0),
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()))),
                MachineControllerSpec.defaultsFor(MMCR.id("controller_component_replacement")));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        ItemInputBusBlockEntity first = RuntimeTestFixtures.itemInput(componentPos);
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, first);
        CraftingRuntime crafting = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = new MachineRecipe(MMCR.id("controller_component_replacement_recipe"), machine.registryName(),
                20, List.of(), List.of(), List.of(), 0, 1);
        assertThat(crafting.start(recipe, 1).isCrafting()).isTrue();
        long capabilityVersion = controller.runtimeSnapshot().capabilityVersion();
        long componentStateVersion = controller.runtimeSnapshot().stateVersion();

        ItemInputBusBlockEntity replacement = RuntimeTestFixtures.itemInput(componentPos);
        RuntimeTestFixtures.replaceBlockEntity(controller, replacement);
        controller.onStructureBlockChanged(componentPos);
        for (int tick = 0; tick < 32 && controller.structureSnapshot().dirty(); tick++) {
            RuntimeTestFixtures.advanceGameTime(controller.getLevel());
            controller.tickStructure((net.minecraft.server.level.ServerLevel) controller.getLevel(), controllerPos);
        }

        assertThat(controller.structureSnapshot().formed()).isTrue();
        assertThat(controller.runtimeSnapshot().capabilityVersion()).isGreaterThan(capabilityVersion);
        assertThat(controller.runtimeSnapshot().stateVersion()).isGreaterThan(componentStateVersion);
        assertThat(crafting.versionsCurrent()).isFalse();
        assertThat(controller.componentRuntime().components()).extracting(component -> component.getContainer())
                .containsExactly(replacement);
        assertThat(controller.componentRuntime().capabilities())
                .containsExactlyElementsOf(replacement.capabilitySnapshot().capabilities());
    }

    @Test
    void structure_diagnostics_preserve_mismatch_and_port_requirement_details() {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        BlockPos relative = new BlockPos(1, 0, 0);
        DynamicMachine machine = new DynamicMachine(MMCR.id("controller_diagnostic"), "Controller Diagnostic",
                new BlockArray(Map.of(relative, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))),
                MachineControllerSpec.defaultsFor(MMCR.id("controller_diagnostic")));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        controller.setMachine(machine);
        controller.setLevel(LevelStub.create(Map.of(
                controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get(),
                controllerPos.offset(relative), Blocks.GOLD_BLOCK), List.of(controller)));

        String mismatch = MachineControllerBlockEntity.structureMismatchDiagnostic(
                machine, Direction.SOUTH, machine.pattern(), controller.getLevel(), controllerPos);
        assertThat(mismatch).contains("reason=blockMismatch")
                .contains("relativePos=" + relative)
                .contains("actualBlock=");

        String failure = MachineControllerBlockEntity.formationFailureDiagnostic(machine, Direction.SOUTH, controllerPos,
                new PortRequirementSpec.Failure("energy_input_hatch", 0, 1, OptionalInt.empty(),
                        PortRequirementSpec.FailureReason.MISSING));
        assertThat(failure).contains("reason=portRequirementMismatch")
                .contains("portId=energy_input_hatch")
                .contains("requiredMin=1");
    }

    @Test
    void count_ports_adds_each_combined_input_family_alias_once() throws Exception {
        BlockPos controllerPos = BlockPos.ZERO;
        BlockPos portPos = controllerPos.offset(1, 0, 0);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        CombinedPort port = new CombinedPort(portPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        controller.setLevel(LevelStub.create(Map.of(
                controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get(),
                portPos, ModBlocks.BLOCKS.get("item_input_bus").get()), List.of(controller, port)));

        var method = MachineControllerBlockEntity.class.getDeclaredMethod(
                "countPorts", BlockArray.class, CompiledMachinePattern.class, Direction.class);
        method.setAccessible(true);
        PortRequirementSpec.PortCounts counts = (PortRequirementSpec.PortCounts) method.invoke(
                controller,
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.Any())),
                null,
                Direction.SOUTH);

        assertThat(counts.count("combined_input_test")).isEqualTo(1);
        assertThat(counts.count("item_input_bus")).isEqualTo(1);
        assertThat(counts.count("fluid_input_hatch")).isEqualTo(1);
    }

    @Test
    void count_ports_adds_each_combined_output_family_alias_once() throws Exception {
        BlockPos controllerPos = BlockPos.ZERO;
        BlockPos portPos = controllerPos.offset(1, 0, 0);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        CombinedPort port = new CombinedPort(portPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState(), IOType.OUTPUT);
        controller.setLevel(LevelStub.create(Map.of(
                controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get(),
                portPos, ModBlocks.BLOCKS.get("item_input_bus").get()), List.of(controller, port)));

        var method = MachineControllerBlockEntity.class.getDeclaredMethod(
                "countPorts", BlockArray.class, CompiledMachinePattern.class, Direction.class);
        method.setAccessible(true);
        PortRequirementSpec.PortCounts counts = (PortRequirementSpec.PortCounts) method.invoke(
                controller,
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.Any())),
                null,
                Direction.SOUTH);

        assertThat(counts.count("combined_output_test")).isEqualTo(1);
        assertThat(counts.count("item_output_bus")).isEqualTo(1);
        assertThat(counts.count("fluid_output_hatch")).isEqualTo(1);
    }

    private static final class CombinedPort extends IOPortBlockEntity {
        private static final IOPortKind INPUT_KIND = combinedKind(IOType.INPUT, "combined_input_test");
        private static final IOPortKind OUTPUT_KIND = combinedKind(IOType.OUTPUT, "combined_output_test");
        private final IOPortKind kind;

        private CombinedPort(BlockPos pos, BlockState state) {
            this(pos, state, IOType.INPUT);
        }

        private CombinedPort(BlockPos pos, BlockState state, IOType ioType) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), pos, state);
            kind = ioType == IOType.INPUT ? INPUT_KIND : OUTPUT_KIND;
        }

        @Override
        public IOType ioType() {
            return kind.ioType();
        }

        @Override
        public IOPortKind kind() {
            return kind;
        }

        @Override
        public CapabilitySnapshot capabilitySnapshot() {
            return new CapabilitySnapshot(List.of());
        }
    }

    private static IOPortKind combinedKind(IOType ioType, String id) {
        return new PortKinds.CombinedKind(id, ioType, List.of(
                new PortFamilyDescriptor(PortFamilyIds.ITEM, ioType, 2,
                        List.of(ioType == IOType.INPUT ? "item_input_bus" : "item_output_bus")),
                new PortFamilyDescriptor(PortFamilyIds.FLUID, ioType, 2,
                        List.of(ioType == IOType.INPUT ? "fluid_input_hatch" : "fluid_output_hatch"))),
                CombinedPort::new, List.of(CapabilityFactories.ITEM_BUS, CapabilityFactories.FLUID_HATCH));
    }

    private static void resolveSharedRequests(MachineControllerBlockEntity controller) {
        if (controller.resourceDomain() != null) {
            SharedIoCoordinator.get((ServerLevel) controller.getLevel()).resolve(controller.resourceDomain());
        }
    }

    private static ServerPlayer testPlayer(ServerLevel level, BlockPos pos) throws Exception {
        ServerPlayer player = (ServerPlayer) unsafe().allocateInstance(ServerPlayer.class);
        setField(Entity.class, player, "level", level);
        setField(Entity.class, player, "position", net.minecraft.world.phys.Vec3.atCenterOf(pos));
        player.connection = (ServerGamePacketListenerImpl) unsafe().allocateInstance(TestConnection.class);
        return player;
    }

    private static sun.misc.Unsafe unsafe() throws ReflectiveOperationException {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (sun.misc.Unsafe) unsafeField.get(null);
    }

    private static void setField(Class<?> declaringClass, Object target, String name, Object value) throws Exception {
        Field field = null;
        Class<?> type = declaringClass;
        while (type != null && field == null) {
            try {
                field = type.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        if (field == null) throw new NoSuchFieldException(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void bind(Object deferredHolder, MenuType<FactoryControllerMenu> menuType) throws Exception {
        Class<?> type = deferredHolder.getClass();
        Field holder = null;
        while (type != null && holder == null) {
            try {
                holder = type.getDeclaredField("holder");
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        if (holder == null) throw new NoSuchFieldException("holder");
        holder.setAccessible(true);
        holder.set(deferredHolder, Holder.direct(menuType));
    }

    private static final class TestConnection extends ServerGamePacketListenerImpl {
        private TestConnection() {
            super(null, null, null, null);
        }

        @Override
        public void send(Packet<?> packet) {
        }
    }
}
