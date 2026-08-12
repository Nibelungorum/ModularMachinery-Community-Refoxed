package org.nibelungorum;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
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
        assertThat(recipes).hasSize(21);

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
    void component_recipe_includes_chanced_item_and_fluid_outputs() {
        installDefaultRuntimeContent();
        DefaultRecipes.ensureRegistered();

        var recipe = RecipeRegistry.getRecipe(MMCR.id("blast_furnace_component_chanced_outputs"));

        assertThat(recipe).isNotNull();
        assertThat(recipe.machineOutputs()).hasSize(3);
        assertThat(recipe.machineOutputs().get(0).chance()).isEqualTo(1F);
        assertThat(recipe.machineOutputs().get(1).chance()).isEqualTo(0.5F);
        assertThat(recipe.machineOutputs().get(2).chance()).isEqualTo(0.25F);
        assertThat(recipe.outputs()).extracting(stack -> stack.getItem())
                .containsExactly(Items.EMERALD, Items.DIAMOND);
        assertThat(recipe.fluidOutputs()).singleElement().satisfies(stack -> {
            assertThat(stack.getFluid()).isEqualTo(Fluids.LAVA);
            assertThat(stack.getAmount()).isEqualTo(250);
        });
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
                .filter(recipe -> !recipe.id().getPath().contains("_component_"))
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
    void purpur_furnace_recipes_use_mode_to_select_the_output() {
        installDefaultRuntimeContent();
        DefaultRecipes.ensureRegistered();

        assertPurpurFurnaceRecipe("purpur_furnace_mode_1", 1F, Items.DIAMOND, 2);
        assertPurpurFurnaceRecipe("purpur_furnace_mode_2", 2F, Items.GOLD_INGOT, 4);
        assertPurpurFurnaceRecipe("purpur_furnace_mode_3", 3F, Items.IRON_INGOT, 8);
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

        assertThat(RecipeRegistry.byMachineId(MMCR.id("blast_furnace"))).hasSize(21);
        assertThat(RecipeRegistry.byMachineId(MMCR.id("alloy_furnace"))).hasSize(23);
        assertThat(RecipeRegistry.byMachineId(MMCR.id("cracker"))).hasSize(21);
        assertThat(RecipeRegistry.byMachineId(MMCR.id("reactor"))).hasSize(21);
        assertThat(RecipeRegistry.byMachineId(MMCR.id("thermal_smelting_furnace"))).hasSize(16);
        assertThat(RecipeRegistry.byMachineId(MMCR.id("purpur_furnace"))).hasSize(3);
        assertThat(RecipeRegistry.registeredRecipeCount()).isEqualTo(105);
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

    @Test
    void default_recipes_include_data_component_examples_for_every_default_machine() {
        installDefaultRuntimeContent();
        DefaultRecipes.ensureRegistered();

        for (String machine : java.util.List.of("blast_furnace", "alloy_furnace", "cracker", "reactor", "thermal_smelting_furnace")) {
            assertThat(RecipeRegistry.byMachineId(MMCR.id(machine)))
                    .filteredOn(recipe -> recipe.id().getPath().startsWith(machine + "_component_"))
                    .hasSize(11);
        }

        MachineRecipe chanced = RecipeRegistry.getRecipe(MMCR.id("blast_furnace_component_chanced_input"));
        MachineIngredient.ItemIngredient chancedInput = (MachineIngredient.ItemIngredient) chanced.inputs().getFirst();
        assertThat(chancedInput.consumeChance()).isEqualTo(0.5F);
        assertThat(chancedInput.components().matches(namedStack(Items.DIAMOND, "Chance"))).isTrue();
        assertThat(chancedInput.components().matches(new ItemStack(Items.DIAMOND))).isFalse();

        MachineIngredient.ItemIngredient keptInput = (MachineIngredient.ItemIngredient) RecipeRegistry
                .getRecipe(MMCR.id("blast_furnace_component_non_consumable_input"))
                .inputs().getFirst();
        assertThat(keptInput.consumeChance()).isZero();

        assertThat(RecipeRegistry.getRecipe(MMCR.id("blast_furnace_component_plain_input_to_output"))
                .outputs().getFirst().get(DataComponents.CUSTOM_NAME)).isEqualTo(Component.literal("Output Only"));
        assertThat(RecipeRegistry.getRecipe(MMCR.id("blast_furnace_component_mixed_outputs"))
                .outputs()).satisfiesExactly(
                stack -> assertThat(stack.get(DataComponents.CUSTOM_NAME)).isEqualTo(Component.literal("Named Output")),
                stack -> assertThat(stack.get(DataComponents.CUSTOM_NAME)).isNull());

        MachineRecipe enchanted = RecipeRegistry.getRecipe(MMCR.id("blast_furnace_component_non_consumable_sharpness_input"));
        assertThat(enchanted.tickTime()).isEqualTo(100);
        assertThat(enchanted.outputs()).isEmpty();
        MachineIngredient.ItemIngredient enchantedInput = (MachineIngredient.ItemIngredient) enchanted.inputs().getFirst();
        assertThat(enchantedInput.item().items().map(holder -> holder.value()).toList()).containsExactly(Items.DIAMOND_SWORD);
        assertThat(enchantedInput.count()).isEqualTo(1);
        assertThat(enchantedInput.consumeChance()).isZero();
        JsonObject enchantments = DataComponentPredicateSet.CODEC.encodeStart(JsonOps.INSTANCE, enchantedInput.components())
                .getOrThrow()
                .getAsJsonObject()
                .getAsJsonObject("minecraft:enchantments")
                .getAsJsonObject("value");
        assertThat(enchantments.get("minecraft:sharpness").getAsInt()).isEqualTo(2);
        assertThat(enchantments.has("levels")).isFalse();
        assertThat(enchantments.has("show_in_tooltip")).isFalse();
        assertThat(enchantedInput.components().values())
                .containsKey(DataComponents.ENCHANTMENTS)
                .doesNotContainKey(DataComponents.REPAIR_COST);
        var registryOps = RegistryOps.create(JsonOps.INSTANCE, net.minecraft.data.registries.VanillaRegistries.createLookup());
        assertThat(enchantedInput.components().matches(enchantedSword("minecraft:sharpness", 2), registryOps)).isTrue();
        ItemStack withoutRepairCost = enchantedSword("minecraft:sharpness", 2);
        withoutRepairCost.remove(DataComponents.REPAIR_COST);
        assertThat(enchantedInput.components().matches(withoutRepairCost, registryOps)).isTrue();
        assertThat(enchantedInput.components().matches(enchantedSword("minecraft:sharpness", 1), registryOps)).isFalse();
        assertThat(enchantedInput.components().matches(enchantedSword("minecraft:unbreaking", 3), registryOps)).isFalse();
    }

    @Test
    void recipesCanBuildBeforeVanillaDefaultComponentsAreBound() {
        assertThat(DefaultRecipes.recipes()).containsKey(MMCR.id("blast_furnace_component_enchanted_output"));
    }

    @Test
    void data_defined_item_output_uses_registered_holder() {
        var recipe = DefaultRecipes.recipes().get(MMCR.id("blast_furnace_component_enchanted_output"));
        var output = recipe.outputs().getFirst();

        assertThat(output.typeHolder().unwrapKey()).isPresent();
        assertThat(output.typeHolder().unwrapKey().orElseThrow().identifier())
                .isEqualTo(Identifier.parse("minecraft:iron_sword"));
    }

    @Test
    void complex_recipe_registers_three_inputs_and_three_outputs_with_correct_chances() {
        installDefaultRuntimeContent();
        DefaultRecipes.ensureRegistered();

        MachineRecipe recipe = RecipeRegistry.getRecipe(MMCR.id("blast_furnace_component_complex"));
        assertThat(recipe).isNotNull();
        assertThat(recipe.inputs()).hasSize(3);
        assertThat(recipe.outputs()).hasSize(3);

        assertThat(recipe.requirements())
                .filteredOn(r -> r instanceof ItemRequirement itemReq && r.io() == RecipeModifier.IOType.INPUT)
                .hasSize(3)
                .extracting(r -> ((ItemRequirement) r).consumeChance())
                .containsExactly(0F, 0.5F, 0.25F);
        assertThat(recipe.machineOutputs())
                .extracting(MachineOutput::chance)
                .containsExactly(1F, 0.5F, 0.25F);
    }

    private static void installDefaultRuntimeContent() {
        MachineLevelRegistry.beginRegistration();
        DefaultMachineLevels.register();
        MachineLevelRegistry.freezeRegistration();
        MachineStructureRegistry.replaceDynamic(DefaultMachines.structures());
    }

    private static void assertPurpurFurnaceRecipe(String id, float mode, net.minecraft.world.item.Item output, int count) {
        var recipe = RecipeRegistry.getRecipe(MMCR.id(id));
        assertThat(recipe.machineId()).isEqualTo(MMCR.id("purpur_furnace"));
        assertThat(recipe.tickTime()).isEqualTo(200);
        assertThat(recipe.inputs()).hasSize(2);
        assertThat(((MachineIngredient.ItemIngredient) recipe.inputs().get(0)).item().items().toList().getFirst().value()).isEqualTo(Items.COAL);
        assertThat(((MachineIngredient.EnergyIngredient) recipe.inputs().get(1)).fePerTick()).isEqualTo(5);
        assertThat(recipe.outputs()).singleElement().satisfies(stack -> {
            assertThat(stack.getItem()).isEqualTo(output);
            assertThat(stack.getCount()).isEqualTo(count);
        });
        assertThat(recipe.requirements()).filteredOn(SmartInterfaceRequirement.class::isInstance).singleElement()
                .isEqualTo(SmartInterfaceRequirement.input("Mode", mode));
    }

    private static ItemStack namedStack(net.minecraft.world.item.Item item, String name) {
        item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64)
                .build());
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static ItemStack enchantedSword(String enchantmentId, int level) {
        Items.DIAMOND_SWORD.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 1)
                .build());
        var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var enchantment = lookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(
                Registries.ENCHANTMENT, Identifier.parse(enchantmentId)));
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(enchantment, level);
        stack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        stack.set(DataComponents.REPAIR_COST, 1);
        return stack;
    }
}
