package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

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
    public static final String TARGET_CHEMICAL = "chemical";
    public static final String TARGET_HEAT = "heat";

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

    public static float applyChemicalInputChance(List<RecipeModifier> modifiers, float chance) {
        if (modifiers == null || modifiers.isEmpty()) return MachineOutput.clampChance(chance);
        return MachineOutput.clampChance(RecipeModifier.applyModifiers(modifiers, TARGET_CHEMICAL, RecipeModifier.IOType.INPUT, chance, true));
    }

    public static float applyChemicalOutputChance(List<RecipeModifier> modifiers, float chance) {
        if (modifiers == null || modifiers.isEmpty()) return MachineOutput.clampChance(chance);
        return MachineOutput.clampChance(RecipeModifier.applyModifiers(modifiers, TARGET_CHEMICAL,
                RecipeModifier.IOType.OUTPUT, chance, true));
    }

    public static long applyChemical(List<RecipeModifier> modifiers, long amount, RecipeModifier.IOType io) {
        if (modifiers == null || modifiers.isEmpty()) return amount;
        double adjusted = RecipeModifier.applyModifiers(modifiers, TARGET_CHEMICAL, io, (double) amount, false);
        if (Double.isNaN(adjusted) || adjusted <= 1D) return 1L;
        if (adjusted >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return Math.max(1L, (long) Math.floor(adjusted));
    }

    public static double applyHeat(List<RecipeModifier> modifiers, double heat, RecipeModifier.IOType io) {
        if (modifiers == null || modifiers.isEmpty()) return heat;
        double adjusted = RecipeModifier.applyModifiers(modifiers, TARGET_HEAT, io, heat, false);
        if (Double.isNaN(adjusted) || adjusted <= 0D) return 0D;
        return Double.isInfinite(adjusted) ? Double.MAX_VALUE : adjusted;
    }

    public static long applyEnergy(List<RecipeModifier> modifiers, long fePerTick) {
        if (modifiers == null || modifiers.isEmpty()) return fePerTick;
        double adjusted = RecipeModifier.applyModifiers(modifiers, TARGET_ENERGY,
                RecipeModifier.IOType.INPUT, (double) fePerTick, false);
        if (Double.isNaN(adjusted) || adjusted <= 0D) return 0L;
        return adjusted >= Long.MAX_VALUE || Double.isInfinite(adjusted)
                ? Long.MAX_VALUE : (long) Math.floor(adjusted);
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
