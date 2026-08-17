package cn.howxu.mmcr.mixin.compat.jei;

import mezz.jei.api.gui.drawable.IScalableDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Reserved hook for a future MMCR-specific JEI recipe-page background.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(targets = "mezz.jei.library.recipes.RecipeManager")
abstract class JeiRecipeManagerMixin {
    @ModifyVariable(
            method = {
                    "createRecipeLayoutDrawableOrShowError(Lmezz/jei/api/recipe/category/IRecipeCategory;Ljava/lang/Object;Lmezz/jei/api/recipe/IFocusGroup;)Lmezz/jei/api/gui/IRecipeLayoutDrawable;",
                    "createRecipeLayoutDrawable(Lmezz/jei/api/recipe/category/IRecipeCategory;Ljava/lang/Object;Lmezz/jei/api/recipe/IFocusGroup;)Ljava/util/Optional;"
            },
            at = @At(value = "STORE"),
            ordinal = 0)
    private static IScalableDrawable mmcr$replaceRecipeBackground(
            IScalableDrawable original,
            IRecipeCategory<?> recipeCategory,
            Object recipe,
            IFocusGroup focusGroup) {
        return original;
    }
}
