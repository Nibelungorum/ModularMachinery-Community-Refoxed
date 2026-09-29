package cn.howxu.mmcr.compat.jade;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.impl.ui.ProgressOverlayElement;

/**
 * Renders a Mekanism chemical icon (a 16×16 sprite in the BLOCKS atlas) inside a
 * Jade tooltip line. The {@code tint} argument is the chemical's tint that overlays
 * the sprite; pass {@code -1} for no tint.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JadeChemicalElement extends ProgressOverlayElement {

    private final ResourceLocation spriteLocation;
    private final int tint;
    private final int width;
    private final int height;

    // 方形
    public JadeChemicalElement(ResourceLocation spriteLocation, int tint, int size) {
        this.spriteLocation = spriteLocation;
        this.tint = tint;
        this.width = size;
        this.height = size;
    }

    public JadeChemicalElement(ResourceLocation spriteLocation, int tint, int width, int height) {
        this.spriteLocation = spriteLocation;
        this.tint = tint;
        this.width = width;
        this.height = height;
    }

    @Override
    public Component getNarration() {
        return null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        TextureAtlasSprite sprite = minecraft.getAtlasManager()
                .getAtlasOrThrow(AtlasIds.BLOCKS)
                .getSprite(spriteLocation);
        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
        int drawX = floatingRect == null ? getX() : (int) floatingRect.getX();
        int drawY = floatingRect == null ? getY() : (int) floatingRect.getY();
        int drawW = floatingRect == null ? this.width : (int) floatingRect.getWidth();
        int drawH = floatingRect == null ? this.height : (int) floatingRect.getHeight();
        graphics.blitSprite(pipeline, sprite, drawX, drawY, drawW, drawH, tint);
    }
}