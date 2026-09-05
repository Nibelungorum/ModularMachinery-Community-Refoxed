package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.internal.item.TerminalAction;
import cn.howxu.mmcr.internal.item.TerminalData;
import cn.howxu.mmcr.internal.item.TerminalInventoryMode;
import cn.howxu.mmcr.internal.network.PktTerminalActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;
import java.util.Map;

/** Compact server-authoritative terminal configuration screen.
 * @author howxu <dev@howxu.cn>
 */
public final class TerminalScreen extends Screen {
    private static final int PANEL_WIDTH = 310;
    private static final int PANEL_HEIGHT = 156;
    private static final int TEXT_COLOR = 0xFF202020;
    private TerminalData data;
    private boolean controllerAvailable;
    private boolean storageAvailable;
    private List<Integer> stages;
    private List<Integer> previewLayers;
    private Component machineName;
    private String statusKey;
    private Button inventoryModeButton;
    private Button typeButton;
    private Button levelButton;
    private Button stageButton;
    private Button plusButton;
    private Button minusButton;
    private Button resetButton;
    private Button previewButton;
    private Button checkButton;
    private Button buildButton;
    private Button dismantleButton;

    public TerminalScreen(TerminalData data, boolean controllerAvailable, boolean storageAvailable,
            List<Integer> stages, String statusKey) {
        this(data, controllerAvailable, storageAvailable, stages, Component.empty(), List.of(), statusKey);
    }

    public TerminalScreen(TerminalData data, boolean controllerAvailable, boolean storageAvailable,
            List<Integer> stages, Component machineName, List<Integer> previewLayers, String statusKey) {
        super(Component.translatable("gui.mmcr.terminal.title"));
        this.data = data;
        this.controllerAvailable = controllerAvailable;
        this.storageAvailable = storageAvailable;
        this.stages = List.copyOf(stages);
        this.machineName = machineName == null ? Component.empty() : machineName;
        this.previewLayers = List.copyOf(previewLayers);
        this.statusKey = statusKey;
    }

