package cn.howxu.mmcr.mixin.compat.jei;

import cn.howxu.mmcr.compat.jei.JeiPreviewLifecycle;
import mezz.jei.gui.recipes.lookups.ILookupState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * JEI exposes no widget/layout disposal API; state replacement discards its recipe layouts.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(targets = "mezz.jei.gui.recipes.RecipeGuiLogic")
abstract class JeiRecipeGuiLogicMixin {
    @Inject(method = "setState", at = @At("HEAD"))
    private void mmcr$closeDiscardedPreviews(ILookupState state, boolean addToHistory,
            CallbackInfoReturnable<Boolean> callback) {
        JeiPreviewLifecycle.closeActive();
    }
}
