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
 * Replaces the background used by JEI's bookmark recipe preview.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(targets = "mezz.jei.gui.overlay.elements.RecipeBookmarkElement")
abstract class JeiRecipeBookmarkElementMixin {
    @ModifyArgs(
            method = "getRecipeLayoutDrawable",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/api/recipe/IRecipeManager;createRecipeLayoutDrawable(Lmezz/jei/api/recipe/category/IRecipeCategory;Ljava/lang/Object;Lmezz/jei/api/recipe/IFocusGroup;Lmezz/jei/api/gui/drawable/IScalableDrawable;I)Ljava/util/Optional;"
            )
    )
    private void mmcr$replaceBookmarkPreviewBackground(Args args) {
        IRecipeCategory<?> recipeCategory = args.get(0);
        if (recipeCategory instanceof MachineStructureCategory) {
            args.set(3, JeiRecipeBackground.PREVIEW);
        } else if (recipeCategory instanceof MachineRecipeCategory) {
            args.set(3, JeiRecipeBackground.INSTANCE);
        }
    }
}
