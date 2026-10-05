package cn.howxu.mmcr.compat.jei;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.IJeiGuiEventListener;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.joml.Vector4f;

import java.util.List;
import java.util.Optional;

/** Whole-row pages for details that cannot fit below even a single ingredient grid row.
 * Keeps JEI's real slots and tooltips; the native scroll grid only supports 18x18 cells.
 * @author howxu <dev@howxu.cn>
 */
final class MachineRecipeMetadataWidget implements ISlottedRecipeWidget, IJeiGuiEventListener {
    private final MachineRecipeCategory category;
    private final MachineRecipeDisplay recipe;
    private final MachineRecipeLayout layout;
    private final List<DetailSlot> slots;
    private int pageIndex;

    @SuppressWarnings("removal")
    MachineRecipeMetadataWidget(MachineRecipeCategory category, MachineRecipeDisplay recipe,
                                MachineRecipeLayout layout, List<IRecipeSlotDrawable> slots) {
        this.category = category;
        this.recipe = recipe;
        this.layout = layout;
        this.slots = slots.stream().map(slot -> {
            // JEI 19.57's background area is a union with the unpositioned background offset.
            // Capture the actual renderer rect before moving it into the widget's coordinates.
            var area = slot.getRect();
            return new DetailSlot(slot, area.getX(), area.getY(), area.getHeight());
        }).toList();
        positionSlots();
    }

    @Override public ScreenPosition getPosition() { return new ScreenPosition(0, layout.metadataViewportY()); }
    @Override public ScreenRectangle getArea() {
        return new ScreenRectangle(getPosition(), MachineRecipeLayout.CATEGORY_WIDTH, layout.metadataViewportHeight());
    }
    @Override public ScreenRectangle getScreenRectangle() { return getArea(); }

    private MachineRecipeLayout.MetadataPage page() { return layout.metadataPages().get(pageIndex); }

    private boolean visible(DetailSlot slot) {
        return slot.y() >= page().startY() && slot.y() + slot.height() <= page().endY();
    }

    private void positionSlots() {
        for (var slot : slots) {
            slot.drawable().setPosition(slot.x(), visible(slot) ? slot.y() - page().startY() + 1 : -1000);
        }
    }

    @Override
    public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
        if (mouseX < 0 || mouseX >= MachineRecipeLayout.CATEGORY_WIDTH || mouseY < 0
                || mouseY >= page().endY() - page().startY() + 1) return Optional.empty();
        return slots.stream().filter(this::visible).map(DetailSlot::drawable)
                .filter(slot -> slot.isMouseOver(mouseX, mouseY))
                .findFirst().map(slot -> new RecipeSlotUnderMouse(slot, getPosition()));
    }

    @Override
    public void drawWidget(GuiGraphics graphics, double mouseX, double mouseY) {
        int contentHeight = page().endY() - page().startY() + 1;
        var pose = graphics.pose().last().pose();
        var topLeft = pose.transform(new Vector4f(0, 0, 0, 1));
        var bottomRight = pose.transform(new Vector4f(MachineRecipeLayout.CATEGORY_WIDTH, contentHeight, 0, 1));
        graphics.enableScissor((int) topLeft.x(), (int) topLeft.y(), (int) bottomRight.x(), (int) bottomRight.y());
        try {
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(0, 1 - page().startY(), 0);
                category.drawMetadata(recipe, layout, graphics);
            } finally {
                graphics.pose().popPose();
            }
            for (var slot : slots) {
                if (visible(slot)) slot.drawable().draw(graphics, slot.drawable().isMouseOver(mouseX, mouseY));
            }
        } finally {
            graphics.disableScissor();
        }
        graphics.drawString(Minecraft.getInstance().font,
                Component.translatable("jei.mmcr.machine_recipe.details_page", pageIndex + 1, layout.metadataPages().size()),
                8, layout.metadataViewportHeight() - MachineRecipeLayout.PAGE_FOOTER_HEIGHT, 0xFF404040, false);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
        if (mouseY >= 1 && mouseY < page().endY() - page().startY() + 1) {
            category.metadataTooltip(tooltip, recipe, layout, mouseX, mouseY + page().startY() - 1);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) return false;
        changePage(scrollY < 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || mouseX < 8 || mouseX >= 144
                || mouseY < layout.metadataViewportHeight() - MachineRecipeLayout.PAGE_FOOTER_HEIGHT) return false;
        changePage(mouseX < 76 ? -1 : 1);
        return true;
    }

    private void changePage(int direction) {
        pageIndex = Math.clamp(pageIndex + direction, 0, layout.metadataPages().size() - 1);
        positionSlots();
    }

    /** Original logical slot placement, independent of its current page. @author howxu <dev@howxu.cn> */
    private record DetailSlot(IRecipeSlotDrawable drawable, int x, int y, int height) {}
}
