package cn.howxu.mmcr.mixin.compat.emi;

import cn.howxu.mmcr.compat.emi.EmiRenderScope;
import cn.howxu.mmcr.compat.emi.MachineEmiRecipe;
import cn.howxu.mmcr.compat.emi.MachineStructureEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.widget.RecipeScreenshotButtonWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The queued screenshot Runnable owns its scope when executed, not when submitted.
 * @author howxu <dev@howxu.cn>
 */
@Mixin(value = RecipeScreenshotButtonWidget.class, remap = false)
public abstract class EmiRecipeScreenshotButtonWidgetMixin {
    @Shadow @Final protected EmiRecipe recipe;

    @ModifyArg(method = "mouseClicked(III)Z", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/runtime/EmiScreenshotRecorder;saveScreenshot(Ljava/lang/String;IILjava/lang/Runnable;)V"), index = 3)
    private Runnable mmcr$scopeScreenshot(Runnable originalRender) {
        return recipe instanceof MachineEmiRecipe || recipe instanceof MachineStructureEmiRecipe
                ? EmiRenderScope.screenshot(originalRender) : originalRender;
    }
}
