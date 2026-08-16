package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies schema construction from machine structures.
 *
 * @author howxu <dev@howxu.cn>
 */
class StructurePreviewSchemaFactoryTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void factory_uses_preferred_states_and_preserves_controller_relative_positions() {
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                new BlockPos(1, 2, -1), new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK)));
        DynamicMachine machine = new DynamicMachine(MMCR.id("preview_machine"), "machine.preview", pattern);

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(machine);

        assertThat(schema.machineId()).isEqualTo(MMCR.id("preview_machine"));
        assertThat(schema.stateAt(BlockPos.ZERO)).isEqualTo(Blocks.IRON_BLOCK.defaultBlockState());
        assertThat(schema.stateAt(new BlockPos(1, 2, -1))).isEqualTo(Blocks.GOLD_BLOCK.defaultBlockState());
    }

    @Test
    void factory_skips_unrepresentable_predicates_without_failing_the_schema() {
        BlockArray pattern = new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.Any()));
        DynamicMachine machine = new DynamicMachine(MMCR.id("unrepresentable"), "machine.unrepresentable", pattern);

        assertThat(new StructurePreviewSchemaFactory().create(machine).states()).isEmpty();
    }

    @Test
    void factory_rejects_machines_without_structure_stages() {
        BlockArray pattern = new BlockArray(Map.of());
        Machine machine = new Machine() {
            @Override
            public net.minecraft.resources.Identifier registryName() {
                return MMCR.id("empty_stages");
            }

            @Override
            public BlockArray pattern() {
                return pattern;
            }

            @Override
            public MachineControllerSpec controller() {
                return MachineControllerSpec.defaultsFor(registryName());
            }

            @Override
            public List<cn.howxu.mmcr.api.machine.MachineStructureStage> structureStages() {
                return List.of();
            }
        };

        assertThatThrownBy(() -> new StructurePreviewSchemaFactory().create(machine))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void factory_keeps_level_slots_only_for_resolved_states() {
        BlockPos resolved = BlockPos.ZERO;
        BlockPos unresolved = new BlockPos(1, 0, 0);
        MachineStructureStage stage = new MachineStructureStage(1, new BlockArray(Map.of(
                resolved, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                unresolved, new BlockPredicate.Any())), PortRequirementSpec.none(), PortTierRequirementSpec.none(),
                List.of(), Map.of(), Map.of(resolved, MMCR.id("coil"), unresolved, MMCR.id("casing")));

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, MMCR.id("slots"),
                StructurePreviewVariantSelection.defaults());

        assertThat(schema.levelSlots()).containsOnly(Map.entry(resolved, MMCR.id("coil")));
    }

    @Test
    void factory_uses_only_the_complete_first_stage_of_a_multi_stage_machine() {
        BlockPos firstStageOnly = new BlockPos(1, 0, 0);
        MachineStructureStage firstStage = new MachineStructureStage(1, new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                firstStageOnly, new BlockPredicate.OfBlock(Blocks.COPPER_BLOCK))), PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), Map.of(), Map.of(firstStageOnly, MMCR.id("first_slot")));
        BlockPos finalStageOnly = new BlockPos(2, 0, 0);
        MachineStructureStage finalStage = new MachineStructureStage(2, new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                finalStageOnly, new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK))), PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), Map.of(), Map.of(finalStageOnly, MMCR.id("final_slot")));
        Machine machine = machineWithStages(new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.EMERALD_BLOCK))), List.of(firstStage, finalStage));

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(machine);

        assertThat(schema.states()).containsOnly(
                Map.entry(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState()),
                Map.entry(firstStageOnly, Blocks.COPPER_BLOCK.defaultBlockState()));
        assertThat(schema.stateAt(finalStageOnly)).isNull();
        assertThat(schema.levelSlots()).containsOnly(Map.entry(firstStageOnly, MMCR.id("first_slot")));
    }

    @Test
    void factory_stage_overload_keeps_the_preferred_state_for_default_selection() {
        BlockPredicate predicate = new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)));
        BlockState preferredState = predicate.preferredState().orElseThrow();
        MachineStructureStage stage = new MachineStructureStage(1, new BlockArray(Map.of(BlockPos.ZERO, predicate)),
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of(), Map.of());

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, MMCR.id("default_variant"),
                StructurePreviewVariantSelection.defaults());

        assertThat(schema.stateAt(BlockPos.ZERO)).isEqualTo(preferredState);
    }

    @Test
    void factory_orients_controller_face_away_from_the_structure() {
        BlockState controller = ModBlocks.CONTROLLER.get().defaultBlockState();
        MachineStructureStage stage = new MachineStructureStage(1, new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(controller.getBlock()),
                new BlockPos(0, 0, -1), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))),
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of(), Map.of());

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, MMCR.id("preview_machine"),
                StructurePreviewVariantSelection.defaults());

        assertThat(schema.stateAt(BlockPos.ZERO).getValue(MachineControllerBlock.FACING)).isEqualTo(Direction.SOUTH);
    }

    private static Machine machineWithStages(BlockArray pattern, List<MachineStructureStage> stages) {
        return new Machine() {
            @Override
            public Identifier registryName() {
                return MMCR.id("multi_stage");
            }

            @Override
            public BlockArray pattern() {
                return pattern;
            }

            @Override
            public MachineControllerSpec controller() {
                return MachineControllerSpec.defaultsFor(registryName());
            }

            @Override
            public List<MachineStructureStage> structureStages() {
                return stages;
            }
        };
    }
}
