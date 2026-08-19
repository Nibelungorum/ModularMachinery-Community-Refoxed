package org.nibelungorum;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.LevelRequirement;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import net.minecraft.world.item.alchemy.Potion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import java.util.stream.Stream;

/**
 * Built-in actual recipe content. Machine startup registration creates the recipe
 * family only; this class registers concrete recipes after structures are installed.
 */
public final class DefaultRecipes {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRecipes.class);

    private static final Identifier BLAST_FURNACE_ID = MMCR.id("blast_furnace");
    private static final Identifier ALLOY_FURNACE_ID = MMCR.id("alloy_furnace");
    private static final Identifier ALLOY_FURNACE_NETHERITE_ID = MMCR.id("alloy_furnace_netherite");
    private static final Identifier CRACKER_ID = MMCR.id("cracker");
    private static final Identifier REACTOR_ID = MMCR.id("reactor");
    private static final Identifier THERMAL_SMELTING_FURNACE_ID = MMCR.id("thermal_smelting_furnace");
    private static final Identifier PURPUR_FURNACE_ID = MMCR.id("purpur_furnace");
    private static final Identifier DISTILLATION_TOWER_TEST_ID = MMCR.id("distillation_tower_test");
    private static final Identifier DISTILLATION_TOWER_ID = MMCR.id("distillation_tower");
    private static final Identifier ECO_MATRIX_ID = MMCR.id("eco_matrix");
    private static final Identifier SPACE_ELEVATOR_ID = MMCR.id("space_elevator");
    private static final Identifier SPACE_REASSEMBLER_ID = MMCR.id("space_reassembler");

    private DefaultRecipes() {
    }

    public static void ensureRegistered() {
        registerStatic(recipes().values().stream().toList());
    }

    public static Map<Identifier, MachineRecipe> recipes() {
        Map<Identifier, MachineRecipe> recipes = new LinkedHashMap<>();
        for (Definition definition : definitions()) {
            MachineRecipe recipe = createRecipe(definition);
            recipes.put(recipe.id(), recipe);
        }
        for (MachineRecipe recipe : componentExampleRecipes()) {
            recipes.put(recipe.id(), recipe);
        }
        for (MachineRecipe recipe : tagExampleRecipes()) {
            recipes.put(recipe.id(), recipe);
        }
        for (MachineRecipe recipe : purpurFurnaceRecipes()) {
            recipes.put(recipe.id(), recipe);
        }
        for (MachineRecipe recipe : distillationTowerRecipes()) {
            recipes.put(recipe.id(), recipe);
        }
        MachineRecipe ecoMatrixRecipe = ecoMatrixRecipe();
        recipes.put(ecoMatrixRecipe.id(), ecoMatrixRecipe);
        for (MachineRecipe recipe : spaceRecipes()) recipes.put(recipe.id(), recipe);
        return Map.copyOf(recipes);
    }

    public static List<MachineRecipe> gameTestRecipes() {
        return List.of(distillationTowerTestRecipe());
    }

    public static void registerStatic(List<MachineRecipe> recipes) {
        for (MachineRecipe recipe : recipes) {
            if (RecipeRegistry.getRecipe(recipe.id()) == null) {
                register(recipe);
            }
        }
    }

    private static MachineRecipe createRecipe(Definition definition) {
        return new MachineRecipe(definition.id(), definition.machineId(), definition.ticks(), definition.inputs(),
                definition.outputs(), List.of(), 0, definition.maxThreads(), true, definition.fluidOutputs(), List.of(), true,
                definition.levelRequirements());
    }

    private static List<Definition> definitions() {
        return List.of(
                standardDefinitions(BLAST_FURNACE_ID, "blast_furnace", new Definition(MMCR.id("blast_furnace_iron_to_nugget"), BLAST_FURNACE_ID, 200, List.of(itemInput(Items.IRON_INGOT, 1), energyInput(1)), List.of(item(Items.IRON_NUGGET, 1)), List.of())),
                alloyFurnaceDefinitions(),
                standardDefinitions(CRACKER_ID, "cracker", new Definition(MMCR.id("cracker_coal_lapis"), CRACKER_ID, 160, List.of(itemInput(Items.COAL, 8), itemInput(Items.LAPIS_LAZULI, 1), energyInput(100)), List.of(item(Items.REDSTONE, 4)), List.of(fluidOutput(Fluids.WATER, 500)))),
                standardDefinitions(REACTOR_ID, "reactor", new Definition(MMCR.id("reactor_diamond_water"), REACTOR_ID, 200, List.of(itemInput(Items.DIAMOND, 1), fluidInput(Fluids.WATER, 500), energyOutput(100)), List.of(item(Items.COAL, 1)), List.of(fluidOutput(Fluids.LAVA, 500)))),
                thermalSmeltingFurnaceDefinitions()
        ).stream().flatMap(List::stream).toList();
    }

    private static List<MachineRecipe> distillationTowerRecipes() {
        return List.of(
                discardableRecipe("distillation_tower_coal", Items.COAL, Items.COAL, Items.CHARCOAL, Items.GUNPOWDER),
                discardableRecipe("distillation_tower_oak_log", Items.OAK_LOG, Items.CHARCOAL, Items.STICK, Items.COAL),
                discardableRecipe("distillation_tower_dried_kelp", Items.DRIED_KELP, Items.KELP, Items.COAL, Items.BONE_MEAL));
    }

    private static List<MachineRecipe> spaceRecipes() {
        return List.of(
                new MachineRecipe(MMCR.id("space_elevator_thread_dispersal"), SPACE_ELEVATOR_ID, 1_000,
                        List.of(itemInput(ModItems.THREAD_DISPERSER.get(), 1, 0F), energyInput(10_000)), List.of(),
                        List.of(), 0, 1, true, List.of(), List.of(), false, List.of()),
                spaceReassemblerRecipe("space_reassembler_steak_to_golden_carrot", 600,
                        itemInput(Items.COOKED_BEEF, 4), item(Items.GOLDEN_CARROT, 1), 15_000),
                spaceReassemblerRecipe("space_reassembler_water_to_healing", 400,
                        waterBottleInput(), potion(Potions.HEALING), 8_000),
                spaceReassemblerRecipe("space_reassembler_water_to_swiftness", 400,
                        awkwardPotionInput(), potion(Potions.SWIFTNESS), 8_000));
    }

    private static MachineRecipe spaceReassemblerRecipe(String id, int ticks, MachineIngredient input, ItemStack output, int fePerTick) {
        return new MachineRecipe(MMCR.id(id), SPACE_REASSEMBLER_ID, ticks, List.of(input, energyInput(fePerTick)), List.of(output),
                List.of(), 0, 1, true, List.of(), List.of(), false, List.of(), Set.of(SPACE_ELEVATOR_ID));
    }

    private static ItemStack potion(Holder<Potion> potion) {
        bindItem(Items.POTION);
        return PotionContents.createItemStack(Items.POTION, potion);
    }

    private static MachineIngredient.ItemIngredient waterBottleInput() {
        return itemInputFromData("""
                {"id":"minecraft:potion","components":{"minecraft:potion_contents":{"potion":"minecraft:water"}}}
                """, 1F);
    }

    private static MachineIngredient.ItemIngredient awkwardPotionInput() {
        return itemInputFromData("""
                {"id":"minecraft:potion","components":{"minecraft:potion_contents":{"potion":"minecraft:awkward"}}}
                """, 1F);
    }

    private static MachineRecipe discardableRecipe(String id, Item input, Item first, Item second, Item third) {
        return new MachineRecipe(MMCR.id(id), DISTILLATION_TOWER_ID, 200,
                List.of(itemInput(input, 1), energyInput(40)),
                List.of(item(first, 1), item(second, 1), item(third, 1)),
                List.of(), 0, 4, true, List.of(), List.of(), true, List.of(), true);
    }

    private static MachineRecipe ecoMatrixRecipe() {
        return new MachineRecipe(MMCR.id("eco_matrix_energy_drain"), ECO_MATRIX_ID, 200,
                List.of(energyInput(100)), List.of(), List.of(), 0, 1, true,
                List.of(), List.of(), false, List.of());
    }

    private static List<MachineRecipe> componentExampleRecipes() {
        return List.of(
                BLAST_FURNACE_ID,
                ALLOY_FURNACE_ID,
                CRACKER_ID,
                REACTOR_ID,
                THERMAL_SMELTING_FURNACE_ID
        ).stream().flatMap(machineId -> componentExampleRecipes(machineId).stream()).toList();
    }

    private static List<MachineRecipe> componentExampleRecipes(Identifier machineId) {
        String prefix = machineId.getPath() + "_component_";
        return List.of(
                componentRecipe(machineId, prefix + "chanced_input",
                        List.of(componentItemInput(Items.DIAMOND, 1, "Chance", 0.5F)),
                        List.of(item(Items.EMERALD, 1))),
                componentRecipe(machineId, prefix + "non_consumable_input",
                        List.of(componentItemInput(Items.DIAMOND, 1, "Keep", 0F)),
                        List.of(item(Items.EMERALD, 1))),
                enchantedNonConsumableRecipe(machineId, prefix + "non_consumable_sharpness_input"),
                enchantedOutputRecipe(machineId, prefix + "enchanted_output"),
                componentRecipe(machineId, prefix + "input_to_plain_output",
                        List.of(componentItemInput(Items.DIAMOND, 1, "Input Only", 1F)),
                        List.of(item(Items.EMERALD, 1))),
                componentRecipe(machineId, prefix + "plain_input_to_output",
                        List.of(itemInput(Items.IRON_INGOT, 1)),
                        List.of(namedItem(Items.GOLD_INGOT, 1, "Output Only"))),
                componentRecipe(machineId, prefix + "input_to_output",
                        List.of(componentItemInput(Items.DIAMOND, 1, "Input", 1F)),
                        List.of(namedItem(Items.GOLD_INGOT, 1, "Output"))),
                componentRecipe(machineId, prefix + "mixed_inputs",
                        List.of(componentItemInput(Items.DIAMOND, 1, "Named", 1F), itemInput(Items.IRON_INGOT, 1)),
                        List.of(item(Items.EMERALD, 1))),
                componentRecipe(machineId, prefix + "mixed_outputs",
                        List.of(itemInput(Items.IRON_INGOT, 1)),
                        List.of(namedItem(Items.GOLD_INGOT, 1, "Named Output"), item(Items.EMERALD, 1))),
                chancedOutputRecipe(machineId, prefix + "chanced_outputs"),
                complexRecipe(machineId, prefix + "complex")
        );
    }

    private static List<MachineRecipe> tagExampleRecipes() {
        return List.of(
                componentRecipe(BLAST_FURNACE_ID, "blast_furnace_component_tag_input",
                        List.of(tagItemInput(ItemTags.LOGS, 1)),
                        List.of(item(Items.CHARCOAL, 1))),
                componentRecipe(BLAST_FURNACE_ID, "blast_furnace_component_tag_named_input",
                        List.of(tagComponentItemInput(ItemTags.PLANKS, 1, namedPredicate("Validated"), 1F)),
                        List.of(item(Items.EMERALD, 1))),
                componentRecipe(BLAST_FURNACE_ID, "blast_furnace_component_tag_enchanted_input",
                        List.of(tagComponentItemInput(ItemTags.SWORDS, 1, componentsFromData("""
                                {"minecraft:enchantments": {"minecraft:sharpness": 2}}
                                """), 1F)),
                        List.of(item(Items.DIAMOND, 1)))
        );
    }

    private static MachineRecipe complexRecipe(Identifier machineId, String path) {
        var stick = new MachineIngredient.ItemIngredient(Ingredient.of(Items.STICK), 1, DataComponentPredicateSet.EMPTY, 0F);
        var ironNugget = new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_NUGGET), 1, DataComponentPredicateSet.EMPTY, 0.5F);
        var goldNugget = new MachineIngredient.ItemIngredient(Ingredient.of(Items.GOLD_NUGGET), 1, DataComponentPredicateSet.EMPTY, 0.25F);
        return new MachineRecipe(MMCR.id(path), machineId, 20,
                List.of(stick, ironNugget, goldNugget),
                List.of(item(Items.EMERALD, 1), item(Items.DIAMOND, 1), item(Items.REDSTONE, 1)),
                List.of(), 0, 1, true,
                List.of(),
                List.of(
                        MachineRequirement.fromInput(stick),
                        MachineRequirement.fromInput(ironNugget),
                        MachineRequirement.fromInput(goldNugget),
                        MachineRequirement.itemOutput(item(Items.EMERALD, 1), 1F),
                        MachineRequirement.itemOutput(item(Items.DIAMOND, 1), 0.5F),
                        MachineRequirement.itemOutput(item(Items.REDSTONE, 1), 0.25F)),
                true, List.of());
    }

    private static MachineRecipe componentRecipe(Identifier machineId, String path,
                                                 List<MachineIngredient> inputs, List<ItemStack> outputs) {
        return new MachineRecipe(MMCR.id(path), machineId, 20, inputs, outputs,
                List.of(), 0, 1, true, List.of(), List.of(), true, List.of());
    }

    private static MachineIngredient componentItemInput(Item item, int count, String name, float consumeChance) {
        return new MachineIngredient.ItemIngredient(Ingredient.of(item), count, namedPredicate(name), consumeChance);
    }

    private static MachineIngredient tagItemInput(TagKey<Item> tag, int count) {
        return new MachineIngredient.ItemIngredient(Ingredient.of(tagItems(tag)), count);
    }

    private static MachineIngredient tagComponentItemInput(TagKey<Item> tag, int count,
                                                          DataComponentPredicateSet components, float consumeChance) {
        return new MachineIngredient.ItemIngredient(Ingredient.of(tagItems(tag)), count, components, consumeChance);
    }

    private static HolderSet.Named<Item> tagItems(TagKey<Item> tag) {
        return BuiltInRegistries.ITEM.get(tag)
                .orElseGet(() -> HolderSet.emptyNamed(BuiltInRegistries.ITEM, tag));
    }

    private static MachineRecipe enchantedNonConsumableRecipe(Identifier machineId, String path) {
        return new MachineRecipe(MMCR.id(path), machineId, 100,
                List.of(itemInputFromData("""
                        {components: {"minecraft:enchantments": {"minecraft:sharpness": 2}}, count: 1, id: "minecraft:diamond_sword"}
                        """, 0F)),
                List.of(), List.of(), 0, 1, true, List.of(), List.of(), true, List.of());
    }

    private static MachineRecipe enchantedOutputRecipe(Identifier machineId, String path) {
        var input = itemInput(Items.IRON_SWORD, 1);
        return new MachineRecipe(MMCR.id(path), machineId, 100,
                List.of(input),
                List.of(),
                List.of(), 0, 1, true, List.of(), List.of(
                        MachineRequirement.fromInput(input),
                        itemOutputRequirementFromData("""
                                 {components: {"minecraft:enchantments": {"minecraft:sharpness": 2}, "minecraft:repair_cost": 1}, count: 1, id: "minecraft:iron_sword"}
                                 """)), true, List.of());
    }

    private static MachineRecipe chancedOutputRecipe(Identifier machineId, String path) {
        return new MachineRecipe(MMCR.id(path), machineId, 20, List.of(itemInput(Items.IRON_INGOT, 1)), List.of(),
                List.of(), 0, 1, true, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.APPLE), 1, ItemStack.EMPTY),
                MachineRequirement.itemOutput(item(Items.EMERALD, 1)),
                MachineRequirement.itemOutput(item(Items.DIAMOND, 1), 0.5F),
                MachineRequirement.fluidOutput(fluidOutput(Fluids.LAVA, 250), 0.25F)), true, List.of());
    }

    private static MachineIngredient.ItemIngredient itemInputFromData(String itemData, float consumeChance) {
        JsonObject root = JsonParser.parseString(itemData).getAsJsonObject();
        Identifier id = Identifier.parse(root.get("id").getAsString());
        int count = root.has("count") ? root.get("count").getAsInt() : 1;
        return new MachineIngredient.ItemIngredient(Ingredient.of(BuiltInRegistries.ITEM.getValue(id)), count,
                componentsFromData(root), consumeChance);
    }

    private static MachineRequirement itemOutputRequirementFromData(String itemData) {
        JsonObject root = JsonParser.parseString(itemData).getAsJsonObject();
        Identifier id = Identifier.parse(root.get("id").getAsString());
        int count = root.has("count") ? root.get("count").getAsInt() : 1;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        item.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        ItemStack stack = new ItemStack(item, count);
        return new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, stack, 1F, List.of(), componentsFromData(root), 1F);
    }

    private static DataComponentPredicateSet componentsFromData(JsonObject root) {
        JsonObject components = root.getAsJsonObject("components");
        Map<DataComponentType<?>, ComponentPredicate> predicates = new LinkedHashMap<>();
        for (var entry : components.entrySet()) {
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(entry.getKey()));
            if (type == null) throw new IllegalArgumentException("Unknown data component type " + entry.getKey());
            predicates.put(type, ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE, entry.getValue())));
        }
        return new DataComponentPredicateSet(predicates);
    }

    private static DataComponentPredicateSet componentsFromData(String componentsData) {
        JsonObject components = JsonParser.parseString(componentsData).getAsJsonObject();
        Map<DataComponentType<?>, ComponentPredicate> predicates = new LinkedHashMap<>();
        for (var entry : components.entrySet()) {
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(entry.getKey()));
            if (type == null) throw new IllegalArgumentException("Unknown data component type " + entry.getKey());
            predicates.put(type, ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE, entry.getValue())));
        }
        return new DataComponentPredicateSet(predicates);
    }

    private static DataComponentPredicateSet namedPredicate(String name) {
        return new DataComponentPredicateSet(Map.of(DataComponents.CUSTOM_NAME,
                ComponentPredicate.text(name, ComponentPredicate.TextMode.PLAIN)));
    }

    private static List<Definition> thermalSmeltingFurnaceDefinitions() {
        return List.of(
                new Definition(MMCR.id("thermal_smelting_furnace_coal_iron_to_netherite_scrap"), THERMAL_SMELTING_FURNACE_ID, 80,
                        List.of(itemInput(Items.COAL, 1), itemInput(Items.RAW_IRON, 1), energyInput(200)), List.of(item(Items.IRON_INGOT, 1)), List.of(), 4),
                thermalSmeltingDefinition("copper", DefaultMachineLevels.COPPER_COIL, 120, Items.RAW_COPPER, Items.COPPER_INGOT, 400),
                thermalSmeltingDefinition("iron", DefaultMachineLevels.IRON_COIL, 160, Items.IRON_INGOT, Items.GOLD_INGOT, 800),
                thermalSmeltingDefinition("gold", DefaultMachineLevels.GOLD_COIL, 200, Items.GOLD_INGOT, Items.DIAMOND, 1_200),
                thermalSmeltingDefinition("diamond", DefaultMachineLevels.DIAMOND_COIL, 240, Items.DIAMOND, Items.NETHERITE_INGOT, 2_000));
    }

    private static List<MachineRecipe> purpurFurnaceRecipes() {
        return List.of(
                purpurFurnaceRecipe("mode_1", 200, 5, Items.DIAMOND, 2,
                        SmartInterfaceRequirement.input("Mode", 1F)),
                purpurFurnaceRecipe("mode_2", 200, 5, Items.GOLD_INGOT, 4,
                        SmartInterfaceRequirement.input("Mode", 2F)),
                purpurFurnaceRecipe("mode_3", 200, 5, Items.IRON_INGOT, 8,
                        SmartInterfaceRequirement.input("Mode", 3F)),
                purpurFurnaceRecipe("temperature_400", 320, 3, Items.APPLE, 8,
                        SmartInterfaceRequirement.input("Temperature", 400F)),
                purpurFurnaceRecipe("temperature_1600", 240, 6, Items.BAKED_POTATO, 6,
                        SmartInterfaceRequirement.input("Temperature", 1600F)),
                purpurFurnaceRecipe("temperature_3200", 160, 9, Items.BRICK, 4,
                        SmartInterfaceRequirement.input("Temperature", 3200F)),
                purpurFurnaceRecipe("temperature_6800", 60, 14, Items.CHARCOAL, 2,
                        SmartInterfaceRequirement.input("Temperature", 6800F)),
                purpurFurnaceRecipe("conversion_0", 200, 2, Items.STICK, 1,
                        SmartInterfaceRequirement.input("ConversionRate", 0F)),
                purpurFurnaceRecipe("conversion_50", 200, 6, Items.BONE_MEAL, 4,
                        SmartInterfaceRequirement.input("ConversionRate", 0.5F)),
                purpurFurnaceRecipe("conversion_100", 200, 12, Items.GLOWSTONE_DUST, 8,
                        SmartInterfaceRequirement.input("ConversionRate", 1F)),
                purpurFurnaceRecipe("mode_temperature", 120, 10, Items.POPPED_CHORUS_FRUIT, 3,
                        SmartInterfaceRequirement.input("Mode", 2F),
                        SmartInterfaceRequirement.input("Temperature", 3200F)),
                purpurFurnaceRecipe("mode_conversion", 200, 9, Items.STRING, 6,
                        SmartInterfaceRequirement.input("Mode", 3F),
                        SmartInterfaceRequirement.input("ConversionRate", 0.75F)),
                purpurFurnaceRecipe("temperature_conversion", 90, 15, Items.CLAY_BALL, 5,
                        SmartInterfaceRequirement.input("Temperature", 5200F),
                        SmartInterfaceRequirement.input("ConversionRate", 0.8F)),
                purpurFurnaceRecipe("mode_temperature_conversion", 80, 18, Items.ENDER_PEARL, 4,
                        SmartInterfaceRequirement.input("Mode", 1F),
                        SmartInterfaceRequirement.input("Temperature", 5200F),
                        SmartInterfaceRequirement.input("ConversionRate", 1F)));
    }

    private static MachineRecipe distillationTowerTestRecipe() {
        return new MachineRecipe(MMCR.id("distillation_tower_test_shared"), DISTILLATION_TOWER_TEST_ID, 20,
                List.of(itemInput(Items.COAL, 1), energyInput(10)), List.of(), List.of(), 0, 1, true,
                List.of(
                        fluidOutput(Fluids.WATER, 1_000),
                        fluidOutput(Fluids.LAVA, 1_000),
                        fluidOutput(Fluids.WATER, 1_000)),
                List.of(), true, List.of(), true);
    }

    private static MachineRecipe purpurFurnaceRecipe(String path, int ticks, int energyPerTick, Item output, int count,
                                                     SmartInterfaceRequirement... smartRequirements) {
        var coal = itemInput(Items.COAL, 1);
        var energy = energyInput(energyPerTick);
        var result = item(output, count);
        List<MachineRequirement> requirements = new ArrayList<>();
        requirements.add(MachineRequirement.fromInput(coal));
        requirements.add(MachineRequirement.fromInput(energy));
        requirements.add(MachineRequirement.itemOutput(result));
        requirements.addAll(List.of(smartRequirements));
        return new MachineRecipe(MMCR.id("purpur_furnace_" + path), PURPUR_FURNACE_ID, ticks,
                List.of(coal, energy), List.of(result), List.of(), 0, 1, true, List.of(),
                requirements, true, List.of());
    }

    private static Definition thermalSmeltingDefinition(String level, Identifier levelId, int ticks,
                                                        Item input, Item output, int energy) {
        return new Definition(MMCR.id("thermal_smelting_furnace_" + level), THERMAL_SMELTING_FURNACE_ID, ticks,
                List.of(itemInput(Items.COAL, 1), itemInput(input, 1), energyInput(energy)), List.of(item(output, 1)), List.of(), 4,
                List.of(new LevelRequirement(DefaultMachineLevels.THERMAL_SMELTING_COIL_TYPE, levelId)));
    }

    private static List<Definition> alloyFurnaceDefinitions() {
        List<Definition> definitions = new ArrayList<>(standardDefinitions(ALLOY_FURNACE_ID, "alloy_furnace",
                new Definition(ALLOY_FURNACE_NETHERITE_ID, ALLOY_FURNACE_ID, 100, List.of(itemInput(Items.ANCIENT_DEBRIS, 1), itemInput(Items.GOLD_INGOT, 1), energyInput(5)), List.of(item(Items.NETHERITE_INGOT, 1)), List.of())));
        definitions.add(new Definition(MMCR.id("alloy_furnace_jei_large"), ALLOY_FURNACE_ID, 400,
                largeItemInputs(), largeItemOutputs(), List.of()));
        definitions.add(new Definition(MMCR.id("alloy_furnace_jei_25x25"), ALLOY_FURNACE_ID, 500,
                twentyFiveItemInputs(), twentyFiveItemOutputs(), List.of()));
        return List.copyOf(definitions);
    }

    private static List<MachineIngredient> largeItemInputs() {
        return Stream.of(
                Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.REDSTONE, Items.LAPIS_LAZULI,
                Items.COAL, Items.DIAMOND, Items.EMERALD, Items.QUARTZ, Items.AMETHYST_SHARD,
                Items.NETHERITE_SCRAP, Items.IRON_NUGGET, Items.GOLD_NUGGET, Items.COPPER_BLOCK, Items.IRON_BLOCK,
                Items.GOLD_BLOCK, Items.REDSTONE_BLOCK, Items.LAPIS_BLOCK, Items.DIAMOND_BLOCK, Items.EMERALD_BLOCK,
                Items.QUARTZ_BLOCK
        ).map(item -> itemInput(item, 1)).toList();
    }

    private static List<ItemStack> largeItemOutputs() {
        return Stream.of(
                Items.IRON_NUGGET, Items.GOLD_NUGGET, Items.COPPER_NUGGET, Items.REDSTONE, Items.LAPIS_LAZULI,
                Items.COAL, Items.DIAMOND, Items.EMERALD, Items.QUARTZ, Items.AMETHYST_SHARD,
                Items.NETHERITE_SCRAP, Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.IRON_BLOCK,
                Items.GOLD_BLOCK, Items.COPPER_BLOCK, Items.REDSTONE_BLOCK, Items.LAPIS_BLOCK, Items.DIAMOND_BLOCK,
                Items.EMERALD_BLOCK
        ).map(item -> item(item, 1)).toList();
    }

    private static List<MachineIngredient> twentyFiveItemInputs() {
        return Stream.of(
                Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.REDSTONE, Items.LAPIS_LAZULI,
                Items.COAL, Items.DIAMOND, Items.EMERALD, Items.QUARTZ, Items.AMETHYST_SHARD,
                Items.NETHERITE_SCRAP, Items.IRON_NUGGET, Items.GOLD_NUGGET, Items.COPPER_BLOCK, Items.IRON_BLOCK,
                Items.GOLD_BLOCK, Items.REDSTONE_BLOCK, Items.LAPIS_BLOCK, Items.DIAMOND_BLOCK, Items.EMERALD_BLOCK,
                Items.QUARTZ_BLOCK, Items.COAL_BLOCK, Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_COPPER
        ).map(item -> itemInput(item, 1)).toList();
    }

    private static List<ItemStack> twentyFiveItemOutputs() {
        return Stream.of(
                Items.RAW_COPPER, Items.RAW_GOLD, Items.RAW_IRON, Items.COAL_BLOCK, Items.QUARTZ_BLOCK,
                Items.EMERALD_BLOCK, Items.DIAMOND_BLOCK, Items.LAPIS_BLOCK, Items.REDSTONE_BLOCK, Items.GOLD_BLOCK,
                Items.IRON_BLOCK, Items.COPPER_BLOCK, Items.GOLD_NUGGET, Items.IRON_NUGGET, Items.NETHERITE_SCRAP,
                Items.AMETHYST_SHARD, Items.QUARTZ, Items.EMERALD, Items.DIAMOND, Items.COAL,
                Items.LAPIS_LAZULI, Items.REDSTONE, Items.COPPER_INGOT, Items.GOLD_INGOT, Items.IRON_INGOT
        ).map(item -> item(item, 1)).toList();
    }

    private static List<Definition> standardDefinitions(Identifier machineId, String prefix, Definition first) {
        return List.of(
                first,
                new Definition(MMCR.id(prefix + "_copper_to_nugget"), machineId, 200, List.of(itemInput(Items.COPPER_INGOT, 1), energyInput(2)), List.of(item(Items.COPPER_NUGGET, 1)), List.of()),
                new Definition(MMCR.id(prefix + "_gold_to_nugget"), machineId, 200, List.of(itemInput(Items.GOLD_INGOT, 1), energyInput(3)), List.of(item(Items.GOLD_NUGGET, 1)), List.of()),
                new Definition(MMCR.id(prefix + "_multi_item"), machineId, 200, List.of(itemInput(Items.IRON_INGOT, 1), itemInput(Items.GOLD_INGOT, 1), itemInput(Items.COPPER_INGOT, 1), energyInput(4)), List.of(item(Items.DIAMOND, 1)), List.of()),
                new Definition(MMCR.id(prefix + "_multi_output"), machineId, 200, List.of(itemInput(Items.IRON_INGOT, 1), energyInput(5)), List.of(item(Items.IRON_NUGGET, 1), item(Items.GOLD_NUGGET, 1), item(Items.COPPER_NUGGET, 1)), List.of()),
                new Definition(MMCR.id(prefix + "_water_input"), machineId, 200, List.of(fluidInput(Fluids.WATER, 250), energyInput(6)), List.of(item(Items.CLAY_BALL, 1)), List.of()),
                new Definition(MMCR.id(prefix + "_lava_output"), machineId, 200, List.of(itemInput(Items.COAL, 1), energyInput(7)), List.of(item(Items.REDSTONE, 1)), List.of(fluidOutput(Fluids.LAVA, 250))),
                new Definition(MMCR.id(prefix + "_water_to_lava"), machineId, 200, List.of(fluidInput(Fluids.WATER, 500), energyInput(8)), List.of(item(Items.COAL, 1)), List.of(fluidOutput(Fluids.LAVA, 500))),
                new Definition(MMCR.id(prefix + "_mixed_input"), machineId, 200, List.of(fluidInput(Fluids.WATER, 250), itemInput(Items.IRON_INGOT, 1), itemInput(Items.GOLD_INGOT, 1), energyInput(9)), List.of(item(Items.EMERALD, 1)), List.of()),
                new Definition(MMCR.id(prefix + "_mixed_output"), machineId, 200, List.of(fluidInput(Fluids.WATER, 250), itemInput(Items.DIAMOND, 1), energyOutput(100)), List.of(item(Items.IRON_NUGGET, 1), item(Items.GOLD_NUGGET, 1)), List.of(fluidOutput(Fluids.LAVA, 125)))
        );
    }

    private static ItemStack item(Item item, int count) {
        item.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        return new ItemStack(item, count);
    }

    private static ItemStack namedItem(Item item, int count, String name) {
        bindItem(item);
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static MachineIngredient itemInput(Item item, int count) {
        return new MachineIngredient.ItemIngredient(Ingredient.of(item), count);
    }

    private static MachineIngredient itemInput(Item item, int count, float consumeChance) {
        return new MachineIngredient.ItemIngredient(Ingredient.of(item), count, DataComponentPredicateSet.EMPTY, consumeChance);
    }

    private static MachineIngredient fluidInput(Fluid fluid, int amount) {
        return new MachineIngredient.FluidIngredient(FluidIngredient.of(fluid), amount);
    }

    private static MachineIngredient energyInput(int fePerTick) {
        return new MachineIngredient.EnergyIngredient(fePerTick);
    }

    private static MachineIngredient energyOutput(int fePerTick) {
        return new MachineIngredient.EnergyIngredient(RecipeModifier.IOType.OUTPUT, fePerTick);
    }

    private static FluidStack fluidOutput(Fluid fluid, int amount) {
        return new FluidStack(boundFluid(fluid), amount);
    }

    private record Definition(Identifier id, Identifier machineId, int ticks, List<MachineIngredient> inputs,
                              List<ItemStack> outputs, List<FluidStack> fluidOutputs, int maxThreads,
                              List<LevelRequirement> levelRequirements) {
        private Definition(Identifier id, Identifier machineId, int ticks, List<MachineIngredient> inputs,
                           List<ItemStack> outputs, List<FluidStack> fluidOutputs) {
            this(id, machineId, ticks, inputs, outputs, fluidOutputs, 1, List.of());
        }

        private Definition(Identifier id, Identifier machineId, int ticks, List<MachineIngredient> inputs,
                           List<ItemStack> outputs, List<FluidStack> fluidOutputs, int maxThreads) {
            this(id, machineId, ticks, inputs, outputs, fluidOutputs, maxThreads, List.of());
        }
    }

    private static Holder<Fluid> boundFluid(Fluid fluid) {
        var holder = fluid.builtInRegistryHolder();
        holder.bindComponents(DataComponentMap.EMPTY);
        return holder;
    }

    private static void bindItem(Item item) {
        item.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64)
                .build());
    }

    private static void register(MachineRecipe recipe) {
        RecipeRegistry.register(recipe);
    }
}
