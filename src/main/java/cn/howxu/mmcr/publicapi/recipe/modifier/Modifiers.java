package cn.howxu.mmcr.publicapi.recipe.modifier;

import cn.howxu.mmcr.internal.api.facade.recipe.ModifierAdapters;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import java.util.Collection;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/** Factories delegating validation and arithmetic to the shared implementation.
 * @author howxu <dev@howxu.cn> */
public final class Modifiers {
    private Modifiers() {}
    public static NumericModifierSpec numeric(String target, ModifierScope scope, double value, ModifierOperation operation, boolean affectsChance) { return ModifierAdapters.numeric(target, scope, value, operation, affectsChance); }
    public static ParallelizationModifierSpec parallelized(boolean value) { return ModifierAdapters.parallelized(value); }
    public static ModifierBundle bundle(List<ModifierSpec> values) { return ModifierAdapters.bundle(values); }
    public static ModifierBundle bundle(ModifierSpec... values) { return bundle(List.of(values)); }
    public static ModifierBundle combine(ModifierBundle... values) { return ModifierAdapters.combine(values); }
    public static RecipeAdjustmentSpec recipe(String target, IoDirection io, float value, ModifierOperation operation, boolean affectsChance) { return ModifierAdapters.recipe(target, io, value, operation, affectsChance); }
    public static float apply(Collection<RecipeAdjustmentSpec> modifiers, String target, IoDirection io, float value, boolean chance) { return ModifierAdapters.apply(modifiers, target, io, value, chance); }
    public static double apply(Collection<RecipeAdjustmentSpec> modifiers, String target, IoDirection io, double value, boolean chance) { return ModifierAdapters.apply(modifiers, target, io, value, chance); }
    public static List<RecipeAdjustmentSpec> recipeModifiers(Collection<ModifierSpec> modifiers) { return ModifierAdapters.recipeModifiers(modifiers); }
    /**
     * Maps the actual interface value to an operation operand, not the final recipe value.
     * The mapping must be pure and return a finite number; it is not sampled at registration
     * or guaranteed to run every tick. No interpolation or clamping is applied.
     */
    public static SmartModifierSpec smart(String type, String target, ModifierScope scope, boolean chance, DoubleUnaryOperator mapping, ModifierOperation operation) { return ModifierAdapters.smart(type, target, scope, chance, mapping, operation); }
    public static SmartModifierSpec smart(String type, String target, IoDirection io, boolean chance, DoubleUnaryOperator mapping, ModifierOperation operation) { return ModifierAdapters.smart(type, target, io, chance, mapping, operation); }
    public static SmartModifierSpec smartDuration(String type, DoubleUnaryOperator mapping, ModifierOperation op) { return smart(type, "duration", ModifierScope.INPUT, false, mapping, op); }
    public static SmartModifierSpec smartEnergy(String type, DoubleUnaryOperator mapping, ModifierOperation op) { return smart(type, "energy", ModifierScope.INPUT, false, mapping, op); }
    public static SmartModifierSpec smartItem(String type, IoDirection io, boolean chance, DoubleUnaryOperator mapping, ModifierOperation op) { return smart(type, "item", io, chance, mapping, op); }
    public static SmartModifierSpec smartFluid(String type, IoDirection io, boolean chance, DoubleUnaryOperator mapping, ModifierOperation op) { return smart(type, "fluid", io, chance, mapping, op); }
    public static SmartModifierSpec smartChemical(String type, IoDirection io, boolean chance, DoubleUnaryOperator mapping, ModifierOperation op) { return smart(type, "chemical", io, chance, mapping, op); }
    public static SmartModifierSpec smartHeat(String type, IoDirection io, DoubleUnaryOperator mapping, ModifierOperation op) { return smart(type, "heat", io, false, mapping, op); }
}
