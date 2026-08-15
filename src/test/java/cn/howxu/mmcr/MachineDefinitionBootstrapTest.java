package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nibelungorum.BuiltinMachines;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineDefinitionBootstrapTest {

    @BeforeEach
    void resetDefinitions() {
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();
        MachineStructureRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
        MachineDefinitions.beginRegistryPhase();
    }

    @AfterEach
    void cleanup() {
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();
        MachineStructureRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
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
    void space_elevator_uses_its_own_controller_overlay() {
        BuiltinMachines.register();
        MachineDefinitions.bootstrapBuiltins();

        assertThat(MachineDefinitions.getRegistration(MMCR.id("space_elevator")).controllerSpec().frontTexture())
                .isEqualTo(MMCR.id("block/space_elevator_controller"));
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

    @Test
    void registrations_are_rejected_after_registry_freeze() {
        MachineDefinitions.register(testRegistration("press"));
        MachineDefinitions.freezeRegistryPhase();

        assertThatThrownBy(() -> MachineDefinitions.register(testRegistration("late")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("registry phase");
    }

    @Test
    void reload_does_not_change_startup_registration_count() {
        MachineDefinitions.register(testRegistration("press"));
        int registrationCount = MachineDefinitions.allRegistrations().size();

        DynamicContentReloadService.reload(candidate ->
                candidate.registerStructure(structure("mmcr:press")));

        assertThat(MachineDefinitions.allRegistrations()).hasSize(registrationCount);
    }

    @Test
    void reload_accepts_existing_machine_and_rejects_unknown_machine() {
        MachineDefinitions.register(testRegistration("press"));

        DynamicContentReloadService.begin().registerStructure(structure("mmcr:press"));

        assertThatThrownBy(() -> DynamicContentReloadService.begin()
                .registerStructure(structure("mmcr:unknown")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No startup machine registration");
    }

    private static MachineRegistration testRegistration(String path) {
        return MachineRegistration.builder(MMCR.id(path)).localizedName(path).build();
    }

    private static MachineStructureDefinition structure(String id) {
        return new MachineStructureDefinition(Identifier.parse(id), new cn.howxu.mmcr.api.machine.BlockArray(Map.of()),
                PortRequirementSpec.none(), List.of(), Map.of());
    }
}
