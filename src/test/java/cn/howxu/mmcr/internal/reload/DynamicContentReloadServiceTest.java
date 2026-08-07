package cn.howxu.mmcr.internal.reload;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamicContentReloadServiceTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void producerFailureRetainsPreviousDynamicSnapshot() {
        DynamicContentReloadService.reload(candidate -> {
            candidate.registerMachine(machine("mmcr:old"));
            candidate.registerRecipe(recipe("mmcr:old_recipe", "mmcr:old"));
        });

        assertThatThrownBy(() -> DynamicContentReloadService.reload(candidate -> {
            candidate.registerMachine(machine("mmcr:new"));
            throw new IllegalStateException("script failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(MachineRegistry.getMachine(Identifier.parse("mmcr:old"))).isNotNull();
        assertThat(RecipeRegistry.getRecipe(Identifier.parse("mmcr:old_recipe"))).isNotNull();
        assertThat(MachineRegistry.getMachine(Identifier.parse("mmcr:new"))).isNull();
    }

    @Test
    void successfulReloadReportsRemovedMachinesAndDropsTheirRecipes() {
        DynamicContentReloadService.reload(candidate -> {
            candidate.registerMachine(machine("mmcr:old"));
            candidate.registerMachine(machine("mmcr:retained"));
            candidate.registerRecipe(recipe("mmcr:old_recipe", "mmcr:old"));
        });

        var result = DynamicContentReloadService.reload(candidate ->
                candidate.registerMachine(machine("mmcr:retained")));

        assertThat(result.removedMachines()).containsExactly(Identifier.parse("mmcr:old"));
        assertThat(MachineRegistry.getCompiled(Identifier.parse("mmcr:old"))).isNull();
        assertThat(RecipeRegistry.getRecipe(Identifier.parse("mmcr:old_recipe"))).isNull();
    }

    private static DynamicMachine machine(String id) {
        Identifier identifier = Identifier.parse(id);
        return new DynamicMachine(identifier, id, new BlockArray(Map.of()));
    }

    private static MachineRecipe recipe(String id, String machineId) {
        return new MachineRecipe(Identifier.parse(id), Identifier.parse(machineId), 1, List.of(), List.of());
    }
}
