package cn.howxu.mmcr.api.machine.modifier;

import cn.howxu.mmcr.api.recipe.modifier.ModifierMath;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Validated declaration that changes machine scheduling or recipe execution properties.
 * @author howxu <dev@howxu.cn>
 */
public sealed interface MachineModifier permits MachineModifier.Numeric, MachineModifier.Parallelized, MachineModifier.Candidates {
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
        if (modifiers == null || modifiers.isEmpty()) return value;
        return terms(modifiers, target, affectsChance, value).apply(value);
    }

    private static ModifierMath terms(Collection<? extends MachineModifier> modifiers, ModifierTarget target,
                                      boolean chance, double base) {
        double add = 0D, ratio = 0D, multiply = 1D;
        for (MachineModifier modifier : modifiers) {
            if (modifier instanceof Candidates group) {
                if (group.target() != target || group.chance() != chance) continue;
                ModifierMath block = terms(group.block(), target, chance, base);
                ModifierMath item = terms(group.item(), target, chance, base);
                double a = block.apply(base), b = item.apply(base);
                ModifierMath best = (group.prefersLower() ? a <= b : a >= b) ? block : item;
                add = ModifierMath.add(add, best.flat());
                ratio = ModifierMath.add(ratio, best.ratio());
                multiply = ModifierMath.multiply(multiply, best.factor());
                continue;
            }
            if (!(modifier instanceof Numeric numeric) || numeric.target() != target || numeric.affectsChance() != chance) continue;
            switch (numeric.operation()) {
                case ADD -> add = ModifierMath.add(add, numeric.value());
                case SUBTRACT -> add = ModifierMath.add(add, -numeric.value());
                case ADD_RATIO -> ratio = ModifierMath.add(ratio, numeric.value());
                case MULTIPLY -> multiply = ModifierMath.multiply(multiply, numeric.value());
                case DIVIDE -> {
                    if (numeric.value() != 0D) multiply = ModifierMath.multiply(multiply, 1D / numeric.value());
                }
            }
        }
        return new ModifierMath(add, ratio, multiply);
    }

    /** Merge two single-unit sources without arbitrarily choosing a whole bundle. */
    static List<MachineModifier> bestOf(List<MachineModifier> block, List<MachineModifier> item) {
        if (block.equals(item) || item.isEmpty()) return block;
        if (block.isEmpty()) return item;
        List<MachineModifier> result = new ArrayList<>();
        for (ModifierTarget target : ModifierTarget.values()) {
            if (!target.numeric()) continue;
            addBest(result, block, item, target, false);
            if (target.supportsChance()) addBest(result, block, item, target, true);
        }
        if (block.stream().anyMatch(Parallelized.class::isInstance) || item.stream().anyMatch(Parallelized.class::isInstance)) {
            result.add(parallelized(!block.contains(parallelized(false)) && !item.contains(parallelized(false))));
        }
        return List.copyOf(result);
    }

    private static void addBest(List<MachineModifier> result, List<MachineModifier> block, List<MachineModifier> item,
                                ModifierTarget target, boolean chance) {
        List<Numeric> left = numericGroup(block, target, chance);
        List<Numeric> right = numericGroup(item, target, chance);
        if (left.isEmpty()) { result.addAll(right); return; }
        if (right.isEmpty() || left.equals(right)) { result.addAll(left); return; }
        result.add(new Candidates(target, chance, left, right));
    }

    private static List<Numeric> numericGroup(List<MachineModifier> source, ModifierTarget target, boolean chance) {
        return source.stream().filter(Numeric.class::isInstance).map(Numeric.class::cast)
                .filter(n -> n.target() == target && n.affectsChance() == chance).toList();
    }

    /** Two immutable, single-unit alternatives for one property; selected at its real base.
     * @author HowXu {@literal <dev@howxu.cn>}
     */
    record Candidates(ModifierTarget target, boolean chance, List<Numeric> block, List<Numeric> item) implements MachineModifier {
        public Candidates {
            block = List.copyOf(block);
            item = List.copyOf(item);
        }

        private boolean prefersLower() {
            return switch (target) {
                case DURATION, ENERGY, CHEMICAL, HEAT -> true;
                default -> false;
            };
        }
    }

    static List<RecipeModifier> recipeModifiers(Collection<? extends MachineModifier> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) return List.of();
        List<RecipeModifier> result = new ArrayList<>();
        for (MachineModifier modifier : modifiers) {
            if (modifier instanceof Candidates group) {
                result.addAll(RecipeModifier.bestOf(recipeModifiers(group.block()), recipeModifiers(group.item()),
                        group.prefersLower()));
                continue;
            }
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
            double operand = numeric.operation() == RecipeModifier.Operation.ADD_RATIO
                    ? numeric.value() : finiteFloat(numeric.value());
            result.add(new RecipeModifier(target, io, operand, numeric.operation(), numeric.affectsChance()));
        }
        return List.copyOf(result);
    }

    private static float finiteFloat(double value) {
        if (value >= Float.MAX_VALUE) return Float.MAX_VALUE;
        if (value <= -Float.MAX_VALUE) return -Float.MAX_VALUE;
        return (float) value;
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
