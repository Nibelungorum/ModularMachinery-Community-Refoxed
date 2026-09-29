package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.client.preview.StructureMaterialSummary.Entry;
import cn.howxu.mmcr.client.preview.StructurePreviewPanel;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema.Candidate;
import cn.howxu.mmcr.internal.network.PktBlueprintStageUpdatePayload;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.util.ReadableNumber;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Client-side host for the standalone blueprint structure preview.
 * @author howxu <dev@howxu.cn>
 */
public final class BlueprintScreen extends Screen {
    private static final int BASE_WIDTH = 420;
    private static final int BASE_HEIGHT = 330;
    private static final int OUTER_MARGIN = 8;
    private static final int GAP = 6;
    private static final float MATERIAL_QUANTITY_SCALE = 0.6F;
    private static final int PREVIEW_HEIGHT = 190;
    private static final int SLOT_ICON_OFFSET = 4;
    static final int MATERIAL_SLOT_SIZE = 25;
    static final int MATERIAL_COLUMNS = 9;
    static final int MATERIAL_SLOT_GAP = 2;
    private static final int PREVIEW_WIDTH = 10 * MATERIAL_SLOT_SIZE + 9 * MATERIAL_SLOT_GAP;
    private static final int MATERIAL_TO_CANDIDATES_GAP = 10;
    private static final int MATERIAL_CONTENT_LEFT_PADDING = MATERIAL_SLOT_GAP + 4;
    private static final int MATERIAL_CONTENT_TOP_PADDING = MATERIAL_SLOT_GAP + 5;
    private static final int CANDIDATE_CONTENT_LEFT_PADDING = MATERIAL_SLOT_GAP + 5;
    private static final int CANDIDATE_TITLE_LEFT_OFFSET = 4;
    private static final int CANDIDATE_TITLE_TOP_OFFSET = 8;
    private static final int SCROLLBAR_WIDTH = 12;
    private static final int SCROLLBAR_HANDLE_HEIGHT = 32;
    private static final int MATERIAL_SCROLLBAR_LEFT_OFFSET = 4;
    private static final int MATERIAL_SCROLLBAR_TOP_OFFSET = 6;
    private static final int MATERIAL_SCROLLBAR_BOTTOM_INSET = 10;
    private static final int CANDIDATE_SCROLLBAR_LEFT_OFFSET = 5;
    private static final int CANDIDATE_SCROLLBAR_TOP_OFFSET = -6;
    private static final int CANDIDATE_SCROLLBAR_BOTTOM_INSET = 4;
    private static final int CANDIDATE_COLUMNS = 4;
    private static final int CANDIDATE_SLOT_SIZE = MATERIAL_SLOT_SIZE - 2;
    private static final int CANDIDATE_ROW_GAP = MATERIAL_SLOT_GAP + 4;
    private static final int CANDIDATE_TITLE_HEIGHT = 12;
    private static final int CANDIDATE_CONTENT_TOP_PADDING = CANDIDATE_TITLE_HEIGHT + MATERIAL_SLOT_GAP + 10;
    private static final int SELECTED_BLOCK_LINE_STEP = 12;
    private static final float BLUEPRINT_TITLE_SCALE = 1.2F;
    private static final int BUTTON_HEIGHT = 18;
    private static final int TEXT_COLOR = 0xFF202020;
    private static final int PANEL_COLOR = 0xFFB6E4F2;
    private static final int OUTER_BORDER_COLOR = 0xFF40798B;
    private static final int INNER_BORDER_COLOR = 0xFFDDF4FA;
    private static final int SLOT_FRAME_DARK = 0xFF373737;
    private static final int SLOT_FRAME_LIGHT = 0xFF8B8B8B;
    private static final int MATERIAL_LIST_TEXTURE_WIDTH = 268;
    private static final int MATERIAL_LIST_TEXTURE_HEIGHT = 118;
    private static final int SELECTED_LIST_TEXTURE_WIDTH = 126;
    private static final int SELECTED_LIST_TEXTURE_HEIGHT = 148;
    private static final ResourceLocation SCROLLER = MMCR.id("textures/gui/scroller.png");
    private static final ResourceLocation MATERIAL_LIST = MMCR.id("textures/gui/blueprint/material_list.png");
    private static final ResourceLocation SELECTED_LIST = MMCR.id("textures/gui/blueprint/selected_list.png");

    private final Machine machine;
    private final ItemStack blueprint;
    private StructurePreviewPanel panel;
    private @Nullable BlueprintLayout layout;
    private @Nullable Button previousLayerButton;
    private @Nullable Button nextLayerButton;
    private @Nullable Button allLayersButton;
    private @Nullable Button resetButton;
    private @Nullable Button nextStageButton;
    private @Nullable Button previousStageButton;
    private int materialScrollOffset;
    private int candidateScrollOffset;
    private boolean draggingMaterialScrollbar;
    private boolean draggingCandidateScrollbar;
    private int materialScrollbarDragOffsetY;
    private int candidateScrollbarDragOffsetY;
    private boolean closed;

    public BlueprintScreen(Machine machine, ItemStack blueprint) {
        super(Component.translatable("gui.mmcr.blueprint.title", machine.displayName()));
        this.machine = Objects.requireNonNull(machine, "machine");
        this.blueprint = Objects.requireNonNull(blueprint, "blueprint");
        this.panel = new StructurePreviewPanel(machine,
                blueprint.getOrDefault(ModDataComponents.BLUEPRINT_STAGE.get(), 0));
    }

