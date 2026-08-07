package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeRegistryTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        RecipeRegistry.clearForTesting();
    }

    @Test
    void replacingDynamicRecipesRebuildsMergedMachineIndex() {
        var staticRecipe = recipe("mmcr:static_recipe", "mmcr:static_machine");
        var dynamicRecipe = recipe("mmcr:dynamic_recipe", "mmcr:dynamic_machine");
        RecipeRegistry.register(staticRecipe);
        long version = RecipeRegistry.reloadVersion();

        RecipeRegistry.replaceDynamic(Map.of(dynamicRecipe.id(), dynamicRecipe));

        assertThat(RecipeRegistry.byMachineId(staticRecipe.machineId())).containsExactly(staticRecipe);
        assertThat(RecipeRegistry.byMachineId(dynamicRecipe.machineId())).containsExactly(dynamicRecipe);

        RecipeRegistry.replaceDynamic(Map.of());

        assertThat(RecipeRegistry.getRecipe(dynamicRecipe.id())).isNull();
        assertThat(RecipeRegistry.getRecipe(staticRecipe.id())).isSameAs(staticRecipe);
        assertThat(RecipeRegistry.reloadVersion()).isGreaterThan(version);
    }

    private static MachineRecipe recipe(String id, String machineId) {
        return new MachineRecipe(Identifier.parse(id), Identifier.parse(machineId), 1, List.of(), List.of());
    }
}
