package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
                PortRequirementSpec.none(), List.of(), cn.howxu.mmcr.api.machine.MachineStructureRequirements.EMPTY);
    }
}
