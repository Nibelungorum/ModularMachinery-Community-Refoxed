package cn.howxu.mmcr.compat.jade;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.ui.Element;

/**
 * Renders a Mekanism chemical icon (a 16×16 sprite in the BLOCKS atlas) inside a
 * Jade tooltip line. The {@code tint} argument is the chemical's tint that overlays
 * the sprite; pass {@code -1} for no tint.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JadeChemicalElement extends Element {

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
    public Vec2 getSize() {
        return new Vec2(width, height);
    }

    @Override
    public void render(GuiGraphics graphics, float x, float y, float maxX, float maxY) {
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(spriteLocation);
        try {
            if (tint != -1) {
                graphics.setColor(
                        FastColor.ARGB32.red(tint) / 255.0F,
                        FastColor.ARGB32.green(tint) / 255.0F,
                        FastColor.ARGB32.blue(tint) / 255.0F,
                        FastColor.ARGB32.alpha(tint) / 255.0F);
            }
            graphics.blit((int) x, (int) y, 0, width, height, sprite);
        } finally {
            if (tint != -1) graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
