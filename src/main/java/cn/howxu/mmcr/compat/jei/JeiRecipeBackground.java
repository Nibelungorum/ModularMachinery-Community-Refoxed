package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import mezz.jei.api.gui.drawable.IScalableDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
        guiGraphics.blit(texture, x, y, 0.0F, 0.0F, width, height, width, height);
    }
}
