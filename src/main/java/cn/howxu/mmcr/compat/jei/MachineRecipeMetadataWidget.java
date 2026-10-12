package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.client.recipe.RecipeDetailsLayout;
import cn.howxu.mmcr.client.recipe.RecipeDisplayDrawing;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.IJeiGuiEventListener;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Page-owned body and whole-row details, including transitions into and out of pagination.
 * Keeps JEI's real slots and tooltips; the native scroll grid only supports 18x18 cells.
 * @author howxu <dev@howxu.cn>
 */
final class MachineRecipeMetadataWidget implements ISlottedRecipeWidget, IJeiGuiEventListener {
    private final MachineRecipeCategory category;
    private final MachineRecipeDisplay recipe;
    private MachineRecipeLayout layout;
    private final Supplier<MachineRecipeLayout> layouts;
    private final MachineRecipeLayout.Live live;
    private final int originY;
    private final boolean ownsBody;
    private final List<DetailSlot> slots;
    private final IDrawable bodyArrow;
    private int pageIndex;
    private int scrollY;

    MachineRecipeMetadataWidget(MachineRecipeCategory category, MachineRecipeDisplay recipe,
                                MachineRecipeLayout layout, List<IRecipeSlotDrawable> slots) {
        this(category, recipe, layout, () -> layout, null, layout.metadataViewportY(), false, slots);
    }

    MachineRecipeMetadataWidget(MachineRecipeCategory category, MachineRecipeDisplay recipe,
                                MachineRecipeLayout.Live live, List<IRecipeSlotDrawable> slots) {
        this(category, recipe, live.current(), live::current, live, 0, true, slots);
    }

    @SuppressWarnings("removal")
    private MachineRecipeMetadataWidget(MachineRecipeCategory category, MachineRecipeDisplay recipe,
            MachineRecipeLayout layout, Supplier<MachineRecipeLayout> layouts, MachineRecipeLayout.Live live,
            int originY, boolean ownsBody, List<IRecipeSlotDrawable> slots) {
        this.category = category;
        this.recipe = recipe;
        this.layout = layout;
        this.layouts = layouts;
        this.live = live;
        this.originY = originY;
        this.ownsBody = ownsBody;
        this.bodyArrow = ownsBody || layout.pagesIncludeIngredients() ? category.bodyArrow(layout) : null;
        this.slots = slots.stream().map(slot -> {
            // JEI 19.57's background area is a union with the unpositioned background offset.
            // Capture the actual renderer rect before moving it into the widget's coordinates.
            var area = slot.getRect();
            String name = slot.getSlotName().orElse("");
            int level = name.startsWith("mmcr_details_level_")
                    ? Integer.parseInt(name.substring("mmcr_details_level_".length())) : -1;
            return new DetailSlot(slot, area.getX(), area.getY(), area.getHeight(), name.startsWith("mmcr_body_"), level);
        }).toList();
        positionSlots();
    }

    private void refresh() {
        MachineRecipeLayout next = layouts.get();
        if (next == layout) return;
        layout = next;
        pageIndex = Math.clamp(pageIndex, 0, Math.max(0, layout.metadataPages().size() - 1));
        scrollY = 0;
        positionSlots();
    }

    @Override public ScreenPosition getPosition() { return new ScreenPosition(0, originY); }
    @Override public ScreenRectangle getArea() {
        refresh();
        return new ScreenRectangle(getPosition(), MachineRecipeLayout.CATEGORY_WIDTH,
                ownsBody ? layout.height() : layout.metadataViewportHeight());
    }
    @Override public ScreenRectangle getScreenRectangle() { return getArea(); }
    @Override public void tick() { refresh(); }

    private MachineRecipeLayout.MetadataPage page() { return layout.metadataPages().get(pageIndex); }

    private RecipeDetailsLayout.Viewport viewport() {
        int contentHeight = page().endY() - page().startY();
        int capacity = layout.metadataViewportHeight() - MachineRecipeLayout.PAGE_FOOTER_HEIGHT - 2;
        return new RecipeDetailsLayout.Viewport(0, layout.metadataViewportY() - originY + 1, MachineRecipeLayout.CATEGORY_WIDTH,
                Math.min(contentHeight, capacity), scrollY);
    }

    private boolean visible(DetailSlot slot) {
        if (!paged(slot)) return true;
        int y = logicalY(slot);
        return y >= page().startY() && y + slot.height() <= page().endY();
    }

    private boolean paged(DetailSlot slot) {
        return !layout.metadataPages().isEmpty() && (!slot.body() || layout.pagesIncludeIngredients());
    }

    private int logicalY(DetailSlot slot) {
        return live != null && slot.level() >= 0 ? layout.levelRequirementSlotY(recipe, slot.level()) : slot.y();
    }

