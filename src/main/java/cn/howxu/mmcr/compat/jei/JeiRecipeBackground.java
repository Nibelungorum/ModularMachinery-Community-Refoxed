package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import mezz.jei.api.gui.drawable.IScalableDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * MMCR-only scalable background for JEI recipe pages.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JeiRecipeBackground implements IScalableDrawable {
    public static final JeiRecipeBackground INSTANCE = new JeiRecipeBackground();

    private JeiRecipeBackground() {
    }

    @Override
    public void draw(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height) {
        int scale = (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale());
        scale = Math.max(1, Math.min(4, scale));
        Identifier texture = MMCR.id("textures/gui/jei/recipe/" + scale + "x.png");
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, width, height, width, height);
    }
}
