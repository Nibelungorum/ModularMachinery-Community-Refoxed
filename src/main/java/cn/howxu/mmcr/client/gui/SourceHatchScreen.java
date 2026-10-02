package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.render.FluidGuiRenderer;
import cn.howxu.mmcr.compat.ars_nouveau.SourcePortMenu;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.util.ReadableNumber;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Read-only source storage display; source transport is configured with Ars tools.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SourceHatchScreen extends AbstractPortScreen<SourcePortMenu> {
    private static final ResourceLocation TEXTURE = MMCR.id("textures/gui/guibar.png");
    private static final int GUI_TEXTURE_SIZE = 256;
    private static final int SOURCE_X = 15;
    private static final int SOURCE_Y = 10;
    private static final int SOURCE_W = 20;
    private static final int SOURCE_H = 61;
    private static final int TITLE_COLOR = ControllerTextLine.DEFAULT_COLOR;

    public SourceHatchScreen(SourcePortMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 166);
        titleLabelX += 32;
        titleLabelY += 3;
    }

    @Override protected BlockPos portPos() { return menu.pos(); }
    @Override protected IOType ownerIOType() { return menu.owner() == null ? null : menu.owner().ioType(); }
    @Override protected boolean supportsAutoIOControlPage() { return false; }
    @Override protected int portSlotCount() { return 0; }
    @Override protected ResourceLocation texture(boolean autoIOPage) { return TEXTURE; }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        clearTooltipEntries();
        graphics.drawString(font, title, titleLabelX, titleLabelY, TITLE_COLOR, false);
        Component amount = amountLine(menu);
        graphics.drawString(font, amount, titleLabelX, titleLabelY + 12, TITLE_COLOR, false);
        List<Component> tooltip = tooltipLines(menu);
        addTooltip(leftPos + titleLabelX, topPos + titleLabelY + 12, font.width(amount), 10, tooltip);
        addTooltip(leftPos + SOURCE_X, topPos + SOURCE_Y, SOURCE_W, SOURCE_H, tooltip);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0,
                imageWidth, imageHeight, GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
        int filled = filledHeight(menu);
        if (filled > 0) graphics.blit(TEXTURE, leftPos + SOURCE_X,
                topPos + SOURCE_Y + SOURCE_H - filled, 196, SOURCE_H - filled, SOURCE_W, filled,
                GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
    }

    static Component amountLine(SourcePortMenu menu) {
        return Component.translatable("gui.mmcr.source.amount", ReadableNumber.format(menu.storedSource()),
                ReadableNumber.format(menu.sourceCapacity()));
    }

    static List<Component> tooltipLines(SourcePortMenu menu) {
        return List.of(Component.translatable("gui.mmcr.source.exact",
                ReadableNumber.formatExact(menu.storedSource()) + " / "
                        + ReadableNumber.formatExact(menu.sourceCapacity())));
    }

    static int filledHeight(SourcePortMenu menu) {
        return FluidGuiRenderer.fillHeight(menu.storedSource(), menu.sourceCapacity(), SOURCE_H);
    }
}
