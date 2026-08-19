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

    record TileSource(int x, int y, int textureWidth, int textureHeight) {
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
        int atlasWidth = Math.round(sprite.contents().width() / (sprite.getU1() - sprite.getU0()));
        int atlasHeight = Math.round(sprite.contents().height() / (sprite.getV1() - sprite.getV0()));
        for (Tile tile : tiles(x, y, width, height)) {
            TileSource source = tileSource(sprite.getX(), sprite.getY(), atlasWidth, atlasHeight, tile);
            graphics.blit(RenderPipelines.GUI_TEXTURED, sprite.atlasLocation(),
                    tile.x(), tile.y() + tile.maskTop(), source.x(), source.y(),
                    tile.width(), tile.height(), source.textureWidth(), source.textureHeight(), color);
        }
    }

    static TileSource tileSource(int spriteX, int spriteY, int atlasWidth, int atlasHeight, Tile tile) {
        return new TileSource(spriteX, spriteY + tile.maskTop(), atlasWidth, atlasHeight);
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
