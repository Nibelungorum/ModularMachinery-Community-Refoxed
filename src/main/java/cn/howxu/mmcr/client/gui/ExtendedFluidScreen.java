package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.ExtendedFluidMenu;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.FluidStorageEntry;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.util.ReadableNumber;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Text screen for an extended fluid port.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ExtendedFluidScreen extends AbstractPortScreen<ExtendedFluidMenu> {
    static final String TEXTURE_PATH = "textures/gui/guicontroller_large.png";
    private static final Identifier TEXTURE = MMCR.id(TEXTURE_PATH);
    private static final Identifier AUTO_IO_TEXTURE = MMCR.id("textures/gui/guismartinterface.png");
    private static final int GUI_TEXTURE_SIZE = 256;
    private static final int IMAGE_HEIGHT = 213;
    private static final int TITLE_X = 12;
    private static final int TITLE_Y = 12;
    private static final int ROW_X = 12;
    private static final int TEXT_COLOR = 0xFFE0E0E0;

    public ExtendedFluidScreen(ExtendedFluidMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, IMAGE_HEIGHT);
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
        return 0;
    }

    @Override
    protected Identifier texture(boolean autoIOPage) {
        return autoIOPage ? AUTO_IO_TEXTURE : TEXTURE;
    }

    @Override
    protected int scrollableTextLineCount() {
        return 1 + nonEmptyEntries(menu.entries()).size();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture(autoIOPage), leftPos, topPos, 0, 0,
                imageWidth, imageHeight, GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        clearTooltipEntries();
        if (autoIOPage) return;
        graphics.text(font, title, TITLE_X, TITLE_Y, TEXT_COLOR, false);
        graphics.pose().pushMatrix();
        graphics.pose().scale(TEXT_DETAIL_SCALE, TEXT_DETAIL_SCALE);
        List<FluidStorageEntry> entries = nonEmptyEntries(menu.entries());
        clampTextScrollOffset();
        int row = 0;
        if (entries.isEmpty()) {
            Component empty = emptyLine();
            if (isTextLineVisible(row)) {
                int y = textLineY(visibleTextRow(row));
                graphics.text(font, empty, (int) (ROW_X / TEXT_DETAIL_SCALE),
                        (int) (y / TEXT_DETAIL_SCALE), 0xFF55FF55, false);
            }
        } else {
            Component stored = Component.translatable("gui.mmcr.port.stored");
            if (isTextLineVisible(row)) {
                int y = textLineY(visibleTextRow(row));
                graphics.text(font, stored, (int) (ROW_X / TEXT_DETAIL_SCALE),
                        (int) (y / TEXT_DETAIL_SCALE), 0xFF55FF55, false);
            }
            row = 1;
        }
        for (FluidStorageEntry entry : entries) {
            if (!isTextLineVisible(row)) {
                row++;
                continue;
            }
            Component line = displayLine(entry);
            int y = textLineY(visibleTextRow(row++));
            graphics.text(font, line, (int) (ROW_X / TEXT_DETAIL_SCALE), (int) (y / TEXT_DETAIL_SCALE), TEXT_COLOR, false);
            addTooltip(leftPos + ROW_X, topPos + y, (int) (font.width(line) * TEXT_DETAIL_SCALE),
                    TEXT_DETAIL_LINE_SPACING, tooltipLines(entry));
        }
        graphics.pose().popMatrix();
    }

    static List<Component> displayLines(List<FluidStorageEntry> entries) {
        List<Component> lines = new ArrayList<>();
        for (FluidStorageEntry entry : nonEmptyEntries(entries)) lines.add(displayLine(entry));
        return lines.isEmpty() ? List.of(emptyLine()) : List.copyOf(lines);
    }

    static List<Component> tooltipLines(FluidStorageEntry entry) {
        return List.of(entry.resource().getHoverName(), Component.literal(ReadableNumber.formatExact(entry.amount())
                + " / " + ReadableNumber.formatExact(entry.capacity()) + " mB"));
    }

    private static Component displayLine(FluidStorageEntry entry) {
        return Component.literal(ReadableNumber.format(entry.amount()) + " ")
                .append(entry.resource().getHoverName());
    }

    private static Component emptyLine() {
        return Component.translatable("gui.mmcr.port.empty").withStyle(ChatFormatting.GREEN);
    }

    private static List<FluidStorageEntry> nonEmptyEntries(List<FluidStorageEntry> entries) {
        return entries.stream().filter(entry -> entry.amount() > 0 && !entry.resource().isEmpty()).toList();
    }
}
