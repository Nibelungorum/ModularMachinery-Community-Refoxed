package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
}
