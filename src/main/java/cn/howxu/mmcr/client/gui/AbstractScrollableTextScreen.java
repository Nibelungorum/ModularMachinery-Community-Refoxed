package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.client.render.ChemicalGuiRenderer;
import cn.howxu.mmcr.client.render.FluidGuiRenderer;
import cn.howxu.mmcr.compat.ars_nouveau.client.SourceGuiRenderer;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;

/**
 * Shared scrolling behavior for screens that render text in a GUI viewport.
 *
 * @param <M> the menu displayed by this screen
 * @author howxu <dev@howxu.cn>
 */
abstract class AbstractScrollableTextScreen<M extends AbstractContainerMenu>
        extends AbstractContainerScreen<M> {

    protected record TextViewport(int x, int y, int width, int height,
                                  float scale, int lineSpacing) {
    }

    private int textScrollOffset;
    private boolean frameActive;
    private List<ControllerScreenTextComposer.VisualLine> frameLines;

    protected AbstractScrollableTextScreen(M menu, Inventory inventory,
                                           Component title, int imageWidth, int imageHeight) {
        super(menu, inventory, title);
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.inventoryLabelY = imageHeight - 94;
    }

    static int visibleLineCount(int viewportHeight, float scale, int lineSpacing, int fontLineHeight) {
        int scaledHeight = (int) Math.floor(viewportHeight * scale);
        int scaledFontHeight = Math.max(1, (int) Math.ceil(fontLineHeight * scale));
        double rowStride = Math.max(1.0, Math.max(lineSpacing, fontLineHeight) * scale);
        return Math.max(1, 1 + (int) Math.floor((scaledHeight - scaledFontHeight) / rowStride));
    }

    static int maxScrollOffset(int lineCount, int visibleLineCount) {
        return Math.max(0, lineCount - visibleLineCount);
    }

    static boolean hasScrollableOverflow(int lineCount, int visibleLineCount) {
        return lineCount > visibleLineCount;
    }

    static int clampScrollOffset(int offset, int lineCount, int visibleLineCount) {
        return Math.min(Math.max(0, offset), maxScrollOffset(lineCount, visibleLineCount));
    }

    static int scrollOffsetAfter(int offset, int lineCount, int visibleLineCount, double deltaY) {
        return clampScrollOffset(offset - (int) Math.signum(deltaY), lineCount, visibleLineCount);
    }

    static int scrollOffsetAfter(int offset, int lineCount, int visibleLineCount, double deltaY,
                                 boolean pageScroll) {
        int step = pageScroll ? visibleLineCount : 1;
        return clampScrollOffset(offset - (int) Math.signum(deltaY) * step, lineCount, visibleLineCount);
    }

    static boolean containsViewport(TextViewport viewport, int left, int top, double mouseX, double mouseY) {
        double viewportLeft = left + viewport.x();
        double viewportTop = top + viewport.y();
        return mouseX >= viewportLeft && mouseX < viewportLeft + viewport.width()
                && mouseY >= viewportTop && mouseY < viewportTop + viewport.height();
    }

    static boolean containsTextLine(int x, int y, int textXOffset, int textWidth,
                                    float scale, int lineSpacing, double mouseX, double mouseY) {
        int width = (int) ((textXOffset + textWidth) * scale);
        return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + lineSpacing;
    }

    static int textLineIndexAt(TextViewport viewport, int top, int firstVisibleLine, double mouseY) {
        if (mouseY < top + viewport.y()) return -1;
        int row = (int) ((mouseY - top - viewport.y()) / viewport.lineSpacing());
        return firstVisibleLine + row;
    }

    protected abstract TextViewport scrollableTextViewport();

    protected List<ControllerTextLine> scrollableTextLines() {
        return List.of();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        frameLines = null;
        frameActive = true;
        try {
            super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        } finally {
            frameActive = false;
            frameLines = null;
        }
    }

    protected final List<ControllerScreenTextComposer.VisualLine> wrappedTextLines() {
        TextViewport viewport = scrollableTextViewport();
        List<ControllerScreenTextComposer.VisualLine> lines;
        if (frameActive && frameLines != null) {
            lines = frameLines;
        } else {
            lines = ControllerScreenTextComposer.wrap(font, scrollableTextLines(), viewport.width());
            if (frameActive) frameLines = lines;
        }
        textScrollOffset = clampScrollOffset(textScrollOffset, lines.size(), visibleLineCount(
                viewport.height(), viewport.scale(), viewport.lineSpacing(), font.lineHeight));
        return lines;
    }

    protected int scrollableTextLineCount() {
        return wrappedTextLines().size();
    }

    protected boolean handleAdditionalScroll(double mouseX, double mouseY,
                                             double deltaX, double deltaY) {
        return false;
    }

    protected final int visibleTextLineCount() {
        TextViewport viewport = scrollableTextViewport();
        return visibleLineCount(viewport.height(), viewport.scale(), viewport.lineSpacing(), font.lineHeight);
    }

    protected final int firstVisibleTextLine() {
        clampTextScrollOffset();
        return textScrollOffset;
    }

    protected final int lastVisibleTextLineExclusive() {
        int firstLine = firstVisibleTextLine();
        return Math.min(scrollableTextLineCount(), firstLine + visibleTextLineCount());
    }

    protected final boolean isTextLineVisible(int lineIndex) {
        return lineIndex >= firstVisibleTextLine() && lineIndex < lastVisibleTextLineExclusive();
    }

    protected final int visibleTextRow(int lineIndex) {
        return lineIndex - firstVisibleTextLine();
    }

    protected final int textLineY(int visibleRow) {
        TextViewport viewport = scrollableTextViewport();
        return viewport.y() + visibleRow * viewport.lineSpacing();
    }

    protected final void clampTextScrollOffset() {
        textScrollOffset = clampScrollOffset(textScrollOffset, scrollableTextLineCount(), visibleTextLineCount());
    }

    protected final void resetTextScrollOffset() {
        textScrollOffset = 0;
    }

    protected final void renderVisualLine(GuiGraphics graphics,
                                           ControllerScreenTextComposer.VisualLine line, int x, int y) {
        ControllerTextLine source = line.source();
        if (line.firstSegment() && source.icon() != null) {
            renderIcon(graphics, source.icon(), x + source.leftIndent(), y, 9);
        }
        graphics.drawString(font, line.text(), x + line.textXOffset(), y, line.color(), false);
    }

    protected final void renderTextLine(GuiGraphics graphics, ControllerTextLine line, int x, int y) {
        if (line.icon() != null) renderIcon(graphics, line.icon(), x + line.leftIndent(), y, 9);
        graphics.drawString(font, line.text(), x + line.textXOffset(), y, line.color(), false);
    }

    protected final void renderScrollableTooltip(GuiGraphics graphics, int mouseX, int mouseY,
                                                  int textX) {
        TextViewport viewport = scrollableTextViewport();
        if (!containsViewport(viewport, leftPos, topPos, mouseX, mouseY)) return;
        int firstVisibleLine = firstVisibleTextLine();
        int index = textLineIndexAt(viewport, topPos, firstVisibleLine, mouseY);
        int row = index - firstVisibleLine;
        List<ControllerScreenTextComposer.VisualLine> lines = wrappedTextLines();
        if (row < 0 || index < 0 || index >= lines.size()) return;
        ControllerScreenTextComposer.VisualLine line = lines.get(index);
        if (!containsTextLine(leftPos + textX, topPos + viewport.y() + row * viewport.lineSpacing(),
                line.textXOffset(), font.width(line.text()), viewport.scale(), viewport.lineSpacing(),
                mouseX, mouseY)) return;
        List<Component> tooltip = line.source().tooltip();
        if (!tooltip.isEmpty()) graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    protected static void renderIcon(GuiGraphics graphics, ControllerTextLine.Icon icon,
                                      int x, int y, int size) {
        switch (icon) {
            case ControllerTextLine.ItemIcon item -> {
                graphics.pose().pushPose();
                graphics.pose().translate(x, y, 0);
                graphics.pose().scale(size / 16F, size / 16F, 1F);
                graphics.renderFakeItem(item.stack(), 0, 0);
                graphics.pose().popPose();
            }
            case ControllerTextLine.FluidIcon fluid ->
                    FluidGuiRenderer.drawFluid(graphics, fluid.stack(), x, y, size, size);
            case ControllerTextLine.SourceIcon source ->
                    SourceGuiRenderer.drawSource(graphics, x, y, size, size);
            case ControllerTextLine.ChemicalIcon chemical -> {
                MekanismBridge.ChemicalRenderData data = MekanismBridge.get().chemicalRenderData(chemical.chemicalId());
                if (data != null) {
                    ChemicalGuiRenderer.drawChemical(graphics,
                            new ChemicalGuiRenderer.ChemicalRenderState(data.spriteLocation(), data.tint(), size),
                            x, y, size, size);
                }
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        TextViewport viewport = scrollableTextViewport();
        int lineCount = scrollableTextLineCount();
        int visibleLines = visibleLineCount(viewport.height(), viewport.scale(),
                viewport.lineSpacing(), font.lineHeight);
        if (containsViewport(viewport, leftPos, topPos, mouseX, mouseY)) {
            if (!hasScrollableOverflow(lineCount, visibleLines)) return false;
            textScrollOffset = scrollOffsetAfter(textScrollOffset, lineCount, visibleLines, deltaY,
                    minecraft != null && Screen.hasShiftDown());
            return true;
        }
        return handleAdditionalScroll(mouseX, mouseY, deltaX, deltaY)
                || super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }
}