    private void positionSlots() {
        for (var slot : slots) {
            int x = slot.x();
            if (live != null && slot.level() >= 0) {
                var requirement = MachineRecipeCategory.sortedLevelRequirements(recipe.recipe()).get(slot.level());
                x = MachineRecipeCategory.levelSlotX(layout.durationTextX(), live.textWidth(MachineRecipeCategory.levelLabel(requirement)));
            }
            int y = logicalY(slot) - originY;
            if (paged(slot)) y = visible(slot) ? logicalY(slot) - page().startY() + viewport().y() - scrollY : -1000;
            slot.drawable().setPosition(x, y);
        }
    }

    @Override
    public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
        refresh();
        return slots.stream().filter(this::visible)
                .filter(slot -> !paged(slot) || viewport().contains(mouseX, mouseY)).map(DetailSlot::drawable)
                .filter(slot -> slot.isMouseOver(mouseX, mouseY))
                .findFirst().map(slot -> new RecipeSlotUnderMouse(slot, getPosition()));
    }

    @Override
    public void drawWidget(GuiGraphics graphics, double mouseX, double mouseY) {
        refresh();
        if (ownsBody && !layout.pagesIncludeIngredients()) {
            category.drawBodyText(recipe, layout, graphics);
            drawArrow(graphics);
        }
        if (layout.metadataPages().isEmpty()) {
            category.drawMetadata(recipe, layout, graphics);
            drawSlots(graphics, mouseX, mouseY, false);
            return;
        }
        drawSlots(graphics, mouseX, mouseY, false);
        var viewport = viewport();
        RecipeDisplayDrawing.drawClipped(graphics, viewport, () -> {
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(0, viewport.y() - page().startY() - viewport.scrollY(), 0);
                category.drawMetadata(recipe, layout, graphics);
                if (layout.pagesIncludeIngredients()) {
                    category.drawBodyText(recipe, layout, graphics);
                    drawArrow(graphics);
                }
            } finally {
                graphics.pose().popPose();
            }
            drawSlots(graphics, mouseX, mouseY, true);
        });
        graphics.drawString(Minecraft.getInstance().font,
                Component.translatable("jei.mmcr.machine_recipe.details_page", pageIndex + 1, layout.metadataPages().size()),
                8, footerY(), 0xFF404040, false);
    }

    private void drawArrow(GuiGraphics graphics) {
        if (bodyArrow != null) bodyArrow.draw(graphics, layout.arrow().x(), layout.arrow().y());
    }

    private void drawSlots(GuiGraphics graphics, double mouseX, double mouseY, boolean paginated) {
        for (var slot : slots) {
            if (paged(slot) == paginated && visible(slot)) slot.drawable().draw(graphics,
                    (!paginated || viewport().contains(mouseX, mouseY)) && slot.drawable().isMouseOver(mouseX, mouseY));
        }
    }

    private int footerY() {
        return layout.metadataViewportY() - originY + layout.metadataViewportHeight() - MachineRecipeLayout.PAGE_FOOTER_HEIGHT;
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
        refresh();
        if (layout.metadataPages().isEmpty()) {
            category.metadataTooltip(tooltip, recipe, layout, mouseX, mouseY + originY);
            return;
        }
        var viewport = viewport();
        if (viewport.contains(mouseX, mouseY)) {
            category.metadataTooltip(tooltip, recipe, layout, mouseX, viewport.contentY(mouseY) + page().startY());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        refresh();
        if (scrollY == 0 || mouseX < 0 || mouseX >= MachineRecipeLayout.CATEGORY_WIDTH
                || layout.metadataPages().isEmpty() || mouseY < layout.metadataViewportY() - originY
                || mouseY >= layout.metadataViewportY() - originY + layout.metadataViewportHeight()) return false;
        int direction = scrollY < 0 ? 1 : -1;
        int maxScroll = Math.max(0, page().endY() - page().startY() - viewport().height());
        int nextScroll = Math.clamp(this.scrollY + direction * MachineRecipeLayout.TEXT_LINE_SPACING, 0, maxScroll);
        if (nextScroll != this.scrollY) {
            this.scrollY = nextScroll;
            positionSlots();
        } else {
            changePage(direction);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        refresh();
        if (button != 0 || layout.metadataPages().isEmpty() || mouseX < 8 || mouseX >= 144
                || mouseY < footerY() || mouseY >= footerY() + MachineRecipeLayout.PAGE_FOOTER_HEIGHT) return false;
        changePage(mouseX < 76 ? -1 : 1);
        return true;
    }

    private void changePage(int direction) {
        int nextPage = Math.clamp(pageIndex + direction, 0, layout.metadataPages().size() - 1);
        if (nextPage == pageIndex) return;
        pageIndex = nextPage;
        scrollY = 0;
        positionSlots();
    }

    /** Original logical slot placement, independent of its current page. @author howxu <dev@howxu.cn> */
    private record DetailSlot(IRecipeSlotDrawable drawable, int x, int y, int height, boolean body, int level) {}
}
