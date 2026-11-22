package cn.howxu.mmcr.api.machine.modifier;

import cn.howxu.mmcr.api.recipe.modifier.ModifierMath;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.Operation;
import java.util.List;

/** Per-registration unit folding. Absence of a declaration is distinct from disabling all stacking.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
public enum ModifierStacking {
    LEGACY, UNIQUE, ADD, MULTIPLY;

    public boolean countsBlocks() { return this == ADD || this == MULTIPLY; }

    public List<MachineModifier> blocks(List<MachineModifier> effects, long count) {
        if (count <= 0) return List.of();
        return !countsBlocks() || count == 1 ? effects : effects.stream().map(m -> fold(m, count, false)).toList();
    }

    public List<MachineModifier> items(List<MachineModifier> effects, long count) {
        if (count <= 0) return List.of();
        return this == UNIQUE || count == 1 ? effects : effects.stream().map(m -> fold(m, count, true)).toList();
    }

    private MachineModifier fold(MachineModifier modifier, long count, boolean item) {
        if (!(modifier instanceof MachineModifier.Numeric n)) return modifier;
        Operation op = n.operation();
        double v = n.value();
        double operand;
        if (op == Operation.ADD || op == Operation.SUBTRACT) {
            operand = ModifierMath.multiply(v, count);
        } else if (op == Operation.ADD_RATIO) {
            operand = !item && this == MULTIPLY ? ModifierMath.power(1D + v, count) - 1D : ModifierMath.multiply(v, count);
        } else if (item || this == MULTIPLY) {
            operand = ModifierMath.power(v, count);
        } else {
            if (op == Operation.DIVIDE && v == 0D) return n;
            double factor = op == Operation.MULTIPLY ? v : 1D / v;
            operand = ModifierMath.add(1D, ModifierMath.multiply(factor - 1D, count));
            op = Operation.MULTIPLY;
        }
        return new MachineModifier.Numeric(n.target(), operand, op, n.affectsChance());
    }
}
