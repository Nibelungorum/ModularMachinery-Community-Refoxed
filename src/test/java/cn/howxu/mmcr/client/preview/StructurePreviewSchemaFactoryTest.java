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
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
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
}
