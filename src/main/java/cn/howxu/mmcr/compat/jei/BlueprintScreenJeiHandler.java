package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.client.gui.BlueprintScreen;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.gui.handlers.IScreenHandler;
import mezz.jei.api.runtime.IClickableIngredient;

import java.util.Optional;

/**
 * Exposes blueprint preview slots to JEI's ingredient shortcut handling.
 *
 * @author howxu <dev@howxu.cn>
 */
final class BlueprintScreenJeiHandler implements IScreenHandler<BlueprintScreen> {
    @Override
    public IGuiProperties apply(BlueprintScreen screen) {
        return new ScreenProperties(screen, screen.guiBounds());
    }

    @Override
    public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(
            IClickableIngredientFactory factory, BlueprintScreen screen, double mouseX, double mouseY) {
        BlueprintScreen.HoveredIngredient ingredient = screen.hoveredIngredient(mouseX, mouseY);
        if (ingredient == null) return Optional.empty();
        return factory.createBuilder(ingredient.stack()).buildWithArea(ingredient.area());
    }
}
