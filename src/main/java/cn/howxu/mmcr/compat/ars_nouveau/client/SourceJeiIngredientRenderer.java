package cn.howxu.mmcr.compat.ars_nouveau.client;

import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Draws a full source sprite with an exact, direction-aware JEI tooltip.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SourceJeiIngredientRenderer implements IIngredientRenderer<SourceJeiIngredient> {
    @Override
    public void render(GuiGraphics graphics, SourceJeiIngredient ingredient) {
        SourceGuiRenderer.drawSource(graphics, 0, 0, 16, 16);
    }

    @Override
    @Deprecated(forRemoval = true)
    public List<Component> getTooltip(SourceJeiIngredient ingredient, TooltipFlag tooltipFlag) {
        return List.of(ingredient.tooltip());
    }

    @Override
    public List<Component> getTooltip(SourceJeiIngredient ingredient, Item.TooltipContext context,
                                      @Nullable Player player, TooltipFlag tooltipFlag) {
        return List.of(ingredient.tooltip());
    }
}
