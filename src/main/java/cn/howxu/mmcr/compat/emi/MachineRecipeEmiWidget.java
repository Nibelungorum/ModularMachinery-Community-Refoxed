package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.client.recipe.RecipeDetailsLayout;
import cn.howxu.mmcr.client.recipe.RecipeDisplayDrawing;
import cn.howxu.mmcr.client.recipe.RecipeMetadataRow;
import cn.howxu.mmcr.client.recipe.RecipeSlotLayout;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/** Native resource viewport and whole-row detail pages, sharing clipping and input coordinates.
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeEmiWidget extends Widget implements EmiInteractiveWidget {
    private final int width, height;
    private final RecipeSlotLayout layout;
    private final List<RecipeMetadataRow> rows;
    private final List<Integer> rowHeights;
    private final List<RecipeDetailsLayout.Page> pages;
    private final List<EmiRecipeSlotWidget> slots = new ArrayList<>();
    private final int capacity;
    private int page;
    private int scrollY;
    private boolean closed;

    public static void add(WidgetHolder holder, MachineEmiRecipe recipe) {
        int width = holder.getWidth();
        List<Integer> heights = RecipeDisplayDrawing.metadataRowHeights(recipe.details(), Math.max(1, width - 16));
        int[] index = {0};
        MachineRecipeEmiWidget widget = new MachineRecipeEmiWidget(recipe, width, holder.getHeight(), row -> heights.get(index[0]++));
        holder.addDrawable(0, 0, width, holder.getHeight(), (graphics, mouseX, mouseY, delta) ->
                RecipeDisplayDrawing.drawBackground(graphics, false, 0, 0, width, holder.getHeight()));
        holder.add(widget);
        widget.slots.forEach(holder::add);
    }

    // Measured row heights are injected for pure widget-state tests; production uses the shared font measurement.
    MachineRecipeEmiWidget(MachineEmiRecipe recipe, int width, int height, ToIntFunction<RecipeMetadataRow> measure) {
        this.width = width;
        this.height = height;
        capacity = Math.max(18, height - 30);
        rows = recipe.details();
        rowHeights = rows.stream().map(measure::applyAsInt).toList();
        pages = RecipeDetailsLayout.pages(rowHeights, capacity);
        var order = Comparator.comparingInt((MachineEmiRecipe.Resource resource) -> RecipeSlotLayout.kindOrder(switch (resource.entry().kind()) {
            case SOURCE -> RecipeSlotLayout.Kind.SOURCE;
            case FLUID -> RecipeSlotLayout.Kind.FLUID;
            case CHEMICAL -> RecipeSlotLayout.Kind.CHEMICAL;
            case ITEM -> RecipeSlotLayout.Kind.ITEM;
            case TEXT -> RecipeSlotLayout.Kind.OTHER;
        }));
        var inputs = recipe.resources().stream().filter(resource -> resource.entry().io() == IOType.INPUT)
                .filter(resource -> !resource.ingredient().isEmpty()).sorted(order).toList();
        var outputs = recipe.resources().stream().filter(resource -> resource.entry().io() == IOType.OUTPUT)
                .filter(resource -> !resource.ingredient().isEmpty()).sorted(order).toList();
        layout = RecipeSlotLayout.forCounts(inputs.size(), outputs.size());
        addSlots(recipe, inputs, layout.inputs());
        addSlots(recipe, outputs, layout.outputs());
        if (slots.isEmpty() && !pages.isEmpty()) page = 1;
    }

    private void addSlots(MachineEmiRecipe recipe, List<MachineEmiRecipe.Resource> resources, List<RecipeSlotLayout.Position> positions) {
        for (int index = 0; index < resources.size(); index++) {
            var resource = resources.get(index);
            var position = positions.get(index);
            EmiRecipeSlotWidget slot = new EmiRecipeSlotWidget(resource.entry(), resource.ingredient(), () -> slotBounds(position));
            if (resource.entry().io() == IOType.OUTPUT) slot.recipeContext(recipe);
            slots.add(slot);
        }
    }

    List<EmiRecipeSlotWidget> slots() { return List.copyOf(slots); }
    private Bounds slotBounds(RecipeSlotLayout.Position position) {
        if (closed || page != 0) return Bounds.EMPTY;
        var viewport = viewport();
        int y = position.y() - scrollY;
        // Partially clipped icons never become an invisible shortcut/query target.
        if (position.x() < viewport.x() || position.x() + 18 > viewport.x() + viewport.width()
                || y < viewport.y() || y + 18 > viewport.y() + viewport.height()) return Bounds.EMPTY;
        return new Bounds(position.x(), y, 18, 18);
    }

    public RecipeDetailsLayout.Viewport viewport() {
        return new RecipeDetailsLayout.Viewport(8, 8, Math.max(1, width - 16), capacity, scrollY);
    }

    public boolean changePage(int direction) {
        int next = page + direction;
        int first = slots.isEmpty() ? 1 : 0;
        if (closed || direction == 0 || next < first || next > pages.size()) return false;
        page = next;
        scrollY = 0;
        return true;
    }

    /** Scroll this page before allowing page navigation at its boundaries. */
    public boolean scroll(int direction) {
        if (closed || direction == 0) return false;
        int maximum = page == 0 ? Math.max(0, layout.bottom() - 8 - capacity) : pages.get(page - 1).maxScrollY(capacity);
        int next = Math.max(0, Math.min(maximum, scrollY + direction * 18));
        if (next == scrollY) return false;
        scrollY = next;
        return true;
    }

    int rowAt(double x, double y) {
        var viewport = viewport();
        if (closed || page == 0 || !viewport.contains(x, y)) return -1;
        var detail = pages.get(page - 1);
        double contentY = viewport.contentY(y);
        int top = 0;
        for (int row = detail.firstRow(); row < detail.endRow(); row++) {
            int bottom = top + rowHeights.get(row);
            if (contentY >= top && contentY < bottom) return row;
            top = bottom;
        }
        return -1;
    }

    List<RecipeMetadataRow> tooltipRows(double x, double y) {
        int row = rowAt(x, y);
        return row < 0 ? List.of() : List.of(rows.get(row));
    }

    @Override public Bounds getBounds() { return closed ? Bounds.EMPTY : new Bounds(0, 0, width, height); }
    @Override public boolean visible() { return !closed; }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (closed) return;
        var viewport = viewport();
        RecipeDisplayDrawing.drawClipped(graphics, viewport, () -> {
            if (page > 0) {
                var detail = pages.get(page - 1);
                int y = viewport.y() - scrollY;
                for (int row = detail.firstRow(); row < detail.endRow(); row++) {
                    RecipeDisplayDrawing.drawMetadataRow(graphics, rows.get(row), viewport.x(), y, viewport.width());
                    y += rowHeights.get(row);
                }
            } else if (layout.arrow() != null) {
                var arrow = layout.arrow();
                Component label = Component.literal(layout.orientation() == RecipeSlotLayout.Orientation.HORIZONTAL ? "→" : "↓");
                graphics.drawString(Minecraft.getInstance().font, label, arrow.x() + 4, arrow.y() - scrollY + 4, 0xFF404040, false);
            }
        });
        var font = Minecraft.getInstance().font;
        int first = slots.isEmpty() ? 1 : 0;
        Component label = Component.translatable("jei.mmcr.machine_recipe.details_page", page - first + 1, pages.size() - first + 1);
        graphics.drawString(font, label, Math.max(24, (width - font.width(label)) / 2), height - 14, 0xFF404040, false);
        graphics.drawString(font, Component.translatable("mmcr.auto_io.page_back"), 8, height - 14, 0xFF404040, false);
        graphics.drawString(font, Component.literal(">"), width - 16, height - 14, 0xFF404040, false);
    }

    @Override public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
        List<ClientTooltipComponent> result = new ArrayList<>();
        tooltipRows(mouseX, mouseY).forEach(row -> {
            result.add(ClientTooltipComponent.create(row.text().getVisualOrderText()));
            row.tooltip().forEach(text -> result.add(ClientTooltipComponent.create(text.getVisualOrderText())));
        });
        return result;
    }

    @Override public boolean mouseClicked(int x, int y, int button) { return mousePressed(x, y, button); }
    @Override public boolean mousePressed(double x, double y, int button) {
        if (closed || button != 0 || y < height - 18 || y >= height - 2) return false;
        if (x >= 4 && x < 24) return changePage(-1);
        if (x >= width - 24 && x < width - 4) return changePage(1);
        return false;
    }
    @Override public boolean capturesDrag(double x, double y, int button) { return false; }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (closed || vertical == 0 || !viewport().contains(x, y)) return false;
        int direction = vertical < 0 ? 1 : -1;
        return scroll(direction) || changePage(direction);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return false; }
    @Override public boolean mouseReleased(double x, double y, int button) { return false; }
    @Override public void tick() { }
    @Override public void close() { closed = true; }
}
