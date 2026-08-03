package cn.howxu.mmcr.internal.machine;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
        assertThat(machine.pattern().pattern()).hasSize(26);
        assertThat(machine.pattern().get(BlockPos.ZERO))
                .isEqualTo(new BlockPredicate.OfBlock(ModBlocks.CONTROLLER.get()));
        assertThat(machine.pattern().get(new BlockPos(0, 0, -1))).isNull();
        assertThat(machine.pattern().get(new BlockPos(0, -1, -1)))
                .isEqualTo(new BlockPredicate.OfBlock(ModBlocks.CASING.get()));
        assertThat(machine.pattern().get(new BlockPos(-1, 0, -2)))
                .isEqualTo(new BlockPredicate.OfBlock(ModBlocks.CASING.get()));
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)))
                .isEqualTo(portPredicate());
        assertThat(machine.pattern().get(new BlockPos(-1, 1, 0)))
                .isEqualTo(new BlockPredicate.OfBlock(ModBlocks.CASING.get()));
    }

    private static BlockPredicate portPredicate() {
        return new BlockPredicate.AnyOf(java.util.List.of(
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("io_port_item_basic").get()),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("io_port_fluid_basic").get())));
    }
}
