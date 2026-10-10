package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Lane;
import cn.howxu.mmcr.client.controller.ControllerScreenTextCache;
import cn.howxu.mmcr.client.controller.ui.ControllerUiClientEvents;
import cn.howxu.mmcr.config.ClientConfig;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * MMCE-style two-column factory controller display backed by the owned session snapshot.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FactoryControllerScreen extends AbstractScrollableTextScreen<FactoryControllerMenu> {
    private static final int CONTROLLER_TITLE_COLOR = ControllerTextLine.DEFAULT_COLOR;
    static final int IMAGE_WIDTH = 280;
    static final int IMAGE_HEIGHT = 216;
    static final int THREAD_ROW_X = 8;
    static final int THREAD_ROW_Y = 9;
    public static final int THREAD_ROW_WIDTH = 86;
    static final int THREAD_ROW_HEIGHT = 32;
    static final int THREAD_ROW_GAP = 1;
    static final int VISIBLE_THREADS = 6;
    private static final int THREAD_OUTPUT_ICON_SIZE = 16;
    private static final int THREAD_OUTPUT_ICON_PADDING = 2;
    private static final int THREAD_PROGRESS_TEXT_Y_OFFSET = 21;
    private static final float THREAD_PROGRESS_TEXT_SCALE = 0.7F;
    private static final int THREAD_TEXT_COLOR = 0xFF222222;
    static final int SCROLLBAR_X = 95;
    static final int SCROLLBAR_Y = 9;
    static final int SCROLLBAR_HEIGHT = 197;
    static final int SCROLLBAR_HANDLE_WIDTH = 12;
    static final int SCROLLBAR_HANDLE_HEIGHT = 32;
    private static final int ELEMENT_TEXTURE_WIDTH = 256;
    private static final int ELEMENT_TEXTURE_HEIGHT = 256;
    private static final int THREAD_ELEMENT_Y_OFFSET = 0;
    static final int PROGRESS_THREAD_OVERLAY = 0x6600AA55;
    private static final int DETAIL_LINE_SPACING = 10;
    private static final int DETAIL_X = 115;
    private static final float DETAIL_TEXT_SCALE = 0.85F;
    private static final float THREAD_TEXT_SCALE = 0.85F;
    static final int RECIPE_POOL_BUTTON_X = 258;
    static final int RECIPE_POOL_BUTTON_Y = 9;
    private static final Identifier BACKGROUND = MMCR.id("textures/gui/guifactory.png");
    private static final Identifier ELEMENTS = MMCR.id("textures/gui/guifactoryelements.png");
    private static final Identifier SELECTED_ELEMENTS = MMCR.id("textures/gui/guifactoryelements_selected.png");
    private static final Identifier SCROLLER = MMCR.id("textures/gui/scroller.png");
    private int scrollOffset;
    private boolean draggingScrollbar;
    private int scrollbarDragOffsetY;
    private StyledButton recipePoolButton;
    private final Supplier<ControllerUiSnapshot> snapshot;
    private String selectedLaneId;

    public FactoryControllerScreen(FactoryControllerMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, ControllerUiClientEvents.sessionFor(menu, title)::snapshot);
    }

    FactoryControllerScreen(FactoryControllerMenu menu, Inventory inventory, Component title,
                            Supplier<ControllerUiSnapshot> snapshot) {
        super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
        this.snapshot = snapshot;
        titleLabelY = -1000;
        inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        recipePoolButton = addRenderableWidget(new StyledButton(
                leftPos + RECIPE_POOL_BUTTON_X, topPos + RECIPE_POOL_BUTTON_Y, 12, 12,
                Component.literal("M"), button -> {
                    ControllerUiSnapshot value = snapshot.get();
                    minecraft.setScreen(new RecipePoolScreen(this, value.controllerPos(), value.recipePoolIds(),
                            value.currentRecipePoolId().orElse(null)));
                }));
        recipePoolButton.setTooltip(Tooltip.create(Component.translatable("gui.mmcr.recipe_pool.open")));
        updateRecipePoolButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateRecipePoolButton();
    }

    private void updateRecipePoolButton() {
        List<Identifier> recipePoolIds = snapshot.get().recipePoolIds();
        if (recipePoolButton != null) recipePoolButton.visible = recipePoolIds.size() > 1;
    }

    @Override
    protected TextViewport scrollableTextViewport() {
        int bodyY = 12 + DETAIL_LINE_SPACING;
        return new TextViewport(DETAIL_X, bodyY, 160, 123 - bodyY + 1,
                DETAIL_TEXT_SCALE, DETAIL_LINE_SPACING);
    }

    @Override
    protected List<ControllerTextLine> scrollableTextLines() {
        ControllerUiSnapshot value = snapshot.get();
        reconcileSelectedLane(value);
        return ControllerUiTextLines.create(value, selectedLaneId);
    }

    void reconcileSelectedLane(ControllerUiSnapshot value) {
        String next = ControllerUiTextLines.selectedLane(value, selectedLaneId).map(Lane::id).orElse(null);
        selectLane(next);
    }

    void selectLane(String laneId) {
        if (Objects.equals(selectedLaneId, laneId)) return;
        selectedLaneId = laneId;
        resetTextScrollOffset();
    }

    String selectedLaneId() { return selectedLaneId; }

    public static int defaultSelectedThread() { return 0; }

    static int threadIndexAt(int left, int top, int scroll, int mouseX, int mouseY) {
        if (mouseX < left || mouseX >= left + THREAD_ROW_WIDTH) return -1;
        int localY = mouseY - top;
        int rowStride = THREAD_ROW_HEIGHT + THREAD_ROW_GAP;
        int row = localY / rowStride;
        if (mouseY < top || row < 0 || row >= VISIBLE_THREADS
                || localY % rowStride >= THREAD_ROW_HEIGHT) return -1;
        return scroll + row;
    }

    static int threadIndexAt(int left, int top, int scroll, int mouseX, int mouseY,
                             List<FactoryRuntime.ThreadSnapshot> threads) {
        int listIndex = threadIndexAt(left, top, scroll, mouseX, mouseY);
        return listIndex >= 0 && listIndex < threads.size() ? threads.get(listIndex).index() : -1;
    }

    public static int progressWidth(int tick, int totalTick) {
        if (tick <= 0 || totalTick <= 0) return 0;
        return (int) Math.min(THREAD_ROW_WIDTH, (long) tick * THREAD_ROW_WIDTH / totalTick);
    }

    static int progressPercent(int tick, int totalTick) {
        if (tick <= 0 || totalTick <= 0) return 0;
        return (int) Math.min(100L, (long) tick * 100L / totalTick);
    }

    static Component matchedStageLine(int matchedStage) {
        return MachineControllerScreen.matchedStageLine(matchedStage);
    }

    static String selectedFailureUnloc(FactoryControllerMenu menu) {
        return ControllerUiTextLines.selectedFailureKey(menu.legacyUiSnapshot(), menu.selectedThread().laneId());
    }

    static List<ControllerTextLine> controllerTextLines(FactoryControllerMenu menu) {
        List<ControllerTextLine> lines = new ArrayList<>(ControllerScreenTextComposer.merge(detailLines(menu),
                ControllerScreenTextCache.linesAt(menu.controllerPos(), menu.selectedThread().laneId())));
        ControllerUiTextLines.selectedLane(menu.legacyUiSnapshot(), menu.selectedThread().laneId()).ifPresent(lane ->
                lines.addAll(ControllerRecipeTextLines.createSnapshot(lane.recipe())));
        return List.copyOf(lines);
    }

    static List<ControllerTextLine> detailLines(FactoryControllerMenu menu) {
        return ControllerUiTextLines.details(menu.legacyUiSnapshot(), menu.selectedThread().laneId());
    }

    static int elementTextureWidth() { return ELEMENT_TEXTURE_WIDTH; }
    static int elementTextureHeight() { return ELEMENT_TEXTURE_HEIGHT; }
    static int threadElementY(int y) { return y + THREAD_ELEMENT_Y_OFFSET; }
    static int progressOverlayX(int x) { return x; }
    static int progressOverlayY(int y) { return threadElementY(y); }
    static int progressOverlayHeight() { return THREAD_ROW_HEIGHT; }
    static int progressOverlayRight(int x, int progress) { return progressOverlayX(x) + progress; }
    static int progressOverlayBottom(int y) { return progressOverlayY(y) + progressOverlayHeight(); }
    static int detailTitleY(int y) { return y; }
    static int nextDetailY(int y) { return y + DETAIL_LINE_SPACING; }
    static int detailTextY(int localY) { return (int) (localY / DETAIL_TEXT_SCALE); }
    static int detailTextY(int screenTop, int localY) { return detailTextY(screenTop + localY); }
    static int detailLineY(int statusY, int statusLocalY, int localY) {
        return statusY + localY - statusLocalY;
    }
    static boolean shouldRenderProgress(boolean active, int totalTick) { return active && totalTick > 0; }
    static int visibleThreadCount(int threadCount) { return Math.min(VISIBLE_THREADS, Math.max(0, threadCount)); }
    static int clampScrollOffset(int scrollOffset, int threadCount) {
        return Math.max(0, Math.min(Math.max(0, threadCount - VISIBLE_THREADS), scrollOffset));
    }
    static boolean shouldRenderScrollbar(int threadCount) { return true; }
    static boolean isScrollbarInteractive(int threadCount) { return threadCount > VISIBLE_THREADS; }
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
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ControllerUiSnapshot value = snapshot.get();
        reconcileSelectedLane(value);
        List<Lane> threads = value.lanes();
        scrollOffset = clampScrollOffset(scrollOffset, threads.size());
        int visibleThreadCount = visibleThreadCount(threads.size());
        boolean showOutputIcon = ClientConfig.showFactoryThreadOutputIcon();
        boolean showRecipeProgress = ClientConfig.showFactoryThreadRecipeProgress();
        for (int row = 0; row < visibleThreadCount; row++) {
            int index = scrollOffset + row;
            if (index >= threads.size()) break;
            Lane thread = threads.get(index);
            int y = THREAD_ROW_Y + row * (THREAD_ROW_HEIGHT + THREAD_ROW_GAP);
            int elementX = THREAD_ROW_X;
            int progressOverlayX = progressOverlayX(elementX);
            int progressOverlayY = progressOverlayY(y);
            Identifier elements = thread.id().equals(selectedLaneId) ? SELECTED_ELEMENTS : ELEMENTS;
            graphics.blit(RenderPipelines.GUI_TEXTURED, elements, THREAD_ROW_X, threadElementY(y), 0, 0,
                    THREAD_ROW_WIDTH, THREAD_ROW_HEIGHT, ELEMENT_TEXTURE_WIDTH, ELEMENT_TEXTURE_HEIGHT);
            int progress = progressWidth(thread.tick(), thread.totalTick());
            if (progress > 0) graphics.fill(progressOverlayX, progressOverlayY,
                    progressOverlayRight(elementX, progress), progressOverlayBottom(y), PROGRESS_THREAD_OVERLAY);
            renderThreadText(graphics, Component.translatable("gui.mmcr.factory.thread", thread.index()),
                    THREAD_ROW_X + 3, y + 3);
            renderThreadText(graphics, Component.translatable(thread.active() ? "gui.mmcr.controller.running" : "gui.mmcr.controller.idle"),
                    THREAD_ROW_X + 3, y + 15);
            if (thread.active() && (showOutputIcon || showRecipeProgress)) {
                int iconX = elementX + THREAD_ROW_WIDTH - THREAD_OUTPUT_ICON_SIZE - THREAD_OUTPUT_ICON_PADDING;
                int elementY = threadElementY(y);
                if (showOutputIcon) {
                    ControllerRecipeTextLines.firstSnapshotOutputIcon(thread.recipe()).ifPresent(icon ->
                            renderIcon(graphics, icon, iconX,
                                    elementY + THREAD_OUTPUT_ICON_PADDING, THREAD_OUTPUT_ICON_SIZE));
                }
                if (showRecipeProgress && thread.totalTick() > 0) {
                    renderThreadProgress(graphics, progressPercent(thread.tick(), thread.totalTick()),
                            iconX, elementY + THREAD_PROGRESS_TEXT_Y_OFFSET);
                }
            }
        }
        if (shouldRenderScrollbar(threads.size())) {
            int scrollbarX = SCROLLBAR_X;
            int scrollbarY = scrollbarHandleY(scrollOffset, threads.size());
            graphics.blit(RenderPipelines.GUI_TEXTURED, SCROLLER, scrollbarX, scrollbarY, 0, 0,
                    SCROLLBAR_HANDLE_WIDTH, SCROLLBAR_HANDLE_HEIGHT, 32, 32);
        }
        Lane selected = ControllerUiTextLines.selectedLane(value, selectedLaneId).orElse(null);
        int x = DETAIL_X;
        int y = 12;
        graphics.pose().pushMatrix();
        graphics.pose().scale(DETAIL_TEXT_SCALE, DETAIL_TEXT_SCALE);
        x = (int) (x / DETAIL_TEXT_SCALE);
        y = (int) (y / DETAIL_TEXT_SCALE);
        graphics.text(font, detailTitle(value.machineName(), selected == null ? 0 : selected.index()), x, detailTitleY(y),
                CONTROLLER_TITLE_COLOR, false);
        List<ControllerScreenTextComposer.VisualLine> lines = wrappedTextLines();
        clampTextScrollOffset();
        int first = firstVisibleTextLine();
        int last = lastVisibleTextLineExclusive();
        for (int index = first; index < last; index++) {
            ControllerScreenTextComposer.VisualLine line = lines.get(index);
            int textY = detailTextY(textLineY(visibleTextRow(index)));
            renderVisualLine(graphics, line, x, textY);
        }
        renderScrollableTooltip(graphics, mouseX, mouseY, DETAIL_X);
        graphics.pose().popMatrix();
    }

    static Component detailTitle(Component title, String machineName, int threadIndex) {
        Component name = machineName.isEmpty() ? title : Component.translatable(machineName);
        return Component.empty().append(name).append(" #" + threadIndex);
    }

    static Component detailTitle(Component machineName, int threadIndex) {
        return Component.empty().append(machineName).append(" #" + threadIndex);
    }

    private void renderThreadText(GuiGraphicsExtractor graphics, Component text, int x, int y) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(THREAD_TEXT_SCALE, THREAD_TEXT_SCALE);
        graphics.text(font, text, 0, 0, THREAD_TEXT_COLOR, false);
        graphics.pose().popMatrix();
    }

    private void renderThreadProgress(GuiGraphicsExtractor graphics, int percent, int x, int y) {
        Component text = Component.literal(percent + "%");
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(THREAD_PROGRESS_TEXT_SCALE, THREAD_PROGRESS_TEXT_SCALE);
        int availableWidth = (int) (THREAD_OUTPUT_ICON_SIZE / THREAD_PROGRESS_TEXT_SCALE);
        graphics.text(font, text, (availableWidth - font.width(text)) / 2, 0, THREAD_TEXT_COLOR, false);
        graphics.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        ControllerUiSnapshot value = snapshot.get();
        reconcileSelectedLane(value);
        List<Lane> threads = value.lanes();
        scrollOffset = clampScrollOffset(scrollOffset, threads.size());
        if (event.button() == 0) {
            if (mouseOverScrollbar((int) event.x(), (int) event.y())) {
                draggingScrollbar = true;
                scrollbarDragOffsetY = Math.max(0, Math.min(SCROLLBAR_HANDLE_HEIGHT,
                        (int) event.y() - topPos - scrollbarHandleY(scrollOffset, threads.size())));
                scrollOffset = scrollOffsetFromScrollbarY((int) event.y() - topPos, threads.size(), scrollbarDragOffsetY);
                return true;
            }
            int threadIndex = threadIndexAt(leftPos + THREAD_ROW_X, topPos + THREAD_ROW_Y, scrollOffset,
                    (int) event.x(), (int) event.y());
            if (threadIndex >= 0 && threadIndex < threads.size()) {
                selectLane(threads.get(threadIndex).id());
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
            scrollOffset = scrollOffsetFromScrollbarY((int) event.y() - topPos, snapshot.get().lanes().size(), scrollbarDragOffsetY);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    protected boolean handleAdditionalScroll(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (!mouseOverThreadList((int) mouseX, (int) mouseY)) return false;
        scrollOffset = clampScrollOffset(scrollOffset - (int) Math.signum(deltaY), snapshot.get().lanes().size());
        return true;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private boolean mouseOverScrollbar(int mouseX, int mouseY) {
        return isScrollbarInteractive(snapshot.get().lanes().size())
                && mouseX >= leftPos + SCROLLBAR_X
                && mouseX < leftPos + SCROLLBAR_X + SCROLLBAR_HANDLE_WIDTH
                && mouseY >= topPos + SCROLLBAR_Y
                && mouseY < topPos + SCROLLBAR_Y + SCROLLBAR_HEIGHT;
    }

    private boolean mouseOverThreadList(int mouseX, int mouseY) {
        return mouseOverThreadList(leftPos, topPos, mouseX, mouseY);
    }

    static boolean mouseOverThreadList(int left, int top, int mouseX, int mouseY) {
        int listX = left + THREAD_ROW_X;
        int listY = top + THREAD_ROW_Y;
        int listHeight = VISIBLE_THREADS * THREAD_ROW_HEIGHT + (VISIBLE_THREADS - 1) * THREAD_ROW_GAP;
        return mouseX >= listX && mouseX < listX + THREAD_ROW_WIDTH
                && mouseY >= listY && mouseY < listY + listHeight;
    }

}
