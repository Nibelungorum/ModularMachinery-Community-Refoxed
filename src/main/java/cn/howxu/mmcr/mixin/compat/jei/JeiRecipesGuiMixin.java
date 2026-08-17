package cn.howxu.mmcr.mixin.compat.jei;

import cn.howxu.mmcr.compat.jei.JeiPreviewLifecycle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Closes previews when JEI's recipe screen is dismissed without another state transition.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(targets = "mezz.jei.gui.recipes.RecipesGui")
abstract class JeiRecipesGuiMixin {
    @Inject(method = "onClose", at = @At("HEAD"))
    private void mmcr$closePreviews(CallbackInfo callback) {
        JeiPreviewLifecycle.closeActive();
    }
}
