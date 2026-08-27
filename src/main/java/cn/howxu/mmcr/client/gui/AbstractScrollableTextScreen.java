package cn.howxu.mmcr.client.gui;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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

    protected AbstractScrollableTextScreen(M menu, Inventory inventory,
                                           Component title, int imageWidth, int imageHeight) {
        super(menu, inventory, title, imageWidth, imageHeight);
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

    static boolean containsViewport(TextViewport viewport, int left, int top, double mouseX, double mouseY) {
        double viewportLeft = left + viewport.x();
        double viewportTop = top + viewport.y();
        return mouseX >= viewportLeft && mouseX < viewportLeft + viewport.width()
                && mouseY >= viewportTop && mouseY < viewportTop + viewport.height();
    }

    protected abstract TextViewport scrollableTextViewport();

    protected List<ControllerTextLine> scrollableTextLines() {
        return List.of();
    }

    protected final List<ControllerScreenTextComposer.VisualLine> wrappedTextLines() {
        TextViewport viewport = scrollableTextViewport();
        List<ControllerScreenTextComposer.VisualLine> lines = ControllerScreenTextComposer.wrap(
                font, scrollableTextLines(), viewport.width());
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        TextViewport viewport = scrollableTextViewport();
        int lineCount = scrollableTextLineCount();
        int visibleLines = visibleLineCount(viewport.height(), viewport.scale(),
                viewport.lineSpacing(), font.lineHeight);
        if (containsViewport(viewport, leftPos, topPos, mouseX, mouseY)) {
            if (!hasScrollableOverflow(lineCount, visibleLines)) return false;
            textScrollOffset = scrollOffsetAfter(textScrollOffset, lineCount, visibleLines, deltaY);
            return true;
        }
        return handleAdditionalScroll(mouseX, mouseY, deltaX, deltaY)
                || super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }
}
