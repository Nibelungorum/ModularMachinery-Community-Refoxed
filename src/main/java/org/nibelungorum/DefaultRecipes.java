package org.nibelungorum;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    private DefaultRecipes() {
    }

    public static void ensureRegistered() {
        registerStatic(recipes().values().stream().toList());
    }

    public static Map<Identifier, MachineRecipe> recipes() {
        Map<Identifier, MachineRecipe> recipes = new java.util.LinkedHashMap<>();
        for (Definition definition : definitions()) {
            MachineRecipe recipe = createRecipe(definition);
            recipes.put(recipe.id(), recipe);
        }
        return Map.copyOf(recipes);
    }

    public static void registerStatic(List<MachineRecipe> recipes) {
        for (MachineRecipe recipe : recipes) {
            if (RecipeRegistry.getRecipe(recipe.id()) == null) {
                register(recipe);
            } else {
                LOG.info("ensureRegistered: built-in recipe {} already registered; skipping", recipe.id());
            }
        }
    }

    private static MachineRecipe createRecipe(Definition definition) {
        return new MachineRecipe(definition.id(), definition.machineId(), definition.ticks(), definition.inputs(),
                definition.outputs(), List.of(), 0, 1, true, definition.fluidOutputs(), List.of(), true);
    }

    private static List<Definition> definitions() {
        return List.of(
                standardDefinitions(BLAST_FURNACE_ID, "blast_furnace", new Definition(MMCR.id("blast_furnace_iron_to_nugget"), BLAST_FURNACE_ID, 200, List.of(itemInput(Items.IRON_INGOT, 1), energyInput(1)), List.of(item(Items.IRON_NUGGET, 1)), List.of())),
                alloyFurnaceDefinitions(),
                standardDefinitions(CRACKER_ID, "cracker", new Definition(MMCR.id("cracker_coal_lapis"), CRACKER_ID, 160, List.of(itemInput(Items.COAL, 8), itemInput(Items.LAPIS_LAZULI, 1), energyInput(100)), List.of(item(Items.REDSTONE, 4)), List.of(fluidOutput(Fluids.WATER, 500)))),
                standardDefinitions(REACTOR_ID, "reactor", new Definition(MMCR.id("reactor_diamond_water"), REACTOR_ID, 200, List.of(itemInput(Items.DIAMOND, 1), fluidInput(Fluids.WATER, 500), energyOutput(100)), List.of(item(Items.COAL, 1)), List.of(fluidOutput(Fluids.LAVA, 500))))
        ).stream().flatMap(List::stream).toList();
    }

    private static List<Definition> alloyFurnaceDefinitions() {
        List<Definition> definitions = new java.util.ArrayList<>(standardDefinitions(ALLOY_FURNACE_ID, "alloy_furnace",
                new Definition(ALLOY_FURNACE_NETHERITE_ID, ALLOY_FURNACE_ID, 100, List.of(itemInput(Items.ANCIENT_DEBRIS, 1), itemInput(Items.GOLD_INGOT, 1), energyInput(5)), List.of(item(Items.NETHERITE_INGOT, 1)), List.of())));
        definitions.add(new Definition(MMCR.id("alloy_furnace_jei_large"), ALLOY_FURNACE_ID, 400,
                largeItemInputs(), largeItemOutputs(), List.of()));
        definitions.add(new Definition(MMCR.id("alloy_furnace_jei_25x25"), ALLOY_FURNACE_ID, 500,
                twentyFiveItemInputs(), twentyFiveItemOutputs(), List.of()));
        return List.copyOf(definitions);
    }

    private static List<MachineIngredient> largeItemInputs() {
        return java.util.stream.Stream.of(
                Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.REDSTONE, Items.LAPIS_LAZULI,
                Items.COAL, Items.DIAMOND, Items.EMERALD, Items.QUARTZ, Items.AMETHYST_SHARD,
                Items.NETHERITE_SCRAP, Items.IRON_NUGGET, Items.GOLD_NUGGET, Items.COPPER_BLOCK, Items.IRON_BLOCK,
                Items.GOLD_BLOCK, Items.REDSTONE_BLOCK, Items.LAPIS_BLOCK, Items.DIAMOND_BLOCK, Items.EMERALD_BLOCK,
                Items.QUARTZ_BLOCK
        ).map(item -> itemInput(item, 1)).toList();
    }

    private static List<ItemStack> largeItemOutputs() {
        return java.util.stream.Stream.of(
                Items.IRON_NUGGET, Items.GOLD_NUGGET, Items.COPPER_NUGGET, Items.REDSTONE, Items.LAPIS_LAZULI,
                Items.COAL, Items.DIAMOND, Items.EMERALD, Items.QUARTZ, Items.AMETHYST_SHARD,
                Items.NETHERITE_SCRAP, Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.IRON_BLOCK,
                Items.GOLD_BLOCK, Items.COPPER_BLOCK, Items.REDSTONE_BLOCK, Items.LAPIS_BLOCK, Items.DIAMOND_BLOCK,
                Items.EMERALD_BLOCK
        ).map(item -> item(item, 1)).toList();
    }

    private static List<MachineIngredient> twentyFiveItemInputs() {
        return java.util.stream.Stream.of(
                Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.REDSTONE, Items.LAPIS_LAZULI,
                Items.COAL, Items.DIAMOND, Items.EMERALD, Items.QUARTZ, Items.AMETHYST_SHARD,
                Items.NETHERITE_SCRAP, Items.IRON_NUGGET, Items.GOLD_NUGGET, Items.COPPER_BLOCK, Items.IRON_BLOCK,
                Items.GOLD_BLOCK, Items.REDSTONE_BLOCK, Items.LAPIS_BLOCK, Items.DIAMOND_BLOCK, Items.EMERALD_BLOCK,
                Items.QUARTZ_BLOCK, Items.COAL_BLOCK, Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_COPPER
        ).map(item -> itemInput(item, 1)).toList();
    }

    private static List<ItemStack> twentyFiveItemOutputs() {
        return java.util.stream.Stream.of(
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

    private static ItemStack item(net.minecraft.world.item.Item item, int count) {
        return new ItemStack(Holder.direct(item, DataComponentMap.EMPTY), count);
    }

    private static MachineIngredient itemInput(net.minecraft.world.item.Item item, int count) {
        return new MachineIngredient.ItemIngredient(Ingredient.of(item), count);
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
                              List<ItemStack> outputs, List<FluidStack> fluidOutputs) {
    }

    private static Holder<Fluid> boundFluid(Fluid fluid) {
        var holder = fluid.builtInRegistryHolder();
        holder.bindComponents(DataComponentMap.EMPTY);
        return holder;
    }

    private static void register(MachineRecipe recipe) {
        RecipeRegistry.register(recipe);
        long totalEnergyIn = recipe.inputs().stream()
                .filter(i -> i instanceof MachineIngredient.EnergyIngredient)
                .mapToLong(i -> (long) ((MachineIngredient.EnergyIngredient) i).fePerTick() * recipe.tickTime())
                .sum();
        long totalEnergyOut = recipe.energyOutputs().stream()
                .mapToLong(fe -> (long) fe * recipe.tickTime())
                .sum();
        LOG.info("ensureRegistered: registered built-in recipe id={} machine={} tickTime={}t ({}s) priority={} maxThreads={} modifiers={} energyIn={}FE energyOut={}FE",
                recipe.id(), recipe.machineId(), recipe.tickTime(), String.format("%.2f", recipe.tickTime() / 20.0),
                recipe.priority(), recipe.maxThreads(), recipe.modifiers().size(), totalEnergyIn, totalEnergyOut);
        LOG.info("  inputs  = [{}]", describeInputs(recipe));
        LOG.info("  outputs = [{}]", describeOutputs(recipe));
        LOG.info("  entry points = RecipeRegistry.byMachine({}) and /mmcr reload", recipe.machineId());
    }

    private static String describeInputs(MachineRecipe recipe) {
        return recipe.inputs().stream().map(DefaultRecipes::describeIngredient).collect(Collectors.joining(", "));
    }

    private static String describeOutputs(MachineRecipe recipe) {
        return recipe.outputs().stream()
                .map(s -> s.getCount() + "x " + s.getItem().builtInRegistryHolder().getRegisteredName())
                .collect(Collectors.joining(", "));
    }

    private static String describeIngredient(MachineIngredient ingredient) {
        if (ingredient instanceof MachineIngredient.ItemIngredient item) {
            return "item " + item.count() + "x " + firstStackName(item.item());
        }
        if (ingredient instanceof MachineIngredient.FluidIngredient fluid) {
            return "fluid " + fluid.amount() + "mb " + firstFluidName(fluid.fluid());
        }
        if (ingredient instanceof MachineIngredient.EnergyIngredient energy) {
            return "energy " + energy.fePerTick() + "FE/t";
        }
        return ingredient.getClass().getSimpleName();
    }

    private static String firstStackName(Ingredient ingredient) {
        return ingredient.items()
                .findFirst()
                .map(h -> h.value().builtInRegistryHolder().getRegisteredName())
                .orElseGet(ingredient::toString);
    }

    private static String firstFluidName(FluidIngredient fluid) {
        return fluid.fluids().stream()
                .findFirst()
                .map(h -> h.value().builtInRegistryHolder().getRegisteredName())
                .orElseGet(fluid::toString);
    }
}
