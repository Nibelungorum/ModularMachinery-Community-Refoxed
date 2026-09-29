package cn.howxu.mmcr.compat.jei;

import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Type-tagged recipe IO value passed to JEI adapters.
 *
 * @author howxu <dev@howxu.cn>
 */
public record RecipeIoEntry(RecipeIngredientRole role, ResourceLocation typeId,
                            Object value, long amount, float chance) {
    public RecipeIoEntry {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(value, "value");
        if (amount < 0L) throw new IllegalArgumentException("amount must be non-negative");
        if (!Float.isFinite(chance) || chance < 0F || chance > 1F) {
            throw new IllegalArgumentException("chance must be in [0, 1]");
        }
    }
}
