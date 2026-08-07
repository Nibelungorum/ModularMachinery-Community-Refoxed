package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable JEI-facing view of a machine recipe.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineRecipeDisplay(
        MachineRecipe recipe,
        Identifier recipeId,
        Identifier machineId,
        int durationTicks,
        List<Ingredient> itemInputs,
        List<Integer> itemInputCounts,
        List<ItemStack> itemOutputs,
        List<FluidIngredient> fluidInputs,
        List<Integer> fluidInputAmounts,
        List<FluidStack> fluidOutputs,
        List<EnergyIngredient> energyInputs,
        List<EnergyIngredient> energyOutputs,
        List<MachineOutput> outputs
) {

    public static MachineRecipeDisplay from(MachineRecipe recipe) {
        List<Ingredient> itemInputs = new ArrayList<>();
        List<Integer> itemInputCounts = new ArrayList<>();
        List<FluidIngredient> fluidInputs = new ArrayList<>();
        List<Integer> fluidInputAmounts = new ArrayList<>();
        List<EnergyIngredient> energyInputs = new ArrayList<>();
        List<EnergyIngredient> energyOutputs = new ArrayList<>();

        for (var requirement : recipe.runtimeRequirements()) {
            if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.INPUT) {
                itemInputs.add(item.item());
                itemInputCounts.add(item.count());
            } else if (requirement instanceof FluidRequirement fluid && fluid.io() == RecipeModifier.IOType.INPUT) {
                fluidInputs.add(fluid.fluid());
                fluidInputAmounts.add(fluid.amount());
            } else if (requirement instanceof EnergyRequirement energy) {
                EnergyIngredient ingredient = new EnergyIngredient(energy.fePerTick(), energy.io() == RecipeModifier.IOType.INPUT);
                if (ingredient.input()) energyInputs.add(ingredient);
                else energyOutputs.add(ingredient);
            }
        }

        List<MachineOutput> outputs = recipe.runtimeMachineOutputs();
        List<ItemStack> itemOutputs = new ArrayList<>();
        List<FluidStack> fluidOutputs = new ArrayList<>();
        for (MachineOutput output : outputs) {
            if (output instanceof MachineOutput.ItemOutput item) {
                itemOutputs.add(item.stack().copy());
            } else if (output instanceof MachineOutput.FluidOutput fluid) {
                fluidOutputs.add(fluid.stack().copy());
            }
        }

        return new MachineRecipeDisplay(
                recipe,
                recipe.id(),
                recipe.machineId(),
                recipe.tickTime(),
                List.copyOf(itemInputs),
                List.copyOf(itemInputCounts),
                List.copyOf(itemOutputs),
                List.copyOf(fluidInputs),
                List.copyOf(fluidInputAmounts),
                List.copyOf(fluidOutputs),
                List.copyOf(energyInputs),
                List.copyOf(energyOutputs),
                List.copyOf(outputs)
        );
    }
}
