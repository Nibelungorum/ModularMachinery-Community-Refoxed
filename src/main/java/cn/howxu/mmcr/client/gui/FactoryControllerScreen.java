package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.client.controller.ControllerScreenTextCache;
import cn.howxu.mmcr.config.ClientConfig;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.runtime.ControllerSyncRuntime;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.text.NumberFormat;

/**
 * MMCE-style two-column factory controller display backed only by synchronized menu data.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FactoryControllerScreen extends AbstractScrollableTextScreen<FactoryControllerMenu> {
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance();
    private static final ControllerSyncRuntime SYNC_RUNTIME = new ControllerSyncRuntime();
    private static final int CONTROLLER_TITLE_COLOR = ControllerTextLine.DEFAULT_COLOR;
    private static final int STATUS_LABEL_COLOR = CONTROLLER_TITLE_COLOR;
    private static final int FORMED_STATUS_COLOR = 0xFF55FF55;
    private static final int UNFORMED_STATUS_COLOR = 0xFFFF5555;
    private static final int IDLE_STATUS_COLOR = 0xFFFFAA00;
    private static final int PROGRESS_STATUS_COLOR = -1;
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
    private static final ResourceLocation BACKGROUND = MMCR.id("textures/gui/guifactory.png");
    private static final ResourceLocation ELEMENTS = MMCR.id("textures/gui/guifactoryelements.png");
    private static final ResourceLocation SELECTED_ELEMENTS = MMCR.id("textures/gui/guifactoryelements_selected.png");
    private static final ResourceLocation SCROLLER = MMCR.id("textures/gui/scroller.png");
    private int scrollOffset;
    private boolean draggingScrollbar;
    private int scrollbarDragOffsetY;
    private StyledButton recipePoolButton;

    public FactoryControllerScreen(FactoryControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
        titleLabelY = -1000;
        inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        recipePoolButton = addRenderableWidget(new StyledButton(
                leftPos + RECIPE_POOL_BUTTON_X, topPos + RECIPE_POOL_BUTTON_Y, 12, 12,
                Component.literal("M"), button -> minecraft.setScreen(
                        new RecipePoolScreen(this, menu.controllerPos(), menu.recipePoolIds(),
                                menu.currentRecipePoolId()))));
        recipePoolButton.setTooltip(Tooltip.create(Component.translatable("gui.mmcr.recipe_pool.open")));
        updateRecipePoolButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateRecipePoolButton();
    }

    private void updateRecipePoolButton() {
        List<ResourceLocation> recipePoolIds = menu.recipePoolIds();
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
        return controllerTextLines(menu);
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

    static long selectedParallelism(FactoryControllerMenu menu) {
        return menu.currentParallelism();
    }

    private static Component levelLine(MachineLevel level) {
        var type = MachineLevelRegistry.getType(level.typeId());
        if (type == null || !(level.statePredicate() instanceof BlockPredicate.OfBlockState(
                net.minecraft.world.level.block.state.BlockState state
        ))) return Component.empty();
        return Component.translatable("gui.mmcr.controller.level", type.displayName(), state.getBlock().getName());
    }

    private static String controllerStatusKey(boolean formed, boolean active) {
        if (!formed) return "gui.mmcr.controller.unformed";
        return active ? "gui.mmcr.controller.running" : "gui.mmcr.controller.idle";
    }

    private static int controllerStatusColor(boolean formed, boolean active) {
        if (!formed) return UNFORMED_STATUS_COLOR;
        return active ? FORMED_STATUS_COLOR : IDLE_STATUS_COLOR;
    }

    private static Component parallelSlotLine(int parallelSlots) {
        return Component.translatable("gui.mmcr.controller.parallel_slots", Component.literal(NUMBER_FORMAT.format(parallelSlots)));
    }

    static Component matchedStageLine(int matchedStage) {
        return Component.translatable("gui.mmcr.controller.matched_stage",
                Component.literal(NUMBER_FORMAT.format(matchedStage)));
    }

    private static Component parallelLine(long parallelism, long maxParallelism) {
        return Component.translatable("gui.mmcr.controller.parallel", Component.literal(NUMBER_FORMAT.format(parallelism)),
                Component.literal(NUMBER_FORMAT.format(maxParallelism)));
    }

    private static Component factoryThreadLine(int activeThreadCount, int threadCount) {
        return Component.translatable("gui.mmcr.controller.threads", Component.literal(NUMBER_FORMAT.format(activeThreadCount)),
                Component.literal(NUMBER_FORMAT.format(threadCount)));
    }

    static List<Component> levelLines(List<String> levelIds) {
        List<Component> lines = new ArrayList<>();
        for (String levelId : levelIds) {
            MachineLevel level = MachineLevelRegistry.getLevel(ResourceLocation.parse(levelId));
            if (level != null) lines.add(levelLine(level));
        }
        return List.copyOf(lines);
    }

    static String selectedFailureUnloc(FactoryControllerMenu menu) {
        FactoryRuntime.ThreadSnapshot selected = menu.selectedThread();
        String threadFailure = SYNC_RUNTIME.failureMessage(menu.selectedFailure());
        if (!threadFailure.isEmpty()) return threadFailure;
        return selected.active() ? "" : menu.lastFailureUnloc();
    }

    static List<ControllerTextLine> controllerTextLines(FactoryControllerMenu menu) {
        List<ControllerTextLine> lines = new ArrayList<>(ControllerScreenTextComposer.merge(detailLines(menu),
                ControllerScreenTextCache.linesAt(menu.controllerPos(), menu.selectedThread().laneId())));
        FactoryRuntime.ThreadSnapshot thread = menu.selectedThread();
        lines.addAll(ControllerRecipeTextLines.create(thread.presentation()));
        return List.copyOf(lines);
    }

    static List<ControllerTextLine> detailLines(FactoryControllerMenu menu) {
        List<ControllerTextLine> lines = new ArrayList<>();
        FactoryRuntime.ThreadSnapshot selected = menu.selectedThread();
        lines.add(new ControllerTextLine(Component.translatable("gui.mmcr.controller.status_label")
                        .append(Component.literal(" "))
                        .append(Component.translatable(controllerStatusKey(menu.isFormed(), selected.active()))),
                controllerStatusColor(menu.isFormed(), selected.active())));
        ResourceLocation recipePoolId = MachineControllerScreen.displayedRecipePoolId(
                menu.currentRecipePoolId(), menu.recipePoolIds());
        if (recipePoolId != null) {
            lines.add(new ControllerTextLine(Component.translatable("gui.mmcr.controller.recipe_pool",
                    RecipePoolDisplayName.component(recipePoolId)), STATUS_LABEL_COLOR));
        }
        if (menu.isFormed() && menu.matchedStage() > 0 && menu.stageCount() > 1) {
            lines.add(new ControllerTextLine(matchedStageLine(menu.matchedStage()), STATUS_LABEL_COLOR));
        }
        for (Component levelLine : levelLines(menu.foundLevelIds())) {
            lines.add(new ControllerTextLine(levelLine, STATUS_LABEL_COLOR));
        }
        String failure = selectedFailureUnloc(menu);
        if (!failure.isEmpty()) {
            lines.add(new ControllerTextLine(Component.translatable("gui.mmcr.controller.last_failure",
                    Component.translatable(failure)), STATUS_LABEL_COLOR));
        }
        lines.addAll(MachineControllerScreen.moduleStatusLines(false, menu.isModuleController(),
                0, menu.connectedHostId()));
        if (menu.parallelSlots() > 0) {
            lines.add(new ControllerTextLine(parallelSlotLine(menu.parallelSlots()), STATUS_LABEL_COLOR));
        }
        lines.add(new ControllerTextLine(parallelLine(selectedParallelism(menu), menu.maxParallelism()),
                STATUS_LABEL_COLOR));
        if (menu.isRedstonePaused()) {
            lines.add(new ControllerTextLine(Component.translatable("gui.mmcr.controller.redstone_stopped"),
                    STATUS_LABEL_COLOR));
        }
        lines.add(new ControllerTextLine(factoryThreadLine(menu.activeThreadCount(), menu.threadCount()),
                STATUS_LABEL_COLOR));
        if (selected.totalTick() > 0) {
            int percent = progressPercent(selected.tick(), selected.totalTick());
            lines.add(new ControllerTextLine(Component.translatable("gui.mmcr.controller.progress", percent + "%"),
                    PROGRESS_STATUS_COLOR));
        }
        return lines;
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
    public void extractRenderState(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        scrollOffset = clampScrollOffset(scrollOffset, menu.threads().size());
        int visibleThreadCount = visibleThreadCount(menu.threads().size());
        boolean showOutputIcon = ClientConfig.showFactoryThreadOutputIcon();
        boolean showRecipeProgress = ClientConfig.showFactoryThreadRecipeProgress();
        for (int row = 0; row < visibleThreadCount; row++) {
            int index = scrollOffset + row;
            if (index >= menu.threads().size()) break;
            FactoryRuntime.ThreadSnapshot thread = menu.threads().get(index);
            int y = topPos + THREAD_ROW_Y + row * (THREAD_ROW_HEIGHT + THREAD_ROW_GAP);
            int elementX = leftPos + THREAD_ROW_X;
            int progressOverlayX = progressOverlayX(elementX);
            int progressOverlayY = progressOverlayY(y);
            ResourceLocation elements = thread.index() == menu.selectedThread().index() ? SELECTED_ELEMENTS : ELEMENTS;
            graphics.blit(RenderPipelines.GUI_TEXTURED, elements, leftPos + THREAD_ROW_X, threadElementY(y), 0, 0,
                    THREAD_ROW_WIDTH, THREAD_ROW_HEIGHT, ELEMENT_TEXTURE_WIDTH, ELEMENT_TEXTURE_HEIGHT);
            int progress = progressWidth(thread.tick(), thread.totalTick());
            if (progress > 0) graphics.fill(progressOverlayX, progressOverlayY,
                    progressOverlayRight(elementX, progress), progressOverlayBottom(y), PROGRESS_THREAD_OVERLAY);
            renderThreadText(graphics, Component.translatable("gui.mmcr.factory.thread", thread.index()),
                    leftPos + THREAD_ROW_X + 3, y + 3);
            renderThreadText(graphics, Component.translatable(thread.active() ? "gui.mmcr.controller.running" : "gui.mmcr.controller.idle"),
                    leftPos + THREAD_ROW_X + 3, y + 15);
            if (thread.active() && (showOutputIcon || showRecipeProgress)) {
                int iconX = elementX + THREAD_ROW_WIDTH - THREAD_OUTPUT_ICON_SIZE - THREAD_OUTPUT_ICON_PADDING;
                int elementY = threadElementY(y);
                if (showOutputIcon) {
                    ControllerRecipeTextLines.firstRenderableOutputIcon(thread.presentation()).ifPresent(icon ->
                            renderIcon(graphics, icon, iconX,
                                    elementY + THREAD_OUTPUT_ICON_PADDING, THREAD_OUTPUT_ICON_SIZE));
                }
                if (showRecipeProgress && thread.totalTick() > 0) {
                    renderThreadProgress(graphics, progressPercent(thread.tick(), thread.totalTick()),
                            iconX, elementY + THREAD_PROGRESS_TEXT_Y_OFFSET);
                }
            }
        }
        if (shouldRenderScrollbar(menu.threads().size())) {
            int scrollbarX = leftPos + SCROLLBAR_X;
            int scrollbarY = topPos + scrollbarHandleY(scrollOffset, menu.threads().size());
            graphics.blit(RenderPipelines.GUI_TEXTURED, SCROLLER, scrollbarX, scrollbarY, 0, 0,
                    SCROLLBAR_HANDLE_WIDTH, SCROLLBAR_HANDLE_HEIGHT, 32, 32);
        }
        FactoryRuntime.ThreadSnapshot selected = menu.selectedThread();
        int x = leftPos + DETAIL_X;
        int y = topPos + 12;
        graphics.pose().pushMatrix();
        graphics.pose().scale(DETAIL_TEXT_SCALE, DETAIL_TEXT_SCALE);
        x = (int) (x / DETAIL_TEXT_SCALE);
        y = (int) (y / DETAIL_TEXT_SCALE);
        graphics.text(font, detailTitle(title, menu.machineName(), selected.index()), x, detailTitleY(y),
                CONTROLLER_TITLE_COLOR, false);
        List<ControllerScreenTextComposer.VisualLine> lines = wrappedTextLines();
        clampTextScrollOffset();
        int first = firstVisibleTextLine();
        int last = lastVisibleTextLineExclusive();
        for (int index = first; index < last; index++) {
            ControllerScreenTextComposer.VisualLine line = lines.get(index);
            int textY = detailTextY(topPos, textLineY(visibleTextRow(index)));
            renderVisualLine(graphics, line, x, textY);
        }
        graphics.pose().popMatrix();
        renderScrollableTooltip(graphics, mouseX, mouseY, DETAIL_X);
    }

    static Component detailTitle(Component title, String machineName, int threadIndex) {
        Component name = machineName.isEmpty() ? title : Component.translatable(machineName);
        return Component.empty().append(name).append(" #" + threadIndex);
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
    protected boolean handleAdditionalScroll(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (!mouseOverThreadList((int) mouseX, (int) mouseY)) return false;
        scrollOffset = clampScrollOffset(scrollOffset - (int) Math.signum(deltaY), menu.threads().size());
        return true;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private boolean mouseOverScrollbar(int mouseX, int mouseY) {
        return isScrollbarInteractive(menu.threads().size())
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
