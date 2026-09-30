package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.client.preview.StructurePreviewPanel;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import mezz.jei.api.constants.VanillaTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import mezz.jei.api.runtime.IIngredientManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.LongSupplier;

/**
 * JEI adapter for one independently-owned structure preview.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JeiStructurePreviewWidget implements IRecipeWidget, IJeiInputHandler, AutoCloseable {
    private static final float CONTROL_SCALE = 0.9F;
    private static final int CONTROL_SIZE = Math.round(15 * CONTROL_SCALE);
    private static final int CONTROL_STEP = CONTROL_SIZE + 4;
    private static final int UI_X_OFFSET = -3;
    private static final int CONTROL_Y_OFFSET = -13;
    private static final float LAYER_TEXT_SCALE = 0.9F;
    private static final int LAYER_TEXT_Y_OFFSET = -24;
    private static final int CANDIDATE_SIZE = 16;
    private static final int CANDIDATE_STEP = 18;
    private static final int LAYOUT_WIDTH = 168;
    private static final int PREVIEW_INPUT_TARGET = 1;
    private static final int CONTROL_INPUT_TARGET_BASE = 100;
    private static final int CANDIDATE_INPUT_TARGET_BASE = 200;
    private final @Nullable StructurePreviewPanel panel;
    private final @Nullable Preview testingPreview;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final @Nullable IIngredientManager ingredientManager;
    private final LongSupplier clock = System::currentTimeMillis;
    private boolean previewDragActive;
    private int pressedInputTarget = -1;
    private boolean closed;

    public JeiStructurePreviewWidget(Machine machine, int x, int y, int width, int height,
            IIngredientManager ingredientManager) {
        this.panel = new StructurePreviewPanel(machine);
        this.testingPreview = null;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.ingredientManager = ingredientManager;
    }

    private JeiStructurePreviewWidget(Preview preview, int x, int y, int width, int height) {
        this.panel = null;
        this.testingPreview = preview;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.ingredientManager = null;
    }

    static JeiStructurePreviewWidget forTesting(Preview preview, int x, int y, int width, int height) {
        return new JeiStructurePreviewWidget(preview, x, y, width, height);
    }

    @Override public ScreenPosition getPosition() { return new ScreenPosition(x, y); }
    @Override public ScreenRectangle getScreenRectangle() { return new ScreenRectangle(x, y, LAYOUT_WIDTH, height + 54); }
    @Override public ScreenRectangle getArea() { return getScreenRectangle(); }

    @Override
    public void drawWidget(GuiGraphics graphics, double mouseX, double mouseY) {
        if (previewDragActive && !Minecraft.getInstance().mouseHandler.isLeftPressed()) cancelPreviewDrag();
        if (panel == null) return;
        ScreenPosition origin = guiOrigin(graphics);
        panel.render(graphics, width, height, 0.0F, origin.x(), origin.y(),
                origin.x() + (int) mouseX, origin.y() + (int) mouseY, origin.x(), origin.y());
        if (!panel.isReady()) return;
        String[] labels = panel.hasMultipleStages() ? new String[]{"+", "-", "A", "R", "M"} : new String[]{"+", "-", "A", "R"};
        for (int index = 0; index < labels.length; index++) {
            int controlX = UI_X_OFFSET + index * CONTROL_STEP;
            int controlY = height + CONTROL_Y_OFFSET;
            graphics.fill(controlX, controlY, controlX + CONTROL_SIZE, controlY + CONTROL_SIZE, 0xFF808080);
            graphics.pose().pushPose();
            graphics.pose().translate(controlX + CONTROL_SIZE / 2.0F, controlY + CONTROL_SIZE / 2.0F, 0.0F);
            graphics.pose().scale(CONTROL_SCALE, CONTROL_SCALE, 1.0F);
            int labelWidth = Minecraft.getInstance().font.width(labels[index]);
            graphics.drawString(Minecraft.getInstance().font, Component.literal(labels[index]),
                    -labelWidth / 2, -Minecraft.getInstance().font.lineHeight / 2, 0xFFFFFFFF, false);
            graphics.pose().popPose();
        }
        int selectedLayer = panel.selectedLayer();
        StructurePreviewSchema schema = panel.schema();
        List<Integer> layers = schema == null ? List.of() : schema.layers();
        Component layerText = selectedLayer < 0
                ? Component.translatable("jei.mmcr.structure_preview.all_layers")
                : Component.translatable("jei.mmcr.structure_preview.layer", selectedLayer, layers.indexOf(selectedLayer) + 1, layers.size());
        graphics.pose().pushPose();
        graphics.pose().scale(LAYER_TEXT_SCALE, LAYER_TEXT_SCALE, 1.0F);
        int layerTextY = (int) ((height + LAYER_TEXT_Y_OFFSET) / LAYER_TEXT_SCALE);
        int layerTextX = (int) (UI_X_OFFSET / LAYER_TEXT_SCALE);
        graphics.drawString(Minecraft.getInstance().font, layerText, layerTextX, layerTextY, 0xFFFFFFFF, false);
        if (panel.hasMultipleStages()) {
            int levelTextX = Minecraft.getInstance().font.width(layerText) + 4;
            graphics.drawString(Minecraft.getInstance().font, Component.literal("Level=" + panel.stageNumber()),
                    (int) ((UI_X_OFFSET + levelTextX) / LAYER_TEXT_SCALE), layerTextY, 0xFFFFFFFF, false);
        }
        graphics.pose().popPose();
        renderCandidates(graphics);
    }

    private void renderCandidates(GuiGraphics graphics) {
        if (panel == null) return;
        List<StructurePreviewSchema.Candidate> candidates = panel.selectedCandidates();
        if (candidates.isEmpty()) return;
        int visibleCount = Math.min(maxVisibleCandidates(), candidates.size());
        long timeMillis = clock.getAsLong();
        for (int index = 0; index < visibleCount; index++) {
            StructurePreviewSchema.Candidate candidate = candidateForSlot(candidates, index, timeMillis);
            if (candidate != null) graphics.renderItem(candidate.stack(), 0, index * CANDIDATE_STEP, 0);
        }
    }

    private StructurePreviewSchema.@Nullable Candidate candidateForSlot(
            List<StructurePreviewSchema.Candidate> candidates, int slot, long timeMillis) {
        if (panel == null) return null;
        if (slot == 0) return candidates.getFirst();
        int remainingCandidates = candidates.size() - 1;
        if (remainingCandidates == 0) return null;
        int offset = (int) Math.floorDiv(timeMillis, 1_000L) % remainingCandidates;
        return candidates.get(1 + Math.floorMod(slot - 1 + offset, remainingCandidates));
    }

    private static ScreenPosition guiOrigin(GuiGraphics graphics) {
        var pose = graphics.pose().last().pose();
        return new ScreenPosition((int) Math.round(pose.m30()), (int) Math.round(pose.m31()));
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        cancelPreviewDrag();
        if (panel != null) panel.close();
        if (testingPreview != null) testingPreview.close();
    }

    @Override
    public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
        boolean simulate = input.isSimulate();
        if (input.getKey().getType() != InputConstants.Type.MOUSE) {
            if (!simulate) {
                pressedInputTarget = -1;
                cancelPreviewDrag();
            }
            return false;
        }
        int button = input.getKey().getValue();
        if (button != 0 || !isReady()) {
            if (!simulate) {
                pressedInputTarget = -1;
                cancelPreviewDrag();
            }
            return false;
        }
        int inputTarget = inputTarget(mouseX, mouseY);
        if (simulate) {
            pressedInputTarget = inputTarget;
            return inputTarget >= 0;
        }
        int pressedTarget = pressedInputTarget;
        pressedInputTarget = -1;
        if (pressedTarget != inputTarget) {
            cancelPreviewDrag();
            return false;
        }
        if (previewDragActive) {
            previewDragActive = false;
            boolean inside = insidePreview(mouseX, mouseY);
            boolean handled = mouseReleased(previewMouseX(mouseX), previewMouseY(mouseY), button);
            return inside || handled;
        }
        int control = controlAt(mouseX, mouseY);
        if (control >= 0) {
            switch (control) {
                case 0 -> selectPreviousLayer();
                case 1 -> selectNextLayer();
                case 2 -> showAllLayers();
                case 3 -> reset();
                case 4 -> selectNextStage();
                default -> { }
            }
            previewDragActive = false;
            return true;
        }
        if (candidateAt(mouseX, mouseY) >= 0) {
            return true;
        }
        if (!insidePreview(mouseX, mouseY)) return false;
        if (!mouseClicked(previewMouseX(mouseX), previewMouseY(mouseY), button)) return false;
        return mouseReleased(previewMouseX(mouseX), previewMouseY(mouseY), button);
    }

    @Override
    public boolean handleMouseDragged(double mouseX, double mouseY, InputConstants.Key mouseKey, double dragX, double dragY) {
        if (mouseKey.getType() != InputConstants.Type.MOUSE || mouseKey.getValue() != 0 || !isReady()
                || controlAt(mouseX, mouseY) >= 0 || candidateAt(mouseX, mouseY) >= 0
                || !insidePreview(mouseX, mouseY)) {
            cancelPreviewDrag();
            return false;
        }
        if (!previewDragActive) {
            double pressX = previewMouseX(mouseX - dragX);
            double pressY = previewMouseY(mouseY - dragY);
            previewDragActive = mouseClicked(pressX, pressY, mouseKey.getValue());
            if (!previewDragActive) return false;
        }
        boolean handled = mouseDragged(previewMouseX(mouseX), previewMouseY(mouseY), mouseKey.getValue(), dragX, dragY);
        if (!handled) cancelPreviewDrag();
        return handled;
    }

    @Override
    public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        cancelPreviewDrag();
        return controlAt(mouseX, mouseY) < 0 && candidateAt(mouseX, mouseY) < 0 && insidePreview(mouseX, mouseY)
                && mouseScrolled(previewMouseX(mouseX), previewMouseY(mouseY), scrollDeltaY);
    }

    private void cancelPreviewDrag() {
        pressedInputTarget = -1;
        if (!previewDragActive) return;
        previewDragActive = false;
        mouseReleased(-1.0D, -1.0D, 0);
    }

    private int candidateAt(double mouseX, double mouseY) {
        if (panel == null) return -1;
        int count = Math.min(maxVisibleCandidates(), panel.selectedCandidates().size());
        if (mouseX < 0 || mouseX >= CANDIDATE_SIZE || mouseY < 0) return -1;
        int index = (int) Math.floor(mouseY / CANDIDATE_STEP);
        return index < count && mouseY < index * CANDIDATE_STEP + CANDIDATE_SIZE ? index : -1;
    }

    private int inputTarget(double mouseX, double mouseY) {
        int control = controlAt(mouseX, mouseY);
        if (control >= 0) return CONTROL_INPUT_TARGET_BASE + control;
        int candidate = candidateAt(mouseX, mouseY);
        if (candidate >= 0) return CANDIDATE_INPUT_TARGET_BASE + candidate;
        return insidePreview(mouseX, mouseY) ? PREVIEW_INPUT_TARGET : -1;
    }

    private static int maxVisibleCandidates() {
        return switch ((int) Minecraft.getInstance().getWindow().getGuiScale()) {
            case 1 -> 12;
            case 2 -> 10;
            case 3 -> 8;
            default -> 4;
        };
    }

    private boolean insidePreview(double mouseX, double mouseY) {
        return mouseX >= 0 && mouseX < width && mouseY >= 0 && mouseY < height;
    }
    private double previewMouseX(double mouseX) { return mouseX; }
    private double previewMouseY(double mouseY) { return mouseY; }
    private int controlAt(double mouseX, double mouseY) {
        if (mouseX < 0 || mouseY < height + CONTROL_Y_OFFSET || mouseY >= height + CONTROL_Y_OFFSET + CONTROL_SIZE) return -1;
        int relativeX = (int) Math.floor(mouseX - UI_X_OFFSET);
        if (relativeX < 0) return -1;
        int column = relativeX / CONTROL_STEP;
        int controls = panel != null && panel.hasMultipleStages() ? 5 : 4;
        return column >= 0 && column < controls
                && mouseX < UI_X_OFFSET + column * CONTROL_STEP + CONTROL_SIZE ? column : -1;
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
        if (!isReady()) return;
        int control = controlAt(mouseX, mouseY);
        if (control >= 0) {
            String[] keys = {"previous_layer", "next_layer", "all_layers", "reset", "next_level"};
            tooltip.add(Component.translatable("jei.mmcr.structure_preview." + keys[control]));
            return;
        }
        int candidateIndex = candidateAt(mouseX, mouseY);
        if (candidateIndex >= 0 && panel != null) {
            List<StructurePreviewSchema.Candidate> candidates = panel.selectedCandidates();
            StructurePreviewSchema.Candidate candidate = candidateForSlot(candidates, candidateIndex, clock.getAsLong());
            if (candidate == null) return;
            ItemStack stack = candidate.stack();
            tooltip.add(stack.getHoverName());
            if (candidate.modifier()) tooltip.add(Component.translatable("jei.mmcr.structure_preview.modifier"));
            ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, stack, false)
                    .ifPresent(tooltip::setIngredient);
        }
    }

    private boolean isReady() {
        return testingPreview != null || panel != null && panel.isReady();
    }

    private boolean mouseClicked(double mouseX, double mouseY, int button) {
        return testingPreview != null
                ? testingPreview.mouseClicked(mouseX, mouseY, button)
                : panel.mouseClicked(mouseX, mouseY, button);
    }

    private boolean mouseReleased(double mouseX, double mouseY, int button) {
        return testingPreview != null
                ? testingPreview.mouseReleased(mouseX, mouseY, button)
                : panel.mouseReleased(mouseX, mouseY, button);
    }

    private boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return testingPreview != null
                ? testingPreview.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                : panel.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        return testingPreview != null
                ? testingPreview.mouseScrolled(mouseX, mouseY, scrollDelta)
                : panel.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    private void selectPreviousLayer() {
        if (testingPreview != null) testingPreview.previous();
        else panel.selectPreviousLayer();
    }

    private void selectNextLayer() {
        if (testingPreview != null) testingPreview.next();
        else panel.selectNextLayer();
    }

    private void showAllLayers() {
        if (testingPreview != null) testingPreview.all();
        else panel.showAllLayers();
    }

    private void reset() {
        if (testingPreview != null) testingPreview.reset();
        else panel.reset();
    }

    private void selectNextStage() {
        if (panel != null) panel.selectNextStage();
    }

    interface Preview {
        boolean mouseClicked(double mouseX, double mouseY, int button);
        default boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }
        default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return false; }
        default boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) { return false; }
        default void close() { }
        default void previous() { }
        default void next() { }
        default void all() { }
        default void reset() { }
    }

}
