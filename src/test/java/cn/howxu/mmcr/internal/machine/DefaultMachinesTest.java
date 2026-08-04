package cn.howxu.mmcr.internal.machine;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import org.nibelungorum.DefaultMachines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMachinesTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        MachineRegistry.clearForTesting();
    }

    @Test
    void ensureRegistered_registers_default_blast_furnace_once() {
        DefaultMachines.ensureRegistered();
        DefaultMachines.ensureRegistered();

        var machine = MachineRegistry.getMachine(MMCR.id("blast_furnace"));

        assertThat(machine).isNotNull();
        assertThat(machine.localizedName()).isEqualTo("高炉");
        assertThat(machine.controller().id()).isEqualTo(MMCR.id("blast_furnace_controller"));
        assertThat(machine.pattern().pattern()).hasSize(26);
        assertThat(machine.pattern().get(BlockPos.ZERO))
                .isEqualTo(new BlockPredicate.OfBlock(ModBlocks.controllerFor(machine).get()));
        assertThat(machine.pattern().get(new BlockPos(0, 0, -1))).isNull();
        assertThat(machine.pattern().get(new BlockPos(0, -1, -1)))
                .isEqualTo(new BlockPredicate.OfBlock(ModBlocks.CASING.get()));
        assertThat(machine.pattern().get(new BlockPos(-1, 0, -2)))
                .isEqualTo(new BlockPredicate.OfBlock(ModBlocks.CASING.get()));
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)))
                .isEqualTo(portPredicate());
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)).matches(ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)).matches(ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)).matches(ModBlocks.BLOCKS.get("fluid_input_hatch").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)).matches(ModBlocks.BLOCKS.get("fluid_output_hatch").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)).matches(ModBlocks.BLOCKS.get("energy_input_hatch").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)).matches(ModBlocks.BLOCKS.get("energy_output_hatch").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, 1, 0)))
                .isEqualTo(new BlockPredicate.OfBlock(ModBlocks.CASING.get()));
    }

    @Test
    void default_blast_furnace_raw_pattern_faces_south() {
        Machine machine = DefaultMachines.blastFurnace(
                ModBlocks.CASING.get(),
                ModBlocks.BLOCKS.get("item_input_bus").get(),
                ModBlocks.BLOCKS.get("item_output_bus").get(),
                ModBlocks.BLOCKS.get("fluid_input_hatch").get(),
                ModBlocks.BLOCKS.get("fluid_output_hatch").get(),
                ModBlocks.BLOCKS.get("energy_input_hatch").get(),
                ModBlocks.BLOCKS.get("energy_output_hatch").get());
        BlockPos controller = new BlockPos(10, 4, 10);
        Map<BlockPos, Block> blocks = new HashMap<>();
        for (var entry : machine.pattern().pattern().entrySet()) {
            blocks.put(controller.offset(entry.getKey()), switch (entry.getValue()) {
                case BlockPredicate.OfBlock of -> of.block();
                case BlockPredicate.AnyOf ignored -> ModBlocks.BLOCKS.get("item_input_bus").get();
                default -> ModBlocks.CASING.get();
            });
        }

        assertThat(StructureMatcher.matches(machine.pattern(), LevelStub.create(blocks), controller, Direction.SOUTH))
                .as("默认三层模板中 C 在下方，原始坐标应识别为向南/向下")
                .isTrue();
        assertThat(StructureMatcher.matches(machine.pattern(), LevelStub.create(blocks), controller, Direction.NORTH))
                .as("同一原始模板不应再识别为向北/向上")
                .isFalse();
    }

    private static BlockPredicate portPredicate() {
        return new BlockPredicate.AnyOf(java.util.List.of(
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_output_bus").get()),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("fluid_input_hatch").get()),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("fluid_output_hatch").get()),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("energy_input_hatch").get()),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("energy_output_hatch").get())));
    }
}
