package cn.howxu.mmcr.compat.jei;

import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.math.Axis;

/**
 * Preserves the native JEI arrow animation when pointing downward.
 *
 * @author howxu <dev@howxu.cn>
 */
final class RecipeArrowDrawable implements IDrawable {
    private final IDrawable delegate;
    private final boolean vertical;

    RecipeArrowDrawable(IDrawable delegate, boolean vertical) {
        this.delegate = delegate;
        this.vertical = vertical;
    }

    @Override
    public int getWidth() {
        return vertical ? delegate.getHeight() : delegate.getWidth();
    }

    @Override
    public int getHeight() {
        return vertical ? delegate.getWidth() : delegate.getHeight();
    }

    @Override
    public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
        if (!vertical) {
            delegate.draw(guiGraphics, xOffset, yOffset);
            return;
        }
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(xOffset + getWidth(), yOffset, 0);
        guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(90));
        delegate.draw(guiGraphics, 0, 0);
        guiGraphics.pose().popPose();
    }
}
