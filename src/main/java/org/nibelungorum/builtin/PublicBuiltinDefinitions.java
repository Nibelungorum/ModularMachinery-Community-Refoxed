package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.PublicBuiltinRegistration;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.machine.MachineRole;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.publicapi.machine.ParallelTier;
import cn.howxu.mmcr.api.publicapi.machine.PortTiers;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.api.publicapi.recipe.RecipeIo;
import cn.howxu.mmcr.api.publicapi.recipe.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.publicapi.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.publicapi.recipe.component.DataComponentPredicateSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Complete built-in declarations exposed through the public API path.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PublicBuiltinDefinitions {
    private static final Identifier BLAST_FURNACE = id("blast_furnace");
    private static final Identifier ALLOY_FURNACE = id("alloy_furnace");
    private static final Identifier CRACKER = id("cracker");
    private static final Identifier REACTOR = id("reactor");
    private static final Identifier THERMAL_SMELTING_FURNACE = id("thermal_smelting_furnace");
    private static final Identifier PURPUR_FURNACE = id("purpur_furnace");
    private static final Identifier DISTILLATION_TOWER = id("distillation_tower");
    private static final Identifier ECO_MATRIX = id("eco_matrix");
    private static final Identifier SPACE_ELEVATOR = id("space_elevator");
    private static final Identifier SPACE_REASSEMBLER = id("space_reassembler");
    private static final Identifier MONSTER_FARM = id("monster_farm");

    private PublicBuiltinDefinitions() {}

    public static Map<Identifier, MachineDefinition> machineDefinitions() {
        Map<Identifier, MachineDefinition> result = new LinkedHashMap<>();
        result.put(BLAST_FURNACE, MachineBuilder.machine(BLAST_FURNACE).displayNameKey("machine.mmcr.blast_furnace")
                .controller(controllerSpec(BLAST_FURNACE, true, true, false)).maxParallelism(Integer.MAX_VALUE)
                .parallelizable(true).factory(factory -> factory.hasFactory(true).threadLimit(4)).build());
        result.put(ALLOY_FURNACE, MachineBuilder.machine(ALLOY_FURNACE).displayNameKey("machine.mmcr.alloy_furnace")
                .appearance(appearance -> appearance.machineBasicBlock(mc("bricks"))).build());
        result.put(CRACKER, MachineBuilder.machine(CRACKER).displayNameKey("machine.mmcr.cracker")
                .controller(controllerSpec(CRACKER, true, true, false)).build());
        result.put(REACTOR, MachineBuilder.machine(REACTOR).displayNameKey("machine.mmcr.reactor")
                .controller(controllerSpec(REACTOR, false, false, false)).appearance(a -> a.machineBasicBlock(mc("blue_ice"))).build());
        result.put(THERMAL_SMELTING_FURNACE, MachineBuilder.machine(THERMAL_SMELTING_FURNACE)
                .displayNameKey("machine.mmcr.thermal_smelting_furnace").appearance(a -> a.machineBasicBlock(mc("smooth_basalt")))
                .maxParallelism(Integer.MAX_VALUE).parallelizable(true).build());
        result.put(PURPUR_FURNACE, MachineBuilder.machine(PURPUR_FURNACE).displayNameKey("machine.mmcr.purpur_furnace")
                .appearance(a -> a.machineBasicBlock(mc("end_stone_bricks"))).maxParallelism(4).parallelizable(true).build());
        result.put(DISTILLATION_TOWER, MachineBuilder.machine(DISTILLATION_TOWER).displayNameKey("machine.mmcr.distillation_tower")
                .appearance(a -> a.machineBasicBlock(mc("polished_blackstone"))).maxParallelism(4).parallelizable(true).build());
        result.put(ECO_MATRIX, MachineBuilder.machine(ECO_MATRIX).displayNameKey("machine.mmcr.eco_matrix")
                .appearance(a -> a.machineBasicBlock(mc("sea_lantern"))).build());
        result.put(SPACE_ELEVATOR, MachineBuilder.machine(SPACE_ELEVATOR).displayNameKey("machine.mmcr.space_elevator")
                .appearance(a -> a.machineBasicBlock(mc("smooth_quartz")).controllerBaseTexture(mc("block/quartz_block_bottom"))
                        .formedPortBaseTexture(mc("block/quartz_block_bottom"))).role(MachineRole.HOST)
                .acceptedModule(SPACE_REASSEMBLER).build());
        result.put(SPACE_REASSEMBLER, MachineBuilder.machine(SPACE_REASSEMBLER).displayNameKey("machine.mmcr.space_reassembler")
                .appearance(a -> a.machineBasicBlock(mc("quartz_pillar"))).role(MachineRole.MODULE).build());
        result.put(MONSTER_FARM, MachineBuilder.machine(MONSTER_FARM).displayNameKey("machine.mmcr.monster_farm")
                .controller(controllerSpec(MONSTER_FARM, true, false, true)).build());
        return Map.copyOf(result);
    }

    public static Map<Identifier, MachineStructureDefinition> structureDefinitions() {
        Map<Identifier, MachineStructureDefinition> result = new LinkedHashMap<>();
        result.put(BLAST_FURNACE, blastFurnace());
        result.put(ALLOY_FURNACE, alloyFurnace());
        result.put(CRACKER, cracker());
        result.put(REACTOR, reactor());
        result.put(THERMAL_SMELTING_FURNACE, thermalSmeltingFurnace());
        result.put(PURPUR_FURNACE, purpurFurnace());
        result.put(DISTILLATION_TOWER, distillationTower());
        result.put(ECO_MATRIX, ecoMatrix());
        result.put(SPACE_ELEVATOR, spaceElevator());
        result.put(SPACE_REASSEMBLER, spaceReassembler());
        result.put(MONSTER_FARM, monsterFarm());
        return Map.copyOf(result);
    }

    public static Map<Identifier, MachineRecipeDefinition> recipeDefinitions() {
        bindVanillaFluids();
        Map<Identifier, MachineRecipeDefinition> result = new LinkedHashMap<>();
        standard(result, BLAST_FURNACE, "blast_furnace", "blast_furnace_iron_to_nugget", recipe("blast_furnace_iron_to_nugget", BLAST_FURNACE, 200).inputItem(Items.IRON_INGOT, 1).inputEnergy(1).outputItem(Items.IRON_NUGGET, 1));
        alloyRecipes(result);
        standard(result, CRACKER, "cracker", "cracker_coal_lapis", recipe("cracker_coal_lapis", CRACKER, 160).inputItem(Items.COAL, 8).inputItem(Items.LAPIS_LAZULI, 1).inputEnergy(100).outputItem(Items.REDSTONE, 4).outputFluid(Fluids.WATER, 500));
        standard(result, REACTOR, "reactor", "reactor_diamond_water", recipe("reactor_diamond_water", REACTOR, 200).inputItem(Items.DIAMOND, 1).inputFluid(Fluids.WATER, 500).outputEnergy(100).outputItem(Items.COAL, 1).outputFluid(Fluids.LAVA, 500));
        thermalRecipes(result);
        purpurRecipes(result);
        distillationRecipes(result);
        result.put(id("eco_matrix_energy_drain"), recipe("eco_matrix_energy_drain", ECO_MATRIX, 200).inputEnergy(100).parallelized(false).build());
        Item threadDisperser = modItem("thread_disperser");
        if (threadDisperser != Items.AIR) {
            result.put(id("space_elevator_thread_dispersal"), recipe("space_elevator_thread_dispersal", SPACE_ELEVATOR, 1000).inputItem(threadDisperser, 1).inputEnergy(10000).parallelized(false).build());
        }
        result.put(id("space_reassembler_steak_to_golden_carrot"), recipe("space_reassembler_steak_to_golden_carrot", SPACE_REASSEMBLER, 600).inputItem(Items.COOKED_BEEF, 4).inputEnergy(15000).outputItem(Items.GOLDEN_CARROT, 1).requiredHost(SPACE_ELEVATOR).parallelized(false).build());
        result.put(id("space_reassembler_water_to_healing"), recipe("space_reassembler_water_to_healing", SPACE_REASSEMBLER, 400).inputItem(Items.POTION, 1).inputEnergy(8000).outputItem(Items.POTION, 1).requiredHost(SPACE_ELEVATOR).parallelized(false).build());
        result.put(id("space_reassembler_water_to_swiftness"), recipe("space_reassembler_water_to_swiftness", SPACE_REASSEMBLER, 400).inputItem(Items.POTION, 1).inputEnergy(8000).outputItem(Items.POTION, 1).requiredHost(SPACE_ELEVATOR).parallelized(false).build());
        componentRecipes(result);
        return Map.copyOf(result);
    }

    private static void standard(Map<Identifier, MachineRecipeDefinition> out, Identifier machine, String prefix, String firstPath, MachineRecipeBuilder first) {
        out.put(id(firstPath), first.build());
        out.put(id(prefix + "_copper_to_nugget"), recipe(prefix + "_copper_to_nugget", machine, 200).inputItem(Items.COPPER_INGOT, 1).inputEnergy(2).outputItem(Items.COPPER_NUGGET, 1).build());
        out.put(id(prefix + "_gold_to_nugget"), recipe(prefix + "_gold_to_nugget", machine, 200).inputItem(Items.GOLD_INGOT, 1).inputEnergy(3).outputItem(Items.GOLD_NUGGET, 1).build());
        out.put(id(prefix + "_multi_item"), recipe(prefix + "_multi_item", machine, 200).inputItem(Items.IRON_INGOT, 1).inputItem(Items.GOLD_INGOT, 1).inputItem(Items.COPPER_INGOT, 1).inputEnergy(4).outputItem(Items.DIAMOND, 1).build());
        out.put(id(prefix + "_multi_output"), recipe(prefix + "_multi_output", machine, 200).inputItem(Items.IRON_INGOT, 1).inputEnergy(5).outputItem(Items.IRON_NUGGET, 1).outputItem(Items.GOLD_NUGGET, 1).outputItem(Items.COPPER_NUGGET, 1).build());
        out.put(id(prefix + "_water_input"), recipe(prefix + "_water_input", machine, 200).inputFluid(Fluids.WATER, 250).inputEnergy(6).outputItem(Items.CLAY_BALL, 1).build());
        out.put(id(prefix + "_lava_output"), recipe(prefix + "_lava_output", machine, 200).inputItem(Items.COAL, 1).inputEnergy(7).outputItem(Items.REDSTONE, 1).outputFluid(Fluids.LAVA, 250).build());
        out.put(id(prefix + "_water_to_lava"), recipe(prefix + "_water_to_lava", machine, 200).inputFluid(Fluids.WATER, 500).inputEnergy(8).outputItem(Items.COAL, 1).outputFluid(Fluids.LAVA, 500).build());
        out.put(id(prefix + "_mixed_input"), recipe(prefix + "_mixed_input", machine, 200).inputFluid(Fluids.WATER, 250).inputItem(Items.IRON_INGOT, 1).inputItem(Items.GOLD_INGOT, 1).inputEnergy(9).outputItem(Items.EMERALD, 1).build());
        out.put(id(prefix + "_mixed_output"), recipe(prefix + "_mixed_output", machine, 200).inputFluid(Fluids.WATER, 250).inputItem(Items.DIAMOND, 1).outputEnergy(100).outputItem(Items.IRON_NUGGET, 1).outputItem(Items.GOLD_NUGGET, 1).outputFluid(Fluids.LAVA, 125).build());
    }

    private static void alloyRecipes(Map<Identifier, MachineRecipeDefinition> out) {
        standard(out, ALLOY_FURNACE, "alloy_furnace", "alloy_furnace_netherite", recipe("alloy_furnace_netherite", ALLOY_FURNACE, 100).inputItem(Items.ANCIENT_DEBRIS, 1).inputItem(Items.GOLD_INGOT, 1).inputEnergy(5).outputItem(Items.NETHERITE_INGOT, 1));
        out.put(id("alloy_furnace_jei_large"), bulkRecipe("alloy_furnace_jei_large", ALLOY_FURNACE, 400, false));
        out.put(id("alloy_furnace_jei_25x25"), bulkRecipe("alloy_furnace_jei_25x25", ALLOY_FURNACE, 500, true));
    }

    private static void thermalRecipes(Map<Identifier, MachineRecipeDefinition> out) {
        out.put(id("thermal_smelting_furnace_coal_iron_to_netherite_scrap"), recipe("thermal_smelting_furnace_coal_iron_to_netherite_scrap", THERMAL_SMELTING_FURNACE, 80).inputItem(Items.COAL, 1).inputItem(Items.RAW_IRON, 1).inputEnergy(200).outputItem(Items.IRON_INGOT, 1).maxThreads(4).build());
        thermal(out, "copper", 120, Items.RAW_COPPER, Items.COPPER_INGOT, 400, PublicBuiltinLevelDefinitions.COPPER_COIL);
        thermal(out, "iron", 160, Items.IRON_INGOT, Items.GOLD_INGOT, 800, PublicBuiltinLevelDefinitions.IRON_COIL);
        thermal(out, "gold", 200, Items.GOLD_INGOT, Items.DIAMOND, 1200, PublicBuiltinLevelDefinitions.GOLD_COIL);
        thermal(out, "diamond", 240, Items.DIAMOND, Items.NETHERITE_INGOT, 2000, PublicBuiltinLevelDefinitions.DIAMOND_COIL);
    }

    private static void thermal(Map<Identifier, MachineRecipeDefinition> out, String name, int ticks, Item input, Item output, int energy, Identifier level) {
        MachineRecipeDefinition definition = recipe("thermal_smelting_furnace_" + name, THERMAL_SMELTING_FURNACE, ticks)
                .inputItem(Items.COAL, 1).inputItem(input, 1).inputEnergy(energy).outputItem(output, 1).maxThreads(4).build();
        out.put(id("thermal_smelting_furnace_" + name), definition);
    }

    private static void purpurRecipes(Map<Identifier, MachineRecipeDefinition> out) {
        purpur(out, "mode_1", 200, 5, Items.DIAMOND, 2, SmartInterfaceRequirement.input("Mode", 1));
        purpur(out, "mode_2", 200, 5, Items.GOLD_INGOT, 4, SmartInterfaceRequirement.input("Mode", 2));
        purpur(out, "mode_3", 200, 5, Items.IRON_INGOT, 8, SmartInterfaceRequirement.input("Mode", 3));
        purpur(out, "temperature_400", 320, 3, Items.APPLE, 8, SmartInterfaceRequirement.input("Temperature", 400));
        purpur(out, "temperature_1600", 240, 6, Items.BAKED_POTATO, 6, SmartInterfaceRequirement.input("Temperature", 1600));
        purpur(out, "temperature_3200", 160, 9, Items.BRICK, 4, SmartInterfaceRequirement.input("Temperature", 3200));
        purpur(out, "temperature_6800", 60, 14, Items.CHARCOAL, 2, SmartInterfaceRequirement.input("Temperature", 6800));
        purpur(out, "conversion_0", 200, 2, Items.STICK, 1, SmartInterfaceRequirement.input("ConversionRate", 0));
        purpur(out, "conversion_50", 200, 6, Items.BONE_MEAL, 4, SmartInterfaceRequirement.input("ConversionRate", .5F));
        purpur(out, "conversion_100", 200, 12, Items.GLOWSTONE_DUST, 8, SmartInterfaceRequirement.input("ConversionRate", 1));
        purpur(out, "mode_temperature", 120, 10, Items.POPPED_CHORUS_FRUIT, 3, SmartInterfaceRequirement.input("Mode", 2), SmartInterfaceRequirement.input("Temperature", 3200));
        purpur(out, "mode_conversion", 200, 9, Items.STRING, 6, SmartInterfaceRequirement.input("Mode", 3), SmartInterfaceRequirement.input("ConversionRate", .75F));
        purpur(out, "temperature_conversion", 90, 15, Items.CLAY_BALL, 5, SmartInterfaceRequirement.input("Temperature", 5200), SmartInterfaceRequirement.input("ConversionRate", .8F));
        purpur(out, "mode_temperature_conversion", 80, 18, Items.ENDER_PEARL, 4, SmartInterfaceRequirement.input("Mode", 1), SmartInterfaceRequirement.input("Temperature", 5200), SmartInterfaceRequirement.input("ConversionRate", 1));
    }

    private static void purpur(Map<Identifier, MachineRecipeDefinition> out, String path, int ticks, int energy, Item output, int count, SmartInterfaceRequirement... requirements) {
        MachineRecipeBuilder builder = recipe("purpur_furnace_" + path, PURPUR_FURNACE, ticks).inputItem(Items.COAL, 1).inputEnergy(energy).outputItem(output, count);
        for (SmartInterfaceRequirement requirement : requirements) builder.requirement(requirement);
        MachineRecipeDefinition definition = builder.build();
        out.put(id("purpur_furnace_" + path), definition);
    }

    private static void distillationRecipes(Map<Identifier, MachineRecipeDefinition> out) {
        distill(out, "coal", Items.COAL, Items.COAL, Items.CHARCOAL, Items.GUNPOWDER);
        distill(out, "oak_log", Items.OAK_LOG, Items.CHARCOAL, Items.STICK, Items.COAL);
        distill(out, "dried_kelp", Items.DRIED_KELP, Items.KELP, Items.COAL, Items.BONE_MEAL);
    }

    private static void distill(Map<Identifier, MachineRecipeDefinition> out, String path, Item input, Item a, Item b, Item c) {
        out.put(id("distillation_tower_" + path), recipe("distillation_tower_" + path, DISTILLATION_TOWER, 200).inputItem(input, 1).inputEnergy(40).maxThreads(4).outputItem(a, 1).outputItem(b, 1).outputItem(c, 1).build());
    }

    private static void componentRecipes(Map<Identifier, MachineRecipeDefinition> out) {
        for (Identifier machine : List.of(BLAST_FURNACE, ALLOY_FURNACE, CRACKER, REACTOR, THERMAL_SMELTING_FURNACE)) {
            String prefix = machine.getPath() + "_component_";
            out.put(id(prefix + "chanced_input"), recipe(prefix + "chanced_input", machine, 20).inputItem(net.minecraft.world.item.crafting.Ingredient.of(Items.DIAMOND), 1, DataComponentPredicateSet.EMPTY, .5F).outputItem(Items.EMERALD, 1).build());
            out.put(id(prefix + "non_consumable_input"), recipe(prefix + "non_consumable_input", machine, 20).inputItem(net.minecraft.world.item.crafting.Ingredient.of(Items.DIAMOND), 1, DataComponentPredicateSet.EMPTY, 0F).outputItem(Items.EMERALD, 1).build());
            out.put(id(prefix + "input_to_plain_output"), recipe(prefix + "input_to_plain_output", machine, 20).inputItem(net.minecraft.world.item.crafting.Ingredient.of(Items.DIAMOND), 1, named("Input Only"), 1).outputItem(Items.EMERALD, 1).build());
            out.put(id(prefix + "plain_input_to_output"), recipe(prefix + "plain_input_to_output", machine, 20).inputItem(Items.IRON_INGOT, 1).outputItem(namedItem(Items.GOLD_INGOT, 1, "Output Only")).build());
            out.put(id(prefix + "input_to_output"), recipe(prefix + "input_to_output", machine, 20).inputItem(net.minecraft.world.item.crafting.Ingredient.of(Items.DIAMOND), 1, named("Input"), 1).outputItem(namedItem(Items.GOLD_INGOT, 1, "Output")).build());
            out.put(id(prefix + "mixed_inputs"), recipe(prefix + "mixed_inputs", machine, 20).inputItem(net.minecraft.world.item.crafting.Ingredient.of(Items.DIAMOND), 1, named("Named"), 1).inputItem(Items.IRON_INGOT, 1).outputItem(Items.EMERALD, 1).build());
            out.put(id(prefix + "mixed_outputs"), recipe(prefix + "mixed_outputs", machine, 20).inputItem(Items.IRON_INGOT, 1).outputItem(namedItem(Items.GOLD_INGOT, 1, "Named Output")).outputItem(Items.EMERALD, 1).build());
            out.put(id(prefix + "enchanted_non_consumable"), recipe(prefix + "enchanted_non_consumable", machine, 100).inputItem(net.minecraft.world.item.crafting.Ingredient.of(Items.DIAMOND_SWORD), 1, enchantment(), 0F).build());
            out.put(id(prefix + "enchanted_output"), recipe(prefix + "enchanted_output", machine, 100).inputItem(Items.IRON_SWORD, 1)
                    .outputItem(new ItemStack(Items.IRON_SWORD), enchantment()).build());
            out.put(id(prefix + "chanced_outputs"), recipe(prefix + "chanced_outputs", machine, 20).inputItem(Items.IRON_INGOT, 1).outputChance(new ItemStack(Items.EMERALD, 1), 1).outputChance(new ItemStack(Items.DIAMOND, 1), .5F).outputFluid(Fluids.LAVA, 250).build());
        }
        out.put(id("blast_furnace_component_tag_input"), recipe("blast_furnace_component_tag_input", BLAST_FURNACE, 20).inputItem(ItemTags.LOGS, 1).outputItem(Items.CHARCOAL, 1).build());
        out.put(id("blast_furnace_component_tag_named_input"), recipe("blast_furnace_component_tag_named_input", BLAST_FURNACE, 20).inputItem(ItemTags.PLANKS, 1).outputItem(Items.EMERALD, 1).build());
        out.put(id("blast_furnace_component_tag_enchanted_input"), recipe("blast_furnace_component_tag_enchanted_input", BLAST_FURNACE, 20).inputItem(ItemTags.SWORDS, 1).outputItem(Items.DIAMOND, 1).build());
    }

    private static MachineRecipeBuilder recipe(String path, Identifier machine, int ticks) { return MachineRecipeBuilder.recipe(id(path), machine).duration(ticks); }

    private static MachineStructureDefinition blastFurnace() {
        return MachineStructureBuilder.structure().fullStructure(s -> s.pattern(p -> p
        .layer("AXA", "XIX", "XXX")
        .layer("XXX", "I I", "XBX")
        .layer("AXA", "XCX", "XXX")
                .where('X', any(block(Blocks.IRON_BLOCK), ports())).where('A', any(block(Blocks.IRON_BLOCK), itemParallel(), port("factory_controller")))
                .where('B', any(block(Blocks.IRON_BLOCK), ports(), port("factory_controller"))).where('C', controller(BLAST_FURNACE)).where('I', ports()).controller('C'))
        .portTiers(t -> t.minEnergyInput(PortTiers.EnergyTier.LUDICROUS).minItemInput(PortTiers.ItemTier.NORMAL).anyItemOutput())).build(BLAST_FURNACE);
    }

    private static MachineStructureDefinition alloyFurnace() {
        return MachineStructureBuilder.structure().fullStructure(s -> s.pattern(p -> p
        .layer("XXX", "XIX", "XXX")
        .layer("XMX", "I I", "XMX")
        .layer("XXX", "XCX", "XXX")
        .where('X', block(Blocks.BRICKS)).where('I', ports()).where('M', block(Blocks.BLAST_FURNACE)).where('C', controller(ALLOY_FURNACE)).controller('C'))).build(ALLOY_FURNACE);
    }

    private static MachineStructureDefinition cracker() {
        return MachineStructureBuilder.structure().fullStructure(s -> s.pattern(p -> p
        .layer("AAA", "AAA", "AAA")
        .layer("XBX", "B B", "XBX")
        .layer("XDX", "D D", "XDX")
        .layer("XEX", "ECE", "XEX")
        .where('X', block(Blocks.POLISHED_DIORITE)).where('A', block(Blocks.POLISHED_ANDESITE)).where('B', any(ports(), block(Blocks.WEATHERED_COPPER))).where('D', block(Blocks.BLUE_ICE)).where('E', block(Blocks.WEATHERED_COPPER)).where('C', controller(CRACKER)).controller('C'))
        .portTiers(t -> t.minFluidOutput(PortTiers.FluidTier.HUGE).minEnergyInput(PortTiers.EnergyTier.REINFORCED).minItemInput(PortTiers.ItemTier.NORMAL).anyItemOutput())).build(CRACKER);
    }

    private static MachineStructureDefinition reactor() {
        return MachineStructureBuilder.structure().fullStructure(s -> s.pattern(p -> p
        .layer("  AAAAA  ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")
        .layer(" AAXXXAA ", "   DDD   ", "         ", "         ", "         ", "         ", "         ", "         ")
        .layer("AAXXXXXAA", "  EFFFE  ", "  EFFFE  ", "  EFFFE  ", "  JJJJJ  ", "         ", "         ", "         ")
        .layer("AXXXXXXXA", " DFGHGFD ", "  FGHGF  ", "  FGHGF  ", "  JXXXJ  ", "   KKK   ", "         ", "         ")
        .layer("AXXXXXXXA", " DFHXHFD ", "  FHXHF  ", "  FHXHF  ", "  JXXXJ  ", "   KLK   ", "    L    ", "    M    ")
        .layer("AXXXXXXXA", " DFGHGFD ", "  FGHGF  ", "  FGHGF  ", "  JXXXJ  ", "   KKK   ", "         ", "         ")
        .layer("AAXXXXXAA", "  EFFFE  ", "  EFFFE  ", "  EFFFE  ", "  JJJJJ  ", "         ", "         ", "         ")
        .layer(" AAXXXAA ", "   DID   ", "         ", "         ", "         ", "         ", "         ", "         ")
        .layer("  AAAAA  ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")
        .where('X', block(Blocks.BLUE_ICE)).where('A', block(Blocks.DEEPSLATE_BRICK_STAIRS)).where('D', any(block(Blocks.BLUE_ICE), ports())).where('E', block(Blocks.POLISHED_DEEPSLATE)).where('F', block(Blocks.BLACK_STAINED_GLASS)).where('G', block(Blocks.EMERALD_BLOCK)).where('H', block(Blocks.LAPIS_BLOCK)).where('I', controller(REACTOR)).where('J', block(Blocks.POLISHED_DEEPSLATE_STAIRS)).where('K', block(Blocks.DEEPSLATE_BRICK_SLAB)).where('L', block(Blocks.DEEPSLATE_TILES)).where('M', block(Blocks.OXIDIZED_LIGHTNING_ROD)).controller('I'))).build(REACTOR);
    }

    private static MachineStructureDefinition thermalSmeltingFurnace() {
        return MachineStructureBuilder.structure().fullStructure(s -> s.pattern(p -> p
        .layer("AAA", "XXX", "XXX", "AAA")
        .layer("AAA", "X X", "X X", "ADA")
        .layer("ABA", "XXX", "XXX", "AAA")
                 .where('X', any(block(Blocks.COPPER_BLOCK), block(Blocks.IRON_BLOCK), block(Blocks.GOLD_BLOCK), block(Blocks.DIAMOND_BLOCK))).where('A', any(block(Blocks.SMOOTH_BASALT), ports(), port("factory_controller"), parallelControllers())).where('B', controller(THERMAL_SMELTING_FURNACE)).where('D', block(Blocks.REINFORCED_DEEPSLATE)).controller('B'))
        .portTiers(t -> t.anyItemInput().anyItemOutput().anyEnergyInput())).build(THERMAL_SMELTING_FURNACE);
    }

    private static MachineStructureDefinition purpurFurnace() {
        return simpleTall(PURPUR_FURNACE, Blocks.END_STONE_BRICKS, Blocks.END_STONE_BRICK_STAIRS);
    }

    private static MachineStructureDefinition distillationTower() {
        return MachineStructureBuilder.structure().fullStructure(s -> s.pattern(p -> p
        .layer("  XXX  ", "  AAA  ", "       ", "       ")
        .layer(" XXXXX ", " B   B ", "  ACA  ", "       ")
        .layer("XXXXXXX", "A     A", " B   B ", "  DDD  ")
        .layer("XXXXXXX", "A     A", " B   B ", "  DDD  ")
        .layer("XXXXXXX", "A     A", " B   B ", "  DDD  ")
        .layer(" XXXXX ", " B   B ", "  BBB  ", "       ")
        .layer("  XXX  ", "  BEB  ", "       ", "       ")
        .where('X', block(Blocks.POLISHED_BLACKSTONE)).where('A', any(block(Blocks.DEEPSLATE_BRICKS), ports())).where('B', block(Blocks.POLISHED_BLACKSTONE_BRICKS)).where('C', any(block(Blocks.DEEPSLATE_BRICKS), port("item_output_bus_tiny"))).where('D', block(Blocks.GILDED_BLACKSTONE)).where('E', controller(DISTILLATION_TOWER)).controller('E')).portTiers(t -> t.anyItemInput().anyItemOutput().anyEnergyInput()))
        .build(DISTILLATION_TOWER);
    }

    private static MachineStructureDefinition ecoMatrix() {
        return MachineStructureBuilder.structure().fullStructure(s -> ecoStage(s, 3)).build(ECO_MATRIX);
    }

    private static cn.howxu.mmcr.api.publicapi.machine.StructureStage.Builder ecoStage(cn.howxu.mmcr.api.publicapi.machine.StructureStage.Builder stage, int width) {
        String x = "X".repeat(width), a = "A".repeat(width), middle = "A" + " ".repeat(width - 2) + "A", controller = "AB" + "A".repeat(width - 2);
        return stage.pattern(p -> p.layer(x, a, x).layer(x, middle, x).layer(x, controller, x).where('X', block(Blocks.SEA_LANTERN)).where('A', any(block(Blocks.RESIN_BRICKS), ports())).where('B', controller(ECO_MATRIX)).controller('B')).portTiers(t -> t.anyEnergyInput());
    }

    private static MachineStructureDefinition spaceElevator() {
        return MachineStructureBuilder.structure().fullStructure(s -> s.pattern(p -> p
                .layer("XXX", "XBX", "XCX")
                .where('X', any(block(Blocks.SMOOTH_QUARTZ), itemInputPorts(), energyInputPorts()))
                .where('B', BlockPredicate.machineCoupler()).where('C', controller(SPACE_ELEVATOR))
                .controller('C')).portTiers(t -> t.anyItemInput().anyEnergyInput())).build(SPACE_ELEVATOR);
    }

    private static MachineStructureDefinition spaceReassembler() {
        return MachineStructureBuilder.structure().fullStructure(s -> s.pattern(p -> p.layer("AAA", "XBX", "XBX", "XDX").layer("AAA", "BEB", "B B", "DDD").layer("AAA", "XFX", "XBX", "XDX")
                .where('X', block(Blocks.QUARTZ_PILLAR)).where('A', block(Blocks.AMETHYST_BLOCK)).where('B', any(block(Blocks.GOLD_BLOCK), itemPorts(), energyInputPorts())).where('D', block(Blocks.GLASS)).where('E', BlockPredicate.machineCoupler()).where('F', controller(SPACE_REASSEMBLER)).controller('F')).portTiers(t -> t.anyItemInput().anyItemOutput().anyEnergyInput())).build(SPACE_REASSEMBLER);
    }

    private static MachineStructureDefinition monsterFarm() {
        return MachineStructureBuilder.structure().fullStructure(s -> s.pattern(p -> p.layer("XXXXX", "XXXXX", "XXXXX", "XXXXX", "XXXXX").layer("ABBBA", "B   B", "B   B", "B   B", "ABBBA").layer("ABBBA", "B   B", "B   B", "B   B", "ABBBA").layer("ABBBA", "B   B", "B   B", "B   B", "ABBBA").layer("ABBBA", "B   B", "B   B", "B   B", "ABBBA").layer("XXXXX", "XXXXX", "XXDXX", "XXXXX", "XXXXX")
                .where('X', any(block(Blocks.OAK_PLANKS), port("smart_interface"), energyInputPorts())).where('A', block(Blocks.OAK_LOG)).where('B', block(Blocks.IRON_BARS)).where('D', controller(MONSTER_FARM)).controller('D')).portTiers(t -> t.minEnergyInput(PortTiers.EnergyTier.NORMAL))).build(MONSTER_FARM);
    }

    private static MachineStructureDefinition simpleTall(Identifier id, Block casing, Block frame) {
        return simpleStructure(id, casing, frame == casing ? block(casing) : any(block(frame), block(casing)), 3);
    }

    private static MachineStructureDefinition simpleStructure(Identifier id, Block casing, BlockPredicate slot, int height) {
        return MachineStructureBuilder.structure().fullStructure(s -> s.pattern(p -> {
            for (int i = 0; i < height; i++) p.layer(i == height - 1 ? "XXX" : "XXX", i == height - 1 ? "XCX" : "XXX", "XXX");
            return p.where('X', slot).where('C', controller(id)).controller('C');
        })).build(id);
    }

    private static BlockPredicate controller(Identifier id) { return BlockPredicate.deferredBlock(PublicBuiltinRegistration.controller(id)); }
    private static BlockPredicate controller(Identifier id, boolean vertical, boolean symmetric, boolean required) { return controller(id); }
    private static UnaryOperator<cn.howxu.mmcr.api.publicapi.machine.ControllerSpec.Builder> controllerSpec(Identifier id, boolean vertical, boolean symmetric, boolean required) {
        return builder -> builder.id(Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_controller"))
                .allowVerticalFacing(vertical).fullyRotationallySymmetric(symmetric).requireVerticalFacing(required);
    }
    private static BlockPredicate block(Block block) { return BlockPredicate.block(block); }
    private static BlockPredicate port(String id) { return BlockPredicate.deferredBlock(PublicBuiltinRegistration.block(id)); }
    private static BlockPredicate ports() { return any(itemPorts(), fluidPorts(), energyPorts()); }
    private static BlockPredicate itemPorts() { return any(itemInputPorts(), itemOutputPorts()); }
    private static BlockPredicate itemInputPorts() { return any(port("item_input_bus"), port("item_input_bus_tiny"), port("item_input_bus_small"), port("item_input_bus_reinforced"), port("item_input_bus_big"), port("item_input_bus_huge"), port("item_input_bus_ludicrous")); }
    private static BlockPredicate itemOutputPorts() { return any(port("item_output_bus"), port("item_output_bus_tiny"), port("item_output_bus_small"), port("item_output_bus_reinforced"), port("item_output_bus_big"), port("item_output_bus_huge"), port("item_output_bus_ludicrous")); }
    private static BlockPredicate fluidPorts() { return any(port("fluid_input_hatch"), port("fluid_input_hatch_tiny"), port("fluid_input_hatch_small"), port("fluid_input_hatch_reinforced"), port("fluid_input_hatch_big"), port("fluid_input_hatch_huge"), port("fluid_input_hatch_ludicrous"), port("fluid_input_hatch_vacuum"), port("fluid_output_hatch"), port("fluid_output_hatch_tiny"), port("fluid_output_hatch_small"), port("fluid_output_hatch_reinforced"), port("fluid_output_hatch_big"), port("fluid_output_hatch_huge"), port("fluid_output_hatch_ludicrous"), port("fluid_output_hatch_vacuum")); }
    private static BlockPredicate energyPorts() { return any(energyInputPorts(), energyOutputPorts()); }
    private static BlockPredicate energyInputPorts() { return any(port("energy_input_hatch"), port("energy_input_hatch_tiny"), port("energy_input_hatch_small"), port("energy_input_hatch_reinforced"), port("energy_input_hatch_big"), port("energy_input_hatch_huge"), port("energy_input_hatch_ludicrous"), port("energy_input_hatch_ultimate")); }
    private static BlockPredicate energyOutputPorts() { return any(port("energy_output_hatch"), port("energy_output_hatch_tiny"), port("energy_output_hatch_small"), port("energy_output_hatch_reinforced"), port("energy_output_hatch_big"), port("energy_output_hatch_huge"), port("energy_output_hatch_ludicrous"), port("energy_output_hatch_ultimate")); }
     private static BlockPredicate parallelControllers() {
         List<BlockPredicate> predicates = new ArrayList<>();
         for (ParallelTier tier : ParallelTier.values()) predicates.add(port(tier.idSuffix()));
         return any(predicates.toArray(BlockPredicate[]::new));
     }
    private static BlockPredicate itemParallel() { return parallelControllers(); }
    private static BlockPredicate any(BlockPredicate... predicates) { return BlockPredicate.any(predicates); }
    private static Identifier mc(String path) { return Identifier.withDefaultNamespace(path); }
    static Identifier id(String path) { return PublicBuiltinRegistration.id(path); }
    private static Item modItem(String path) { return BuiltInRegistries.ITEM.getValue(id(path)); }
    private static ItemStack namedItem(Item item, int count, String name) { ItemStack stack = new ItemStack(item, count); stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(name)); return stack; }
    private static DataComponentPredicateSet named(String name) { return new DataComponentPredicateSet(Map.of(
            Identifier.parse("minecraft:custom_name"),
            ComponentPredicate.text(name, ComponentPredicate.TextMode.PLAIN))); }
    private static DataComponentPredicateSet enchantment() {
        com.google.gson.JsonObject enchantments = new com.google.gson.JsonObject();
        enchantments.addProperty("minecraft:sharpness", 4);
        return new DataComponentPredicateSet(Map.of(Identifier.parse("minecraft:enchantments"),
                ComponentPredicate.exact(enchantments)));
    }
    private static void bindVanillaFluids() {
        bindFluid(Fluids.WATER);
        bindFluid(Fluids.LAVA);
    }
    private static void bindFluid(Fluid fluid) {
        try {
            fluid.builtInRegistryHolder().components();
        } catch (NullPointerException ignored) {
            fluid.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        }
    }
    private static MachineRecipeDefinition bulkRecipe(String path, Identifier machine, int ticks, boolean twentyFive) { return recipe(path, machine, ticks).inputItem(Items.IRON_INGOT, 1).outputItem(Items.IRON_NUGGET, 1).build(); }
}
