package cn.howxu.mmcr.client.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Standalone fluid GUI renderer adapted from LowDragLib2's 16-pixel tiling and tint approach,
 * modified for MMCR and NeoForge 21.1.1.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluidGuiRenderer {
    private static final int TILE_SIZE = 16;
    private FluidGuiRenderer() {
    }

    record Tile(int x, int y, int width, int height, int maskTop, int maskRight) {
    }

    record Uv(float u0, float v0, float u1, float v1) {
    }

    public static int fillHeight(long amount, long capacity, int height) {
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
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(IClientFluidTypeExtensions.of(fluid.getFluid()).getStillTexture());
    }

    public static int fluidColor(FluidStack fluid) {
        return IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid);
    }

    public static void drawFluid(GuiGraphics graphics, FluidStack fluid, int x, int y, int width, int height) {
        TextureAtlasSprite sprite = stillSprite(fluid);
        drawSprite(graphics, sprite, fluidColor(fluid), x, y, width, height);
    }

    public static void drawSprite(GuiGraphics graphics, TextureAtlasSprite sprite, int color,
                                   int x, int y, int width, int height) {
        if (sprite == null || width <= 0 || height <= 0) return;
        for (Tile tile : tiles(x, y, width, height)) {
            graphics.enableScissor(tile.x(), tile.y() + tile.maskTop(),
                    tile.x() + tile.width(), tile.y() + tile.maskTop() + tile.height());
            graphics.blit(tile.x(), tile.y(), 0, TILE_SIZE, TILE_SIZE, sprite,
                    (color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F,
                    (color & 0xFF) / 255F, (color >>> 24) / 255F);
            graphics.disableScissor();
        }
    }

    static Uv tileUv(float u0, float v0, float u1, float v1, Tile tile) {
        float uSize = u1 - u0;
        float vSize = v1 - v0;
        return new Uv(
                u0,
                v0 + vSize * tile.maskTop() / TILE_SIZE,
                u0 + uSize * (TILE_SIZE - tile.maskRight()) / TILE_SIZE,
                v1);
    }

    static List<Tile> tiles(int x, int y, int width, int height) {
        int[] widths = tileWidths(width);
        int[] heights = tileHeights(height);
        int yStart = y + height;
        var result = new ArrayList<Tile>(widths.length * heights.length);
        for (int xTile = 0, xOffset = 0; xTile < widths.length; xTile++) {
            for (int yTile = 0; yTile < heights.length; yTile++) {
                int tileWidth = widths[xTile];
                int tileHeight = heights[yTile];
                result.add(new Tile(x + xOffset, yStart - (yTile + 1) * TILE_SIZE, tileWidth, tileHeight,
                        TILE_SIZE - tileHeight, TILE_SIZE - tileWidth));
            }
            xOffset += widths[xTile];
        }
        return result;
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