    @Override
    protected void init() {
        Layout layout = layout();
        int x = left() + layout.contentX();
        inventoryModeButton = addRenderableWidget(button("gui.mmcr.terminal.inventory_mode", x, top() + 34, 92, button ->
                send(TerminalAction.SET_INVENTORY_MODE, nextInventoryMode().ordinal(), null, null)));
        typeButton = addRenderableWidget(button("gui.mmcr.terminal.level_type", x, top() + 54,
                layout.levelTypeWidth(), button -> {
                    LevelView view = levelView(levelTypes(), data.selectedLevelType(), data.selectedLevels());
                    if (!view.typeButtonActive()) return;
                    Identifier nextType = nextType(view.typeId());
                    send(TerminalAction.SET_LEVEL, 0, nextType, data.selectedLevels().get(nextType));
                }));
        levelButton = addRenderableWidget(button("gui.mmcr.terminal.level", left() + layout.levelX(), top() + 54,
                layout.levelWidth(), button -> {
                    LevelView view = levelView(levelTypes(), data.selectedLevelType(), data.selectedLevels());
                    if (!view.levelButtonActive()) return;
                    send(TerminalAction.SET_LEVEL, 0, view.typeId(), nextLevel(view.typeId(), view.levelId()));
                }));
        stageButton = addRenderableWidget(button("gui.mmcr.terminal.stage", x, top() + 74, 92, button ->
                send(TerminalAction.SET_STAGE, nextStage(), null, null)));
        plusButton = addRenderableWidget(Button.builder(Component.literal("+"), button ->
                sendPreviewLayer(nextLayer(data.previewLayer(), previewLayers), false))
                .bounds(x, top() + 94, 18, 18).build());
        minusButton = addRenderableWidget(Button.builder(Component.literal("-"), button ->
                sendPreviewLayer(previousLayer(data.previewLayer(), previewLayers), false))
                .bounds(x + 20, top() + 94, 18, 18).build());
        resetButton = addRenderableWidget(Button.builder(Component.literal("R"), button ->
                sendPreviewLayer(resetLayer(), true))
                .bounds(x + 40, top() + 94, 18, 18).build());
        int footer = left() + 8;
        previewButton = addRenderableWidget(actionButton("gui.mmcr.terminal.preview", footer,
                TerminalAction.SET_PREVIEW_ENABLED));
        checkButton = addRenderableWidget(actionButton("gui.mmcr.terminal.check", footer + 74, TerminalAction.CHECK));
        buildButton = addRenderableWidget(actionButton("gui.mmcr.terminal.build", footer + 148, TerminalAction.BUILD));
        dismantleButton = addRenderableWidget(actionButton("gui.mmcr.terminal.dismantle", footer + 222,
                TerminalAction.DEMOLISH));
        updateWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.fill(left(), top(), left() + PANEL_WIDTH, top() + PANEL_HEIGHT, 0xFFB6E4F2);
        graphics.fill(left(), top(), left() + PANEL_WIDTH, top() + 1, 0xFF40798B);
        graphics.fill(left(), top() + PANEL_HEIGHT - 1, left() + PANEL_WIDTH, top() + PANEL_HEIGHT, 0xFF40798B);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        text(graphics, machineLabel(), 8, 8);
        text(graphics, Component.translatable("gui.mmcr.terminal.inventory"), 8, 38);
        text(graphics, Component.translatable("gui.mmcr.terminal.level_blocks"), 8, 58);
        text(graphics, Component.translatable("gui.mmcr.terminal.machine_stage"), 8, 78);
        text(graphics, Component.translatable("gui.mmcr.terminal.preview_layer", layerLabel()), 8, 98);
        if (!statusKey.isEmpty()) text(graphics, Component.translatable(statusKey), 194, 38);
        LevelView view = levelView(levelTypes(), data.selectedLevelType(), data.selectedLevels());
        if (!view.slotStack().isEmpty()) {
            int slotX = left() + layout().slotX();
            int slotY = top() + 55;
            graphics.item(view.slotStack(), slotX, slotY, 0);
            if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                graphics.setComponentTooltipForNextFrame(font,
                        getTooltipFromItem(Minecraft.getInstance(), view.slotStack()), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void applyState(TerminalData data, boolean controllerAvailable, boolean storageAvailable,
            List<Integer> stages, Component machineName, List<Integer> previewLayers, String statusKey) {
        this.data = data;
        this.controllerAvailable = controllerAvailable;
        this.storageAvailable = storageAvailable;
        this.stages = List.copyOf(stages);
        this.machineName = machineName == null ? Component.empty() : machineName;
        this.previewLayers = List.copyOf(previewLayers);
        this.statusKey = statusKey;
        updateWidgets();
    }

    static LevelView levelView(List<LevelType> types, Identifier selectedType,
            Map<Identifier, Identifier> selectedLevels) {
        if (types.isEmpty() || selectedLevels.isEmpty()) {
            return new LevelView(false, false, null, null, ItemStack.EMPTY);
        }
        Identifier typeId = types.stream().map(LevelType::id)
                .filter(id -> id.equals(selectedType) && validLevelSelection(id, selectedLevels))
                .findFirst()
                .orElseGet(() -> types.stream().map(LevelType::id).filter(id -> validLevelSelection(id, selectedLevels))
                        .findFirst().orElse(null));
        if (typeId == null) return new LevelView(false, false, null, null, ItemStack.EMPTY);
        Identifier levelId = selectedLevels.get(typeId);
        MachineLevel level = MachineLevelRegistry.getLevel(levelId);
        boolean multipleLevels = MachineLevelRegistry.levelsForType(typeId).size() > 1;
        return new LevelView(true, level != null && multipleLevels, typeId, levelId,
                level == null ? ItemStack.EMPTY : levelBlockStack(level));
    }

    private static ItemStack levelBlockStack(MachineLevel level) {
        return level.statePredicate().preferredState()
                .map(state -> state.getBlock().asItem().getDefaultInstance())
                .orElse(ItemStack.EMPTY);
    }

    private static boolean validLevelSelection(Identifier typeId, Map<Identifier, Identifier> selectedLevels) {
        MachineLevel level = MachineLevelRegistry.getLevel(selectedLevels.get(typeId));
        return level != null && level.typeId().equals(typeId);
    }

    static int nextLayer(int current, List<Integer> layers) {
        if (layers.isEmpty()) return resetLayer();
        int index = layers.indexOf(current);
        return layers.get(index < 0 || index + 1 == layers.size() ? 0 : index + 1);
    }

    static int resetLayer() {
        return Integer.MAX_VALUE;
    }

    static boolean closesAfter(TerminalAction action) {
        return action == TerminalAction.SET_PREVIEW_ENABLED || action == TerminalAction.CHECK
                || action == TerminalAction.BUILD || action == TerminalAction.DEMOLISH;
    }

    private Button button(String key, int x, int y, int width, Button.OnPress onPress) {
        return Button.builder(Component.translatable(key), onPress).bounds(x, y, width, 18).build();
    }

    private Button actionButton(String key, int x, TerminalAction action) {
        var label = action == TerminalAction.SET_PREVIEW_ENABLED ? previewLabel() : Component.translatable(key);
        if (action == TerminalAction.BUILD) {
            label = label.withStyle(ChatFormatting.GREEN);
        } else if (action == TerminalAction.DEMOLISH) {
            label = label.withStyle(ChatFormatting.RED);
        }
        return Button.builder(label, button -> send(action,
                action == TerminalAction.SET_PREVIEW_ENABLED && !data.previewEnabled() ? 1 : 0, null, null))
                .bounds(x, top() + 128, 70, 20).build();
    }

    private void send(TerminalAction action, int value, Identifier firstId, Identifier secondId) {
        if (!canSend(action)) return;
        ClientPacketDistributor.sendToServer(new PktTerminalActionPayload(action, value, firstId, secondId));
        if (closesAfter(action)) Minecraft.getInstance().setScreen(null);
    }

    private void sendPreviewLayer(int value, boolean reset) {
        if (!controllerAvailable || (!reset && previewLayers.isEmpty())) return;
        send(TerminalAction.SET_PREVIEW_LAYER, value, null, null);
    }

    private void updateWidgets() {
        if (typeButton == null) return;
        LevelView view = levelView(levelTypes(), data.selectedLevelType(), data.selectedLevels());
        ControlState controls = controlState(controllerAvailable, stages, previewLayers);
        inventoryModeButton.setMessage(inventoryModeLabel());
        typeButton.active = controllerAvailable && levelButtonsActive(view);
        levelButton.active = controllerAvailable && levelButtonsActive(view);
        stageButton.active = controls.stageActive();
        stageButton.setMessage(Component.literal(Integer.toString(data.stage())));
        plusButton.active = controls.layerActive();
        minusButton.active = controls.layerActive();
        resetButton.active = controls.resetActive();
        typeButton.setMessage(view.typeId() == null ? Component.translatable("gui.mmcr.terminal.level_type")
                : MachineLevelRegistry.getType(view.typeId()).displayName());
        levelButton.setMessage(levelLabel(view));
        previewButton.active = controllerAvailable;
        previewButton.setMessage(previewLabel());
        checkButton.active = controllerAvailable;
        buildButton.active = controllerAvailable && storageAvailable;
        dismantleButton.active = controllerAvailable && storageAvailable;
    }

    private TerminalInventoryMode nextInventoryMode() {
        return data.inventoryMode() == TerminalInventoryMode.INVENTORY
                ? TerminalInventoryMode.CONTAINER : TerminalInventoryMode.INVENTORY;
    }

    private List<LevelType> levelTypes() {
        return MachineLevelRegistry.types().stream()
                .filter(type -> validLevelSelection(type.id(), data.selectedLevels())).toList();
    }

    private boolean levelButtonsActive(LevelView view) {
        return levelTypes().size() > 1 && view.levelButtonActive();
    }

    private Identifier nextType(Identifier current) {
        List<LevelType> types = levelTypes();
        int index = types.stream().map(LevelType::id).toList().indexOf(current);
        return types.get(index < 0 || index + 1 == types.size() ? 0 : index + 1).id();
    }

    private Identifier nextLevel(Identifier typeId, Identifier current) {
        List<MachineLevel> levels = MachineLevelRegistry.levelsForType(typeId);
        int index = levels.stream().map(MachineLevel::id).toList().indexOf(current);
        return levels.get(index < 0 || index + 1 == levels.size() ? 0 : index + 1).id();
    }

    private int nextStage() {
        if (stages.isEmpty()) return data.stage();
        int delta = Minecraft.getInstance().hasShiftDown() ? -1 : 1;
        int index = stages.indexOf(data.stage());
        return stages.get(Math.floorMod(index + delta, stages.size()));
    }

    private int previousLayer(int current, List<Integer> layers) {
        if (layers.isEmpty()) return resetLayer();
        int index = layers.indexOf(current);
        return layers.get(index <= 0 ? layers.size() - 1 : index - 1);
    }

    private Component machineLabel() {
        if (data.controller() == null || machineName == null || machineName.getString().isEmpty()) {
            return Component.translatable("gui.mmcr.terminal.no_controller");
        }
        return Component.translatable("gui.mmcr.terminal.machine", machineName,
                data.controller().dimension().identifier().toString(), data.controller().pos().toShortString());
    }

    private Component layerLabel() {
        return data.previewLayer() == Integer.MAX_VALUE ? Component.translatable("gui.mmcr.terminal.all")
                : Component.literal(Integer.toString(data.previewLayer()));
    }

    private Component inventoryModeLabel() {
        return Component.translatable(data.inventoryMode() == TerminalInventoryMode.INVENTORY
                ? "gui.mmcr.terminal.inventory_mode.inventory" : "gui.mmcr.terminal.inventory_mode.container");
    }

    private Component levelLabel(LevelView view) {
        if (view.levelId() == null) return Component.translatable("gui.mmcr.terminal.level");
        MachineLevel level = MachineLevelRegistry.getLevel(view.levelId());
        return level == null ? Component.translatable("gui.mmcr.terminal.level")
                : level.statePredicate().preferredState()
                        .map(state -> (Component) state.getBlock().getName())
                        .orElseGet(() -> view.slotStack().getHoverName());
    }

    private MutableComponent previewLabel() {
        return Component.translatable(data.previewEnabled()
                ? "gui.mmcr.terminal.preview.on" : "gui.mmcr.terminal.preview.off");
    }

    private boolean canSend(TerminalAction action) {
        return switch (action) {
            case REQUEST_STATE -> false;
            case SET_INVENTORY_MODE -> true;
            case SET_STAGE -> controllerAvailable && stages.size() > 1;
            case SET_LEVEL -> controllerAvailable && levelButtonsActive(
                    levelView(levelTypes(), data.selectedLevelType(), data.selectedLevels()));
            case SET_PREVIEW_ENABLED, SET_PREVIEW_LAYER, CHECK -> controllerAvailable;
            case BUILD, DEMOLISH -> controllerAvailable && storageAvailable;
        };
    }

    static ControlState controlState(boolean controllerAvailable, List<Integer> stages, List<Integer> previewLayers) {
        return new ControlState(controllerAvailable && stages.size() > 1,
                controllerAvailable && !previewLayers.isEmpty(), controllerAvailable);
    }

    static Layout layout() {
        return new Layout(96, 164, 232, 64, 64);
    }

    private int left() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (height - PANEL_HEIGHT) / 2;
    }

    private void text(GuiGraphicsExtractor graphics, Component text, int x, int y) {
        graphics.text(font, text, left() + x, top() + y, TEXT_COLOR, false);
    }

    record LevelView(boolean typeButtonActive, boolean levelButtonActive, Identifier typeId, Identifier levelId,
            ItemStack slotStack) {}

    record ControlState(boolean stageActive, boolean layerActive, boolean resetActive) {}

    record Layout(int contentX, int levelX, int slotX, int levelTypeWidth, int levelWidth) {
        int levelButtonEnd() {
            return levelX + levelWidth;
        }
    }
}
