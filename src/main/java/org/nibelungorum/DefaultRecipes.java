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
import java.util.stream.Collectors;

/**
 * 默认内建配方注册入口。当前仅高炉内置:1 铁锭 → 1 铁粒,200 tick 消耗 200 FE。
 */
public final class DefaultRecipes {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRecipes.class);

    private static final Identifier BLAST_FURNACE_ID = MMCR.id("blast_furnace");
    private static final Identifier BLAST_FURNACE_IRON_NUGGET_ID = MMCR.id("blast_furnace_iron_to_nugget");
    private static final Identifier CRACKER_ID = MMCR.id("cracker");
    private static final Identifier CRACKER_COAL_LAPIS_ID = MMCR.id("cracker_coal_lapis");
    private static final Identifier REACTOR_ID = MMCR.id("reactor");
    private static final Identifier REACTOR_DIAMOND_WATER_ID = MMCR.id("reactor_diamond_water");

    private DefaultRecipes() {
    }

    public static void ensureRegistered() {
        if (RecipeRegistry.getRecipe(BLAST_FURNACE_IRON_NUGGET_ID) == null) {
            MachineRecipe recipe = new MachineRecipe(
                    BLAST_FURNACE_IRON_NUGGET_ID,
                    BLAST_FURNACE_ID,
                    200,
                    List.of(
                            new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1),
                            new MachineIngredient.EnergyIngredient(1)
                    ),
                    List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1)),
                    List.of(),
                    0,
                    1,
                    true
            );
            register(recipe);
        } else {
            LOG.info("ensureRegistered: built-in recipe {} already registered; skipping", BLAST_FURNACE_IRON_NUGGET_ID);
        }

        if (RecipeRegistry.getRecipe(CRACKER_COAL_LAPIS_ID) == null) {
            MachineRecipe recipe = new MachineRecipe(
                    CRACKER_COAL_LAPIS_ID,
                    CRACKER_ID,
                    160,
                    List.of(
                            new MachineIngredient.ItemIngredient(Ingredient.of(Items.COAL), 8),
                            new MachineIngredient.ItemIngredient(Ingredient.of(Items.LAPIS_LAZULI), 1),
                            new MachineIngredient.EnergyIngredient(100)
                    ),
                    List.of(new ItemStack(Holder.direct(Items.REDSTONE, DataComponentMap.EMPTY), 4)),
                    List.of(),
                    0,
                    1,
                    true,
                    List.of(new FluidStack(boundFluid(Fluids.WATER), 500))
            );
            register(recipe);
        } else {
            LOG.info("ensureRegistered: built-in recipe {} already registered; skipping", CRACKER_COAL_LAPIS_ID);
        }

        if (RecipeRegistry.getRecipe(REACTOR_DIAMOND_WATER_ID) == null) {
            MachineRecipe recipe = new MachineRecipe(
                    REACTOR_DIAMOND_WATER_ID,
                    REACTOR_ID,
                    200,
                    List.of(
                            new MachineIngredient.ItemIngredient(Ingredient.of(Items.DIAMOND), 1),
                            new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 500),
                            new MachineIngredient.EnergyIngredient(RecipeModifier.IOType.OUTPUT, 100)
                    ),
                    List.of(new ItemStack(Holder.direct(Items.COAL, DataComponentMap.EMPTY), 1)),
                    List.of(),
                    0,
                    1,
                    true,
                    List.of(new FluidStack(boundFluid(Fluids.LAVA), 500))
            );
            register(recipe);
        } else {
            LOG.info("ensureRegistered: built-in recipe {} already registered; skipping", REACTOR_DIAMOND_WATER_ID);
        }
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
