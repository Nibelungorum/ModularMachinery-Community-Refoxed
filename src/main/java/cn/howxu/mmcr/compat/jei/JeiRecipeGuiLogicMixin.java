package cn.howxu.mmcr.compat.jei;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * JEI exposes no widget/layout disposal API; state replacement discards its recipe layouts.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(targets = "mezz.jei.gui.recipes.RecipeGuiLogic")
abstract class JeiRecipeGuiLogicMixin {
    @Inject(method = "setState", at = @At("HEAD"))
    private void mmcr$closeDiscardedPreviews(Object state, boolean addToHistory, CallbackInfo callback) {
        JeiPreviewLifecycle.closeActive();
    }
}
