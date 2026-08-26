package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.network.PktRecipeLockPayload;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.client.gui.MachineControllerScreen.ControllerStatusLine;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

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
    private static final int CONTROLLER_TITLE_COLOR = 0xFFE8E8E8;
    private static final int STATUS_LABEL_COLOR = CONTROLLER_TITLE_COLOR;
    private static final int FORMED_STATUS_COLOR = 0xFF55FF55;
    private static final int UNFORMED_STATUS_COLOR = 0xFFFF5555;
    private static final int IDLE_STATUS_COLOR = 0xFFFFAA00;
    private static final int PROGRESS_STATUS_COLOR = -1;
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
    static final int SCROLLBAR_HANDLE_HEIGHT = 32;
    private static final int ELEMENT_TEXTURE_WIDTH = 256;
    private static final int ELEMENT_TEXTURE_HEIGHT = 256;
    private static final int THREAD_ELEMENT_Y_OFFSET = 0;
    static final int PROGRESS_THREAD_OVERLAY = 0x6600AA55;
    static final int SELECTED_THREAD_OVERLAY = 0x66A8D8FF;
    static final int LOCKED_THREAD_OVERLAY = 0x66FFD966;
    private static final int DETAIL_LINE_SPACING = 10;
    private static final float DETAIL_TEXT_SCALE = 0.85F;
    private static final float THREAD_TEXT_SCALE = 0.85F;
    private static final int RECIPE_LOCK_BUTTON_SIZE = 20;
    private static final int PLAYER_INVENTORY_HEIGHT_WITH_HOTBAR = 82;
    private static final int RECIPE_LOCK_ENABLED_BG_COLOR = 0xFF66BB6A;
    private static final Identifier BACKGROUND = MMCR.id("textures/gui/guifactory.png");
    private static final Identifier ELEMENTS = MMCR.id("textures/gui/guifactoryelements.png");
    private static final Identifier SCROLLER = MMCR.id("textures/gui/scroller.png");
    private int scrollOffset;
    private boolean draggingScrollbar;
    private int scrollbarDragOffsetY;
    private Button recipeLockButton;

    public FactoryControllerScreen(FactoryControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
        titleLabelY = -1000;
        inventoryLabelY = -1000;
    }

    @Override
    protected TextViewport scrollableTextViewport() {
        int bodyY = 12 + DETAIL_LINE_SPACING * 2;
        return new TextViewport(113, bodyY, 160, 123 - bodyY + 1,
                DETAIL_TEXT_SCALE, DETAIL_LINE_SPACING);
    }

    @Override
    protected int scrollableTextLineCount() {
        return detailLines(menu).size();
    }

    public static int defaultSelectedThread() { return 0; }

    @Override
    protected void init() {
        super.init();
        Rect rect = recipeLockButtonRect(leftPos, topPos, imageWidth, imageHeight);
        recipeLockButton = addRenderableWidget(Button.builder(Component.empty(),
                button -> {
                    ClientPacketDistributor.sendToServer(new PktRecipeLockPayload(menu.controllerPos(), menu.selectedThreadIndex()));
                    clearRecipeLockButtonFocus(button);
                }).bounds(rect.left(), rect.top(), rect.width(), rect.height()).build());
        updateRecipeLockTooltip();
    }

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
        return Math.min(THREAD_ROW_WIDTH, tick * THREAD_ROW_WIDTH / totalTick);
    }

    static int selectedParallelism(FactoryControllerMenu menu) {
        return menu.currentParallelism();
    }

    private static Component levelLine(MachineLevel level) {
        var type = MachineLevelRegistry.getType(level.typeId());
        if (type == null || !(level.statePredicate() instanceof BlockPredicate.OfBlockState predicate)) return Component.empty();
        return Component.translatable("gui.mmcr.controller.level", type.displayName(), predicate.state().getBlock().getName());
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

    private static Component parallelLine(int parallelism, int maxParallelism) {
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
            MachineLevel level = MachineLevelRegistry.getLevel(Identifier.parse(levelId));
            if (level != null) lines.add(levelLine(level));
        }
        return List.copyOf(lines);
    }

    static String selectedFailureUnloc(FactoryControllerMenu menu) {
        FactoryRuntime.ThreadSnapshot selected = menu.selectedThread();
        String threadFailure = selected.lastFailureUnloc();
        if (!threadFailure.isEmpty()) return threadFailure;
        return selected.active() ? "" : menu.lastFailureUnloc();
    }

    static List<ControllerStatusLine> detailLines(FactoryControllerMenu menu) {
        List<ControllerStatusLine> lines = new ArrayList<>();
        for (Component levelLine : levelLines(menu.foundLevelIds())) {
            lines.add(new ControllerStatusLine(levelLine, STATUS_LABEL_COLOR));
        }
        String failure = selectedFailureUnloc(menu);
        if (!failure.isEmpty()) {
            lines.add(new ControllerStatusLine(Component.translatable("gui.mmcr.controller.last_failure",
                    Component.translatable(failure)), STATUS_LABEL_COLOR));
        }
        if (menu.parallelSlots() > 0) {
            lines.add(new ControllerStatusLine(parallelSlotLine(menu.parallelSlots()), STATUS_LABEL_COLOR));
        }
        lines.add(new ControllerStatusLine(parallelLine(selectedParallelism(menu), menu.maxParallelism()),
                STATUS_LABEL_COLOR));
        if (menu.isRedstonePaused()) {
            lines.add(new ControllerStatusLine(Component.translatable("gui.mmcr.controller.redstone_stopped"),
                    STATUS_LABEL_COLOR));
        }
        lines.add(new ControllerStatusLine(factoryThreadLine(menu.activeThreadCount(), menu.threadCount()),
                STATUS_LABEL_COLOR));
        FactoryRuntime.ThreadSnapshot selected = menu.selectedThread();
        if (selected.totalTick() > 0) {
            int percent = progressWidth(selected.tick(), selected.totalTick()) * 100 / THREAD_ROW_WIDTH;
            lines.add(new ControllerStatusLine(Component.translatable("gui.mmcr.controller.progress", percent + "%"),
                    PROGRESS_STATUS_COLOR));
        }
        return lines;
    }

    static Rect recipeLockButtonRect(int left, int top, int width, int height) {
        return new Rect(left + width - RECIPE_LOCK_BUTTON_SIZE - 12,
                top + height - PLAYER_INVENTORY_HEIGHT_WITH_HOTBAR - RECIPE_LOCK_BUTTON_SIZE - 12,
                RECIPE_LOCK_BUTTON_SIZE, RECIPE_LOCK_BUTTON_SIZE);
    }

    static List<Component> recipeLockTooltip(boolean locked, String recipeId) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(locked
                ? "gui.mmcr.controller.recipe_lock.enabled"
                : "gui.mmcr.controller.recipe_lock.disabled"));
        if (locked && !recipeId.isEmpty()) {
            lines.add(Component.translatable("gui.mmcr.controller.recipe_lock.recipe", Component.literal(recipeId)));
        }
        return lines;
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
    static int lockedOverlayX(int x) { return selectedOverlayX(x); }
    static int lockedOverlayY(int y) { return selectedOverlayY(y); }
    static int lockedOverlayRight(int x) { return selectedOverlayRight(x); }
    static int lockedOverlayBottom(int y) { return selectedOverlayBottom(y); }
    static int progressOverlayX(int x) { return x; }
    static int progressOverlayY(int y) { return threadElementY(y); }
    static int progressOverlayHeight() { return THREAD_ROW_HEIGHT - 1; }
    static int progressOverlayRight(int x, int progress) { return progressOverlayX(x) + progress; }
    static int progressOverlayBottom(int y) { return progressOverlayY(y) + progressOverlayHeight(); }
    static List<Integer> threadOverlayColors(FactoryRuntime.ThreadSnapshot thread, int selectedThreadIndex) {
        List<Integer> colors = new ArrayList<>();
        if (progressWidth(thread.tick(), thread.totalTick()) > 0) colors.add(PROGRESS_THREAD_OVERLAY);
        if (thread.index() == selectedThreadIndex) colors.add(SELECTED_THREAD_OVERLAY);
        if (thread.locked()) colors.add(LOCKED_THREAD_OVERLAY);
        return colors;
    }
    static int detailTitleY(int y) { return y; }
    static int nextDetailY(int y) { return y + DETAIL_LINE_SPACING; }
    static int detailTextY(int localY) { return (int) (localY / DETAIL_TEXT_SCALE); }
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        scrollOffset = clampScrollOffset(scrollOffset, menu.threads().size());
        updateRecipeLockTooltip();
        renderRecipeLockButtonIcon(graphics);
        int visibleThreadCount = visibleThreadCount(menu.threads().size());
        for (int row = 0; row < visibleThreadCount; row++) {
            int index = scrollOffset + row;
            if (index >= menu.threads().size()) break;
            FactoryRuntime.ThreadSnapshot thread = menu.threads().get(index);
            int y = topPos + THREAD_ROW_Y + row * (THREAD_ROW_HEIGHT + THREAD_ROW_GAP);
            int elementX = leftPos + THREAD_ROW_X;
            int selectedOverlayX = selectedOverlayX(elementX);
            int selectedOverlayY = selectedOverlayY(y);
            int progressOverlayX = progressOverlayX(elementX);
            int progressOverlayY = progressOverlayY(y);
            graphics.blit(RenderPipelines.GUI_TEXTURED, ELEMENTS, leftPos + THREAD_ROW_X, threadElementY(y), 0, 0,
                    THREAD_ROW_WIDTH, THREAD_ROW_HEIGHT, ELEMENT_TEXTURE_WIDTH, ELEMENT_TEXTURE_HEIGHT);
            int progress = progressWidth(thread.tick(), thread.totalTick());
            if (progress > 0) graphics.fill(progressOverlayX, progressOverlayY,
                    progressOverlayRight(elementX, progress), progressOverlayBottom(y), PROGRESS_THREAD_OVERLAY);
            if (thread.index() == menu.selectedThread().index()) {
                graphics.fill(selectedOverlayX, selectedOverlayY,
                        selectedOverlayRight(elementX), selectedOverlayBottom(y), SELECTED_THREAD_OVERLAY);
            }
            if (thread.locked()) {
                graphics.fill(lockedOverlayX(elementX), lockedOverlayY(y),
                        lockedOverlayRight(elementX), lockedOverlayBottom(y), LOCKED_THREAD_OVERLAY);
            }
            renderThreadText(graphics, Component.translatable("gui.mmcr.factory.thread", thread.index()),
                    leftPos + THREAD_ROW_X + 3, y + 3);
            renderThreadText(graphics, Component.translatable(thread.active() ? "gui.mmcr.controller.running" : "gui.mmcr.controller.idle"),
                    leftPos + THREAD_ROW_X + 3, y + 15);
        }
        if (shouldRenderScrollbar(menu.threads().size())) {
            int scrollbarX = leftPos + SCROLLBAR_X;
            int scrollbarY = topPos + scrollbarHandleY(scrollOffset, menu.threads().size());
            graphics.blit(RenderPipelines.GUI_TEXTURED, SCROLLER, scrollbarX, scrollbarY, 0, 0,
                    SCROLLBAR_HANDLE_WIDTH, SCROLLBAR_HANDLE_HEIGHT, 32, 32);
        }
        FactoryRuntime.ThreadSnapshot selected = menu.selectedThread();
        int x = leftPos + 113;
        int y = topPos + 12;
        graphics.pose().pushMatrix();
        graphics.pose().scale(DETAIL_TEXT_SCALE, DETAIL_TEXT_SCALE);
        x = (int) (x / DETAIL_TEXT_SCALE);
        y = (int) (y / DETAIL_TEXT_SCALE);
        String machineName = menu.machineName().isEmpty() ? title.getString() : menu.machineName();
        graphics.text(font, Component.translatable(machineName).append(" #" + selected.index()), x, detailTitleY(y), CONTROLLER_TITLE_COLOR, true);
        int lineY = nextDetailY(y);
        graphics.text(font, Component.translatable("gui.mmcr.controller.status_label"), x, lineY, STATUS_LABEL_COLOR, true);
        graphics.text(font, Component.translatable(controllerStatusKey(menu.isFormed(), selected.active())),
                x + font.width(Component.translatable("gui.mmcr.controller.status_label")) + 4, lineY,
                controllerStatusColor(menu.isFormed(), selected.active()), true);
        List<ControllerStatusLine> lines = detailLines(menu);
        clampTextScrollOffset();
        int first = firstVisibleTextLine();
        int last = lastVisibleTextLineExclusive();
        for (int index = first; index < last; index++) {
            ControllerStatusLine line = lines.get(index);
            int textY = detailTextY(topPos + textLineY(visibleTextRow(index)));
            graphics.text(font, line.text(), x, textY, line.color(), true);
        }
        graphics.pose().popMatrix();
    }

    private void renderThreadText(GuiGraphicsExtractor graphics, Component text, int x, int y) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(THREAD_TEXT_SCALE, THREAD_TEXT_SCALE);
        graphics.text(font, text, 0, 0, 0xFF222222, false);
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

    private void updateRecipeLockTooltip() {
        if (recipeLockButton == null) return;
        recipeLockButton.setTooltip(Tooltip.create(tooltipComponent(recipeLockTooltip(menu.selectedRecipeLocked(), menu.selectedLockedRecipeId()))));
    }

    private void renderRecipeLockButtonIcon(GuiGraphicsExtractor graphics) {
        if (recipeLockButton == null || !recipeLockButton.visible) return;
        int x = recipeLockButton.getX();
        int y = recipeLockButton.getY();
        int width = recipeLockButton.getWidth();
        int height = recipeLockButton.getHeight();
        if (menu.selectedRecipeLocked()) {
            graphics.fill(x, y, x + width, y + height, RECIPE_LOCK_ENABLED_BG_COLOR);
        }
        graphics.item(recipeLockIcon(), x + (width - 16) / 2,
                y + (height - 16) / 2, 0);
    }

    private static ItemStack recipeLockIcon() {
        return new ItemStack(Items.KNOWLEDGE_BOOK);
    }

    static void clearRecipeLockButtonFocus(Button button) {
        button.setFocused(false);
    }

    private static Component tooltipComponent(List<Component> lines) {
        Component component = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) component = component.copy().append("\n");
            component = component.copy().append(lines.get(i));
        }
        return component;
    }

    public record Rect(int left, int top, int width, int height) {
        int right() { return left + width; }
        int bottom() { return top + height; }
        boolean overlaps(Rect other) {
            return left < other.right() && right() > other.left && top < other.bottom() && bottom() > other.top;
        }
    }
}
