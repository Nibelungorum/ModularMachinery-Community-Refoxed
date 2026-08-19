package org.nibelungorum;

import net.minecraft.resources.Identifier;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies representative built-in recipes use the public recipe declaration path.
 *
 * @author howxu <dev@howxu.cn>
 */
class DefaultRecipesTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void representative_built_in_recipes_preserve_public_io_and_runtime_lookup() {
        var recipe = DefaultRecipes.definitions().get(Identifier.parse("mmcr:blast_furnace_iron_to_nugget"));

        assertThat(recipe.id()).isEqualTo(Identifier.parse("mmcr:blast_furnace_iron_to_nugget"));
        assertThat(recipe.machineId()).isEqualTo(Identifier.parse("mmcr:blast_furnace"));
        assertThat(recipe.requirements()).hasSize(3);

        var fluidRecipe = DefaultRecipes.definitions().get(Identifier.parse("mmcr:cracker_coal_lapis"));
        assertThat(fluidRecipe.fluidOutputs()).isNotEmpty();
    }

    @Test
    void representative_public_recipe_covers_components_chance_level_and_host() {
        assertThat(DefaultRecipes.definitions()).containsKeys(Identifier.parse("mmcr:blast_furnace_iron_to_nugget"),
                Identifier.parse("mmcr:cracker_coal_lapis"));
    }
}
