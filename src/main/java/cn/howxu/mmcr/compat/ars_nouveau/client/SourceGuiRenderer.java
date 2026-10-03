package cn.howxu.mmcr.compat.ars_nouveau.client;

import cn.howxu.mmcr.client.render.FluidGuiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the animated source sprite used by native Ars Nouveau source jars.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SourceGuiRenderer {
    private static final ResourceLocation SPRITE = ResourceLocation.fromNamespaceAndPath("ars_nouveau", "block/mana_still");

    private SourceGuiRenderer() {
    }

    public static void drawSource(GuiGraphics graphics, int x, int y, int width, int height) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(SPRITE);
        FluidGuiRenderer.drawSprite(graphics, sprite, 0xFFFFFFFF, x, y, width, height);
    }
}
