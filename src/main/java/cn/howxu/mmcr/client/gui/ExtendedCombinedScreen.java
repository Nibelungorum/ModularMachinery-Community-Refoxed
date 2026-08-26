package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.ExtendedCombinedMenu;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.FluidStorageEntry;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.ItemStorageEntry;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.util.ReadableNumber;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
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
    private static final Identifier TEXTURE = MMCR.id(TEXTURE_PATH);
    private static final Identifier AUTO_IO_TEXTURE = MMCR.id("textures/gui/guismartinterface.png");
    private static final int GUI_TEXTURE_SIZE = 256;
    private static final int IMAGE_HEIGHT = 213;
    private static final int TITLE_X = 12;
    private static final int TITLE_Y = 12;
    private static final int ROW_X = 12;
    private static final int FIRST_ROW_Y = 24;
    private static final int ROW_STEP = 10;
    private static final int TEXT_COLOR = 0xFFE0E0E0;

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
    protected Identifier texture(boolean autoIOPage) {
        return autoIOPage ? AUTO_IO_TEXTURE : TEXTURE;
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
        int y = drawItems(graphics, menu.itemEntries(), FIRST_ROW_Y);
        drawFluids(graphics, menu.fluidEntries(), y);
        graphics.pose().popMatrix();
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

    private int drawItems(GuiGraphicsExtractor graphics, List<ItemStorageEntry> entries, int y) {
        List<ItemStorageEntry> nonEmpty = nonEmptyItems(entries);
        if (nonEmpty.isEmpty()) {
            graphics.text(font, emptySectionLine("gui.mmcr.port.items"),
                    (int) (ROW_X / TEXT_DETAIL_SCALE), (int) (y / TEXT_DETAIL_SCALE),
                    0xFF55FF55, false);
            return y + ROW_STEP;
        }
        graphics.text(font, sectionLabel("gui.mmcr.port.items"),
                (int) (ROW_X / TEXT_DETAIL_SCALE), (int) (y / TEXT_DETAIL_SCALE),
                0xFF55FF55, false);
        y += ROW_STEP;
        for (ItemStorageEntry entry : nonEmpty) {
            Component line = itemLine(entry);
            graphics.text(font, line, (int) (ROW_X / TEXT_DETAIL_SCALE),
                    (int) (y / TEXT_DETAIL_SCALE), TEXT_COLOR, false);
            addTooltip(leftPos + ROW_X, topPos + y, (int) (font.width(line) * TEXT_DETAIL_SCALE),
                    TEXT_DETAIL_LINE_SPACING,
                    ExtendedItemScreen.tooltipLines(entry));
            y += TEXT_DETAIL_LINE_SPACING;
        }
        return y;
    }

    private int drawFluids(GuiGraphicsExtractor graphics, List<FluidStorageEntry> entries, int y) {
        List<FluidStorageEntry> nonEmpty = nonEmptyFluids(entries);
        if (nonEmpty.isEmpty()) {
            graphics.text(font, emptySectionLine("gui.mmcr.port.fluids"),
                    (int) (ROW_X / TEXT_DETAIL_SCALE), (int) (y / TEXT_DETAIL_SCALE),
                    0xFF55FF55, false);
            return y + ROW_STEP;
        }
        graphics.text(font, sectionLabel("gui.mmcr.port.fluids"),
                (int) (ROW_X / TEXT_DETAIL_SCALE), (int) (y / TEXT_DETAIL_SCALE),
                0xFF55FF55, false);
        y += ROW_STEP;
        for (FluidStorageEntry entry : nonEmpty) {
            Component line = fluidLine(entry);
            graphics.text(font, line, (int) (ROW_X / TEXT_DETAIL_SCALE),
                    (int) (y / TEXT_DETAIL_SCALE), TEXT_COLOR, false);
            addTooltip(leftPos + ROW_X, topPos + y, (int) (font.width(line) * TEXT_DETAIL_SCALE),
                    TEXT_DETAIL_LINE_SPACING,
                    ExtendedFluidScreen.tooltipLines(entry));
            y += TEXT_DETAIL_LINE_SPACING;
        }
        return y;
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

    private static List<ItemStorageEntry> nonEmptyItems(List<ItemStorageEntry> entries) {
        return entries.stream().filter(entry -> entry.amount() > 0 && !entry.resource().isEmpty()).toList();
    }

    private static List<FluidStorageEntry> nonEmptyFluids(List<FluidStorageEntry> entries) {
        return entries.stream().filter(entry -> entry.amount() > 0 && !entry.resource().isEmpty()).toList();
    }
}
