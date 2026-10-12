package cn.howxu.mmcr.mixin.compat.emi;

import cn.howxu.mmcr.compat.emi.EmiInputRouter;
import cn.howxu.mmcr.compat.emi.EmiInteractiveWidget;
import cn.howxu.mmcr.compat.emi.EmiPreviewSessions;
import cn.howxu.mmcr.compat.emi.EmiRuntimeReloader;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.screen.RecipeScreen;
import dev.emi.emi.screen.RecipeTab;
import dev.emi.emi.screen.WidgetGroup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Mojmap EMI 1.21.1 input, page replacement and inherited Screen lifecycle.
 * @author howxu <dev@howxu.cn>
 */
@Mixin(value = RecipeScreen.class, remap = false)
public abstract class EmiRecipeScreenMixin extends Screen implements EmiInputRouter.Host, EmiRuntimeReloader.ScreenHost {
    @Shadow private List<WidgetGroup> currentPage;
    @Shadow private Widget pressedSlot;
    @Shadow private Widget hoveredWidget;
    @Shadow private Map<EmiRecipeCategory, List<EmiRecipe>> recipes;
    @Shadow private List<RecipeTab> tabs;
    @Shadow int backgroundHeight;
    @Shadow public AbstractContainerScreen<?> old;
    @Unique private final EmiInputRouter mmcr$router = new EmiInputRouter();

    protected EmiRecipeScreenMixin(Component title) { super(title); }

    @Override public EmiInputRouter mmcrInputRouter() { return mmcr$router; }

    @Override public void mmcrInvalidateRecipes() {
        mmcr$router.close();
        for (WidgetGroup group : currentPage) {
            for (Widget widget : group.widgets) if (widget instanceof EmiInteractiveWidget interactive) interactive.close();
            EmiPreviewSessions.instance().removeHost(group);
        }
        currentPage.clear();
        pressedSlot = null;
        hoveredWidget = null;
    }

    @Override public void mmcrRefreshRecipes() { rebuildWidgets(); }

    @Unique private boolean mmcr$ownsRecipes() {
        return recipes != null && recipes.values().stream().flatMap(List::stream).anyMatch(recipe -> {
            var id = recipe.getId();
            return id != null && id.getNamespace().equals("mmcr")
                    && (id.getPath().startsWith("/recipe/") || id.getPath().startsWith("/structure/"));
        });
    }

    @Inject(method = "init()V", at = @At("HEAD"), cancellable = true)
    private void mmcr$restoreHistory(CallbackInfo ci) {
        mmcrInvalidateRecipes();
        if (mmcr$ownsRecipes() && !EmiRuntimeReloader.isLoaded()) {
            ci.cancel();
            return;
        }
        if (recipes != null) {
            var refreshed = EmiRuntimeReloader.refreshRecipes(recipes);
            if (refreshed != recipes) {
                recipes = refreshed;
                tabs.clear(); // Native init cannot focus an obsolete RecipeDisplay from history.
            }
            if (recipes.isEmpty()) {
                tabs.clear();
                minecraft.setScreen(old);
                ci.cancel();
            }
        }
    }

    @Inject(method = "setPage(III)V", at = @At("HEAD"), cancellable = true)
    private void mmcr$discardPage(int tabPage, int tab, int page, CallbackInfo ci) {
        mmcrInvalidateRecipes();
        if (mmcr$ownsRecipes() && !EmiRuntimeReloader.isLoaded()) {
            ci.cancel();
            return;
        }
        if (recipes == null) return;
        var refreshed = EmiRuntimeReloader.refreshRecipes(recipes);
        if (refreshed == recipes) return;
        recipes = refreshed;
        tabs.clear();
        for (var entry : recipes.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            RecipeTab refreshedTab = new RecipeTab(entry.getKey(), entry.getValue());
            refreshedTab.bakePages(backgroundHeight);
            tabs.add(refreshedTab);
        }
        if (tabs.isEmpty()) {
            minecraft.setScreen(old);
            ci.cancel();
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
    private void mmcr$waitForBake(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (mmcr$ownsRecipes() && EmiRuntimeReloader.suspendIfUnavailable()) ci.cancel();
    }

    @Inject(method = {"init()V", "setPage(III)V"}, at = @At("RETURN"))
    private void mmcr$bindPage(CallbackInfo ci) {
        List<EmiInputRouter.Binding> bindings = new ArrayList<>();
        for (WidgetGroup group : currentPage) {
            for (Widget widget : group.widgets) {
                if (widget instanceof EmiInteractiveWidget interactive) {
                    bindings.add(new EmiInputRouter.Binding(interactive, group.x(), group.y(), widget::getBounds));
                }
            }
        }
        mmcr$router.replace(bindings);
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true)
    private void mmcr$press(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        if (mmcr$blockInput()) { cir.setReturnValue(false); return; }
        if (mmcr$router.mousePressed(x, y, button)) {
            pressedSlot = null;
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged(DDIDD)Z", at = @At("HEAD"), cancellable = true)
    private void mmcr$drag(double x, double y, int button, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (mmcr$blockInput()) { cir.setReturnValue(false); return; }
        if (mmcr$router.mouseDragged(x, y, button, dx, dy)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased(DDI)Z", at = @At("HEAD"), cancellable = true)
    private void mmcr$release(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        if (mmcr$blockInput()) { cir.setReturnValue(false); return; }
        if (mmcr$router.mouseReleased(x, y, button)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), cancellable = true)
    private void mmcr$scroll(double x, double y, double horizontal, double vertical, CallbackInfoReturnable<Boolean> cir) {
        if (mmcr$blockInput()) { cir.setReturnValue(false); return; }
        if (mmcr$router.mouseScrolled(x, y, horizontal, vertical)) cir.setReturnValue(true);
    }

    @Unique private boolean mmcr$blockInput() {
        return mmcr$ownsRecipes() && EmiRuntimeReloader.suspendIfUnavailable();
    }

    @Inject(method = "keyPressed(III)Z", at = @At("HEAD"), cancellable = true)
    private void mmcr$waitForInput(int key, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        // Escape remains available to leave a failed/unfinished bake without touching recipe widgets.
        if (mmcr$blockInput() && key != GLFW.GLFW_KEY_ESCAPE) cir.setReturnValue(false);
    }

    // RecipeScreen has no removed() bytecode. A real inherited override is required here.
    @Override public void removed() {
        mmcrInvalidateRecipes();
        EmiPreviewSessions.instance().removeHost(this);
        super.removed();
    }
}
