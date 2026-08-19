package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Item bus inventory screen.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ItemBusScreen extends AbstractPortScreen<ItemBusMenu> {
    private static final int GUI_TEXTURE_SIZE = 256;
    private static final int BASE_BACKGROUND_HEIGHT = 166;
    private static final int SLOT_SIZE = 18;

    public ItemBusScreen(ItemBusMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.imageHeight());
        titleLabelX -= 4;
        titleLabelY = -1000;
    }

    @Override
    protected BlockPos portPos() {
        return menu.pos();
    }

    @Override
    protected IOType ownerIOType() {
        return menu.owner() == null ? null : menu.owner().ioType();
    }

    @Override
    protected int portSlotCount() {
        return menu.busSlotCount();
    }

    @Override
    protected Identifier texture(boolean autoIOPage) {
        return autoIOPage ? MMCR.id("textures/gui/guismartinterface.png") : MMCR.id(menu.texturePath());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        if (autoIOPage) {
            int height = Math.min(imageHeight, BASE_BACKGROUND_HEIGHT);
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture(true), leftPos, topPos, 0, 0,
                    imageWidth, height, GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
            return;
        }
        for (int destY = 0; destY < imageHeight;) {
            int height = destY == 0 ? Math.min(BASE_BACKGROUND_HEIGHT, imageHeight) : Math.min(SLOT_SIZE, imageHeight - destY);
            int sourceY = destY == 0 ? 0 : GUI_TEXTURE_SIZE - height;
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture(false), leftPos, topPos + destY, 0, sourceY,
                    imageWidth, height, GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
            destY += height;
        }
    }
}
