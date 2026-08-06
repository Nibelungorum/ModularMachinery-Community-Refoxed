package org.nibelungorum;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
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
    void ensureRegistered_publishes_builtin_alloy_furnace_netherite_recipe() {
        DefaultMachines.ensureRegistered();
        DefaultRecipes.ensureRegistered();

        var machine = (DynamicMachine) MachineRegistry.getMachine(MMCR.id("alloy_furnace"));

        assertThat(machine).isNotNull();
        var recipes = RecipeRegistry.byMachine(machine);
        assertThat(recipes).extracting(recipe -> recipe.id()).contains(MMCR.id("alloy_furnace_netherite"));

        var recipe = RecipeRegistry.getRecipe(MMCR.id("alloy_furnace_netherite"));
        assertThat(recipe.tickTime()).isEqualTo(100);
        assertThat(recipe.inputs()).hasSize(3);
        assertThat(recipe.inputs().get(0)).isInstanceOf(MachineIngredient.ItemIngredient.class);
        assertThat(((MachineIngredient.ItemIngredient) recipe.inputs().get(0)).item().items().toList().getFirst().value()).isEqualTo(Items.ANCIENT_DEBRIS);
        assertThat(((MachineIngredient.ItemIngredient) recipe.inputs().get(0)).count()).isEqualTo(1);
        assertThat(recipe.inputs().get(1)).isInstanceOf(MachineIngredient.ItemIngredient.class);
        assertThat(((MachineIngredient.ItemIngredient) recipe.inputs().get(1)).item().items().toList().getFirst().value()).isEqualTo(Items.GOLD_INGOT);
        assertThat(((MachineIngredient.ItemIngredient) recipe.inputs().get(1)).count()).isEqualTo(1);
        assertThat(recipe.inputs().get(2)).isInstanceOf(MachineIngredient.EnergyIngredient.class);
        assertThat(((MachineIngredient.EnergyIngredient) recipe.inputs().get(2)).fePerTick()).isEqualTo(5);
        assertThat(recipe.outputs()).singleElement().satisfies(stack -> {
            assertThat(stack.getItem()).isEqualTo(Items.NETHERITE_INGOT);
            assertThat(stack.getCount()).isEqualTo(1);
        });
    }

    @Test
    void ensureRegistered_publishes_builtin_cracker_coal_lapis_recipe() {
        DefaultMachines.ensureRegistered();
        DefaultRecipes.ensureRegistered();

        var machine = (DynamicMachine) MachineRegistry.getMachine(MMCR.id("cracker"));

        assertThat(machine).isNotNull();
        var recipes = RecipeRegistry.byMachine(machine);
        assertThat(recipes).extracting(recipe -> recipe.id()).contains(MMCR.id("cracker_coal_lapis"));

        var recipe = RecipeRegistry.getRecipe(MMCR.id("cracker_coal_lapis"));
        assertThat(recipe.tickTime()).isEqualTo(160);
        assertThat(recipe.inputs()).hasSize(3);
        assertThat(recipe.inputs().get(0)).isInstanceOf(MachineIngredient.ItemIngredient.class);
        assertThat(((MachineIngredient.ItemIngredient) recipe.inputs().get(0)).item().items().toList().getFirst().value()).isEqualTo(Items.COAL);
        assertThat(((MachineIngredient.ItemIngredient) recipe.inputs().get(0)).count()).isEqualTo(8);
        assertThat(recipe.inputs().get(1)).isInstanceOf(MachineIngredient.ItemIngredient.class);
        assertThat(((MachineIngredient.ItemIngredient) recipe.inputs().get(1)).item().items().toList().getFirst().value()).isEqualTo(Items.LAPIS_LAZULI);
        assertThat(((MachineIngredient.ItemIngredient) recipe.inputs().get(1)).count()).isEqualTo(1);
        assertThat(recipe.inputs().get(2)).isInstanceOf(MachineIngredient.EnergyIngredient.class);
        assertThat(((MachineIngredient.EnergyIngredient) recipe.inputs().get(2)).fePerTick()).isEqualTo(100);
        assertThat(recipe.outputs()).singleElement().satisfies(stack -> {
            assertThat(stack.getItem()).isEqualTo(Items.REDSTONE);
            assertThat(stack.getCount()).isEqualTo(4);
        });
        assertThat(recipe.fluidOutputs()).singleElement().satisfies(stack -> {
            assertThat(stack.getFluid()).isEqualTo(Fluids.WATER);
            assertThat(stack.getAmount()).isEqualTo(500);
        });
    }

    @Test
    void ensureRegistered_publishes_builtin_reactor_recipe() {
        DefaultMachines.ensureRegistered();
        DefaultRecipes.ensureRegistered();

        var machine = (DynamicMachine) MachineRegistry.getMachine(MMCR.id("reactor"));

        assertThat(machine).isNotNull();
        var recipes = RecipeRegistry.byMachine(machine);
        assertThat(recipes).extracting(recipe -> recipe.id()).contains(MMCR.id("reactor_diamond_water"));

        var recipe = RecipeRegistry.getRecipe(MMCR.id("reactor_diamond_water"));
        assertThat(recipe.tickTime()).isEqualTo(200);
        assertThat(recipe.inputs()).hasSize(2);
        assertThat(recipe.inputs().get(0)).isInstanceOf(MachineIngredient.ItemIngredient.class);
        assertThat(((MachineIngredient.ItemIngredient) recipe.inputs().get(0)).item().items().toList().getFirst().value()).isEqualTo(Items.DIAMOND);
        assertThat(((MachineIngredient.ItemIngredient) recipe.inputs().get(0)).count()).isEqualTo(1);
        assertThat(recipe.inputs().get(1)).isInstanceOf(MachineIngredient.FluidIngredient.class);
        assertThat(((MachineIngredient.FluidIngredient) recipe.inputs().get(1)).fluid().fluids().getFirst().value()).isEqualTo(Fluids.WATER);
        assertThat(((MachineIngredient.FluidIngredient) recipe.inputs().get(1)).amount()).isEqualTo(500);
        assertThat(recipe.energyOutputs()).containsExactly(100);
        var energyRequirement = (EnergyRequirement) recipe.requirements().stream()
                .filter(r -> r instanceof EnergyRequirement)
                .findFirst()
                .orElseThrow();
        assertThat(energyRequirement.io()).isEqualTo(RecipeModifier.IOType.OUTPUT);
        assertThat(energyRequirement.fePerTick()).isEqualTo(100);
        assertThat(recipe.outputs()).singleElement().satisfies(stack -> {
            assertThat(stack.getItem()).isEqualTo(Items.COAL);
            assertThat(stack.getCount()).isEqualTo(1);
        });
        assertThat(recipe.fluidOutputs()).singleElement().satisfies(stack -> {
            assertThat(stack.getFluid()).isEqualTo(Fluids.LAVA);
            assertThat(stack.getAmount()).isEqualTo(500);
        });
    }

    @Test
    void ensureRegistered_is_idempotent() {
        DefaultMachines.ensureRegistered();
        DefaultRecipes.ensureRegistered();
        DefaultRecipes.ensureRegistered();

        assertThat(RecipeRegistry.registeredRecipeCount()).isEqualTo(4);
    }
}
