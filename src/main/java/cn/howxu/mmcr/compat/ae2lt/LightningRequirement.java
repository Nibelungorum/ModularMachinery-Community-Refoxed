package cn.howxu.mmcr.compat.ae2lt;

import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import java.util.List;
import java.util.Objects;

/** One-shot lightning consumption or production, expressed per parallel recipe.
 * @author howxu <dev@howxu.cn>
 */
public record LightningRequirement(LightningTier tier, RecipeModifier.IOType io,
                                   long amount, float chance, List<String> tags) implements MachineRequirement {
    public LightningRequirement {
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(io, "io");
        amount = LightningRecipeTypes.recipeAmount(amount);
        if (io.isInput() && chance != 1F) throw new IllegalArgumentException("Lightning inputs do not support chance");
        chance = MachineOutput.clampChance(chance);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public static LightningRequirement input(LightningTier tier, long amount) {
        return new LightningRequirement(tier, RecipeModifier.IOType.INPUT, amount, 1F, List.of());
    }

    public static LightningRequirement output(LightningTier tier, long amount, float chance) {
        return new LightningRequirement(tier, RecipeModifier.IOType.OUTPUT, amount, chance, List.of());
    }

    @Override public RequirementType<LightningRequirement> type() { return LightningRecipeTypes.requirementType(tier); }
}
