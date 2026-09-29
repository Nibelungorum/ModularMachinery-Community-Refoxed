package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.FactorySchedulerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen entry point for a factory scheduler menu.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FactorySchedulerScreen extends AbstractContainerScreen<FactorySchedulerMenu> {
    private static final int IMAGE_WIDTH = 176;
    private static final int IMAGE_HEIGHT = 166;
    private static final int BACKGROUND_TEXTURE_WIDTH = 256;
    private static final int BACKGROUND_TEXTURE_HEIGHT = 256;
    private static final ResourceLocation BACKGROUND = MMCR.id("textures/gui/guifactorycontroller.png");

    public FactorySchedulerScreen(FactorySchedulerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = IMAGE_WIDTH;
        imageHeight = IMAGE_HEIGHT;
        titleLabelY = -1000;
        inventoryLabelY = -1000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0,
                IMAGE_WIDTH, IMAGE_HEIGHT, BACKGROUND_TEXTURE_WIDTH, BACKGROUND_TEXTURE_HEIGHT);
    }

    static int backgroundTextureWidth() {
        return BACKGROUND_TEXTURE_WIDTH;
    }

    static int backgroundTextureHeight() {
        return BACKGROUND_TEXTURE_HEIGHT;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX - 4, titleLabelY - 2, -12566464, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

}
