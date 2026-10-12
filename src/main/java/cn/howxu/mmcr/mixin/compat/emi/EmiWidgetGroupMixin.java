package cn.howxu.mmcr.mixin.compat.emi;

import cn.howxu.mmcr.client.recipe.RecipeDisplayDrawing;
import cn.howxu.mmcr.compat.emi.EmiInteractiveWidget;
import cn.howxu.mmcr.compat.emi.EmiPreviewSessions;
import cn.howxu.mmcr.compat.emi.MachineEmiRecipe;
import cn.howxu.mmcr.compat.emi.MachineStructureEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.DrawableWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.screen.WidgetGroup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Replaces only MMCR group backgrounds and releases owned widgets before EMI clears an error group.
 * @author howxu <dev@howxu.cn>
 */
@Mixin(value = WidgetGroup.class, remap = false)
public abstract class EmiWidgetGroupMixin {
    @Shadow @Final public EmiRecipe recipe;
    @Shadow @Final public int width;
    @Shadow @Final public int height;
    @Shadow @Final public List<Widget> widgets;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mmcr$background(CallbackInfo ci) {
        if (recipe instanceof MachineEmiRecipe || recipe instanceof MachineStructureEmiRecipe) {
            widgets.set(0, new DrawableWidget(-4, -4, width + 8, height + 8,
                    (graphics, mouseX, mouseY, delta) -> RecipeDisplayDrawing.drawBackground(graphics,
                            recipe instanceof MachineStructureEmiRecipe, 0, 0, width + 8, height + 8)));
        }
    }

    @Inject(method = "error(Ljava/lang/Throwable;)V", at = @At("HEAD"))
    private void mmcr$releaseErrorGroup(Throwable failure, CallbackInfo ci) {
        for (Widget widget : widgets) if (widget instanceof EmiInteractiveWidget interactive) interactive.close();
        EmiPreviewSessions.instance().removeHost(this);
    }
}
