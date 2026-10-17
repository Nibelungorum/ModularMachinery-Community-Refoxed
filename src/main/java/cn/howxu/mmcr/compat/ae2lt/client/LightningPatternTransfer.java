package cn.howxu.mmcr.compat.ae2lt.client;

import appeng.api.stacks.GenericStack;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.compat.jei.MachineRecipeDisplay;
import com.moakiee.ae2lt.me.key.LightningKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Restores recipe amounts that the native key-only JEI ingredient cannot carry.
 * @author howxu <dev@howxu.cn>
 */
public final class LightningPatternTransfer {
    private LightningPatternTransfer() {}
    public static Transfer prepare(MachineRecipeDisplay display, List<List<GenericStack>> nativeInputs, List<GenericStack> nativeOutputs) {
        var requirements = LightningJeiDisplay.requirements(display);
        if (requirements.isEmpty()) return new Transfer(nativeInputs, nativeOutputs);
        Map<LightningKey, Long> lightningInputs = new LinkedHashMap<>();
        Map<LightningKey, Long> lightningOutputs = new LinkedHashMap<>();
        for (var requirement : requirements) {
            LightningKey key = switch (requirement.tier()) {
                case HIGH_VOLTAGE -> LightningKey.HIGH_VOLTAGE;
                case EXTREME_HIGH_VOLTAGE -> LightningKey.EXTREME_HIGH_VOLTAGE;
            };
            var amounts = requirement.io() == IOType.INPUT ? lightningInputs : lightningOutputs;
            amounts.merge(key, requirement.amount(), Math::addExact);
        }
        List<List<GenericStack>> inputs = new ArrayList<>();
        for (var alternatives : nativeInputs) {
            var ordinary = alternatives.stream().filter(stack -> !(stack.what() instanceof LightningKey)).toList();
            if (!ordinary.isEmpty()) inputs.add(ordinary);
        }
        lightningInputs.forEach((key, amount) -> inputs.add(List.of(new GenericStack(key, amount))));
        List<GenericStack> outputs = new ArrayList<>(nativeOutputs.stream().filter(stack -> !(stack.what() instanceof LightningKey)).toList());
        lightningOutputs.forEach((key, amount) -> outputs.add(new GenericStack(key, amount)));
        return new Transfer(List.copyOf(inputs), List.copyOf(outputs));
    }
    /** @author howxu <dev@howxu.cn> */
    public record Transfer(List<List<GenericStack>> inputs, List<GenericStack> outputs) {
        public boolean fits(int inputSlots, int outputSlots) { return inputs.size() <= inputSlots && outputs.size() <= outputSlots; }
    }
}
