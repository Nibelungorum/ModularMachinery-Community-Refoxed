package cn.howxu.mmcr.internal.machine;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.registry.MMCRRegistries;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MMCRDefaultMachinesTest {

    @AfterEach
    void cleanup() {
        MachineRegistry.clearForTesting();
    }

    @Test
    void ensureRegistered_registers_default_iron_compressor_once() {
        MMCRDefaultMachines.ensureRegistered();
        MMCRDefaultMachines.ensureRegistered();

        var machine = MachineRegistry.getMachine(MMCR.id("iron_compressor"));

        assertThat(machine).isNotNull();
        assertThat(machine.localizedName()).isEqualTo("Iron Compressor");
        assertThat(machine.pattern().pattern()).hasSize(8);
        assertThat(machine.pattern().get(BlockPos.ZERO)).isNull();
        assertThat(machine.pattern().get(new BlockPos(-1, 0, -1)))
                .isEqualTo(new BlockPredicate.OfBlock(MMCRRegistries.CASING_BLOCK.get()));
    }
}
