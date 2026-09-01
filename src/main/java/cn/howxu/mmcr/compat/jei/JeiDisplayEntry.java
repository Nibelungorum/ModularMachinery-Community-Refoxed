package cn.howxu.mmcr.compat.jei;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Immutable adapter result used by JEI layouts and transfers.
 *
 * @author howxu <dev@howxu.cn>
 */
public record JeiDisplayEntry(RecipeIngredientRole role, Identifier typeId, IIngredientType<?> ingredientType,
                              Object ingredient, int count, float chance, IIngredientRenderer<?> renderer,
                              boolean transferable) {
    public JeiDisplayEntry {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(ingredient, "ingredient");
        if (count < 0) throw new IllegalArgumentException("count must be non-negative");
        if (!Float.isFinite(chance) || chance < 0F || chance > 1F) {
            throw new IllegalArgumentException("chance must be in [0, 1]");
        }
    }

    public boolean isTextOnly() {
        return ingredientType == null;
    }
}
