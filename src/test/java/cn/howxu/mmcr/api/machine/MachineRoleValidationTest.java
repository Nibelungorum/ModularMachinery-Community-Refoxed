package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineRoleValidationTest {

    @AfterEach
    void cleanup() {
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();
        MachineStructureRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void builderStoresImmutableMachineRolesAndAcceptedModuleIds() {
        Identifier moduleId = MMCR.id("module_machine");
        MachineRegistration normal = MachineRegistration.builder(MMCR.id("normal_machine")).build();
        MachineRegistration host = MachineRegistration.builder(MMCR.id("host_machine"))
                .host(moduleId)
                .pattern(patternWithCouplers(1))
                .build();
        MachineRegistration module = MachineRegistration.builder(moduleId)
                .module()
                .pattern(patternWithCouplers(1))
                .build();

        assertThat(normal.role()).isEqualTo(MachineRole.NORMAL);
        assertThat(normal.acceptedModuleIds()).isEmpty();
        assertThat(normal.isHost()).isFalse();
        assertThat(normal.isModule()).isFalse();
        assertThat(host.role()).isEqualTo(MachineRole.HOST);
        assertThat(host.acceptedModuleIds()).containsExactly(moduleId);
        assertThat(host.isHost()).isTrue();
        assertThat(module.role()).isEqualTo(MachineRole.MODULE);
        assertThat(module.acceptedModuleIds()).isEmpty();
        assertThat(module.isModule()).isTrue();

        assertThatThrownBy(() -> host.acceptedModuleIds().add(MMCR.id("other_module")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validatesCouplerCountsForNormalModuleAndHostMachines() {
        Identifier moduleId = MMCR.id("module_machine");
        MachineRegistration normal = MachineRegistration.builder(MMCR.id("normal_machine")).build();
        MachineRegistration host = MachineRegistration.builder(MMCR.id("host_machine"))
                .host(moduleId)
                .pattern(patternWithCouplers(1))
                .build();
        MachineRegistration module = MachineRegistration.builder(moduleId)
                .module()
                .pattern(patternWithCouplers(1))
                .build();

        MachineRoleValidator.validate(List.of(normal, host, module), id -> switch (id.getPath()) {
            case "normal_machine" -> normal;
            case "host_machine" -> host;
            case "module_machine" -> module;
            default -> null;
        });

        assertThatThrownBy(() -> MachineRoleValidator.validate(List.of(
                normal.withPattern(patternWithCouplers(1)), host.withPattern(patternWithCouplers(1)), module.withPattern(patternWithCouplers(1))), id -> switch (id.getPath()) {
            case "normal_machine" -> normal.withPattern(patternWithCouplers(1));
            case "host_machine" -> host.withPattern(patternWithCouplers(1));
            case "module_machine" -> module.withPattern(patternWithCouplers(1));
            default -> null;
        })).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NORMAL machine must declare 0 couplers");

        assertThatThrownBy(() -> MachineRoleValidator.validate(List.of(host.withPattern(patternWithCouplers(0)), module), id -> id.equals(moduleId) ? module : null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HOST machine must declare at least 1 coupler");

        assertThatThrownBy(() -> MachineRoleValidator.validate(List.of(module.withPattern(patternWithCouplers(2))), id -> module))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MODULE machine must declare exactly 1 coupler");
    }

    @Test
    void rejectsMutuallyExclusiveHostAndModuleRoles() {
        assertThatThrownBy(() -> MachineRegistration.builder(MMCR.id("bad_machine"))
                .host(MMCR.id("module_machine"))
                .module()
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Machine roles are mutually exclusive");
    }

    @Test
    void rejectsHostReferencesToMissingOrNonModuleMachines() {
        Identifier missing = MMCR.id("missing_module");
        MachineRegistration host = MachineRegistration.builder(MMCR.id("host_machine"))
                .host(missing)
                .pattern(patternWithCouplers(1))
                .build();

        assertThatThrownBy(() -> MachineRoleValidator.validate(List.of(host), id -> null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown module reference");

        MachineRegistration normal = MachineRegistration.builder(missing).build();
        assertThatThrownBy(() -> MachineRoleValidator.validate(List.of(host, normal), id -> normal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not reference a MODULE machine");
    }

    @Test
    void dynamicReloadValidationFailureRetainsPreviousSnapshot() {
        Identifier oldId = MMCR.id("old_machine");
        Identifier badHostId = MMCR.id("bad_host");
        Identifier moduleId = MMCR.id("module_machine");
        MachineDefinitions.register(MachineRegistration.builder(oldId).build());
        MachineDefinitions.register(MachineRegistration.builder(badHostId).host(moduleId).build());
        MachineDefinitions.register(MachineRegistration.builder(moduleId).module().build());
        DynamicContentReloadService.reload(candidate -> candidate.registerStructure(structure(oldId, patternWithCouplers(0))));

        assertThatThrownBy(() -> DynamicContentReloadService.reload(candidate -> {
            candidate.registerStructure(structure(badHostId, patternWithCouplers(0)));
            candidate.registerStructure(structure(moduleId, patternWithCouplers(1)));
        })).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HOST machine must declare at least 1 coupler");

        assertThat(MachineRegistry.getMachine(oldId)).isNotNull();
        assertThat(MachineRegistry.getMachine(badHostId)).isNull();
    }

    private static MachineStructureDefinition structure(Identifier id, BlockArray pattern) {
        return new MachineStructureDefinition(id, pattern, PortRequirementSpec.none(), List.of(), Map.of());
    }

    private static BlockArray patternWithCouplers(int count) {
        Map<BlockPos, BlockPredicate> pattern = new java.util.LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            pattern.put(new BlockPos(index, 0, 0), BlockPredicate.machineCoupler());
        }
        pattern.put(new BlockPos(0, 1, 0), new BlockPredicate.Any());
        return new BlockArray(pattern);
    }
}
