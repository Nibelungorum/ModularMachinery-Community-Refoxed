package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeContentSyncTest {

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
    void createSnapshotIncludesDynamicStructuresRecipesSpecsAndAppearance() {
        Identifier machineId = MMCR.id("test_cube");
        Identifier recipeId = MMCR.id("runtime_sync_recipe");
        MachineStructureRegistry.replaceDynamic(Map.of(machineId, structure(machineId)));
        RecipeRegistry.replaceDynamic(Map.of(recipeId, recipe(recipeId, machineId)));

        RuntimeContentSnapshot snapshot = RuntimeContentSync.createSnapshot();

        assertThat(snapshot.structures()).containsKey(machineId);
        assertThat(snapshot.recipes()).containsKey(recipeId);
        assertThat(snapshot.controllerSpecs()).containsKey(machineId);
        assertThat(snapshot.appearances()).containsKey(machineId);
        assertThat(snapshot.recipeVersion()).isEqualTo(RecipeRegistry.reloadVersion());
    }

    private static MachineStructureDefinition structure(Identifier id) {
        return new MachineStructureDefinition(id, new BlockArray(Map.of()), PortRequirementSpec.none(), List.of(),
                MachineStructureRequirements.EMPTY);
    }

    private static MachineRecipe recipe(Identifier id, Identifier machineId) {
        return new MachineRecipe(id, machineId, 1, List.of(), List.of());
    }
}
