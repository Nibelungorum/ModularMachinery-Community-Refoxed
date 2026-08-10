package org.nibelungorum;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
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
        MachineStructureRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void default_recipes_are_published_from_org_nibelungorum_package() {
        assertThat(DefaultRecipes.class.getPackageName()).isEqualTo("org.nibelungorum");
    }

    @Test
    void ensureRegistered_publishes_builtin_blast_furnace_iron_to_nugget_recipe() {
        installDefaultRuntimeContent();
        DefaultRecipes.ensureRegistered();

        var machine = (DynamicMachine) MachineRegistry.getMachine(MMCR.id("blast_furnace"));

        assertThat(machine).isNotNull();
        var recipes = RecipeRegistry.byMachine(machine);
        assertThat(recipes).hasSize(10);

        var recipe = RecipeRegistry.getRecipe(MMCR.id("blast_furnace_iron_to_nugget"));
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
        assertThat(recipe.isParallelized()).isTrue();
    }

    @Test
    void ensureRegistered_publishes_builtin_alloy_furnace_netherite_recipe() {
        installDefaultRuntimeContent();
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
    void ensureRegistered_publishes_thermal_smelting_furnace_recipe_with_four_thread_limit() {
        installDefaultRuntimeContent();
        DefaultRecipes.ensureRegistered();

        var recipe = RecipeRegistry.getRecipe(MMCR.id("thermal_smelting_furnace_coal_iron_to_netherite_scrap"));

        assertThat(recipe.machineId()).isEqualTo(MMCR.id("thermal_smelting_furnace"));
        assertThat(recipe.tickTime()).isEqualTo(80);
        assertThat(recipe.maxThreads()).isEqualTo(4);
        assertThat(recipe.inputs()).hasSize(3);
        assertThat(recipe.inputs().get(0)).isInstanceOf(MachineIngredient.ItemIngredient.class);
        assertThat(((MachineIngredient.ItemIngredient) recipe.inputs().get(0)).item().items().toList().getFirst().value()).isEqualTo(Items.COAL);
        assertThat(((MachineIngredient.ItemIngredient) recipe.inputs().get(0)).count()).isEqualTo(1);
        assertThat(recipe.inputs().get(1)).isInstanceOf(MachineIngredient.ItemIngredient.class);
        assertThat(((MachineIngredient.ItemIngredient) recipe.inputs().get(1)).item().items().toList().getFirst().value()).isEqualTo(Items.RAW_IRON);
        assertThat(((MachineIngredient.ItemIngredient) recipe.inputs().get(1)).count()).isEqualTo(1);
        assertThat(recipe.inputs().get(2)).isInstanceOf(MachineIngredient.EnergyIngredient.class);
        assertThat(((MachineIngredient.EnergyIngredient) recipe.inputs().get(2)).fePerTick()).isEqualTo(200);
        assertThat(recipe.outputs()).singleElement().satisfies(stack -> {
            assertThat(stack.getItem()).isEqualTo(Items.IRON_INGOT);
            assertThat(stack.getCount()).isEqualTo(1);
        });
    }

    @Test
    void thermal_smelting_furnace_recipes_are_progressively_distinct() {
        installDefaultRuntimeContent();
        var recipes = DefaultRecipes.recipes().values().stream()
                .filter(recipe -> recipe.machineId().equals(MMCR.id("thermal_smelting_furnace")))
                .toList();

        assertThat(recipes).hasSize(5);
        assertThat(recipes).extracting(recipe -> recipe.tickTime()).containsExactlyInAnyOrder(80, 120, 160, 200, 240);
        assertThat(recipes).extracting(recipe -> ((MachineIngredient.EnergyIngredient) recipe.inputs().get(2)).fePerTick())
                .containsExactlyInAnyOrder(200, 400, 800, 1_200, 2_000);
        assertThat(recipes).extracting(recipe -> recipe.outputs().getFirst().getItem())
                .containsExactlyInAnyOrder(Items.IRON_INGOT, Items.COPPER_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.NETHERITE_INGOT);
    }

    @Test
    void thermal_smelting_furnace_recipes_require_each_coil_level() {
        installDefaultRuntimeContent();
        DefaultRecipes.ensureRegistered();

        assertThat(RecipeRegistry.getRecipe(MMCR.id("thermal_smelting_furnace_copper"))
                .levelRequirements()).singleElement().satisfies(requirement -> {
            assertThat(requirement.typeId()).isEqualTo(DefaultMachineLevels.THERMAL_SMELTING_COIL_TYPE);
            assertThat(requirement.levelId()).isEqualTo(DefaultMachineLevels.COPPER_COIL);
        });
        assertThat(RecipeRegistry.getRecipe(MMCR.id("thermal_smelting_furnace_iron"))
                .levelRequirements()).singleElement().extracting(requirement -> requirement.levelId())
                .isEqualTo(DefaultMachineLevels.IRON_COIL);
        assertThat(RecipeRegistry.getRecipe(MMCR.id("thermal_smelting_furnace_gold"))
                .levelRequirements()).singleElement().extracting(requirement -> requirement.levelId())
                .isEqualTo(DefaultMachineLevels.GOLD_COIL);
        assertThat(RecipeRegistry.getRecipe(MMCR.id("thermal_smelting_furnace_diamond"))
                .levelRequirements()).singleElement().extracting(requirement -> requirement.levelId())
                .isEqualTo(DefaultMachineLevels.DIAMOND_COIL);

        assertThat(MachineLevelRegistry.getLevel(DefaultMachineLevels.COPPER_COIL).modifier().durationMultiplier()).isEqualTo(0.9D);
        assertThat(MachineLevelRegistry.getLevel(DefaultMachineLevels.IRON_COIL).modifier().durationMultiplier()).isEqualTo(0.8D);
        assertThat(MachineLevelRegistry.getLevel(DefaultMachineLevels.GOLD_COIL).modifier().durationMultiplier()).isEqualTo(0.7D);
        assertThat(MachineLevelRegistry.getLevel(DefaultMachineLevels.DIAMOND_COIL).modifier().durationMultiplier()).isEqualTo(0.6D);
    }

    @Test
    void ensureRegistered_publishes_builtin_cracker_coal_lapis_recipe() {
        installDefaultRuntimeContent();
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
        installDefaultRuntimeContent();
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
        installDefaultRuntimeContent();
        DefaultRecipes.ensureRegistered();
        DefaultRecipes.ensureRegistered();

        assertThat(RecipeRegistry.byMachineId(MMCR.id("blast_furnace"))).hasSize(10);
        assertThat(RecipeRegistry.byMachineId(MMCR.id("alloy_furnace"))).hasSize(12);
        assertThat(RecipeRegistry.byMachineId(MMCR.id("cracker"))).hasSize(10);
        assertThat(RecipeRegistry.byMachineId(MMCR.id("reactor"))).hasSize(10);
        assertThat(RecipeRegistry.registeredRecipeCount()).isEqualTo(47);
        assertThat(RecipeRegistry.byMachineId(MMCR.id("cracker")))
                .anySatisfy(recipe -> assertThat(recipe.fluidOutputs()).isNotEmpty());
        assertThat(RecipeRegistry.recipes())
                .anySatisfy(recipe -> assertThat(recipe.outputs()).hasSizeGreaterThan(1));
    }

    @Test
    void default_recipes_include_three_input_and_three_output_ui_examples() {
        installDefaultRuntimeContent();
        DefaultRecipes.ensureRegistered();

        assertThat(RecipeRegistry.getRecipe(MMCR.id("blast_furnace_multi_item")).inputs())
                .filteredOn(MachineIngredient.ItemIngredient.class::isInstance)
                .hasSize(3);
        assertThat(RecipeRegistry.getRecipe(MMCR.id("blast_furnace_multi_output")).outputs())
                .hasSize(3);
    }

    @Test
    void alloy_furnace_has_large_recipe_for_jei_overflow_display() {
        installDefaultRuntimeContent();
        DefaultRecipes.ensureRegistered();

        var recipe = RecipeRegistry.getRecipe(MMCR.id("alloy_furnace_jei_large"));

        assertThat(recipe).isNotNull();
        assertThat(recipe.inputs())
                .filteredOn(MachineIngredient.ItemIngredient.class::isInstance)
                .hasSizeGreaterThan(20);
        assertThat(recipe.outputs()).hasSizeGreaterThan(20);
    }

    @Test
    void alloy_furnace_has_25x25_recipe_for_jei_overflow_display() {
        installDefaultRuntimeContent();
        DefaultRecipes.ensureRegistered();

        var recipe = RecipeRegistry.getRecipe(MMCR.id("alloy_furnace_jei_25x25"));

        assertThat(recipe).isNotNull();
        assertThat(recipe.inputs())
                .filteredOn(MachineIngredient.ItemIngredient.class::isInstance)
                .hasSize(25);
        assertThat(recipe.outputs()).hasSize(25);
    }

    private static void installDefaultRuntimeContent() {
        MachineLevelRegistry.beginRegistration();
        DefaultMachineLevels.register();
        MachineLevelRegistry.freezeRegistration();
        MachineStructureRegistry.replaceDynamic(DefaultMachines.structures());
    }
}
