package cn.howxu.mmcr.compat.mekanism;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import java.util.List;

/** Modifier units and bounds shared by Mekanism's input declarations and their outputs.
 * @author howxu <dev@howxu.cn>
 */
public final class MekanismModifiers {
    private MekanismModifiers() {}

    public static float applyChemicalInputChance(List<RecipeModifier> modifiers, float chance) {
        if (modifiers == null || modifiers.isEmpty()) return MachineOutput.clampChance(chance);
        return MachineOutput.clampChance(RecipeModifier.applyModifiers(modifiers, "chemical", RecipeModifier.IOType.INPUT, chance, true));
    }

    public static float applyChemicalOutputChance(List<RecipeModifier> modifiers, float chance) {
        if (modifiers == null || modifiers.isEmpty()) return MachineOutput.clampChance(chance);
        return MachineOutput.clampChance(RecipeModifier.applyModifiers(modifiers, "chemical",
                RecipeModifier.IOType.OUTPUT, chance, true));
    }

    public static long applyChemical(List<RecipeModifier> modifiers, long amount, RecipeModifier.IOType io) {
        if (modifiers == null || modifiers.isEmpty()) return amount;
        double adjusted = RecipeModifier.applyModifiers(modifiers, "chemical", io, (double) amount, false);
        if (Double.isNaN(adjusted) || adjusted <= 1D) return 1L;
        if (adjusted >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return Math.max(1L, (long) Math.floor(adjusted));
    }

    public static double applyHeat(List<RecipeModifier> modifiers, double heat, RecipeModifier.IOType io) {
        if (modifiers == null || modifiers.isEmpty()) return heat;
        double adjusted = RecipeModifier.applyModifiers(modifiers, "heat", io, heat, false);
        if (Double.isNaN(adjusted) || adjusted <= 0D) return 0D;
        return Double.isInfinite(adjusted) ? Double.MAX_VALUE : adjusted;
    }
}
