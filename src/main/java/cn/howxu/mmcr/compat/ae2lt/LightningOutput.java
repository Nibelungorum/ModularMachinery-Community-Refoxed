package cn.howxu.mmcr.compat.ae2lt;

import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputType;
import java.util.Objects;

/** Serializable lightning output with its canonical tier-specific output type.
 * @author howxu <dev@howxu.cn>
 */
public record LightningOutput(LightningTier tier, long amount, float chance) implements MachineOutput {
    public LightningOutput {
        Objects.requireNonNull(tier, "tier");
        amount = LightningRecipeTypes.recipeAmount(amount);
        chance = MachineOutput.clampChance(chance);
    }

    @Override public OutputType<LightningOutput> outputType() { return LightningRecipeTypes.outputType(tier); }
}
