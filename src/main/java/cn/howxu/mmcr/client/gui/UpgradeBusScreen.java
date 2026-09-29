package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.UpgradeBusMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Client screen for a standalone upgrade bus without AutoIO controls.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class UpgradeBusScreen extends AbstractContainerScreen<UpgradeBusMenu> {
    private static final int GUI_TEXTURE_SIZE = 256;
    private static final int BASE_BACKGROUND_HEIGHT = 166;
    private static final int SLOT_SIZE = 18;

    public UpgradeBusScreen(UpgradeBusMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = menu.imageHeight();
        inventoryLabelY = -1000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ResourceLocation texture = MMCR.id(menu.texturePath());
        for (int destY = 0; destY < imageHeight;) {
            int height = destY == 0 ? Math.min(BASE_BACKGROUND_HEIGHT, imageHeight)
                    : Math.min(SLOT_SIZE, imageHeight - destY);
            int sourceY = destY == 0 ? 0 : GUI_TEXTURE_SIZE - height;
            graphics.blit(texture, leftPos, topPos + destY, 0, sourceY,
                    imageWidth, height, GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
            destY += height;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
