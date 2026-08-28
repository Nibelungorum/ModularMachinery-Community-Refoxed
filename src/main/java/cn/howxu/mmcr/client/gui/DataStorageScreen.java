package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.internal.menu.DataStorageMenu;
import cn.howxu.mmcr.internal.tile.DataStorageBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Minimal client view for typed values in an independent data-storage block.
 * @author howxu <dev@howxu.cn>
 */
public final class DataStorageScreen extends AbstractContainerScreen<DataStorageMenu> {
    private static final int LABEL_COLOR = 0xFF404040;

    public DataStorageScreen(DataStorageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        inventoryLabelY = -1000;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        extractBackground(graphics, mouseX, mouseY, partialTicks);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        graphics.text(font, title, leftPos + 7, topPos + 6, LABEL_COLOR, false);
        DataStorageBlockEntity storage = storage();
        if (storage == null) return;
        int y = topPos + 18;
        for (var entry : storage.storage().values().entrySet()) {
            if (y > topPos + 76) break;
            graphics.text(font, Component.literal(entry.getKey() + " = " + entry.getValue().value()),
                    leftPos + 7, y, LABEL_COLOR, false);
            y += 10;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        graphics.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + 80, 0xFFE8E8E8);
    }

    private DataStorageBlockEntity storage() {
        if (Minecraft.getInstance().level == null) return null;
        return Minecraft.getInstance().level.getBlockEntity(menu.pos()) instanceof DataStorageBlockEntity storage
                ? storage : null;
    }
}