    public Rect2i guiBounds() {
        BlueprintLayout currentLayout = layout != null ? layout : layoutFor(width, height, panel.hasMultipleStages());
        BlueprintRect bounds = scaledRect(currentLayout.left(), currentLayout.top(), 0, 0,
                BASE_WIDTH, BASE_HEIGHT, currentLayout.scale());
        return new Rect2i(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    public @Nullable HoveredIngredient hoveredIngredient(double mouseX, double mouseY) {
        BlueprintLayout currentLayout = layout;
        if (currentLayout == null) return null;

        List<Entry> entries = panel.materials().entries();
        int visibleMaterialRows = visibleMaterialRows(currentLayout);
        int firstMaterial = clampScrollOffset(materialScrollOffset, materialRows(entries), visibleMaterialRows) * MATERIAL_COLUMNS;
        for (int visible = 0; visible < visibleMaterialRows * MATERIAL_COLUMNS; visible++) {
            int index = firstMaterial + visible;
            if (index >= entries.size()) break;
            BlueprintRect slot = materialSlotRect(currentLayout, visible);
            if (slot.contains(mouseX, mouseY)) {
                return new HoveredIngredient(entries.get(index).stack().copyWithCount(1), rect(slot));
            }
        }

        List<Candidate> candidates = panel.selectedPosition() == null ? List.of() : panel.selectedCandidates();
        int visibleCandidateRows = visibleCandidateRows(currentLayout);
        int firstCandidate = clampScrollOffset(candidateScrollOffset, candidateRows(candidates), visibleCandidateRows)
                * CANDIDATE_COLUMNS;
        for (int visible = 0; visible < visibleCandidateRows * CANDIDATE_COLUMNS; visible++) {
            int index = firstCandidate + visible;
            if (index >= candidates.size()) break;
            BlueprintRect slot = candidateSlotRect(currentLayout, visible);
            if (slot.contains(mouseX, mouseY)) {
                return new HoveredIngredient(candidates.get(index).stack().copyWithCount(1), rect(slot));
            }
        }
        return null;
    }

    @Override
    protected void init() {
        if (closed) {
            // JEI restores this same screen instance after its recipe page closes.
            closed = false;
            panel = new StructurePreviewPanel(machine,
                    blueprint.getOrDefault(ModDataComponents.BLUEPRINT_STAGE.get(), 0));
        }
        super.init();
        BlueprintLayout currentLayout = layoutFor(width, height, panel.hasMultipleStages());
        layout = currentLayout;
        previousLayerButton = addRenderableWidget(symbolButton("+", currentLayout.layerButtons()[0],
                "jei.mmcr.structure_preview.previous_layer", panel::selectPreviousLayer));
        nextLayerButton = addRenderableWidget(symbolButton("-", currentLayout.layerButtons()[1],
                "jei.mmcr.structure_preview.next_layer", panel::selectNextLayer));
        allLayersButton = addRenderableWidget(symbolButton("A", currentLayout.layerButtons()[2],
                "jei.mmcr.structure_preview.all_layers", panel::showAllLayers));
        resetButton = addRenderableWidget(symbolButton("R", currentLayout.layerButtons()[3],
                "jei.mmcr.structure_preview.reset", panel::reset));
        if (currentLayout.nextStageButton() != null) {
            nextStageButton = addRenderableWidget(textButton(
                    "gui.mmcr.blueprint.next_level", currentLayout.nextStageButton(),
                    "jei.mmcr.structure_preview.next_level", this::selectNextStage));
            if (currentLayout.previousStageButton() != null) {
                previousStageButton = addRenderableWidget(textButton(
                        "gui.mmcr.blueprint.previous_level", currentLayout.previousStageButton(),
                        "jei.mmcr.structure_preview.previous_level", this::selectPreviousStage));
            }
        }
    }

    @Override
    public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        BlueprintLayout currentLayout = layout;
        if (currentLayout == null) return;

        super.render(graphics, mouseX, mouseY, partialTick);
        renderLabels(graphics, currentLayout);
        renderItems(graphics, currentLayout, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        BlueprintLayout currentLayout = layout;
        if (currentLayout == null) return;

        drawPanel(graphics, currentLayout);
        drawGridBackgrounds(graphics, currentLayout);
        BlueprintRect preview = currentLayout.preview();
        graphics.fill(preview.x(), preview.y(), preview.x() + preview.width(), preview.y() + preview.height(), 0xFF000000);
        graphics.enableScissor(preview.x(), preview.y(), preview.x() + preview.width(), preview.y() + preview.height());
        try {
            panel.render(graphics, preview.width(), preview.height(), partialTick,
                    preview.x(), preview.y(), mouseX, mouseY, preview.x(), preview.y());
        } finally {
            graphics.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        BlueprintLayout currentLayout = layout;
        if (currentLayout != null && button == 0
                && materialScrollbarRect(currentLayout).contains(mouseX, mouseY)) {
            beginMaterialScrollbarDrag(mouseY, currentLayout);
            return true;
        }
        if (currentLayout != null && panel.selectedPosition() != null && button == 0
                && candidateScrollbarRect(currentLayout).contains(mouseX, mouseY)) {
            beginCandidateScrollbarDrag(mouseY, currentLayout);
            return true;
        }
        if (currentLayout != null && gridContains(currentLayout, mouseX, mouseY)) return true;
        if (currentLayout != null && currentLayout.preview().contains(mouseX, mouseY)) {
            return panel.mouseClicked(previewX(mouseX), previewY(mouseY), button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        BlueprintLayout currentLayout = layout;
        if (button == 0 && (draggingMaterialScrollbar || draggingCandidateScrollbar)) {
            draggingMaterialScrollbar = false;
            draggingCandidateScrollbar = false;
            return true;
        }
        if (currentLayout != null && (materialScrollbarRect(currentLayout).contains(mouseX, mouseY)
                || panel.selectedPosition() != null && candidateScrollbarRect(currentLayout).contains(mouseX, mouseY))) return true;
        if (currentLayout != null && gridContains(currentLayout, mouseX, mouseY)) return true;
        if (currentLayout != null && currentLayout.preview().contains(mouseX, mouseY)) {
            return panel.mouseReleased(previewX(mouseX), previewY(mouseY), button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        BlueprintLayout currentLayout = layout;
        if (currentLayout != null && draggingMaterialScrollbar) {
            materialScrollOffset = materialScrollOffsetFrom(mouseY, currentLayout);
            return true;
        }
        if (currentLayout != null && draggingCandidateScrollbar) {
            candidateScrollOffset = candidateScrollOffsetFrom(mouseY, currentLayout);
            return true;
        }
        if (currentLayout != null && gridContains(currentLayout, mouseX, mouseY)) return true;
        if (currentLayout != null && currentLayout.preview().contains(mouseX, mouseY)) {
            float scale = currentLayout.scale();
            return panel.mouseDragged(previewX(mouseX), previewY(mouseY), button, dragX / scale, dragY / scale);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        BlueprintLayout currentLayout = layout;
        if (currentLayout != null && materialScrollbarRect(currentLayout).contains(mouseX, mouseY)) {
            scrollMaterials(scrollY, currentLayout);
            return true;
        }
        if (currentLayout != null && panel.selectedPosition() != null
                && candidateScrollbarRect(currentLayout).contains(mouseX, mouseY)) {
            scrollCandidates(scrollY, currentLayout);
            return true;
        }
        if (currentLayout != null && currentLayout.materials().contains(mouseX, mouseY)) {
            scrollMaterials(scrollY, currentLayout);
            return true;
        }
        if (currentLayout != null && panel.selectedPosition() != null
                && currentLayout.candidates().contains(mouseX, mouseY)) {
            scrollCandidates(scrollY, currentLayout);
            return true;
        }
        if (currentLayout != null && currentLayout.preview().contains(mouseX, mouseY)) {
            return panel.mouseScrolled(previewX(mouseX), previewY(mouseY), scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void removed() {
        if (closed) return;
        closed = true;
        panel.close();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    static BlueprintLayout layoutFor(int screenWidth, int screenHeight, boolean multipleStages) {
        float scale = Math.min(1.0F, Math.min((screenWidth - 2.0F * OUTER_MARGIN) / BASE_WIDTH,
                (screenHeight - 2.0F * OUTER_MARGIN) / BASE_HEIGHT));
        int left = Math.round((screenWidth - BASE_WIDTH * scale) / 2.0F);
        int top = Math.round((screenHeight - BASE_HEIGHT * scale) / 2.0F);

        int contentWidth = BASE_WIDTH - 2 * OUTER_MARGIN;
        int candidatesX = OUTER_MARGIN + PREVIEW_WIDTH + MATERIAL_TO_CANDIDATES_GAP;
        int candidatesWidth = contentWidth - (candidatesX - OUTER_MARGIN);
        int previewBottom = OUTER_MARGIN + PREVIEW_HEIGHT;
        int materialsY = previewBottom + GAP;
        int materialsHeight = BASE_HEIGHT - OUTER_MARGIN - materialsY;
        int titleY = OUTER_MARGIN;
        int selectedBlockY = titleY + BUTTON_HEIGHT + GAP;
        int selectedBlockHeight = SELECTED_BLOCK_LINE_STEP;
        int candidatesY = selectedBlockY + selectedBlockHeight + GAP;
        int candidatesHeight = previewBottom - candidatesY;
        int buttonWidth = (candidatesWidth - GAP) / 2;
        int controlsRows = multipleStages ? 4 : 2;
        int controlsHeight = controlsRows * BUTTON_HEIGHT + (controlsRows - 1) * GAP;
        int controlsY = BASE_HEIGHT - OUTER_MARGIN - controlsHeight;
        BlueprintRect[] layerButtons = new BlueprintRect[]{
                scaledRect(left, top, candidatesX, controlsY, buttonWidth, BUTTON_HEIGHT, scale),
                scaledRect(left, top, candidatesX + buttonWidth + GAP, controlsY, buttonWidth, BUTTON_HEIGHT, scale),
                scaledRect(left, top, candidatesX, controlsY + BUTTON_HEIGHT + GAP, buttonWidth, BUTTON_HEIGHT, scale),
                scaledRect(left, top, candidatesX + buttonWidth + GAP, controlsY + BUTTON_HEIGHT + GAP,
                        buttonWidth, BUTTON_HEIGHT, scale)
        };
        BlueprintRect nextStage = multipleStages
                ? scaledRect(left, top, candidatesX, controlsY + 2 * (BUTTON_HEIGHT + GAP),
                        buttonWidth * 2 + GAP, BUTTON_HEIGHT, scale)
                : null;
        BlueprintRect previousStage = multipleStages
                ? scaledRect(left, top, candidatesX, controlsY + 3 * (BUTTON_HEIGHT + GAP),
                        buttonWidth * 2 + GAP, BUTTON_HEIGHT, scale)
                : null;
        return new BlueprintLayout(left, top, scale,
                scaledRect(left, top, OUTER_MARGIN, OUTER_MARGIN, PREVIEW_WIDTH, PREVIEW_HEIGHT, scale),
                scaledRect(left, top, OUTER_MARGIN, materialsY, PREVIEW_WIDTH, materialsHeight, scale),
                scaledRect(left, top, candidatesX, candidatesY, candidatesWidth, candidatesHeight, scale),
                scaledRect(left, top, candidatesX, titleY, candidatesWidth, BUTTON_HEIGHT, scale),
                scaledRect(left, top, candidatesX, selectedBlockY, candidatesWidth, selectedBlockHeight, scale),
                scaledRect(left, top, candidatesX, controlsY, candidatesWidth, controlsHeight, scale),
                layerButtons, nextStage, previousStage);
    }

    static int clampScrollOffset(int offset, int totalRows, int visibleRows) {
        return Math.clamp(offset, 0, Math.max(0, totalRows - visibleRows));
    }

    static int scrollbarHandleY(int offset, int totalRows, int visibleRows,
            int trackY, int trackHeight, int handleHeight) {
        int range = Math.max(0, totalRows - visibleRows);
        if (range == 0) return trackY;
        int travel = trackHeight - handleHeight;
        return trackY + clampScrollOffset(offset, totalRows, visibleRows) * travel / range;
    }

    static int scrollOffsetFromScrollbarY(int mouseY, int totalRows, int visibleRows,
            int trackY, int trackHeight, int handleHeight, int dragOffsetY) {
        int range = Math.max(0, totalRows - visibleRows);
        if (range == 0) return 0;
        int travel = trackHeight - handleHeight;
        int handleY = Math.clamp(mouseY - trackY - dragOffsetY, 0, travel);
        return clampScrollOffset(Math.round((float) handleY * range / travel), totalRows, visibleRows);
    }

    static int baseHeight() {
        return BASE_HEIGHT;
    }

    static double localMouse(double mouse, int origin, float scale) {
        return (mouse - origin) / scale;
    }

    static double previewMouse(double mouse, int origin) {
        return mouse - origin;
    }

    private Button symbolButton(String symbol, BlueprintRect rect, String tooltipKey, Runnable action) {
        Button button = new StyledButton(rect.x(), rect.y(), rect.width(), rect.height(),
                Component.literal(symbol), clicked -> action.run());
        button.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        return button;
    }

    private Button textButton(String key, BlueprintRect rect, String tooltipKey, Runnable action) {
        Button button = new StyledButton(rect.x(), rect.y(), rect.width(), rect.height(),
                Component.translatable(key), clicked -> action.run());
        button.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        return button;
    }

    private void selectNextStage() {
        if (!panel.isReady()) return;
        panel.selectNextStage();
        saveStage();
    }

    private void selectPreviousStage() {
        if (!panel.isReady()) return;
        panel.selectPreviousStage();
        saveStage();
    }

    private void saveStage() {
        int stageNumber = panel.stageNumber();
        blueprint.set(ModDataComponents.BLUEPRINT_STAGE.get(), stageNumber);
        PacketDistributor.sendToServer(new PktBlueprintStageUpdatePayload(stageNumber));
    }

    private void drawPanel(GuiGraphics graphics, BlueprintLayout currentLayout) {
        int left = currentLayout.left();
        int top = currentLayout.top();
        int right = left + Math.round(BASE_WIDTH * currentLayout.scale());
        int bottom = top + Math.round(BASE_HEIGHT * currentLayout.scale());
        graphics.fill(left, top, right, bottom, PANEL_COLOR);
        graphics.fill(left, top, right, top + 1, OUTER_BORDER_COLOR);
        graphics.fill(left, bottom - 1, right, bottom, OUTER_BORDER_COLOR);
        graphics.fill(left, top, left + 1, bottom, OUTER_BORDER_COLOR);
        graphics.fill(right - 1, top, right, bottom, OUTER_BORDER_COLOR);
        graphics.fill(left + 1, top + 1, right - 1, top + 2, INNER_BORDER_COLOR);
        graphics.fill(left + 1, bottom - 2, right - 1, bottom - 1, INNER_BORDER_COLOR);
        graphics.fill(left + 1, top + 1, left + 2, bottom - 1, INNER_BORDER_COLOR);
        graphics.fill(right - 2, top + 1, right - 1, bottom - 1, INNER_BORDER_COLOR);
    }

    private void renderLabels(GuiGraphics graphics, BlueprintLayout currentLayout) {
        graphics.pose().pushPose();
        graphics.pose().translate(currentLayout.left(), currentLayout.top(), 0);
        graphics.pose().scale(currentLayout.scale(), currentLayout.scale(), 1.0F);
        int titleX = (int) Math.round(localMouse(currentLayout.title().x(), currentLayout.left(), currentLayout.scale()));
        int titleY = (int) Math.round(localMouse(currentLayout.title().y(), currentLayout.top(), currentLayout.scale()));
        graphics.pose().pushPose();
        graphics.pose().translate(titleX + 4, titleY + 3, 0);
        graphics.pose().scale(BLUEPRINT_TITLE_SCALE, BLUEPRINT_TITLE_SCALE, 1.0F);
        graphics.drawString(font, title, 0, 0, TEXT_COLOR, false);
        graphics.pose().popPose();
        int selectedBlockX = (int) Math.round(localMouse(currentLayout.selectedBlock().x(), currentLayout.left(),
                currentLayout.scale()));
        int selectedBlockY = (int) Math.round(localMouse(currentLayout.selectedBlock().y(), currentLayout.top(),
                currentLayout.scale()));
        int selectedLayer = panel.selectedLayer();
        Component layer = selectedLayer < 0
                ? Component.translatable("jei.mmcr.structure_preview.all_layers")
                : Component.literal(Integer.toString(selectedLayer));
        Component layerText = Component.translatable("gui.mmcr.blueprint.y", layer);
        graphics.drawString(font, layerText, selectedBlockX + 4, selectedBlockY + 4,
                TEXT_COLOR, false);
        if (panel.hasMultipleStages()) {
            graphics.drawString(font, Component.translatable("gui.mmcr.blueprint.level", panel.stageNumber()),
                    selectedBlockX + 4 + font.width(layerText) + GAP, selectedBlockY + 4, TEXT_COLOR, false);
        }
        if (panel.selectedPosition() != null) {
            int candidatesX = (int) Math.round(localMouse(currentLayout.candidates().x(), currentLayout.left(), currentLayout.scale()));
            int candidatesY = (int) Math.round(localMouse(currentLayout.candidates().y(), currentLayout.top(), currentLayout.scale()));
            graphics.drawString(font, Component.translatable("gui.mmcr.blueprint.candidates"),
                    candidatesX + MATERIAL_SLOT_GAP + CANDIDATE_TITLE_LEFT_OFFSET,
                    candidatesY + MATERIAL_SLOT_GAP + CANDIDATE_TITLE_TOP_OFFSET,
                    TEXT_COLOR, false);
        }
        graphics.pose().popPose();
    }

    private void renderItems(GuiGraphics graphics, BlueprintLayout currentLayout, int mouseX, int mouseY) {
        List<Entry> entries = panel.materials().entries();
        boolean hasSelectedBlock = panel.selectedPosition() != null;
        List<Candidate> candidates = hasSelectedBlock ? panel.selectedCandidates() : List.of();
        int visibleMaterialRows = visibleMaterialRows(currentLayout);
        int visibleCandidateRows = visibleCandidateRows(currentLayout);
        int materialRows = materialRows(entries);
        int candidateRows = candidateRows(candidates);
        materialScrollOffset = clampScrollOffset(materialScrollOffset, materialRows, visibleMaterialRows);
        candidateScrollOffset = clampScrollOffset(candidateScrollOffset, candidateRows, visibleCandidateRows);
        List<Component> hoveredTooltip = null;

        int firstMaterial = materialScrollOffset * MATERIAL_COLUMNS;
        int visibleMaterialSlots = visibleMaterialRows * MATERIAL_COLUMNS;
        for (int visible = 0; visible < visibleMaterialSlots; visible++) {
            int index = firstMaterial + visible;
            if (index >= entries.size()) break;
            Entry entry = entries.get(index);
            BlueprintRect slot = materialSlotRect(currentLayout, visible);
            renderSlotFrame(graphics, slot);
            if (slot.contains(mouseX, mouseY)) {
                ItemStack iconStack = entry.stack().copyWithCount(1);
                ArrayList<Component> tooltip = new ArrayList<>(getTooltipFromItem(minecraft, iconStack));
                tooltip.add(Component.translatable("jei.mmcr.machine_recipe.item_count",
                        ReadableNumber.formatExact(entry.count())));
                hoveredTooltip = tooltip;
            }
        }

        int firstCandidate = candidateScrollOffset * CANDIDATE_COLUMNS;
        int visibleCandidateSlots = visibleCandidateRows * CANDIDATE_COLUMNS;
        for (int visible = 0; visible < visibleCandidateSlots; visible++) {
            int index = firstCandidate + visible;
            if (index >= candidates.size()) break;
            Candidate candidate = candidates.get(index);
            BlueprintRect slot = candidateSlotRect(currentLayout, visible);
            renderSlotFrame(graphics, slot);
            if (slot.contains(mouseX, mouseY)) {
                ItemStack iconStack = candidate.stack().copyWithCount(1);
                ArrayList<Component> tooltip = new ArrayList<>(getTooltipFromItem(minecraft, iconStack));
                if (candidate.modifier()) tooltip.add(Component.translatable("gui.mmcr.blueprint.modifier"));
                hoveredTooltip = tooltip;
            }
        }

        renderScrollbar(graphics, currentLayout, materialScrollbarRect(currentLayout), materialScrollOffset,
                materialRows, visibleMaterialRows);
        if (hasSelectedBlock) {
            renderScrollbar(graphics, currentLayout, candidateScrollbarRect(currentLayout), candidateScrollOffset,
                    candidateRows, visibleCandidateRows);
        }

        graphics.pose().pushPose();
        graphics.pose().translate(currentLayout.left(), currentLayout.top(), 0);
        graphics.pose().scale(currentLayout.scale(), currentLayout.scale(), 1.0F);
        for (int visible = 0; visible < visibleMaterialSlots; visible++) {
            int index = firstMaterial + visible;
            if (index >= entries.size()) break;
            Entry entry = entries.get(index);
            ItemStack iconStack = entry.stack().copyWithCount(1);
            BlueprintRect slot = materialSlotRect(currentLayout, visible);
            int x = slotX(currentLayout, slot) + SLOT_ICON_OFFSET;
            int y = slotY(currentLayout, slot) + SLOT_ICON_OFFSET;
            graphics.renderItem(iconStack, x, y, visible);
            renderMaterialCount(graphics, iconStack, entry.count(), x, y);
        }
        for (int visible = 0; visible < visibleCandidateSlots; visible++) {
            int index = firstCandidate + visible;
            if (index >= candidates.size()) break;
            Candidate candidate = candidates.get(index);
            BlueprintRect slot = candidateSlotRect(currentLayout, visible);
            graphics.renderItem(candidate.stack().copyWithCount(1), slotX(currentLayout, slot) + SLOT_ICON_OFFSET,
                    slotY(currentLayout, slot) + SLOT_ICON_OFFSET, visible);
        }
        graphics.pose().popPose();
        if (hoveredTooltip != null) graphics.renderComponentTooltip(font, hoveredTooltip, mouseX, mouseY);
    }

    private void renderScrollbar(GuiGraphics graphics, BlueprintLayout currentLayout, BlueprintRect scrollbar,
            int offset, int totalRows, int visibleRows) {
        int handleHeight = Math.max(1, Math.round(SCROLLBAR_HANDLE_HEIGHT * currentLayout.scale()));
        int handleY = scrollbarHandleY(offset, totalRows, visibleRows, scrollbar.y(), scrollbar.height(), handleHeight);
        graphics.blit(SCROLLER, scrollbar.x(), handleY, 0, 0,
                scrollbar.width(), handleHeight, 32, 32);
    }

    private void renderSlotFrame(GuiGraphics graphics, BlueprintRect slot) {
        int right = slot.x() + slot.width();
        int bottom = slot.y() + slot.height();
        graphics.fill(slot.x(), slot.y(), right, bottom, SLOT_FRAME_DARK);
        graphics.fill(slot.x(), slot.y(), right, slot.y() + 1, SLOT_FRAME_LIGHT);
        graphics.fill(slot.x(), slot.y(), slot.x() + 1, bottom, SLOT_FRAME_LIGHT);
        graphics.fill(slot.x(), bottom - 1, right, bottom, 0xFFFFFFFF);
        graphics.fill(right - 1, slot.y(), right, bottom, 0xFFFFFFFF);
    }

    private void drawGridBackgrounds(GuiGraphics graphics, BlueprintLayout currentLayout) {
        BlueprintRect materials = currentLayout.materials();
        graphics.blit(MATERIAL_LIST, materials.x(), materials.y(), 0, 0,
                materials.width(), materials.height(), MATERIAL_LIST_TEXTURE_WIDTH, MATERIAL_LIST_TEXTURE_HEIGHT,
                MATERIAL_LIST_TEXTURE_WIDTH, MATERIAL_LIST_TEXTURE_HEIGHT);
        if (panel.selectedPosition() != null) {
            BlueprintRect candidates = currentLayout.candidates();
            graphics.blit(SELECTED_LIST, candidates.x(), candidates.y(), 0, 0,
                    candidates.width(), candidates.height(), SELECTED_LIST_TEXTURE_WIDTH, SELECTED_LIST_TEXTURE_HEIGHT,
                    SELECTED_LIST_TEXTURE_WIDTH, SELECTED_LIST_TEXTURE_HEIGHT);
        }
    }

    private void renderMaterialCount(GuiGraphics graphics, ItemStack iconStack, long count, int x, int y) {
        String quantity = ReadableNumber.formatForSlot(count, 0, "");
        graphics.pose().pushPose();
        graphics.pose().translate(x + 8, y + 8, 0);
        graphics.pose().scale(MATERIAL_QUANTITY_SCALE, MATERIAL_QUANTITY_SCALE, 1.0F);
        graphics.renderItemDecorations(font, iconStack, 0, 0, quantity);
        graphics.pose().popPose();
    }

    private void beginMaterialScrollbarDrag(double mouseY, BlueprintLayout currentLayout) {
        List<Entry> entries = panel.materials().entries();
        int totalRows = materialRows(entries);
        int visibleRows = visibleMaterialRows(currentLayout);
        BlueprintRect scrollbar = materialScrollbarRect(currentLayout);
        int handleHeight = Math.max(1, Math.round(SCROLLBAR_HANDLE_HEIGHT * currentLayout.scale()));
        materialScrollOffset = clampScrollOffset(materialScrollOffset, totalRows, visibleRows);
        draggingMaterialScrollbar = totalRows > visibleRows;
        materialScrollbarDragOffsetY = Math.clamp((int) mouseY
                - scrollbarHandleY(materialScrollOffset, totalRows, visibleRows, scrollbar.y(), scrollbar.height(), handleHeight),
                0, handleHeight);
        materialScrollOffset = materialScrollOffsetFrom(mouseY, currentLayout);
    }

    private void beginCandidateScrollbarDrag(double mouseY, BlueprintLayout currentLayout) {
        List<Candidate> candidates = panel.selectedCandidates();
        int totalRows = candidateRows(candidates);
        int visibleRows = visibleCandidateRows(currentLayout);
        BlueprintRect scrollbar = candidateScrollbarRect(currentLayout);
        int handleHeight = Math.max(1, Math.round(SCROLLBAR_HANDLE_HEIGHT * currentLayout.scale()));
        candidateScrollOffset = clampScrollOffset(candidateScrollOffset, totalRows, visibleRows);
        draggingCandidateScrollbar = totalRows > visibleRows;
        candidateScrollbarDragOffsetY = Math.clamp((int) mouseY
                - scrollbarHandleY(candidateScrollOffset, totalRows, visibleRows, scrollbar.y(), scrollbar.height(), handleHeight),
                0, handleHeight);
        candidateScrollOffset = candidateScrollOffsetFrom(mouseY, currentLayout);
    }

    private void scrollMaterials(double scrollY, BlueprintLayout currentLayout) {
        List<Entry> entries = panel.materials().entries();
        materialScrollOffset = clampScrollOffset(materialScrollOffset - (int) Math.signum(scrollY),
                materialRows(entries), visibleMaterialRows(currentLayout));
    }

    private void scrollCandidates(double scrollY, BlueprintLayout currentLayout) {
        List<Candidate> candidates = panel.selectedCandidates();
        candidateScrollOffset = clampScrollOffset(candidateScrollOffset - (int) Math.signum(scrollY),
                candidateRows(candidates), visibleCandidateRows(currentLayout));
    }

    private int materialScrollOffsetFrom(double mouseY, BlueprintLayout currentLayout) {
        List<Entry> entries = panel.materials().entries();
        BlueprintRect scrollbar = materialScrollbarRect(currentLayout);
        return scrollOffsetFromScrollbarY((int) mouseY, materialRows(entries), visibleMaterialRows(currentLayout),
                scrollbar.y(), scrollbar.height(), Math.max(1, Math.round(SCROLLBAR_HANDLE_HEIGHT * currentLayout.scale())),
                materialScrollbarDragOffsetY);
    }

    private int candidateScrollOffsetFrom(double mouseY, BlueprintLayout currentLayout) {
        List<Candidate> candidates = panel.selectedCandidates();
        BlueprintRect scrollbar = candidateScrollbarRect(currentLayout);
        return scrollOffsetFromScrollbarY((int) mouseY, candidateRows(candidates), visibleCandidateRows(currentLayout),
                scrollbar.y(), scrollbar.height(), Math.max(1, Math.round(SCROLLBAR_HANDLE_HEIGHT * currentLayout.scale())),
                candidateScrollbarDragOffsetY);
    }

    private boolean gridContains(BlueprintLayout currentLayout, double mouseX, double mouseY) {
        return currentLayout.materials().contains(mouseX, mouseY)
                || panel.selectedPosition() != null && currentLayout.candidates().contains(mouseX, mouseY);
    }

    private static int visibleMaterialRows(BlueprintLayout currentLayout) {
        int height = (int) Math.round(localMouse(currentLayout.materials().height(), 0, currentLayout.scale()));
        return Math.max(1, (height + MATERIAL_SLOT_GAP) / (MATERIAL_SLOT_SIZE + MATERIAL_SLOT_GAP));
    }

    private static int visibleCandidateRows(BlueprintLayout currentLayout) {
        int height = (int) Math.round(localMouse(currentLayout.candidates().height(), 0, currentLayout.scale()));
        return Math.max(1, (height - CANDIDATE_CONTENT_TOP_PADDING + CANDIDATE_ROW_GAP)
                / (CANDIDATE_SLOT_SIZE + CANDIDATE_ROW_GAP));
    }

    private static int materialRows(List<Entry> entries) {
        return (entries.size() + MATERIAL_COLUMNS - 1) / MATERIAL_COLUMNS;
    }

    private static int candidateRows(List<Candidate> candidates) {
        return (candidates.size() + CANDIDATE_COLUMNS - 1) / CANDIDATE_COLUMNS;
    }

    private static BlueprintRect materialSlotRect(BlueprintLayout currentLayout, int visible) {
        BlueprintRect materials = currentLayout.materials();
        int x = slotX(currentLayout, materials) + MATERIAL_CONTENT_LEFT_PADDING
                + visible % MATERIAL_COLUMNS * (MATERIAL_SLOT_SIZE + MATERIAL_SLOT_GAP);
        int y = slotY(currentLayout, materials) + MATERIAL_CONTENT_TOP_PADDING
                + visible / MATERIAL_COLUMNS * (MATERIAL_SLOT_SIZE + MATERIAL_SLOT_GAP);
        return scaledRect(currentLayout.left(), currentLayout.top(), x, y,
                MATERIAL_SLOT_SIZE, MATERIAL_SLOT_SIZE, currentLayout.scale());
    }

    private static BlueprintRect candidateSlotRect(BlueprintLayout currentLayout, int visible) {
        BlueprintRect candidates = currentLayout.candidates();
        int x = slotX(currentLayout, candidates) + CANDIDATE_CONTENT_LEFT_PADDING
                + visible % CANDIDATE_COLUMNS * (CANDIDATE_SLOT_SIZE + MATERIAL_SLOT_GAP);
        int y = slotY(currentLayout, candidates) + CANDIDATE_CONTENT_TOP_PADDING
                + visible / CANDIDATE_COLUMNS * (CANDIDATE_SLOT_SIZE + CANDIDATE_ROW_GAP);
        return scaledRect(currentLayout.left(), currentLayout.top(), x, y,
                CANDIDATE_SLOT_SIZE, CANDIDATE_SLOT_SIZE, currentLayout.scale());
    }

    private static BlueprintRect materialScrollbarRect(BlueprintLayout currentLayout) {
        BlueprintRect materials = currentLayout.materials();
        return scaledRect(currentLayout.left(), currentLayout.top(),
                slotX(currentLayout, materials) + (int) Math.round(localMouse(materials.width(), 0, currentLayout.scale()))
                        - SCROLLBAR_WIDTH - MATERIAL_SCROLLBAR_LEFT_OFFSET,
                slotY(currentLayout, materials) + MATERIAL_SCROLLBAR_TOP_OFFSET, SCROLLBAR_WIDTH,
                (int) Math.round(localMouse(materials.height(), 0, currentLayout.scale())) - MATERIAL_SCROLLBAR_BOTTOM_INSET,
                currentLayout.scale());
    }

    private static BlueprintRect candidateScrollbarRect(BlueprintLayout currentLayout) {
        BlueprintRect candidates = currentLayout.candidates();
        return scaledRect(currentLayout.left(), currentLayout.top(),
                slotX(currentLayout, candidates) + (int) Math.round(localMouse(candidates.width(), 0, currentLayout.scale()))
                        - SCROLLBAR_WIDTH - CANDIDATE_SCROLLBAR_LEFT_OFFSET,
                slotY(currentLayout, candidates) + CANDIDATE_TITLE_HEIGHT + CANDIDATE_SCROLLBAR_TOP_OFFSET,
                SCROLLBAR_WIDTH,
                (int) Math.round(localMouse(candidates.height(), 0, currentLayout.scale())) - CANDIDATE_TITLE_HEIGHT
                        - CANDIDATE_SCROLLBAR_TOP_OFFSET - CANDIDATE_SCROLLBAR_BOTTOM_INSET,
                currentLayout.scale());
    }

    static BlueprintRect slotRect(BlueprintLayout currentLayout, BlueprintRect row, int slot) {
        return scaledRect(currentLayout.left(), currentLayout.top(),
                slotX(currentLayout, row) + slot * (MATERIAL_SLOT_SIZE + MATERIAL_SLOT_GAP), slotY(currentLayout, row),
                MATERIAL_SLOT_SIZE, MATERIAL_SLOT_SIZE, currentLayout.scale());
    }

    private static int slotX(BlueprintLayout currentLayout, BlueprintRect slot) {
        return (int) Math.round(localMouse(slot.x(), currentLayout.left(), currentLayout.scale()));
    }

    private static int slotY(BlueprintLayout currentLayout, BlueprintRect slot) {
        return (int) Math.round(localMouse(slot.y(), currentLayout.top(), currentLayout.scale()));
    }

    private double previewX(double mouseX) {
        BlueprintLayout currentLayout = Objects.requireNonNull(layout, "screen not initialized");
        return previewMouse(mouseX, currentLayout.preview().x());
    }

    private double previewY(double mouseY) {
        BlueprintLayout currentLayout = Objects.requireNonNull(layout, "screen not initialized");
        return previewMouse(mouseY, currentLayout.preview().y());
    }

    private static BlueprintRect scaledRect(int left, int top, int x, int y, int width, int height, float scale) {
        int scaledX = Math.round(left + x * scale);
        int scaledY = Math.round(top + y * scale);
        int scaledRight = Math.round(left + (x + width) * scale);
        int scaledBottom = Math.round(top + (y + height) * scale);
        return new BlueprintRect(scaledX, scaledY, Math.max(1, scaledRight - scaledX), Math.max(1, scaledBottom - scaledY));
    }

    private static Rect2i rect(BlueprintRect rect) {
        return new Rect2i(rect.x(), rect.y(), rect.width(), rect.height());
    }

    /**
     * An item rendered in a blueprint slot with its absolute screen area.
     *
     * @author howxu <dev@howxu.cn>
     */
    public record HoveredIngredient(ItemStack stack, Rect2i area) {
    }
}

record BlueprintRect(int x, int y, int width, int height) {
    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}

record BlueprintLayout(int left, int top, float scale, BlueprintRect preview,
        BlueprintRect materials, BlueprintRect candidates, BlueprintRect title,
        BlueprintRect selectedBlock, BlueprintRect controls, BlueprintRect[] layerButtons,
        @Nullable BlueprintRect nextStageButton, @Nullable BlueprintRect previousStageButton) {
    int materialSlotSize() {
        return BlueprintScreen.MATERIAL_SLOT_SIZE;
    }

    int materialColumns() {
        return BlueprintScreen.MATERIAL_COLUMNS;
    }

    int materialSlotGap() {
        return BlueprintScreen.MATERIAL_SLOT_GAP;
    }
}
