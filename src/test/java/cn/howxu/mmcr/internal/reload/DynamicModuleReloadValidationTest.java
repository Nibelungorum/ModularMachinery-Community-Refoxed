package cn.howxu.mmcr.internal.reload;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author howxu <dev@howxu.cn>
 */
class DynamicModuleReloadValidationTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();
        MachineStructureRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void invalid_candidate_role_coupler_or_module_reference_retains_previous_snapshot() {
        Identifier oldId = MMCR.id("old_machine");
        Identifier hostId = MMCR.id("host_machine");
        Identifier moduleId = MMCR.id("module_machine");
        MachineDefinitions.register(MachineRegistration.builder(oldId).build());
        MachineDefinitions.register(MachineRegistration.builder(hostId).host(moduleId).build());
        MachineDefinitions.register(MachineRegistration.builder(moduleId).module().build());
        DynamicContentReloadService.reload(candidate -> candidate.registerStructure(structure(oldId, 0)));

        assertThatThrownBy(() -> DynamicContentReloadService.reload(candidate -> {
            candidate.registerStructure(structure(hostId, 0));
            candidate.registerStructure(structure(moduleId, 1));
        })).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(hostId.toString())
                .hasMessageContaining("at least 1 coupler");
        assertThat(MachineRegistry.getMachine(oldId)).isNotNull();

        assertThatThrownBy(() -> DynamicContentReloadService.reload(candidate -> {
            candidate.registerStructure(structure(hostId, 1));
            candidate.registerStructure(structure(moduleId, 2));
        })).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(moduleId.toString())
                .hasMessageContaining("exactly 1 coupler");
        assertThat(MachineRegistry.getMachine(oldId)).isNotNull();

        assertThatThrownBy(() -> DynamicContentReloadService.reload(candidate ->
                candidate.registerStructure(structure(hostId, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(hostId.toString())
                .hasMessageContaining("Unknown module reference");
        assertThat(MachineRegistry.getMachine(oldId)).isNotNull();
    }

    @Test
    void candidate_recipe_cannot_target_machine_removed_from_candidate_structures() {
        Identifier oldMachineId = MMCR.id("old_machine");
        Identifier replacementMachineId = MMCR.id("replacement_machine");
        Identifier oldRecipeId = MMCR.id("old_recipe");
        MachineDefinitions.register(MachineRegistration.builder(oldMachineId).build());
        MachineDefinitions.register(MachineRegistration.builder(replacementMachineId).build());
        MachineRecipe oldRecipe = recipe(oldRecipeId, oldMachineId);
        DynamicContentReloadService.reload(candidate -> {
            candidate.registerStructure(structure(oldMachineId, 0));
            candidate.registerRecipe(oldRecipe);
        });

        assertThatThrownBy(() -> DynamicContentReloadService.reload(candidate -> {
            candidate.registerStructure(structure(replacementMachineId, 0));
            candidate.registerRecipe(recipe(MMCR.id("replacement_recipe"), oldMachineId));
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(oldMachineId.toString());
        assertThat(MachineStructureRegistry.dynamicSnapshot())
                .containsOnlyKeys(oldMachineId);
        assertThat(RecipeRegistry.dynamicSnapshot())
                .containsEntry(oldRecipeId, oldRecipe);
    }

    private static MachineStructureDefinition structure(Identifier id, int couplers) {
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        for (int index = 0; index < couplers; index++) {
            pattern.put(new BlockPos(index, 0, 0), BlockPredicate.machineCoupler());
        }
        return new MachineStructureDefinition(id, new BlockArray(pattern), PortRequirementSpec.none(), List.of(), Map.of());
    }

    private static MachineRecipe recipe(Identifier id, Identifier machineId) {
        return new MachineRecipe(id, machineId, 1, List.of(), List.of());
    }
}
