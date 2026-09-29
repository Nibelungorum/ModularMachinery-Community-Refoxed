package cn.howxu.mmcr.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the client-neutral chemical state supplied by the loaded Mekanism menu.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ChemicalGuiRenderer {
    private ChemicalGuiRenderer() {
    }

    public record ChemicalRenderState(ResourceLocation identifier, int tint, int fillHeight) {
    }

    public static ChemicalRenderState state(ResourceLocation identifier, int tint, long amount, long capacity, int height) {
        int fillHeight = identifier == null ? 0 : FluidGuiRenderer.fillHeight(amount, capacity, height);
        return new ChemicalRenderState(identifier, tint, fillHeight);
    }

    public static void drawChemical(GuiGraphics graphics, ChemicalRenderState state,
                                     int x, int y, int width, int height) {
        if (state.identifier() == null || state.fillHeight() <= 0 || width <= 0 || height <= 0) return;
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(state.identifier());
        int fillHeight = Math.min(state.fillHeight(), height);
        FluidGuiRenderer.drawSprite(graphics, sprite, state.tint(), x, y + height - fillHeight,
                width, fillHeight);
    }
}
