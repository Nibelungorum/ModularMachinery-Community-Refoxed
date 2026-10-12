package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.client.recipe.RecipeDisplayDrawing;
import mezz.jei.api.gui.drawable.IScalableDrawable;
import net.minecraft.client.gui.GuiGraphics;

/**
 * MMCR-only scalable background for JEI recipe pages.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JeiRecipeBackground implements IScalableDrawable {
    public static final JeiRecipeBackground INSTANCE = new JeiRecipeBackground(false);
    public static final JeiRecipeBackground PREVIEW = new JeiRecipeBackground(true);

    private final boolean structure;

    private JeiRecipeBackground(boolean structure) {
        this.structure = structure;
    }

    @Override
    public void draw(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        RecipeDisplayDrawing.drawBackground(guiGraphics, structure, x, y, width, height);
    }
}
