package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
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
    static final int VISIBLE_THREADS = 6;
    private static final Identifier BACKGROUND = MMCR.id("textures/gui/guifactory.png");
    private static final Identifier ELEMENTS = MMCR.id("textures/gui/guifactoryelements.png");
    private int scrollOffset;

    public FactoryControllerScreen(FactoryControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
        inventoryLabelY = -1000;
    }

    public static int defaultSelectedThread() { return 0; }

    static int threadIndexAt(int left, int top, int scroll, int mouseX, int mouseY) {
        if (mouseX < left || mouseX >= left + THREAD_ROW_WIDTH) return -1;
        int row = (mouseY - top) / THREAD_ROW_HEIGHT;
        if (mouseY < top || row < 0 || row >= VISIBLE_THREADS) return -1;
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        scrollOffset = Math.min(scrollOffset, Math.max(0, menu.threads().size() - VISIBLE_THREADS));
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_HEIGHT);
        for (int row = 0; row < VISIBLE_THREADS; row++) {
            int index = scrollOffset + row;
            if (index >= menu.threads().size()) break;
            FactoryRecipeScheduler.ThreadSnapshot thread = menu.threads().get(index);
            int y = topPos + THREAD_ROW_Y + row * THREAD_ROW_HEIGHT;
            graphics.blit(RenderPipelines.GUI_TEXTURED, ELEMENTS, leftPos + THREAD_ROW_X, y, 0, 0,
                    THREAD_ROW_WIDTH, THREAD_ROW_HEIGHT, THREAD_ROW_WIDTH, THREAD_ROW_HEIGHT);
            int progress = progressWidth(thread.tick(), thread.totalTick());
            if (progress > 0) graphics.fill(leftPos + THREAD_ROW_X, y, leftPos + THREAD_ROW_X + progress, y + THREAD_ROW_HEIGHT, 0x6600AA55);
            graphics.text(font, Component.translatable("gui.mmcr.factory.thread", thread.index()), leftPos + THREAD_ROW_X + 3, y + 3, 0xFF222222, false);
            graphics.text(font, Component.translatable(thread.active() ? "gui.mmcr.controller.running" : "gui.mmcr.controller.idle"), leftPos + THREAD_ROW_X + 3, y + 15, 0xFF222222, false);
        }
        FactoryRecipeScheduler.ThreadSnapshot selected = menu.selectedThread();
        int x = leftPos + 113;
        int y = topPos + 12;
        graphics.text(font, Component.translatable("gui.mmcr.factory.selected_thread", selected.index()), x, y, 0xFFFFFFFF, true);
        graphics.text(font, Component.translatable("gui.mmcr.factory.recipe", selected.recipeId().isEmpty() ? Component.translatable("gui.mmcr.factory.no_recipe") : Component.literal(truncate(selected.recipeId(), 22))), x, y + 14, 0xFFFFFFFF, true);
        graphics.text(font, Component.translatable("gui.mmcr.factory.thread_progress", selected.tick(), selected.totalTick(), progressWidth(selected.tick(), selected.totalTick()) * 100 / THREAD_ROW_WIDTH + "%"), x, y + 28, 0xFFFFFFFF, true);
        graphics.text(font, Component.translatable("gui.mmcr.factory.thread_parallelism", selected.parallelism()), x, y + 42, 0xFFFFFFFF, true);
        graphics.text(font, Component.translatable("gui.mmcr.factory.formed", menu.isFormed()), x, y + 56, 0xFFFFFFFF, true);
        graphics.text(font, Component.translatable("gui.mmcr.factory.redstone_paused", menu.isRedstonePaused()), x, y + 70, 0xFFFFFFFF, true);
        graphics.text(font, Component.translatable("gui.mmcr.factory.active_threads", menu.activeThreadCount(), menu.threadCount()), x, y + 84, 0xFFFFFFFF, true);
        graphics.text(font, Component.translatable("gui.mmcr.factory.parallelism", menu.currentParallelism(), menu.maxParallelism()), x, y + 98, 0xFFFFFFFF, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
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
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scrollOffset = Math.max(0, Math.min(Math.max(0, menu.threads().size() - VISIBLE_THREADS), scrollOffset - (int) Math.signum(deltaY)));
        return true;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
