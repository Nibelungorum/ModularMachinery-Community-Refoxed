package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;

/**
 * Displays one manually selected page of the default-stage structure materials.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructureMaterialWidget implements ISlottedRecipeWidget, IJeiInputHandler {
    private static final int SLOTS_PER_PAGE = 9;
    private static final int SLOT_STEP = 17;
    private static final int BUTTON_X = SLOTS_PER_PAGE * SLOT_STEP;
    private static final int BUTTON_SIZE = 8;
    private static final int PREVIOUS_BUTTON_Y = 1;
    private static final int NEXT_BUTTON_Y = PREVIOUS_BUTTON_Y + BUTTON_SIZE;
    private static final ResourceLocation BUTTON = MMCR.id("textures/gui/jei/preview/material_list_button.png");
    private static final ResourceLocation ACTIVE_BUTTON = MMCR.id("textures/gui/jei/preview/material_list_button_enable.png");

    private final List<IRecipeSlotDrawable> slots;
    private final ScreenRectangle area;
    private int firstSlot;
    private int pressedStep;

    public StructureMaterialWidget(List<IRecipeSlotDrawable> slots, ScreenPosition origin) {
        this.slots = List.copyOf(slots);
        this.area = new ScreenRectangle(origin, BUTTON_X + BUTTON_SIZE, NEXT_BUTTON_Y + BUTTON_SIZE);
        for (int i = 0; i < slots.size(); i++) {
            slots.get(i).setPosition(i % SLOTS_PER_PAGE * SLOT_STEP, 1);
        }
    }

    @Override public ScreenPosition getPosition() { return area.position(); }
    @Override public ScreenRectangle getScreenRectangle() { return area; }
    @Override public ScreenRectangle getArea() { return area; }

    private int endSlot() {
        return Math.min(firstSlot + SLOTS_PER_PAGE, slots.size());
    }

    @Override
    public void drawWidget(GuiGraphics graphics, double mouseX, double mouseY) {
        for (int i = firstSlot; i < endSlot(); i++) {
            IRecipeSlotDrawable slot = slots.get(i);
            slot.draw(graphics, slot.isMouseOver(mouseX, mouseY));
        }
        int hovered = pageStepAt(mouseX, mouseY);
        // JEI does not deliver a release outside the recipe.
        if (pressedStep != 0 && GLFW.glfwGetMouseButton(
                Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_RELEASE) {
            pressedStep = 0;
        }
        drawButton(graphics, PREVIOUS_BUTTON_Y, 0, hovered == -1 || pressedStep == -1);
        drawButton(graphics, NEXT_BUTTON_Y, BUTTON_SIZE, hovered == 1 || pressedStep == 1);
    }

    private static void drawButton(GuiGraphics graphics, int y, int v, boolean active) {
        graphics.blit(active ? ACTIVE_BUTTON : BUTTON, BUTTON_X, y, 0.0F, v,
                BUTTON_SIZE, BUTTON_SIZE, BUTTON_SIZE, 2 * BUTTON_SIZE);
    }

    @Override
    public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
        for (int i = firstSlot; i < endSlot(); i++) {
            IRecipeSlotDrawable slot = slots.get(i);
            if (slot.isMouseOver(mouseX, mouseY)) return Optional.of(new RecipeSlotUnderMouse(slot, getPosition()));
        }
        return Optional.empty();
    }

    private int pageStepAt(double mouseX, double mouseY) {
        if (mouseX < BUTTON_X || mouseX >= BUTTON_X + BUTTON_SIZE) return 0;
        if (firstSlot > 0 && mouseY >= PREVIOUS_BUTTON_Y && mouseY < NEXT_BUTTON_Y) return -1;
        if (endSlot() < slots.size() && mouseY >= NEXT_BUTTON_Y && mouseY < NEXT_BUTTON_Y + BUTTON_SIZE) return 1;
        return 0;
    }

    @Override
    public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
        if (input.getKey().getType() != InputConstants.Type.MOUSE
                || input.getKey().getValue() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        int step = pageStepAt(mouseX, mouseY);
        if (input.isSimulate()) {
            pressedStep = step;
            return step != 0;
        }
        boolean turnPage = step != 0 && step == pressedStep;
        pressedStep = 0;
        if (turnPage) firstSlot += step * SLOTS_PER_PAGE;
        return turnPage;
    }
}
