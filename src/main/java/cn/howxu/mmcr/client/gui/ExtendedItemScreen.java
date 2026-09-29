package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.ExtendedItemMenu;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.ItemStorageEntry;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.util.ReadableNumber;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Text screen for an extended item port.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ExtendedItemScreen extends AbstractPortScreen<ExtendedItemMenu> {
    static final String TEXTURE_PATH = "textures/gui/guicontroller_large.png";
    private static final ResourceLocation TEXTURE = MMCR.id(TEXTURE_PATH);
    private static final ResourceLocation AUTO_IO_TEXTURE = MMCR.id("textures/gui/guismartinterface.png");
    private static final int GUI_TEXTURE_SIZE = 256;
    private static final int IMAGE_HEIGHT = 213;
    private static final int TITLE_X = 12;
    private static final int TITLE_Y = 12;
    private static final int ROW_X = 12;
    private static final int TEXT_COLOR = ControllerTextLine.DEFAULT_COLOR;

    public ExtendedItemScreen(ExtendedItemMenu menu, Inventory inventory, Component title) {
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
    protected ResourceLocation texture(boolean autoIOPage) {
        return autoIOPage ? AUTO_IO_TEXTURE : TEXTURE;
    }

    @Override
    protected int scrollableTextLineCount() {
        return 1 + nonEmptyEntries(menu.entries()).size();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(texture(autoIOPage), leftPos, topPos, 0, 0,
                imageWidth, imageHeight, GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        clearTooltipEntries();
        if (autoIOPage) return;
        graphics.drawString(font, title, TITLE_X, TITLE_Y, TEXT_COLOR, false);
        graphics.pose().pushPose();
        graphics.pose().scale(TEXT_DETAIL_SCALE, TEXT_DETAIL_SCALE, 1.0F);
        List<ItemStorageEntry> entries = nonEmptyEntries(menu.entries());
        clampTextScrollOffset();
        int row = 0;
        if (entries.isEmpty()) {
            Component empty = emptyLine();
            if (isTextLineVisible(row)) {
                int y = textLineY(visibleTextRow(row));
                graphics.drawString(font, empty, (int) (ROW_X / TEXT_DETAIL_SCALE),
                        (int) (y / TEXT_DETAIL_SCALE), 0xFF55FF55, false);
            }
        } else {
            Component stored = Component.translatable("gui.mmcr.port.stored");
            if (isTextLineVisible(row)) {
                int y = textLineY(visibleTextRow(row));
                graphics.drawString(font, stored, (int) (ROW_X / TEXT_DETAIL_SCALE),
                        (int) (y / TEXT_DETAIL_SCALE), 0xFF55FF55, false);
            }
            row = 1;
        }
        for (ItemStorageEntry entry : entries) {
            if (!isTextLineVisible(row)) {
                row++;
                continue;
            }
            ControllerTextLine line = renderLine(entry);
            int y = textLineY(visibleTextRow(row++));
            renderTextLine(graphics, line, (int) (ROW_X / TEXT_DETAIL_SCALE),
                    (int) (y / TEXT_DETAIL_SCALE));
            addTooltip(leftPos + ROW_X, topPos + y,
                    (int) ((line.textXOffset() + font.width(line.text())) * TEXT_DETAIL_SCALE),
                    TEXT_DETAIL_LINE_SPACING, line.tooltip());
        }
        graphics.pose().popPose();
    }

    static List<Component> displayLines(List<ItemStorageEntry> entries) {
        List<Component> lines = new ArrayList<>();
        for (ItemStorageEntry entry : nonEmptyEntries(entries)) lines.add(displayLine(entry));
        return lines.isEmpty() ? List.of(emptyLine()) : List.copyOf(lines);
    }

    static List<Component> tooltipLines(ItemStorageEntry entry) {
        return List.of(entry.resource().getStyledHoverName(), Component.literal(ReadableNumber.formatExact(entry.amount())
                + " / " + ReadableNumber.formatExact(entry.capacity())));
    }

    private static Component displayLine(ItemStorageEntry entry) {
        return Component.literal(ReadableNumber.format(entry.amount()) + " ")
                .append(entry.resource().getStyledHoverName());
    }

    private static ControllerTextLine renderLine(ItemStorageEntry entry) {
        return new ControllerTextLine(displayLine(entry), TEXT_COLOR,
                new ControllerTextLine.ItemIcon(entry.resource()), tooltipLines(entry));
    }

    private static Component emptyLine() {
        return Component.translatable("gui.mmcr.port.empty").withStyle(ChatFormatting.GREEN);
    }

    private static List<ItemStorageEntry> nonEmptyEntries(List<ItemStorageEntry> entries) {
        return entries.stream().filter(entry -> entry.amount() > 0 && !entry.resource().isEmpty()).toList();
    }
}
