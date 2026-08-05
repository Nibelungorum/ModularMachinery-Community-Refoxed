package org.nibelungorum;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
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

    private DefaultRecipes() {
    }

    public static void ensureRegistered() {
        if (RecipeRegistry.getRecipe(BLAST_FURNACE_IRON_NUGGET_ID) != null) {
            LOG.info("ensureRegistered: built-in recipe {} already registered; skipping", BLAST_FURNACE_IRON_NUGGET_ID);
            return;
        }
        MachineRecipe recipe = new MachineRecipe(
                BLAST_FURNACE_IRON_NUGGET_ID,
                BLAST_FURNACE_ID,
                200,
                List.of(
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1),
                        new MachineIngredient.EnergyIngredient(1)
                ),
                List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1))
        );
        RecipeRegistry.register(recipe);
        int totalEnergy = recipe.inputs().stream()
                .filter(i -> i instanceof MachineIngredient.EnergyIngredient)
                .mapToInt(i -> ((MachineIngredient.EnergyIngredient) i).fePerTick() * recipe.tickTime())
                .sum();
        LOG.info("ensureRegistered: registered built-in recipe id={} machine={} tickTime={}t ({}s) priority={} maxThreads={} modifiers={} totalEnergy={}FE",
                recipe.id(), recipe.machineId(), recipe.tickTime(), String.format("%.2f", recipe.tickTime() / 20.0),
                recipe.priority(), recipe.maxThreads(), recipe.modifiers().size(), totalEnergy);
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
