package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nibelungorum.BuiltinMachines;

import static org.assertj.core.api.Assertions.assertThat;

class MachineDefinitionBootstrapTest {

    @BeforeEach
    void resetDefinitions() {
        MachineDefinitions.clearForTesting();
    }

    @AfterEach
    void cleanup() {
        MachineDefinitions.clearForTesting();
        System.clearProperty("neoforge.enableGameTest");
    }

    @Test
    void runtime_bootstrap_does_not_register_gametest_machine_definitions() {
        System.setProperty("neoforge.enableGameTest", "true");

        BuiltinMachines.register();
        MMCR.registerGameTestMachineDefinitionsIfPresent();
        MachineDefinitions.bootstrapBuiltins();

        assertThat(MachineDefinitions.get(MMCR.id("blast_furnace"))).isNotNull();
        assertThat(MachineDefinitions.get(MMCR.id("test_cube"))).isNull();
        assertThat(MachineDefinitions.get(MMCR.id("controller_tick"))).isNull();
        assertThat(MachineDefinitions.get(MMCR.id("iron_compressor"))).isNull();
    }
}
