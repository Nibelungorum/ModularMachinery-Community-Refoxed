package cn.howxu.mmcr.api.machine.modifier;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;

import java.util.Locale;
import java.util.Objects;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

/** Validated declaration that changes machine scheduling or recipe execution properties.
 * @author howxu <dev@howxu.cn>
 */
public sealed interface MachineModifier permits MachineModifier.Numeric, MachineModifier.Parallelized {
    static Numeric numeric(String target, String scope, double value, String operation, boolean affectsChance) {
        ModifierTarget modifierTarget = ModifierTarget.parse(target, scope);
        if (!modifierTarget.numeric()) {
            throw new IllegalArgumentException("Machine modifier target is not numeric: " + target);
        }
        return new Numeric(modifierTarget, value, parseOperation(operation), affectsChance);
    }

    static Parallelized parallelized(boolean value) {
        return new Parallelized(value);
    }

    static double apply(Collection<MachineModifier> modifiers, ModifierTarget target, double value,
                        boolean affectsChance) {
        double add = 0D;
        double multiply = 1D;
        if (modifiers == null) return value;
        for (MachineModifier modifier : modifiers) {
            if (!(modifier instanceof Numeric numeric) || numeric.target() != target
                    || numeric.affectsChance() != affectsChance) continue;
            switch (numeric.operation()) {
                case ADD -> add = saturatingAdd(add, numeric.value());
                case SUBTRACT -> add = saturatingAdd(add, -numeric.value());
                case MULTIPLY -> multiply = saturatingMultiply(multiply, numeric.value());
                case DIVIDE -> {
                    if (numeric.value() != 0D) multiply = saturatingMultiply(multiply, 1D / numeric.value());
                }
            }
        }
        return saturatingMultiply(saturatingAdd(value, add), multiply);
    }

    static List<RecipeModifier> recipeModifiers(Collection<MachineModifier> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) return List.of();
        List<RecipeModifier> result = new ArrayList<>();
        for (MachineModifier modifier : modifiers) {
            if (!(modifier instanceof Numeric numeric)) continue;
            RecipeModifier.IOType io;
            String target;
            switch (numeric.target()) {
                case DURATION -> {
                    io = RecipeModifier.IOType.INPUT;
                    target = "duration";
                }
                case ENERGY -> {
                    io = RecipeModifier.IOType.INPUT;
                    target = "energy";
                }
                case CHEMICAL -> {
                    io = RecipeModifier.IOType.INPUT;
                    target = "chemical";
                }
                case HEAT -> {
                    io = RecipeModifier.IOType.INPUT;
                    target = "heat";
                }
                case OUTPUT -> {
                    io = RecipeModifier.IOType.OUTPUT;
                    target = "";
                }
                default -> {
                    continue;
                }
            }
            result.add(new RecipeModifier(target, io, finiteFloat(numeric.value()), numeric.operation(),
                    numeric.affectsChance()));
        }
        return List.copyOf(result);
    }

    private static float finiteFloat(double value) {
        if (value >= Float.MAX_VALUE) return Float.MAX_VALUE;
        if (value <= -Float.MAX_VALUE) return -Float.MAX_VALUE;
        return (float) value;
    }

    private static double saturatingAdd(double first, double second) {
        double result = first + second;
        if (Double.isFinite(result)) return result;
        return result < 0D ? -Double.MAX_VALUE : Double.MAX_VALUE;
    }

    private static double saturatingMultiply(double first, double second) {
        double result = first * second;
        if (Double.isFinite(result)) return result;
        if (Double.isNaN(result)) return 0D;
        return Math.copySign(Double.MAX_VALUE, first * Math.signum(second));
    }

    private static RecipeModifier.Operation parseOperation(String operation) {
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("Machine modifier operation must not be blank");
        }
        try {
            return RecipeModifier.Operation.valueOf(operation.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown machine modifier operation: " + operation, exception);
        }
    }

    record Numeric(ModifierTarget target, double value, RecipeModifier.Operation operation,
                   boolean affectsChance) implements MachineModifier {
        public Numeric {
            target = Objects.requireNonNull(target, "target");
            operation = Objects.requireNonNull(operation, "operation");
            if (!target.numeric()) {
                throw new IllegalArgumentException("Machine modifier target is not numeric: " + target.key());
            }
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Machine modifier value must be finite");
            }
            if (affectsChance && !target.supportsChance()) {
                throw new IllegalArgumentException("Machine modifier target does not support chance: " + target.key());
            }
        }
    }

    record Parallelized(boolean value) implements MachineModifier { }
}
