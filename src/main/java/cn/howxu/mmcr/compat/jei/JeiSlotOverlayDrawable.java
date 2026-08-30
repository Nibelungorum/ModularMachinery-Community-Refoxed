package cn.howxu.mmcr.compat.jei;

import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared compact quantity overlay used by JEI item slots.
 *
 * @author howxu <dev@howxu.cn>
 */
final class JeiSlotOverlayDrawable implements IDrawable {
    private final String chanceText;
    private final String quantityText;

    JeiSlotOverlayDrawable(String chanceText, String quantityText) {
        this.chanceText = chanceText;
        this.quantityText = quantityText;
    }

    @Override
    public int getWidth() {
        return 16;
    }

    @Override
    public int getHeight() {
        return 16;
    }

    @Override
    public void draw(GuiGraphicsExtractor guiGraphics, int xOffset, int yOffset) {
        var font = Minecraft.getInstance().font;
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(xOffset, yOffset);
        guiGraphics.pose().scale(MachineRecipeCategory.ITEM_OVERLAY_SCALE, MachineRecipeCategory.ITEM_OVERLAY_SCALE);
        if (!chanceText.isEmpty()) {
            guiGraphics.text(font, chanceText, 0, 0, 0xFFFF4040, false);
        }
        if (!quantityText.isEmpty()) {
            int x = Math.max(0, (int) (16 / MachineRecipeCategory.ITEM_OVERLAY_SCALE) - font.width(quantityText)) + 1;
            int y = (int) (16 / MachineRecipeCategory.ITEM_OVERLAY_SCALE) - font.lineHeight + 1;
            guiGraphics.text(font, quantityText, x, y, 0xFFFFFFFF, true);
        }
        guiGraphics.pose().popMatrix();
    }
}
