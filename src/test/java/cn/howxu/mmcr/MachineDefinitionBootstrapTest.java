package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nibelungorum.BuiltinMachines;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import net.minecraft.resources.Identifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(MachineDefinitions.get(MMCR.id("alloy_furnace"))).isNotNull();
        assertThat(MachineDefinitions.get(MMCR.id("test_cube"))).isNull();
        assertThat(MachineDefinitions.get(MMCR.id("controller_tick"))).isNull();
        assertThat(MachineDefinitions.get(MMCR.id("iron_compressor"))).isNull();
    }

    @Test
    void builtin_cracker_definition_allows_vertical_controller_placement() {
        BuiltinMachines.register();
        MachineDefinitions.bootstrapBuiltins();

        assertThat(MachineDefinitions.get(MMCR.id("cracker")).controller().allowVerticalFacing()).isTrue();
    }

    @Test
    void dynamicDefinitionsMergeWithStaticAndRejectStaticIds() {
        var staticId = Identifier.parse("mmcr:static_machine");
        var dynamicId = Identifier.parse("mmcr:dynamic_machine");
        MachineDefinitions.register(new DynamicMachine(staticId, "Static", new BlockArray(Map.of())));

        MachineDefinitions.replaceDynamic(Map.of(dynamicId,
                new DynamicMachine(dynamicId, "Dynamic", new BlockArray(Map.of()))));

        assertThat(MachineDefinitions.get(staticId)).isNotNull();
        assertThat(MachineDefinitions.get(dynamicId)).isNotNull();
        assertThat(MachineDefinitions.all()).extracting(Machine::registryName)
                .containsExactly(staticId, dynamicId);
        assertThatThrownBy(() -> MachineDefinitions.replaceDynamic(Map.of(staticId,
                new DynamicMachine(staticId, "Conflict", new BlockArray(Map.of())))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MachineDefinitions.get(dynamicId)).isNotNull();
    }
}
