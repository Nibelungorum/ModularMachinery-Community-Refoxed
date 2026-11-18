package cn.howxu.mmcr.compat.ae2lt.client;

import cn.howxu.mmcr.compat.appliedenergistics2.loaded.jei.PatternEncodingContribution;

import appeng.api.stacks.GenericStack;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.compat.jei.MachineRecipeDisplay;
import com.moakiee.ae2lt.me.key.LightningKey;
import net.neoforged.fml.ModList;
import java.util.List;

/** Restores recipe amounts that the native key-only JEI ingredient cannot carry.
 * @author howxu <dev@howxu.cn>
 */
public final class LightningPatternTransfer implements PatternEncodingContribution {
    public static Transfer prepare(MachineRecipeDisplay display, List<List<GenericStack>> nativeInputs, List<GenericStack> nativeOutputs) {
        var transfer = PatternEncodingContribution.prepare(Native.exactSlots(display), nativeInputs, nativeOutputs);
        return new Transfer(transfer.inputs(), transfer.outputs());
    }

    @Override public List<Slot> slots(MachineRecipeDisplay display) {
        // Without AE2LT its optional key class must not be resolved.
        var mods = ModList.get();
        return mods != null && mods.isLoaded("ae2lt") ? Native.exactSlots(display) : List.of();
    }

    private static final class Native {
        private static List<Slot> exactSlots(MachineRecipeDisplay display) {
            return LightningJeiDisplay.requirements(display).stream().map(requirement -> new Slot(requirement.io() == IOType.OUTPUT,
                    new GenericStack(switch (requirement.tier()) {
                        case HIGH_VOLTAGE -> LightningKey.HIGH_VOLTAGE;
                        case EXTREME_HIGH_VOLTAGE -> LightningKey.EXTREME_HIGH_VOLTAGE;
                    }, requirement.amount()))).toList();
        }
    }
    /** @author howxu <dev@howxu.cn> */
    public record Transfer(List<List<GenericStack>> inputs, List<GenericStack> outputs) {
        public boolean fits(int inputSlots, int outputSlots) { return inputs.size() <= inputSlots && outputs.size() <= outputSlots; }
    }
}
