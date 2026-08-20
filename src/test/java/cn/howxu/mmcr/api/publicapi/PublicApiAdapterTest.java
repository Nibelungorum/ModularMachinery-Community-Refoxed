package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition;
import cn.howxu.mmcr.internal.api.PublicMachineAdapter;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the internal adapter is the only public-to-runtime structure bridge.
 * @author howxu <dev@howxu.cn>
 */
class PublicApiAdapterTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void adapters_preserve_definition_structure_and_identifiers() {
        var machineId = MMCR.id("adapter_machine");
        var definition = MachineBuilder.machine(machineId).build();
        MachineStructureDefinition structure = MachineStructureBuilder.structure()
                .fullStructure(stage -> stage.pattern(pattern -> pattern.layer("F")
                        .where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F')))
                .build(machineId);

        assertThat(PublicMachineAdapter.toRegistration(definition).id()).isEqualTo(machineId);
        assertThat(PublicMachineAdapter.toStructureDefinition(structure).machineId()).isEqualTo(machineId);
        assertThat(PublicMachineAdapter.toDynamicMachine(definition, structure).registryName()).isEqualTo(machineId);
    }
}
