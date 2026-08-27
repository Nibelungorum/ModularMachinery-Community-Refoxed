package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.CompiledMachinePattern;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextRegistry;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.network.PktControllerScreenTextPayload;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.ControllerSyncRuntime;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextSnapshot;
import cn.howxu.mmcr.internal.runtime.MachineStateSnapshot;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Final controller structure and preview behavior tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineControllerBlockEntityTest {
    private final List<ControllerScreenTextRegistry.Registration> textRegistrations = new ArrayList<>();

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.MACHINE_CONTROLLER,
                new MenuType<>((containerId, inventory) -> MachineControllerMenu.clientOpen(containerId, inventory),
                        FeatureFlags.VANILLA_SET));
        bind(ModUIs.FACTORY_CONTROLLER,
                new MenuType<>((containerId, inventory) -> FactoryControllerMenu.clientOpen(containerId, inventory),
                        FeatureFlags.VANILLA_SET));
    }

    @BeforeEach
    void openTextRegistration() {
        PublicApiBootstrap.clearForTesting();
        PublicApiBootstrap.begin();
    }

    @AfterEach
    void closeTextRegistration() {
        textRegistrations.forEach(ControllerScreenTextRegistry.Registration::unregister);
        textRegistrations.clear();
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
    void runtime_update_invokes_matching_handlers_and_coalesces_same_component_updates() throws Exception {
        Identifier machineId = MMCR.id("controller_text_runtime");
        MachineControllerBlockEntity controller = textController(machineId);
        MachineControllerRuntime runtime = runtimeOf(controller);
        ServerLevel level = (ServerLevel) controller.getLevel();
        ServerPlayer player = player(level, controller.getBlockPos());
        player.containerMenu = new MachineControllerMenu(1, new Inventory(null, null), controller);
        setPlayers(level, List.of(player));
        AtomicInteger invocations = new AtomicInteger();
        textRegistrations.add(ControllerScreenTextRegistry.register(machineId, context -> {
            invocations.incrementAndGet();
            assertThat(context.controllerPos()).isEqualTo(controller.getBlockPos());
            context.screenText().append(ControllerScreenTextScope.CONTROLLER,
                    MMCR.id("runtime_line"), Component.literal("same"));
        }));

        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());
        long revision = runtime.screenText().revision();
        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());

        assertThat(invocations).hasValue(2);
        assertThat(runtime.screenText().revision()).isEqualTo(revision).isEqualTo(1L);
        assertThat(runtime.screenText().snapshot().lines()).singleElement()
                .satisfies(line -> assertThat(line.text().getString()).isEqualTo("same"));
        assertThat(textPackets(player)).hasSize(1);
    }

    @Test
    void open_controller_menus_receive_only_matching_revisioned_text_snapshots() throws Exception {
        MachineControllerBlockEntity controller = textController(MMCR.id("controller_text_audience"));
        runtimeOf(controller).screenText().append(ControllerScreenTextScope.CONTROLLER,
                MMCR.id("audience_line"), Component.literal("visible"));
        ServerLevel level = (ServerLevel) controller.getLevel();
        ServerPlayer ordinary = player(level, controller.getBlockPos());
        ordinary.containerMenu = new MachineControllerMenu(1, new Inventory(null, null), controller);
        ServerPlayer factory = player(level, controller.getBlockPos());
        factory.containerMenu = new FactoryControllerMenu(2, new Inventory(null, null), controller);
        ServerPlayer wrongPosition = player(level, controller.getBlockPos());
        wrongPosition.containerMenu = new MachineControllerMenu(3, new Inventory(null, null),
                controller.getBlockPos().above());
        ServerPlayer closed = player(level, controller.getBlockPos());
        closed.containerMenu = closedMenu();
        setPlayers(level, List.of(ordinary, factory, wrongPosition, closed));

        invokeSyncOpenText(controller);

        assertThat(textPackets(ordinary)).singleElement()
                .satisfies(packet -> assertThat(packet.controllerPos()).isEqualTo(controller.getBlockPos()))
                .satisfies(packet -> assertThat(packet.lines()).hasSize(1));
        assertThat(textPackets(factory)).hasSize(1);
        assertThat(textPackets(wrongPosition)).isEmpty();
        assertThat(textPackets(closed)).isEmpty();

        invokeSyncOpenText(controller);
        assertThat(textPackets(ordinary)).hasSize(1);
        assertThat(textPackets(factory)).hasSize(1);
    }

    @Test
    void ordinary_and_factory_menu_open_paths_send_the_current_text_snapshot() throws Exception {
        MachineControllerBlockEntity controller = textController(MMCR.id("controller_text_open"));
        runtimeOf(controller).screenText().append(ControllerScreenTextScope.CONTROLLER,
                MMCR.id("open_line"), Component.literal("open"));
        ServerLevel level = (ServerLevel) controller.getLevel();

        ServerPlayer ordinary = player(level, controller.getBlockPos());
        controller.sendRecipeLockState(ordinary);
        assertThat(textPackets(ordinary)).singleElement()
                .satisfies(packet -> assertThat(packet.controllerPos()).isEqualTo(controller.getBlockPos()))
                .satisfies(packet -> assertThat(packet.revision()).isEqualTo(1L));

        MachineControllerBlockEntity factoryController = factoryTextController(MMCR.id("controller_text_factory_open"));
        runtimeOf(factoryController).screenText().append(ControllerScreenTextScope.CONTROLLER,
                MMCR.id("factory_open_line"), Component.literal("factory open"));
        ServerPlayer factory = player((ServerLevel) factoryController.getLevel(), factoryController.getBlockPos());
        new FactoryControllerMenu(2, new Inventory(null, null), factoryController, factory);
        assertThat(textPackets(factory)).singleElement()
                .satisfies(packet -> assertThat(packet.controllerPos()).isEqualTo(factoryController.getBlockPos()))
                .satisfies(packet -> assertThat(packet.lines()).singleElement()
                        .satisfies(line -> assertThat(line.text().getString()).isEqualTo("factory open")));
    }

    @Test
    void empty_text_snapshot_reaches_matching_menus_after_external_text_is_cleared() throws Exception {
        MachineControllerBlockEntity controller = textController(MMCR.id("controller_text_clear"));
        MachineControllerRuntime runtime = runtimeOf(controller);
        runtime.screenText().append(ControllerScreenTextScope.CONTROLLER,
                MMCR.id("clear_line"), Component.literal("clear"));
        ServerLevel level = (ServerLevel) controller.getLevel();
        ServerPlayer player = player(level, controller.getBlockPos());
        player.containerMenu = new MachineControllerMenu(1, new Inventory(null, null), controller);
        setPlayers(level, List.of(player));
        invokeSyncOpenText(controller);

        runtime.screenText().clear(ControllerScreenTextScope.CONTROLLER);
        invokeSyncOpenText(controller);

        assertThat(textPackets(player)).hasSize(2);
        assertThat(textPackets(player).getLast().revision()).isGreaterThan(1L);
        assertThat(textPackets(player).getLast().lines()).isEmpty();
    }

    @Test
    void completed_recipe_clears_operation_text_but_keeps_controller_text() throws Exception {
        Identifier machineId = MMCR.id("controller_text_operation");
        MachineControllerBlockEntity controller = textController(machineId);
        MachineControllerRuntime runtime = runtimeOf(controller);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("controller_text_operation_recipe"), machineId, 1,
                List.of(), List.of());
        assertThat(runtime.craftingRuntime().start(recipe, 1).isCrafting()).isTrue();
        runtime.screenText().append(ControllerScreenTextScope.CONTROLLER,
                MMCR.id("persistent_line"), Component.literal("persistent"));
        runtime.screenText().append(ControllerScreenTextScope.OPERATION,
                MMCR.id("operation_line"), Component.literal("operation"));

        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());

        assertThat(runtime.screenText().snapshot().lines())
                .extracting(ControllerScreenTextSnapshot.Line::scope)
                .containsExactly(ControllerScreenTextScope.CONTROLLER);
    }

    @Test
    void reset_machine_clears_external_text_and_advances_revision() throws Exception {
        MachineControllerBlockEntity controller = textController(MMCR.id("controller_text_reset"));
        MachineControllerRuntime runtime = runtimeOf(controller);
        runtime.screenText().append(ControllerScreenTextScope.CONTROLLER,
                MMCR.id("reset_line"), Component.literal("reset"));
        runtime.screenText().append(ControllerScreenTextScope.OPERATION,
                MMCR.id("reset_operation"), Component.literal("operation"));
        long revision = runtime.screenText().revision();

        controller.invalidateFormedStructure();

        assertThat(runtime.screenText().snapshot().lines()).isEmpty();
        assertThat(runtime.screenText().revision()).isGreaterThan(revision);
    }

    @Test
    void unbinding_configured_machine_clears_external_text_and_advances_revision() throws Exception {
        MachineControllerBlockEntity controller = textController(MMCR.id("controller_text_unbind"));
        MachineControllerRuntime runtime = runtimeOf(controller);
        runtime.screenText().append(ControllerScreenTextScope.CONTROLLER,
                MMCR.id("unbind_line"), Component.literal("unbind"));
        long revision = runtime.screenText().revision();

        controller.setMachine(null);

        assertThat(runtime.screenText().snapshot().lines()).isEmpty();
        assertThat(runtime.screenText().revision()).isGreaterThan(revision);
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

    private static MachineControllerBlockEntity textController(Identifier machineId) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        DynamicMachine machine = new DynamicMachine(machineId, "text test",
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.Any())),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, false, 1);
        RuntimeTestFixtures.formStructure(controller, machine);
        return controller;
    }

    private static MachineControllerBlockEntity factoryTextController(Identifier machineId) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos schedulerPos = controller.getBlockPos().offset(1, 0, 0);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        DynamicMachine machine = new DynamicMachine(machineId, "factory text test",
                new BlockArray(Map.of(new BlockPos(1, 0, 0),
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()))),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, schedulerPos, BlockPos.ZERO, (String) null)));
        RuntimeTestFixtures.republish(controller);
        return controller;
    }

    private static MachineControllerRuntime runtimeOf(MachineControllerBlockEntity controller) throws Exception {
        Field field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
        field.setAccessible(true);
        return (MachineControllerRuntime) field.get(controller);
    }

    private static void invokeSyncOpenText(MachineControllerBlockEntity controller) throws Exception {
        Method method = MachineControllerBlockEntity.class.getDeclaredMethod("syncOpenControllerScreenText");
        method.setAccessible(true);
        method.invoke(controller);
    }

    private static List<PktControllerScreenTextPayload> textPackets(ServerPlayer player) {
        return ((TestConnection) player.connection).packets.stream()
                .filter(packet -> packet instanceof ClientboundCustomPayloadPacket)
                .map(packet -> ((ClientboundCustomPayloadPacket) packet).payload())
                .filter(PktControllerScreenTextPayload.class::isInstance)
                .map(PktControllerScreenTextPayload.class::cast)
                .toList();
    }

    private static AbstractContainerMenu closedMenu() {
        return new AbstractContainerMenu(null, 0) {
            @Override
            public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(net.minecraft.world.entity.player.Player player) {
                return true;
            }
        };
    }

    private static ServerPlayer player(ServerLevel level, BlockPos pos) throws Exception {
        ServerPlayer player = (ServerPlayer) unsafe().allocateInstance(ServerPlayer.class);
        setField(Entity.class, player, "level", level);
        setField(Entity.class, player, "position", Vec3.atCenterOf(pos));
        TestConnection connection = (TestConnection) unsafe().allocateInstance(TestConnection.class);
        connection.packets = new ArrayList<>();
        player.connection = connection;
        return player;
    }

    private static void setPlayers(ServerLevel level, List<ServerPlayer> players) throws Exception {
        setField(ServerLevel.class, level, "players", players);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setField(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = null;
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

    private static void bind(Object deferredHolder, MenuType<?> menuType) throws Exception {
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
        private List<Packet<?>> packets;

        private TestConnection() {
            super(null, null, null, null);
        }

        @Override
        public void send(Packet<?> packet) {
            packets.add(packet);
        }
    }
}
