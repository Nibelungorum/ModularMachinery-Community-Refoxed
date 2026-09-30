package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.ExtendedCombinedMenu;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.FluidStorageEntry;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.ItemStorageEntry;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.util.ReadableNumber;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Text screen for an extended combined port.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ExtendedCombinedScreen extends AbstractPortScreen<ExtendedCombinedMenu> {
    static final String TEXTURE_PATH = "textures/gui/guicontroller_large.png";
    private static final ResourceLocation TEXTURE = MMCR.id(TEXTURE_PATH);
    private static final ResourceLocation AUTO_IO_TEXTURE = MMCR.id("textures/gui/guismartinterface.png");
    private static final int GUI_TEXTURE_SIZE = 256;
    private static final int IMAGE_HEIGHT = 213;
    private static final int TITLE_X = 12;
    private static final int TITLE_Y = 12;
    private static final int ROW_X = 12;
    private static final int TEXT_COLOR = ControllerTextLine.DEFAULT_COLOR;

    public ExtendedCombinedScreen(ExtendedCombinedMenu menu, Inventory inventory, Component title) {
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
        return 1 + nonEmptyItems(menu.itemEntries()).size()
                + 1 + nonEmptyFluids(menu.fluidEntries()).size();
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
        List<ItemStorageEntry> itemEntries = nonEmptyItems(menu.itemEntries());
        List<FluidStorageEntry> fluidEntries = nonEmptyFluids(menu.fluidEntries());
        clampTextScrollOffset();
        int lineIndex = drawItems(graphics, itemEntries, 0);
        drawFluids(graphics, fluidEntries, lineIndex);
        graphics.pose().popPose();
    }

    static List<Component> displayLines(List<ItemStorageEntry> itemEntries, List<FluidStorageEntry> fluidEntries) {
        List<Component> lines = new ArrayList<>();
        List<ItemStorageEntry> nonEmptyItems = nonEmptyItems(itemEntries);
        if (nonEmptyItems.isEmpty()) {
            lines.add(emptySectionLine("gui.mmcr.port.items"));
        } else {
            lines.add(sectionLabel("gui.mmcr.port.items"));
            for (ItemStorageEntry entry : nonEmptyItems) lines.add(itemLine(entry));
        }
        List<FluidStorageEntry> nonEmptyFluids = nonEmptyFluids(fluidEntries);
        if (nonEmptyFluids.isEmpty()) {
            lines.add(emptySectionLine("gui.mmcr.port.fluids"));
        } else {
            lines.add(sectionLabel("gui.mmcr.port.fluids"));
            for (FluidStorageEntry entry : nonEmptyFluids) lines.add(fluidLine(entry));
        }
        return List.copyOf(lines);
    }

    private int drawItems(GuiGraphics graphics, List<ItemStorageEntry> entries, int lineIndex) {
        if (entries.isEmpty()) {
            if (isTextLineVisible(lineIndex)) {
                int y = textLineY(visibleTextRow(lineIndex));
                graphics.drawString(font, emptySectionLine("gui.mmcr.port.items"),
                        (int) (ROW_X / TEXT_DETAIL_SCALE), (int) (y / TEXT_DETAIL_SCALE),
                        0xFF55FF55, false);
            }
            return lineIndex + 1;
        }
        if (isTextLineVisible(lineIndex)) {
            int y = textLineY(visibleTextRow(lineIndex));
            graphics.drawString(font, sectionLabel("gui.mmcr.port.items"),
                    (int) (ROW_X / TEXT_DETAIL_SCALE), (int) (y / TEXT_DETAIL_SCALE),
                    0xFF55FF55, false);
        }
        lineIndex++;
        for (ItemStorageEntry entry : entries) {
            if (!isTextLineVisible(lineIndex)) {
                lineIndex++;
                continue;
            }
            ControllerTextLine line = itemRenderLine(entry);
            int y = textLineY(visibleTextRow(lineIndex));
            renderTextLine(graphics, line, (int) (ROW_X / TEXT_DETAIL_SCALE),
                    (int) (y / TEXT_DETAIL_SCALE));
            addTooltip(leftPos + ROW_X, topPos + y,
                    (int) ((line.textXOffset() + font.width(line.text())) * TEXT_DETAIL_SCALE),
                    TEXT_DETAIL_LINE_SPACING, line.tooltip());
            lineIndex++;
        }
        return lineIndex;
    }

    private int drawFluids(GuiGraphics graphics, List<FluidStorageEntry> entries, int lineIndex) {
        if (entries.isEmpty()) {
            if (isTextLineVisible(lineIndex)) {
                int y = textLineY(visibleTextRow(lineIndex));
                graphics.drawString(font, emptySectionLine("gui.mmcr.port.fluids"),
                        (int) (ROW_X / TEXT_DETAIL_SCALE), (int) (y / TEXT_DETAIL_SCALE),
                        0xFF55FF55, false);
            }
            return lineIndex + 1;
        }
        if (isTextLineVisible(lineIndex)) {
            int y = textLineY(visibleTextRow(lineIndex));
            graphics.drawString(font, sectionLabel("gui.mmcr.port.fluids"),
                    (int) (ROW_X / TEXT_DETAIL_SCALE), (int) (y / TEXT_DETAIL_SCALE),
                    0xFF55FF55, false);
        }
        lineIndex++;
        for (FluidStorageEntry entry : entries) {
            if (!isTextLineVisible(lineIndex)) {
                lineIndex++;
                continue;
            }
            ControllerTextLine line = fluidRenderLine(entry);
            int y = textLineY(visibleTextRow(lineIndex));
            renderTextLine(graphics, line, (int) (ROW_X / TEXT_DETAIL_SCALE),
                    (int) (y / TEXT_DETAIL_SCALE));
            addTooltip(leftPos + ROW_X, topPos + y,
                    (int) ((line.textXOffset() + font.width(line.text())) * TEXT_DETAIL_SCALE),
                    TEXT_DETAIL_LINE_SPACING, line.tooltip());
            lineIndex++;
        }
        return lineIndex;
    }

    private static MutableComponent sectionLabel(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.GREEN);
    }

    private static Component emptySectionLine(String key) {
        return sectionLabel(key).append(Component.literal(" "))
                .append(Component.translatable("gui.mmcr.port.empty").withStyle(ChatFormatting.GREEN));
    }

    private static Component itemLine(ItemStorageEntry entry) {
        return Component.literal(ReadableNumber.format(entry.amount()) + " ")
                .append(entry.resource().getHoverName());
    }

    private static Component fluidLine(FluidStorageEntry entry) {
        return Component.literal(ReadableNumber.format(entry.amount()) + " ")
                .append(entry.resource().getHoverName());
    }

    private static ControllerTextLine itemRenderLine(ItemStorageEntry entry) {
        return new ControllerTextLine(itemLine(entry), TEXT_COLOR,
                new ControllerTextLine.ItemIcon(entry.resource()),
                ExtendedItemScreen.tooltipLines(entry));
    }

    private static ControllerTextLine fluidRenderLine(FluidStorageEntry entry) {
        return new ControllerTextLine(fluidLine(entry), TEXT_COLOR,
                new ControllerTextLine.FluidIcon(entry.resource().copyWithAmount(1)),
                ExtendedFluidScreen.tooltipLines(entry));
    }

    private static List<ItemStorageEntry> nonEmptyItems(List<ItemStorageEntry> entries) {
        return entries.stream().filter(entry -> entry.amount() > 0 && !entry.resource().isEmpty()).toList();
    }

    private static List<FluidStorageEntry> nonEmptyFluids(List<FluidStorageEntry> entries) {
        return entries.stream().filter(entry -> entry.amount() > 0 && !entry.resource().isEmpty()).toList();
    }
}
