package cn.howxu.mmcr.api.recipe.requirement;

import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

/** Value-only description of a scalar requirement. Rate-limited IO and stored quantities have distinct budgets.
 * @author howxu <dev@howxu.cn>
 */
public record ScalarRecipeRule(ResourceLocation capability, long amount, float outputChance, float consumeChance,
                               boolean rateLimited, ResourceLocation failure, boolean supportsCommit) {
    public ScalarRecipeRule {
        Objects.requireNonNull(capability);
        Objects.requireNonNull(failure);
        if (amount < 0 || !Float.isFinite(outputChance) || !Float.isFinite(consumeChance)
                || outputChance < 0F || outputChance > 1F || consumeChance < 0F || consumeChance > 1F)
            throw new IllegalArgumentException("Invalid scalar planning rule");
    }
}
