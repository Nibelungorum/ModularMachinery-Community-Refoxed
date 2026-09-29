package cn.howxu.mmcr.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Shared button presentation for MMCR screens.
 *
 * @author howxu <dev@howxu.cn>
 */
public class StyledButton extends Button {
    public StyledButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics);
        renderString(graphics, Minecraft.getInstance().font, getFGColor());
    }

    protected void drawBackground(GuiGraphics graphics) {
        int baseColor = active ? 0xFF6B6B6B : 0xFF3F3F3F;
        int borderColor = isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFAAAAAA;
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), borderColor);
        graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, baseColor);
    }
}
