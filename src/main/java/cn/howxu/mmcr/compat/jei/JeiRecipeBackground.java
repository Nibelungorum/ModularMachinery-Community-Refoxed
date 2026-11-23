package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.mixin.client.preview.GuiGraphicsExtractorAccessor;
import mezz.jei.api.gui.drawable.IScalableDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * MMCR-only scalable background for JEI recipe pages.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JeiRecipeBackground implements IScalableDrawable {
    public static final JeiRecipeBackground INSTANCE = new JeiRecipeBackground("recipe");
    public static final JeiRecipeBackground PREVIEW = new JeiRecipeBackground("preview");

    private final String textureDirectory;

    private JeiRecipeBackground(String textureDirectory) {
        this.textureDirectory = textureDirectory;
    }

    @Override
    public void draw(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height) {
        int scale = Math.round(Minecraft.getInstance().getWindow().getGuiScale());
        scale = Math.max(1, Math.min(4, scale));
        Identifier texture = MMCR.id("textures/gui/jei/" + textureDirectory + "/" + scale + "x.png");
        ScreenRectangle area = textureArea(scale, x, y, width, height);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture, area.left(), area.top(), 0.0F, 0.0F,
                area.width(), area.height(), area.width(), area.height());
        ((GuiGraphicsExtractorAccessor) guiGraphics).mmcr$getGuiRenderState().nextStratum();
    }

    ScreenRectangle textureArea(int scale, int x, int y, int width, int height) {
        if (this != PREVIEW) return new ScreenRectangle(x, y, width, height);
        // JEI passes getRectWithBorder(); preview artwork is authored in recipe content coordinates.
        int contentWidth = StructurePreviewLayout.WIDTH;
        int contentHeight = StructurePreviewLayout.heightForScale(scale);
        return new ScreenRectangle(x + (width - contentWidth) / 2, y + (height - contentHeight) / 2,
                contentWidth, contentHeight);
    }
}
