package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nibelungorum.BuiltinMachines;
import net.minecraft.resources.Identifier;

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

        assertThat(MachineDefinitions.getRegistration(MMCR.id("blast_furnace"))).isNotNull();
        assertThat(MachineDefinitions.getRegistration(MMCR.id("alloy_furnace"))).isNotNull();
        assertThat(MachineDefinitions.getRegistration(MMCR.id("test_cube"))).isNull();
        assertThat(MachineDefinitions.getRegistration(MMCR.id("controller_tick"))).isNull();
        assertThat(MachineDefinitions.getRegistration(MMCR.id("iron_compressor"))).isNull();
    }

    @Test
    void builtin_cracker_definition_allows_vertical_controller_placement() {
        BuiltinMachines.register();
        MachineDefinitions.bootstrapBuiltins();

        assertThat(MachineDefinitions.getRegistration(MMCR.id("cracker")).controllerSpec().allowVerticalFacing()).isTrue();
    }

    @Test
    void startupRegistrationsRejectDuplicateIds() {
        var staticId = Identifier.parse("mmcr:static_machine");
        MachineDefinitions.register(MachineRegistration.builder(staticId).localizedName("Static").build());

        assertThat(MachineDefinitions.getRegistration(staticId)).isNotNull();
        assertThat(MachineDefinitions.allRegistrations()).extracting(MachineRegistration::id)
                .containsExactly(staticId);
        assertThatThrownBy(() -> MachineDefinitions.register(MachineRegistration.builder(staticId).localizedName("Conflict").build()))
                .isInstanceOf(IllegalStateException.class);
    }
}
