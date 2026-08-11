package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Map;

/**
 * MMCE-style two-column factory controller display backed only by synchronized menu data.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FactoryControllerScreen extends AbstractContainerScreen<FactoryControllerMenu> {
    static final int IMAGE_WIDTH = 280;
    static final int IMAGE_HEIGHT = 213;
    static final int THREAD_ROW_X = 8;
    static final int THREAD_ROW_Y = 8;
    public static final int THREAD_ROW_WIDTH = 86;
    static final int THREAD_ROW_HEIGHT = 32;
    static final int THREAD_ROW_GAP = 1;
    static final int VISIBLE_THREADS = 6;
    static final int SCROLLBAR_X = 94;
    static final int SCROLLBAR_Y = 8;
    static final int SCROLLBAR_HEIGHT = 197;
    static final int SCROLLBAR_HANDLE_WIDTH = 12;
    static final int SCROLLBAR_HANDLE_HEIGHT = 16;
    private static final int ELEMENT_TEXTURE_WIDTH = 256;
    private static final int ELEMENT_TEXTURE_HEIGHT = 256;
    private static final int THREAD_ELEMENT_Y_OFFSET = 0;
    private static final int SCROLLBAR_HANDLE_COLOR = 0xFF000000;
    private static final int SELECTED_THREAD_OVERLAY = 0x66A8D8FF;
    private static final int DETAIL_LINE_SPACING = 14;
    private static final Identifier BACKGROUND = MMCR.id("textures/gui/guifactory.png");
    private static final Identifier ELEMENTS = MMCR.id("textures/gui/guifactoryelements.png");
    private int scrollOffset;
    private boolean draggingScrollbar;
    private int scrollbarDragOffsetY;

    public FactoryControllerScreen(FactoryControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
        titleLabelY = -1000;
        inventoryLabelY = -1000;
    }

    public static int defaultSelectedThread() { return 0; }

    static int threadIndexAt(int left, int top, int scroll, int mouseX, int mouseY) {
        if (mouseX < left || mouseX >= left + THREAD_ROW_WIDTH) return -1;
        int localY = mouseY - top;
        int rowStride = THREAD_ROW_HEIGHT + THREAD_ROW_GAP;
        int row = localY / rowStride;
        if (mouseY < top || row < 0 || localY % rowStride >= THREAD_ROW_HEIGHT) return -1;
        return scroll + row;
    }

    static int threadIndexAt(int left, int top, int scroll, int mouseX, int mouseY,
                             List<FactoryRecipeScheduler.ThreadSnapshot> threads) {
        int listIndex = threadIndexAt(left, top, scroll, mouseX, mouseY);
        return listIndex >= 0 && listIndex < threads.size() ? threads.get(listIndex).index() : -1;
    }

    public static int progressWidth(int tick, int totalTick) {
        if (tick <= 0 || totalTick <= 0) return 0;
        return Math.min(THREAD_ROW_WIDTH, tick * THREAD_ROW_WIDTH / totalTick);
    }

    static int selectedParallelism(FactoryControllerMenu menu) {
        return menu.currentParallelism();
    }

    static List<Component> levelLines(Map<?, MachineLevel> levels) {
        return levels.values().stream().map(MachineMenuScreen::levelLine).toList();
    }

    static String selectedFailureUnloc(FactoryControllerMenu menu) {
        String threadFailure = menu.selectedThread().lastFailureUnloc();
        return threadFailure.isEmpty() ? menu.lastFailureUnloc() : threadFailure;
    }

    static int elementTextureWidth() { return ELEMENT_TEXTURE_WIDTH; }
    static int elementTextureHeight() { return ELEMENT_TEXTURE_HEIGHT; }
    static int threadElementY(int y) { return y + THREAD_ELEMENT_Y_OFFSET; }
    static int selectedOverlayX(int x) { return x; }
    static int selectedOverlayY(int y) { return threadElementY(y); }
    static int selectedOverlayWidth() { return THREAD_ROW_WIDTH; }
    static int selectedOverlayHeight() { return THREAD_ROW_HEIGHT; }
    static int selectedOverlayRight(int x) { return selectedOverlayX(x) + selectedOverlayWidth() - 1; }
    static int selectedOverlayBottom(int y) { return selectedOverlayY(y) + selectedOverlayHeight() - 1; }
    static int progressOverlayX(int x) { return x; }
    static int progressOverlayY(int y) { return threadElementY(y); }
    static int progressOverlayHeight() { return THREAD_ROW_HEIGHT - 1; }
    static int progressOverlayRight(int x, int progress) { return progressOverlayX(x) + progress; }
    static int progressOverlayBottom(int y) { return progressOverlayY(y) + progressOverlayHeight(); }
    static int detailTitleY(int y) { return y; }
    static int nextDetailY(int y) { return y + DETAIL_LINE_SPACING; }
    static boolean shouldRenderProgress(boolean active, int totalTick) { return active && totalTick > 0; }
    static int visibleThreadCount(int threadCount) { return Math.min(VISIBLE_THREADS, Math.max(0, threadCount)); }
    static int clampScrollOffset(int scrollOffset, int threadCount) {
        return Math.max(0, Math.min(Math.max(0, threadCount - VISIBLE_THREADS), scrollOffset));
    }
    static boolean shouldRenderScrollbar(int threadCount) { return threadCount > VISIBLE_THREADS; }
    static int scrollbarHandleY(int scrollOffset, int threadCount) {
        int range = Math.max(0, threadCount - VISIBLE_THREADS);
        if (range == 0) return SCROLLBAR_Y;
        int availableHeight = SCROLLBAR_HEIGHT - SCROLLBAR_HANDLE_HEIGHT;
        return SCROLLBAR_Y + clampScrollOffset(scrollOffset, threadCount) * availableHeight / range;
    }
    static int scrollOffsetFromScrollbarY(int scrollbarY, int threadCount, int dragOffsetY) {
        int range = Math.max(0, threadCount - VISIBLE_THREADS);
        if (range == 0) return 0;
        int availableHeight = SCROLLBAR_HEIGHT - SCROLLBAR_HANDLE_HEIGHT;
        int handleY = Math.max(0, Math.min(availableHeight, scrollbarY - SCROLLBAR_Y - dragOffsetY));
        return clampScrollOffset(Math.round((float) handleY * range / availableHeight), threadCount);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0, 0,
                IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_HEIGHT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        scrollOffset = clampScrollOffset(scrollOffset, menu.threads().size());
        int visibleThreadCount = visibleThreadCount(menu.threads().size());
        for (int row = 0; row < visibleThreadCount; row++) {
            int index = scrollOffset + row;
            if (index >= menu.threads().size()) break;
            FactoryRecipeScheduler.ThreadSnapshot thread = menu.threads().get(index);
            int y = topPos + THREAD_ROW_Y + row * (THREAD_ROW_HEIGHT + THREAD_ROW_GAP);
            int elementX = leftPos + THREAD_ROW_X;
            int selectedOverlayX = selectedOverlayX(elementX);
            int selectedOverlayY = selectedOverlayY(y);
            int progressOverlayX = progressOverlayX(elementX);
            int progressOverlayY = progressOverlayY(y);
            graphics.blit(RenderPipelines.GUI_TEXTURED, ELEMENTS, leftPos + THREAD_ROW_X, threadElementY(y), 0, 0,
                    THREAD_ROW_WIDTH, THREAD_ROW_HEIGHT, ELEMENT_TEXTURE_WIDTH, ELEMENT_TEXTURE_HEIGHT);
            if (thread.index() == menu.selectedThread().index()) {
                graphics.fill(selectedOverlayX, selectedOverlayY,
                        selectedOverlayRight(elementX), selectedOverlayBottom(y), SELECTED_THREAD_OVERLAY);
            }
            int progress = progressWidth(thread.tick(), thread.totalTick());
            if (progress > 0) graphics.fill(progressOverlayX, progressOverlayY,
                    progressOverlayRight(elementX, progress), progressOverlayBottom(y), 0x6600AA55);
            graphics.text(font, Component.translatable("gui.mmcr.factory.thread", thread.index()), leftPos + THREAD_ROW_X + 3, y + 3, 0xFF222222, false);
            graphics.text(font, Component.translatable(thread.active() ? "gui.mmcr.controller.running" : "gui.mmcr.controller.idle"), leftPos + THREAD_ROW_X + 3, y + 15, 0xFF222222, false);
        }
        if (shouldRenderScrollbar(menu.threads().size())) {
            int scrollbarX = leftPos + SCROLLBAR_X;
            int scrollbarY = topPos + scrollbarHandleY(scrollOffset, menu.threads().size());
            graphics.fill(scrollbarX, scrollbarY,
                    scrollbarX + SCROLLBAR_HANDLE_WIDTH, scrollbarY + SCROLLBAR_HANDLE_HEIGHT,
                    SCROLLBAR_HANDLE_COLOR);
        }
        FactoryRecipeScheduler.ThreadSnapshot selected = menu.selectedThread();
        int x = leftPos + 113;
        int y = topPos + 12;
        String machineName = menu.machineName().isEmpty() ? title.getString() : menu.machineName();
        graphics.text(font, Component.literal(machineName + " #" + selected.index()), x, detailTitleY(y), MachineMenuScreen.CONTROLLER_TITLE_COLOR, true);
        int lineY = nextDetailY(y);
        graphics.text(font, Component.translatable("gui.mmcr.controller.status_label"), x, lineY, MachineMenuScreen.STATUS_LABEL_COLOR, true);
        graphics.text(font, Component.translatable(MachineMenuScreen.controllerStatusKey(menu.isFormed(), selected.active())),
                x + font.width(Component.translatable("gui.mmcr.controller.status_label")) + 4, lineY,
                MachineMenuScreen.controllerStatusColor(menu.isFormed(), selected.active()), true);
        lineY = nextDetailY(lineY);
        var owner = menu.resolvedOwner();
        if (owner != null) {
            for (Component levelLine : levelLines(owner.getFoundLevels())) {
                graphics.text(font, levelLine, x, lineY, MachineMenuScreen.STATUS_LABEL_COLOR, true);
                lineY = nextDetailY(lineY);
            }
        }
        String failure = selectedFailureUnloc(menu);
        if (!failure.isEmpty()) {
            graphics.text(font, Component.translatable("gui.mmcr.controller.last_failure", Component.translatable(failure)),
                    x, lineY, MachineMenuScreen.STATUS_LABEL_COLOR, true);
            lineY = nextDetailY(lineY);
        }
        if (menu.parallelSlots() > 0) {
            graphics.text(font, MachineMenuScreen.parallelSlotLine(menu.parallelSlots()), x, lineY, MachineMenuScreen.STATUS_LABEL_COLOR, true);
            lineY = nextDetailY(lineY);
        }
        graphics.text(font, MachineMenuScreen.parallelLine(selectedParallelism(menu), menu.maxParallelism()), x, lineY, MachineMenuScreen.STATUS_LABEL_COLOR, true);
        lineY = nextDetailY(lineY);
        if (menu.isRedstonePaused()) {
            graphics.text(font, Component.translatable("gui.mmcr.controller.redstone_stopped"), x, lineY, MachineMenuScreen.STATUS_LABEL_COLOR, true);
            lineY = nextDetailY(lineY);
        }
        graphics.text(font, MachineMenuScreen.factoryThreadLine(menu.activeThreadCount(), menu.threadCount()), x, lineY, MachineMenuScreen.STATUS_LABEL_COLOR, true);
        lineY = nextDetailY(lineY);
        if (selected.totalTick() > 0) {
            int percent = progressWidth(selected.tick(), selected.totalTick()) * 100 / THREAD_ROW_WIDTH;
            graphics.text(font, Component.translatable("gui.mmcr.controller.progress", percent + "%"), x, lineY,
                    MachineMenuScreen.PROGRESS_STATUS_COLOR, true);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            if (mouseOverScrollbar((int) event.x(), (int) event.y())) {
                draggingScrollbar = true;
                scrollbarDragOffsetY = Math.max(0, Math.min(SCROLLBAR_HANDLE_HEIGHT,
                        (int) event.y() - topPos - scrollbarHandleY(scrollOffset, menu.threads().size())));
                scrollOffset = scrollOffsetFromScrollbarY((int) event.y() - topPos, menu.threads().size(), scrollbarDragOffsetY);
                return true;
            }
            int threadIndex = threadIndexAt(leftPos + THREAD_ROW_X, topPos + THREAD_ROW_Y, scrollOffset,
                    (int) event.x(), (int) event.y(), menu.threads());
            if (threadIndex >= 0) {
                menu.selectThread(threadIndex);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (draggingScrollbar) {
            scrollOffset = scrollOffsetFromScrollbarY((int) event.y() - topPos, menu.threads().size(), scrollbarDragOffsetY);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scrollOffset = clampScrollOffset(scrollOffset - (int) Math.signum(deltaY), menu.threads().size());
        return true;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private boolean mouseOverScrollbar(int mouseX, int mouseY) {
        return shouldRenderScrollbar(menu.threads().size())
                && mouseX >= leftPos + SCROLLBAR_X
                && mouseX < leftPos + SCROLLBAR_X + SCROLLBAR_HANDLE_WIDTH
                && mouseY >= topPos + SCROLLBAR_Y
                && mouseY < topPos + SCROLLBAR_Y + SCROLLBAR_HEIGHT;
    }
}
