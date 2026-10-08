package cn.howxu.mmcr.compat.jei;

import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.gui.GuiGraphicsExtractor;

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
    public void draw(GuiGraphicsExtractor guiGraphics, int xOffset, int yOffset) {
        if (!vertical) {
            delegate.draw(guiGraphics, xOffset, yOffset);
            return;
        }
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(xOffset + getWidth(), yOffset);
        guiGraphics.pose().rotate((float) (Math.PI / 2));
        delegate.draw(guiGraphics, 0, 0);
        guiGraphics.pose().popMatrix();
    }
}
