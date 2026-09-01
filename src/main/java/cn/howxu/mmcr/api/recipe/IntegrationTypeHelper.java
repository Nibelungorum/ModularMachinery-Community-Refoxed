package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.util.List;

public final class IntegrationTypeHelper {

    public static final String TARGET_DURATION = "duration";
    public static final String TARGET_ITEM = "item";
    public static final String TARGET_FLUID = "fluid";
    public static final String TARGET_ENERGY = "energy";

    private IntegrationTypeHelper() {
    }

    public static float applyDuration(List<RecipeModifier> modifiers, int baseTickTime) {
        if (modifiers == null || modifiers.isEmpty()) return baseTickTime;
        return RecipeModifier.applyModifiers(modifiers, TARGET_DURATION, RecipeModifier.IOType.INPUT, baseTickTime, false);
    }

    public static float applyItemInput(List<RecipeModifier> modifiers, int count) {
        if (modifiers == null || modifiers.isEmpty()) return count;
        return RecipeModifier.applyModifiers(modifiers, TARGET_ITEM, RecipeModifier.IOType.INPUT, count, false);
    }

    public static float applyItemOutput(List<RecipeModifier> modifiers, int count) {
        if (modifiers == null || modifiers.isEmpty()) return count;
        return RecipeModifier.applyModifiers(modifiers, TARGET_ITEM, RecipeModifier.IOType.OUTPUT, count, false);
    }

    public static float applyFluidInput(List<RecipeModifier> modifiers, int amount) {
        if (modifiers == null || modifiers.isEmpty()) return amount;
        return RecipeModifier.applyModifiers(modifiers, TARGET_FLUID, RecipeModifier.IOType.INPUT, amount, false);
    }

    public static float applyFluidOutput(List<RecipeModifier> modifiers, int amount) {
        if (modifiers == null || modifiers.isEmpty()) return amount;
        return RecipeModifier.applyModifiers(modifiers, TARGET_FLUID, RecipeModifier.IOType.OUTPUT, amount, false);
    }

    public static float applyItemOutputChance(List<RecipeModifier> modifiers, float chance) {
        if (modifiers == null || modifiers.isEmpty()) return MachineOutput.clampChance(chance);
        return MachineOutput.clampChance(RecipeModifier.applyModifiers(modifiers, TARGET_ITEM, RecipeModifier.IOType.OUTPUT, chance, true));
    }

    public static float applyItemInputChance(List<RecipeModifier> modifiers, float chance) {
        if (modifiers == null || modifiers.isEmpty()) return MachineOutput.clampChance(chance);
        return MachineOutput.clampChance(RecipeModifier.applyModifiers(modifiers, TARGET_ITEM, RecipeModifier.IOType.INPUT, chance, true));
    }

    public static float applyFluidOutputChance(List<RecipeModifier> modifiers, float chance) {
        if (modifiers == null || modifiers.isEmpty()) return MachineOutput.clampChance(chance);
        return MachineOutput.clampChance(RecipeModifier.applyModifiers(modifiers, TARGET_FLUID, RecipeModifier.IOType.OUTPUT, chance, true));
    }

    public static float applyFluidInputChance(List<RecipeModifier> modifiers, float chance) {
        if (modifiers == null || modifiers.isEmpty()) return MachineOutput.clampChance(chance);
        return MachineOutput.clampChance(RecipeModifier.applyModifiers(modifiers, TARGET_FLUID, RecipeModifier.IOType.INPUT, chance, true));
    }

    public static float applyEnergy(List<RecipeModifier> modifiers, int fePerTick) {
        if (modifiers == null || modifiers.isEmpty()) return fePerTick;
        return RecipeModifier.applyModifiers(modifiers, TARGET_ENERGY, RecipeModifier.IOType.INPUT, fePerTick, false);
    }

    public static int asInt(float value) {
        if (value < 0F) return 0;
        return Math.round(value);
    }

    public static Ingredient firstIngredient(MachineIngredient ingredient) {
        if (ingredient instanceof MachineIngredient.ItemIngredient item) {
            return item.item();
        }
        throw new IllegalArgumentException("MachineIngredient is not ItemIngredient: " + ingredient);
    }

    public static FluidIngredient firstFluid(MachineIngredient ingredient) {
        if (ingredient instanceof MachineIngredient.FluidIngredient fluid) {
            return fluid.fluid();
        }
        throw new IllegalArgumentException("MachineIngredient is not FluidIngredient: " + ingredient);
    }

    public static ItemStack firstItemOutput(MachineRecipe recipe) {
        if (recipe == null) return ItemStack.EMPTY;
        return OutputRegistry.itemStacks(recipe.machineOutputs()).stream().findFirst().orElse(ItemStack.EMPTY);
    }
}
