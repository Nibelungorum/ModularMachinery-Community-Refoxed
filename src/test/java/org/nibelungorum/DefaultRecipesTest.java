package org.nibelungorum;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultRecipesTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        MachineRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void default_recipes_are_published_from_org_nibelungorum_package() {
        assertThat(DefaultRecipes.class.getPackageName()).isEqualTo("org.nibelungorum");
    }

    @Test
    void ensureRegistered_publishes_builtin_blast_furnace_iron_to_nugget_recipe() {
        DefaultMachines.ensureRegistered();
        DefaultRecipes.ensureRegistered();

        var machine = (DynamicMachine) MachineRegistry.getMachine(MMCR.id("blast_furnace"));

        assertThat(machine).isNotNull();
        var recipes = RecipeRegistry.byMachine(machine);
        assertThat(recipes).hasSize(1);

        var recipe = recipes.getFirst();
        assertThat(recipe.id()).isEqualTo(MMCR.id("blast_furnace_iron_to_nugget"));
        assertThat(recipe.tickTime()).isEqualTo(200);
        assertThat(recipe.inputs()).hasSize(2);
        assertThat(recipe.outputs()).hasSize(1);
        assertThat(recipe.inputs().get(0)).isInstanceOf(MachineIngredient.ItemIngredient.class);
        assertThat(((MachineIngredient.ItemIngredient) recipe.inputs().get(0)).count()).isEqualTo(1);
        assertThat(recipe.inputs().get(1)).isInstanceOf(MachineIngredient.EnergyIngredient.class);
        assertThat(((MachineIngredient.EnergyIngredient) recipe.inputs().get(1)).fePerTick()).isEqualTo(1);
        assertThat(recipe.outputs().getFirst().getItem()).isEqualTo(net.minecraft.world.item.Items.IRON_NUGGET);
        assertThat(recipe.outputs().getFirst().getCount()).isEqualTo(1);
    }

    @Test
    void ensureRegistered_is_idempotent() {
        DefaultMachines.ensureRegistered();
        DefaultRecipes.ensureRegistered();
        DefaultRecipes.ensureRegistered();

        assertThat(RecipeRegistry.registeredRecipeCount()).isEqualTo(1);
    }
}
