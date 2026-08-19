package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.EnergyHatchMenu;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.util.ReadableNumber;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Energy hatch screen.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class EnergyHatchScreen extends AbstractPortScreen<EnergyHatchMenu> {
    private static final Identifier TEXTURE = MMCR.id("textures/gui/guitank.png");
    private static final Identifier AUTO_IO_TEXTURE = MMCR.id("textures/gui/guismartinterface.png");
    private static final Identifier BAR_TEXTURE = MMCR.id("textures/gui/guibar.png");
    private static final int GUI_TEXTURE_SIZE = 256;
    private static final int ENERGY_X = 15;
    private static final int ENERGY_Y = 10;
    private static final int ENERGY_W = 20;
    private static final int ENERGY_H = 61;
    private static final int TITLE_COLOR = -12566464;

    public EnergyHatchScreen(EnergyHatchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 166);
        titleLabelX += 32;
        titleLabelY += 3;
    }

    @Override protected BlockPos portPos() { return menu.pos(); }
    @Override protected IOType ownerIOType() { return menu.owner() == null ? null : menu.owner().ioType(); }
    @Override protected int portSlotCount() { return 0; }
    @Override protected Identifier texture(boolean autoIOPage) { return autoIOPage ? AUTO_IO_TEXTURE : TEXTURE; }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!autoIOPage) graphics.text(font, title, titleLabelX, titleLabelY, TITLE_COLOR, false);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture(autoIOPage), leftPos, topPos, 0, 0,
                imageWidth, imageHeight, GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
        long capacity = menu.energyCapacity();
        if (autoIOPage || capacity <= 0) return;
        long stored = menu.storedEnergy();
        int filled = stored <= 0 ? 0 : Math.min(ENERGY_H, Math.max(1, (int) Math.min((long) ENERGY_H,
                Math.ceilDiv(stored * ENERGY_H, capacity))));
        if (filled > 0) graphics.blit(RenderPipelines.GUI_TEXTURED, BAR_TEXTURE, leftPos + ENERGY_X,
                topPos + ENERGY_Y + ENERGY_H - filled, 196, ENERGY_H - filled, ENERGY_W, filled,
                GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
        graphics.text(font, Component.literal(ReadableNumber.format(stored) + " / " + ReadableNumber.format(capacity) + " FE"),
                leftPos + titleLabelX, topPos + titleLabelY + 12, TITLE_COLOR, false);
    }
}
