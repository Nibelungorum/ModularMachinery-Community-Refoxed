package cn.howxu.mmcr.mixin.compat.emi;

import cn.howxu.mmcr.client.recipe.RecipeDisplayDrawing;
import cn.howxu.mmcr.client.render.GuiClipContext;
import cn.howxu.mmcr.compat.emi.EmiRenderScope;
import cn.howxu.mmcr.compat.emi.MachineEmiRecipe;
import cn.howxu.mmcr.compat.emi.MachineStructureEmiRecipe;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.GlStateBackup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame-borrowed hover scopes and public shared backgrounds for MMCR only.
 * @author howxu <dev@howxu.cn>
 */
@Mixin(value = EmiRenderHelper.class, remap = false)
public abstract class EmiRenderHelperMixin {
    @Inject(method = "renderRecipeBackground", at = @At("HEAD"), cancellable = true)
    private static void mmcr$background(EmiRecipe recipe, EmiDrawContext context, int x, int y, CallbackInfo ci) {
        if (recipe instanceof MachineEmiRecipe || recipe instanceof MachineStructureEmiRecipe) {
            context.resetColor();
            RecipeDisplayDrawing.drawBackground(context.raw(), recipe instanceof MachineStructureEmiRecipe,
                    x, y, recipe.getDisplayWidth() + 8, recipe.getDisplayHeight() + 8);
            // EMI's second call writes only alpha; submit before its color mask changes again.
            context.raw().flush();
            ci.cancel();
        }
    }

    @WrapMethod(method = "renderRecipe")
    private static void mmcr$renderScope(EmiRecipe recipe, EmiDrawContext context, int x, int y,
                                       boolean showMissing, int overlayColor, Operation<Void> original) {
        if (!(recipe instanceof MachineEmiRecipe || recipe instanceof MachineStructureEmiRecipe)) {
            original.call(recipe, context, x, y, showMissing, overlayColor);
            return;
        }
        Object host = Minecraft.getInstance().screen;
        if (host == null) host = Minecraft.getInstance();
        boolean screenshot = EmiRenderScope.current() != null && EmiRenderScope.current().isScreenshot();
        GlStateBackup state = new GlStateBackup();
        RenderSystem.backupGlState(state);
        float[] color = RenderSystem.getShaderColor().clone();
        try (var scope = screenshot ? EmiRenderScope.screenshot() : EmiRenderScope.hover(host);
             var clip = screenshot ? GuiClipContext.offscreen() : null) {
            scope.preservePose(context.matrices());
            original.call(recipe, context, x, y, showMissing, overlayColor);
            scope.verifySuccess();
        } finally {
            try { context.raw().flush(); }
            finally {
                RenderSystem.restoreGlState(state);
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            }
        }
    }

    @WrapOperation(method = "renderRecipe", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/runtime/EmiDrawContext;pop()V"))
    private static void mmcr$flushBeforeAlpha(EmiDrawContext context, Operation<Void> original) {
        if (EmiRenderScope.current() != null) context.raw().flush();
        original.call(context);
    }

    // The original helper swallows widget errors. A screenshot must not read an unready/failed PNG.
    @WrapOperation(method = "renderRecipe", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/runtime/EmiLog;error(Ljava/lang/String;Ljava/lang/Throwable;)V"))
    private static void mmcr$recordFailure(String message, Throwable failure, Operation<Void> original) {
        if (EmiRenderScope.current() != null) EmiRenderScope.current().failed(failure);
        original.call(message, failure);
    }
}
