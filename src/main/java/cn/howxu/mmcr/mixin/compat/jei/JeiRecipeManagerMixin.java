package cn.howxu.mmcr.mixin.compat.jei;

import cn.howxu.mmcr.compat.jei.JeiRecipeBackground;
import cn.howxu.mmcr.compat.jei.MachineRecipeCategory;
import cn.howxu.mmcr.compat.jei.MachineStructureCategory;
import mezz.jei.api.gui.drawable.IScalableDrawable;
import mezz.jei.api.recipe.category.IRecipeCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Replaces JEI's recipe-page background with the MMCR background.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(targets = "mezz.jei.library.recipes.RecipeManager", remap = false)
abstract class JeiRecipeManagerMixin {
    // JEI's optimized jars omit local-variable metadata, so modify invocation arguments instead.
    @ModifyArgs(
            method = {
                    "createRecipeLayoutDrawableOrShowError(Lmezz/jei/api/recipe/category/IRecipeCategory;Ljava/lang/Object;Lmezz/jei/api/recipe/IFocusGroup;)Lmezz/jei/api/gui/IRecipeLayoutDrawable;",
                    "createRecipeLayoutDrawable(Lmezz/jei/api/recipe/category/IRecipeCategory;Ljava/lang/Object;Lmezz/jei/api/recipe/IFocusGroup;)Ljava/util/Optional;",
                    "createRecipeLayoutDrawable(Lmezz/jei/api/recipe/category/IRecipeCategory;Ljava/lang/Object;Lmezz/jei/api/recipe/IFocusGroup;Lmezz/jei/api/gui/drawable/IScalableDrawable;I)Ljava/util/Optional;"
            },
            at = @At(value = "INVOKE", target = "Lmezz/jei/library/gui/recipes/RecipeLayout;create(Lmezz/jei/api/recipe/category/IRecipeCategory;Ljava/util/Collection;Ljava/lang/Object;Lmezz/jei/api/recipe/IFocusGroup;Lmezz/jei/api/runtime/IIngredientManager;Lmezz/jei/api/gui/drawable/IScalableDrawable;I)Ljava/util/Optional;"))
    private static void mmcr$replaceRecipeBackground(Args args) {
        args.set(5, mmcr$backgroundFor(args.get(0), args.get(5)));
    }

    @ModifyArgs(
            method = "lambda$createRecipeLayoutDrawableOrShowError$0",
            at = @At(value = "INVOKE", target = "Lmezz/jei/common/gui/RecipeLayoutDrawableErrored;<init>(Lmezz/jei/api/recipe/category/IRecipeCategory;Ljava/lang/Object;Lmezz/jei/api/gui/drawable/IScalableDrawable;I)V"))
    private static void mmcr$replaceErrorBackground(Args args) {
        args.set(2, mmcr$backgroundFor(args.get(0), args.get(2)));
    }

    private static IScalableDrawable mmcr$backgroundFor(IRecipeCategory<?> category, IScalableDrawable original) {
        if (category instanceof MachineStructureCategory) return JeiRecipeBackground.PREVIEW;
        return category instanceof MachineRecipeCategory ? JeiRecipeBackground.INSTANCE : original;
    }
}
