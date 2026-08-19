package cn.howxu.mmcr.client.render;

import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Standalone fluid GUI renderer adapted from LowDragLib2's 16-pixel tiling and tint approach,
 * modified for MMCR and NeoForge 26.1.2.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluidGuiRenderer {
    private static final int TILE_SIZE = 16;
    private static final int OPAQUE_WHITE = 0xffffffff;

    private FluidGuiRenderer() {
    }

    static int fillHeight(long amount, long capacity, int height) {
        if (amount <= 0 || capacity <= 0 || height <= 0) {
            return 0;
        }
        return (int) Math.min(height, Math.max(1, (long) Math.ceil((double) amount * height / capacity)));
    }

    static int[] tileWidths(int width) {
        return tileDimensions(width);
    }

    static int[] tileHeights(int height) {
        return tileDimensions(height);
    }

    public static TextureAtlasSprite stillSprite(FluidStack fluid) {
        FluidModel model = model(fluid);
        TextureAtlasSprite sprite = model.stillMaterial().sprite();
        if (Minecraft.getInstance().getTextureManager().getTexture(sprite.atlasLocation()) instanceof TextureAtlas atlas
                && sprite == atlas.missingSprite()) {
            throw new IllegalArgumentException("Missing still sprite for fluid " + fluid.getFluid());
        }
        return sprite;
    }

    public static int fluidColor(FluidStack fluid) {
        var tintSource = model(fluid).fluidTintSource();
        return tintSource == null ? OPAQUE_WHITE : tintSource.colorAsStack(fluid) | 0xff000000;
    }

    public static void drawFluid(GuiGraphicsExtractor graphics, FluidStack fluid, int x, int y, int width, int height) {
        TextureAtlasSprite sprite = stillSprite(fluid);
        int color = fluidColor(fluid);
        int yStart = y + height;
        int[] widths = tileWidths(width);
        int[] heights = tileHeights(height);
        for (int xTile = 0, xOffset = 0; xTile < widths.length; xTile++) {
            for (int yTile = 0, yOffset = 0; yTile < heights.length; yTile++) {
                int tileWidth = widths[xTile];
                int tileHeight = heights[yTile];
                int tileX = x + xOffset;
                int tileY = yStart - yOffset - TILE_SIZE;
                if (tileWidth != TILE_SIZE || tileHeight != TILE_SIZE) {
                    graphics.enableScissor(tileX, yStart - yOffset - tileHeight, tileX + tileWidth, yStart - yOffset);
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, tileX, tileY, TILE_SIZE, TILE_SIZE, color);
                    graphics.disableScissor();
                } else {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, tileX, tileY, TILE_SIZE, TILE_SIZE, color);
                }
                yOffset += tileHeight;
            }
            xOffset += widths[xTile];
        }
    }

    private static FluidModel model(FluidStack fluid) {
        return Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
    }

    private static int[] tileDimensions(int dimension) {
        if (dimension <= 0) {
            return new int[0];
        }
        int fullTiles = dimension / TILE_SIZE;
        int remainder = dimension % TILE_SIZE;
        int[] result = new int[fullTiles + (remainder > 0 ? 1 : 0)];
        Arrays.fill(result, 0, fullTiles, TILE_SIZE);
        if (remainder > 0) {
            result[fullTiles] = remainder;
        }
        return result;
    }
}
