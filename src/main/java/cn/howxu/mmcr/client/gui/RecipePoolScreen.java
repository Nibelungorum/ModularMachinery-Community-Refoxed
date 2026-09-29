package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.network.PktRecipePoolSelectPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

/**
 * Compact controller-wide recipe-pool selector.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipePoolScreen extends Screen {
    static final int IMAGE_WIDTH = 108;
    static final int IMAGE_HEIGHT = 141;
    static final int ROW_X = 4;
    static final int ROW_Y = 6;
    static final int ROW_WIDTH = 86;
    static final int ROW_HEIGHT = 32;
    static final int ROW_GAP = 1;
    static final int VISIBLE_ROWS = 4;
    static final int SCROLLBAR_X = 92;
    static final int SCROLLBAR_Y = 6;
    static final int SCROLLBAR_RIGHT = 104;
    static final int SCROLLBAR_BOTTOM = 125;
    static final int SCROLLBAR_HANDLE_HEIGHT = 32;
    static final int RETURN_X = 92;
    static final int RETURN_Y = 125;
    static final int RETURN_SIZE = 12;
    private static final ResourceLocation BACKGROUND = MMCR.id("textures/gui/gui_recipe_pool.png");
    private static final ResourceLocation ELEMENTS = MMCR.id("textures/gui/guifactoryelements.png");
    private static final ResourceLocation SELECTED_ELEMENTS = MMCR.id("textures/gui/guifactoryelements_selected.png");
    private static final ResourceLocation SCROLLER = MMCR.id("textures/gui/scroller.png");
    private static final int TEXT_COLOR = 0xFF222222;

    private final Screen parent;
    private final BlockPos controllerPos;
    private final List<ResourceLocation> recipePoolIds;
    private ResourceLocation selectedRecipePoolId;
    private int scrollOffset;
    private boolean draggingScrollbar;
    private int scrollbarDragOffsetY;

    public RecipePoolScreen(Screen parent, BlockPos controllerPos, List<ResourceLocation> recipePoolIds,
                            ResourceLocation selectedRecipePoolId) {
        super(Component.translatable("gui.mmcr.recipe_pool.title"));
        this.parent = parent;
        this.controllerPos = controllerPos.immutable();
        this.recipePoolIds = List.copyOf(recipePoolIds);
        this.selectedRecipePoolId = selectedRecipePoolId;
    }

    @Override
    protected void init() {
        addRenderableWidget(new StyledButton(left() + RETURN_X, top() + RETURN_Y, RETURN_SIZE, RETURN_SIZE,
                Component.literal("<"), button -> minecraft.setScreen(parent)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left(), top(), 0, 0,
                IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_HEIGHT);
        int visible = Math.min(VISIBLE_ROWS, recipePoolIds.size() - scrollOffset);
        for (int row = 0; row < visible; row++) {
            ResourceLocation poolId = recipePoolIds.get(scrollOffset + row);
            int y = top() + ROW_Y + row * (ROW_HEIGHT + ROW_GAP);
            ResourceLocation texture = poolId.equals(selectedRecipePoolId) ? SELECTED_ELEMENTS : ELEMENTS;
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, left() + ROW_X, y, 0, 0,
                    ROW_WIDTH, ROW_HEIGHT, 256, 256);
            String label = fitLabel(RecipePoolDisplayName.component(poolId).getString(), ROW_WIDTH - 6);
            int textX = left() + ROW_X + (ROW_WIDTH - font.width(label)) / 2;
            int textY = y + (ROW_HEIGHT - font.lineHeight) / 2;
            graphics.text(font, Component.literal(label), textX, textY, TEXT_COLOR, false);
        }
        int handleY = top() + scrollbarHandleY(scrollOffset, recipePoolIds.size());
        graphics.blit(RenderPipelines.GUI_TEXTURED, SCROLLER, left() + SCROLLBAR_X, handleY, 0, 0,
                SCROLLBAR_RIGHT - SCROLLBAR_X, SCROLLBAR_HANDLE_HEIGHT, 32, 32);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            if (mouseOverScrollbar((int) event.x(), (int) event.y())) {
                draggingScrollbar = true;
                scrollbarDragOffsetY = Math.max(0, Math.min(SCROLLBAR_HANDLE_HEIGHT,
                        (int) event.y() - top() - scrollbarHandleY(scrollOffset, recipePoolIds.size())));
                scrollOffset = scrollOffsetFromScrollbarY((int) event.y() - top(), recipePoolIds.size(),
                        scrollbarDragOffsetY);
                return true;
            }
            int index = rowIndexAt(left(), top(), scrollOffset, (int) event.x(), (int) event.y(), recipePoolIds.size());
            if (index >= 0) {
                selectedRecipePoolId = recipePoolIds.get(index);
                ClientPacketDistributor.sendToServer(new PktRecipePoolSelectPayload(controllerPos, selectedRecipePoolId));
                minecraft.setScreen(parent);
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
            scrollOffset = scrollOffsetFromScrollbarY((int) event.y() - top(), recipePoolIds.size(),
                    scrollbarDragOffsetY);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (!mouseOverListOrScrollbar((int) mouseX, (int) mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
        }
        scrollOffset = clampScrollOffset(scrollOffset - (int) Math.signum(deltaY), recipePoolIds.size());
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    static int rowIndexAt(int left, int top, int scrollOffset, int mouseX, int mouseY, int poolCount) {
        int localX = mouseX - left - ROW_X;
        int localY = mouseY - top - ROW_Y;
        if (localX < 0 || localX >= ROW_WIDTH || localY < 0) return -1;
        int stride = ROW_HEIGHT + ROW_GAP;
        int row = localY / stride;
        if (row >= VISIBLE_ROWS || localY % stride >= ROW_HEIGHT) return -1;
        int index = scrollOffset + row;
        return index < poolCount ? index : -1;
    }

    static int clampScrollOffset(int scrollOffset, int poolCount) {
        return Math.max(0, Math.min(Math.max(0, poolCount - VISIBLE_ROWS), scrollOffset));
    }

    static int scrollbarHandleY(int scrollOffset, int poolCount) {
        int range = Math.max(0, poolCount - VISIBLE_ROWS);
        if (range == 0) return SCROLLBAR_Y;
        int available = SCROLLBAR_BOTTOM - SCROLLBAR_Y - SCROLLBAR_HANDLE_HEIGHT;
        return SCROLLBAR_Y + clampScrollOffset(scrollOffset, poolCount) * available / range;
    }

    static int scrollOffsetFromScrollbarY(int scrollbarY, int poolCount, int dragOffsetY) {
        int range = Math.max(0, poolCount - VISIBLE_ROWS);
        if (range == 0) return 0;
        int available = SCROLLBAR_BOTTOM - SCROLLBAR_Y - SCROLLBAR_HANDLE_HEIGHT;
        int handleY = Math.max(0, Math.min(available, scrollbarY - SCROLLBAR_Y - dragOffsetY));
        return clampScrollOffset(Math.round((float) handleY * range / available), poolCount);
    }

    private String fitLabel(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        String suffix = "...";
        int length = value.length();
        while (length > 0 && font.width(value.substring(0, length) + suffix) > maxWidth) length--;
        return value.substring(0, length) + suffix;
    }

    private int left() {
        return (width - IMAGE_WIDTH) / 2;
    }

    private int top() {
        return (height - IMAGE_HEIGHT) / 2;
    }

    private boolean mouseOverScrollbar(int mouseX, int mouseY) {
        return recipePoolIds.size() > VISIBLE_ROWS && mouseX >= left() + SCROLLBAR_X
                && mouseX < left() + SCROLLBAR_RIGHT && mouseY >= top() + SCROLLBAR_Y
                && mouseY < top() + SCROLLBAR_BOTTOM;
    }

    private boolean mouseOverListOrScrollbar(int mouseX, int mouseY) {
        return mouseOverScrollbar(mouseX, mouseY) || mouseX >= left() + ROW_X
                && mouseX < left() + ROW_X + ROW_WIDTH && mouseY >= top() + ROW_Y
                && mouseY < top() + SCROLLBAR_BOTTOM;
    }
}
