package cn.howxu.mmcr;

import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltinRecipeBootstrapTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        RecipeRegistry.clearForTesting();
    }

    @Test
    void bootstrap_registers_builtin_blast_furnace_recipe_explicitly() {
        TestBootstrap.registerRuntimeBuiltins();

        assertThat(RecipeRegistry.byMachineId(MMCR.id("blast_furnace")))
                .extracting(recipe -> recipe.id())
                .containsExactly(MMCR.id("blast_furnace_iron_to_nugget"));
    }
}
