package cn.howxu.mmcr.compat.jei;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeIngredientRole;

import java.util.Objects;

/**
 * Immutable adapter result used by JEI layouts and transfers.
 *
 * @author howxu <dev@howxu.cn>
 */
public record JeiDisplayEntry(RecipeIngredientRole role, IIngredientType<?> ingredientType,
                              Object ingredient, int count, boolean transferable) {
    public JeiDisplayEntry {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(ingredient, "ingredient");
        if (count < 0) throw new IllegalArgumentException("count must be non-negative");
    }

    public boolean isTextOnly() {
        return ingredientType == null;
    }
}
