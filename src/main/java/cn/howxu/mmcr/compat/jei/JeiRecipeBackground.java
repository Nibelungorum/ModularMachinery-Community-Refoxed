package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import mezz.jei.api.gui.drawable.IScalableDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.resources.ResourceLocation;

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
    public void draw(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        int scale = (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale());
        scale = Math.max(1, Math.min(4, scale));
        ResourceLocation texture = MMCR.id("textures/gui/jei/" + textureDirectory + "/" + scale + "x.png");
        ScreenRectangle area = textureArea(scale, x, y, width, height);
        guiGraphics.blit(texture, area.left(), area.top(), 0.0F, 0.0F,
                area.width(), area.height(), area.width(), area.height());
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
