package org.nibelungorum;

import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
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
        MachineRecipe recipe = DefaultRecipes.publicRecipe(Identifier.parse("mmcr:blast_furnace_iron_to_nugget"));

        assertThat(recipe.id()).isEqualTo(Identifier.parse("mmcr:blast_furnace_iron_to_nugget"));
        assertThat(recipe.machineId()).isEqualTo(Identifier.parse("mmcr:blast_furnace"));
        assertThat(recipe.requirements()).extracting(Object::getClass)
                .contains(ItemRequirement.class, EnergyRequirement.class);

        MachineRecipe fluidRecipe = DefaultRecipes.publicRecipe(Identifier.parse("mmcr:cracker_coal_lapis"));
        assertThat(fluidRecipe.requirements()).anyMatch(FluidRequirement.class::isInstance);
    }

    @Test
    void representative_public_recipe_covers_components_chance_level_and_host() {
        MachineRecipe recipe = DefaultRecipes.publicRecipe(Identifier.parse("mmcr:blast_furnace_component_chance"));

        assertThat(recipe.requirements()).anyMatch(requirement -> requirement instanceof ItemRequirement item
                && item.consumeChance() == 0.5F);
        assertThat(recipe.requirements()).anyMatch(requirement -> requirement instanceof ItemRequirement item
                && item.io() == RecipeModifier.IOType.OUTPUT && item.chance() == 0.25F);

        MachineRecipe leveled = DefaultRecipes.publicRecipe(Identifier.parse("mmcr:thermal_smelting_furnace_level"));
        assertThat(leveled.levelRequirements()).hasSize(1);
        assertThat(leveled.modifiers()).hasSize(1);

        MachineRecipe hosted = DefaultRecipes.publicRecipe(Identifier.parse("mmcr:space_reassembler_hosted"));
        assertThat(hosted.requiredHostIds()).contains(Identifier.parse("mmcr:space_elevator"));
        assertThat(hosted.requirements()).anyMatch(FluidRequirement.class::isInstance);
        assertThat(hosted.requirements()).allMatch(MachineRequirement.class::isInstance);
    }
}
