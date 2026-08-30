package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.UpgradeBusMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
        super(menu, inventory, title, 176, menu.imageHeight());
        inventoryLabelY = -1000;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        Identifier texture = MMCR.id(menu.texturePath());
        for (int destY = 0; destY < imageHeight;) {
            int height = destY == 0 ? Math.min(BASE_BACKGROUND_HEIGHT, imageHeight)
                    : Math.min(SLOT_SIZE, imageHeight - destY);
            int sourceY = destY == 0 ? 0 : GUI_TEXTURE_SIZE - height;
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, leftPos, topPos + destY, 0, sourceY,
                    imageWidth, height, GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
            destY += height;
        }
    }
}
