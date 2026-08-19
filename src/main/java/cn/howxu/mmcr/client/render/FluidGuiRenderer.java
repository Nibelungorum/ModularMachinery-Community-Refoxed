package cn.howxu.mmcr.client.render;

import java.util.Arrays;
import java.util.List;
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

    record Tile(int x, int y, int width, int height, int maskTop, int maskRight) {
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
        for (Tile tile : tiles(x, y, width, height)) {
            float u0 = sprite.getU0();
            float u1 = sprite.getU1() - tile.maskRight / 16f * (sprite.getU1() - u0);
            float v0 = sprite.getV0();
            float v1 = sprite.getV1() - tile.maskTop / 16f * (sprite.getV1() - v0);
            int sourceWidth = Math.max(1, Math.round((u1 - u0) * 16));
            int sourceHeight = Math.max(1, Math.round((v1 - v0) * 16));
            graphics.blit(RenderPipelines.GUI_TEXTURED, sprite.atlasLocation(),
                    tile.x(), tile.y() + tile.maskTop, u0, v0,
                    tile.width(), tile.height(), sourceWidth, sourceHeight, 1, 1, color);
        }
    }

    static List<Tile> tiles(int x, int y, int width, int height) {
        int[] widths = tileWidths(width);
        int[] heights = tileHeights(height);
        int yStart = y + height;
        var result = new java.util.ArrayList<Tile>(widths.length * heights.length);
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
