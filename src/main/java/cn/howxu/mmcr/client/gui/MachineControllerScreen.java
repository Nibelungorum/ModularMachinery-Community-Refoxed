package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot;
import cn.howxu.mmcr.client.controller.ui.ControllerUiClientEvents;
import cn.howxu.mmcr.client.controller.ControllerScreenTextCache;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.menu.InputIsolationMenuState;
import cn.howxu.mmcr.internal.network.PktControllerStructureCheckPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Screen for a machine controller menu.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineControllerScreen extends AbstractScrollableTextScreen<MachineControllerMenu> {
    private static final int IMAGE_WIDTH = 176;
    private static final int IMAGE_HEIGHT = 213;
    private static final ResourceLocation BACKGROUND = MMCR.id("textures/gui/guicontroller_large.png");
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance();
    static final int STATUS_LABEL_COLOR = ControllerTextLine.DEFAULT_COLOR;
    static final int UNFORMED_STATUS_COLOR = 0xFFFF5555;
    private static final int FORMED_STATUS_COLOR = 0xFF55FF55;
    private static final int IDLE_STATUS_COLOR = 0xFFFFAA00;
    private static final float DETAIL_SCALE = 0.85F;
    private static final int DETAIL_LINE_SPACING = 10;
    private static final int ACTION_BUTTON_SIZE = 16;
    private static final int ACTION_BUTTON_GAP = 2;
    private StyledButton recipePoolButton;
    private StyledButton structureCheckButton;
    private IsolationToggleButton isolationButton;
    private final Supplier<ControllerUiSnapshot> snapshot;

    public MachineControllerScreen(MachineControllerMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, ControllerUiClientEvents.sessionFor(menu, title)::snapshot);
    }

    MachineControllerScreen(MachineControllerMenu menu, Inventory inventory, Component title,
                            Supplier<ControllerUiSnapshot> snapshot) {
        super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
        this.snapshot = snapshot;
        titleLabelX += 3;
        titleLabelY += 5;
        inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        int buttonX = leftPos + imageWidth + ACTION_BUTTON_GAP;
        recipePoolButton = addRenderableWidget(new StyledButton(
                buttonX, topPos + 1, ACTION_BUTTON_SIZE, ACTION_BUTTON_SIZE,
                Component.translatable("gui.mmcr.recipe_pool.button"), button -> {
                    ControllerUiSnapshot value = snapshot.get();
                    minecraft.setScreen(new RecipePoolScreen(this, value.controllerPos(), value.recipePoolIds(),
                            value.currentRecipePoolId().orElse(null)));
                }));
        recipePoolButton.setIcon(MMCR.id("textures/gui/button/mode.png"));
        recipePoolButton.setTooltip(Tooltip.create(Component.translatable("gui.mmcr.recipe_pool.open")));
        structureCheckButton = addRenderableWidget(new StyledButton(
                buttonX, topPos + 1, ACTION_BUTTON_SIZE, ACTION_BUTTON_SIZE,
                Component.translatable("gui.mmcr.controller.check_structure.button"), button -> {
                    PacketDistributor.sendToServer(new PktControllerStructureCheckPayload(snapshot.get().controllerPos()));
                    button.setFocused(false);
                }));
        structureCheckButton.setIcon(MMCR.id("textures/gui/button/check.png"));
        structureCheckButton.setTooltip(Tooltip.create(Component.translatable("gui.mmcr.controller.check_structure")));
        isolationButton = addRenderableWidget(new IsolationToggleButton(buttonX, topPos + 1,
                Component.translatable("mmcr.input_isolation.button"), menu.inputIsolation()::enabled, button -> {
            if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                    menu.inputIsolation().enabled() ? InputIsolationMenuState.DISABLE : InputIsolationMenuState.ENABLE);
            button.setFocused(false);
        }));
        isolationButton.setIcon(MMCR.id("textures/gui/button/isolation.png"));
        updateControllerButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateControllerButtons();
    }

    private void updateControllerButtons() {
        boolean showRecipePools = snapshot.get().recipePoolIds().size() > 1;
        int buttonX = leftPos + imageWidth + ACTION_BUTTON_GAP;
        if (recipePoolButton != null) {
            recipePoolButton.visible = showRecipePools;
            recipePoolButton.active = showRecipePools;
            recipePoolButton.setPosition(buttonX, topPos + 1);
        }
        if (structureCheckButton != null) {
            structureCheckButton.setPosition(buttonX,
                    topPos + 1 + (showRecipePools ? ACTION_BUTTON_SIZE + ACTION_BUTTON_GAP : 0));
        }
        if (isolationButton != null) {
            isolationButton.updateState(menu.inputIsolation(), true);
            isolationButton.setPosition(buttonX, topPos + 1 + (showRecipePools ? 2 : 1) * 18);
        }
    }

    @Override
    protected TextViewport scrollableTextViewport() {
        int bodyY = titleLabelY + DETAIL_LINE_SPACING;
        return new TextViewport(10, bodyY, 160, 124 - bodyY + 1,
                DETAIL_SCALE, DETAIL_LINE_SPACING);
    }

    @Override
    protected List<ControllerTextLine> scrollableTextLines() {
        return ControllerUiTextLines.create(snapshot.get(), "base");
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0,
                IMAGE_WIDTH, IMAGE_HEIGHT, 256, 256);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().scale(DETAIL_SCALE, DETAIL_SCALE, 1F);
        graphics.drawString(font, snapshot.get().machineName(), (int) (titleLabelX / DETAIL_SCALE),
                (int) (titleLabelY / DETAIL_SCALE), STATUS_LABEL_COLOR, false);
        renderScrollableText(graphics, (int) (titleLabelX / DETAIL_SCALE), mouseX, mouseY);
        graphics.pose().popPose();
    }

    private void renderScrollableText(GuiGraphics graphics, int x, int mouseX, int mouseY) {
        List<ControllerScreenTextComposer.VisualLine> lines = wrappedTextLines();
        clampTextScrollOffset();
        int first = firstVisibleTextLine();
        int last = lastVisibleTextLineExclusive();
        for (int index = first; index < last; index++) {
            ControllerScreenTextComposer.VisualLine line = lines.get(index);
            int textY = detailTextY(textLineY(visibleTextRow(index)));
            renderVisualLine(graphics, line, x, textY);
        }
    }

    @Override
    protected void renderFrame(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderFrame(graphics, mouseX, mouseY, partialTicks);
        renderTooltip(graphics, mouseX, mouseY);
        renderScrollableTooltip(graphics, mouseX, mouseY, titleLabelX);
    }

    static int detailTextY(int localY) {
        return (int) (localY / DETAIL_SCALE);
    }

    static List<ControllerTextLine> controllerTextLines(ControllerUiSnapshot snapshot) {
        return ControllerUiTextLines.create(snapshot, "base");
    }

    static List<ControllerTextLine> detailLines(ControllerUiSnapshot snapshot) {
        return ControllerUiTextLines.details(snapshot, "base");
    }

    static List<ControllerTextLine> detailLines(MachineControllerMenu menu) {
        return detailLines(menu.legacyUiSnapshot());
    }

    static List<ControllerTextLine> controllerTextLines(MachineControllerMenu menu) {
        var snapshot = menu.legacyUiSnapshot();
        var lines = new ArrayList<>(ControllerScreenTextComposer.merge(detailLines(snapshot),
                ControllerScreenTextCache.linesAt(snapshot.controllerPos())));
        ControllerUiTextLines.selectedLane(snapshot, "base").ifPresent(lane ->
                lines.addAll(ControllerRecipeTextLines.createSnapshot(lane.recipe())));
        return List.copyOf(lines);
    }

    static ResourceLocation displayedRecipePoolId(ResourceLocation current, List<ResourceLocation> supported) {
        return current != null ? current : supported.isEmpty() ? null : supported.getFirst();
    }

    static ControllerTextLine statusLine(boolean formed, boolean active) {
        return new ControllerTextLine(Component.translatable("gui.mmcr.controller.status_label")
                .append(Component.literal(" "))
                .append(Component.translatable(controllerStatusKey(formed, active))),
                controllerStatusColor(formed, active));
    }

    static Component levelLine(MachineLevel level) {
        var type = MachineLevelRegistry.getType(level.typeId());
        if (type == null || !(level.statePredicate() instanceof BlockPredicate.OfBlockState(
                net.minecraft.world.level.block.state.BlockState state
        ))) return Component.empty();
        return Component.translatable("gui.mmcr.controller.level", type.displayName(), state.getBlock().getName());
    }

    static Component parallelLine(long parallelism, long maxParallelism) {
        return Component.translatable("gui.mmcr.controller.parallel", Component.literal(NUMBER_FORMAT.format(parallelism)), Component.literal(NUMBER_FORMAT.format(maxParallelism)));
    }

    static Component parallelSlotLine(int parallelSlots) {
        return Component.translatable("gui.mmcr.controller.parallel_slots", Component.literal(NUMBER_FORMAT.format(parallelSlots)));
    }

    static Component matchedStageLine(int matchedStage) {
        return Component.translatable("gui.mmcr.controller.matched_stage",
                Component.literal(NUMBER_FORMAT.format(matchedStage)));
    }

    static int progressPercent(int tick, int totalTick) {
        if (totalTick <= 0) return 0;
        return Math.clamp((int) ((long) tick * 100 / totalTick), 0, 100);
    }

    static List<ControllerTextLine> moduleStatusLines(boolean hostController, boolean moduleController, int installedModuleCount, Optional<ResourceLocation> connectedHostId) {
        if (hostController) return List.of(new ControllerTextLine(Component.translatable("gui.mmcr.controller.installed_modules", Component.literal(NUMBER_FORMAT.format(installedModuleCount))), STATUS_LABEL_COLOR));
        if (!moduleController) return List.of();
        Component host = connectedHostId.isEmpty() ? Component.translatable("gui.mmcr.controller.module_unconnected") : Component.translatable("gui.mmcr.controller.module_connected", hostName(connectedHostId.get()));
        return List.of(new ControllerTextLine(host, connectedHostId.isPresent() ? STATUS_LABEL_COLOR : UNFORMED_STATUS_COLOR));
    }

    private static Component hostName(ResourceLocation id) {
        var machine = MachineRegistry.getMachine(id);
        return machine == null ? Component.literal(id.toString()) : machine.displayName();
    }

    private static String controllerStatusKey(boolean formed, boolean active) {
        if (!formed) return "gui.mmcr.controller.unformed";
        return active ? "gui.mmcr.controller.running" : "gui.mmcr.controller.idle";
    }

    private static int controllerStatusColor(boolean formed, boolean active) {
        if (!formed) return UNFORMED_STATUS_COLOR;
        return active ? FORMED_STATUS_COLOR : IDLE_STATUS_COLOR;
    }

}
