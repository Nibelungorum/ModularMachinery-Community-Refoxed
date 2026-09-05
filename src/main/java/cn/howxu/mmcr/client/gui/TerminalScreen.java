package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.internal.item.TerminalAction;
import cn.howxu.mmcr.internal.item.TerminalData;
import cn.howxu.mmcr.internal.item.TerminalInventoryMode;
import cn.howxu.mmcr.internal.network.PktTerminalActionPayload;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
    private String statusKey;
    private Button typeButton;
    private Button levelButton;
    private Button previewButton;
    private Button checkButton;
    private Button buildButton;
    private Button dismantleButton;

    public TerminalScreen(TerminalData data, boolean controllerAvailable, boolean storageAvailable,
            List<Integer> stages, String statusKey) {
        super(Component.translatable("gui.mmcr.terminal.title"));
        this.data = data;
        this.controllerAvailable = controllerAvailable;
        this.storageAvailable = storageAvailable;
        this.stages = List.copyOf(stages);
        this.statusKey = statusKey;
    }

    @Override
    protected void init() {
        int x = left() + 98;
        addRenderableWidget(button("gui.mmcr.terminal.inventory_mode", x, top() + 34, 92, button ->
                send(TerminalAction.SET_INVENTORY_MODE, nextInventoryMode().ordinal(), null, null)));
        typeButton = addRenderableWidget(button("gui.mmcr.terminal.level_type", x, top() + 54, 92, button -> {
            LevelView view = levelView(levelTypes(), data.selectedLevels());
            if (!view.typeButtonActive()) return;
            Identifier nextType = nextType(view.typeId());
            send(TerminalAction.SET_LEVEL, 0, nextType, data.selectedLevels().get(nextType));
        }));
        levelButton = addRenderableWidget(button("gui.mmcr.terminal.level", x + 96, top() + 54, 92, button -> {
            LevelView view = levelView(levelTypes(), data.selectedLevels());
            if (!view.levelButtonActive()) return;
            send(TerminalAction.SET_LEVEL, 0, view.typeId(), nextLevel(view.typeId(), view.levelId()));
        }));
        addRenderableWidget(button("gui.mmcr.terminal.stage", x, top() + 74, 92, button ->
                send(TerminalAction.SET_STAGE, nextStage(button), null, null)));
        addRenderableWidget(Button.builder(Component.literal("+"), button ->
                send(TerminalAction.SET_PREVIEW_LAYER, nextLayer(data.previewLayer(), layers()), null, null))
                .bounds(x + 96, top() + 94, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("-"), button ->
                send(TerminalAction.SET_PREVIEW_LAYER, previousLayer(data.previewLayer(), layers()), null, null))
                .bounds(x + 116, top() + 94, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("R"), button ->
                send(TerminalAction.SET_PREVIEW_LAYER, resetLayer(), null, null))
                .bounds(x + 136, top() + 94, 18, 18).build());
        int footer = left() + 8;
        previewButton = addRenderableWidget(actionButton("gui.mmcr.terminal.preview", footer, TerminalAction.SET_PREVIEW_ENABLED));
        checkButton = addRenderableWidget(actionButton("gui.mmcr.terminal.check", footer + 74, TerminalAction.CHECK));
        buildButton = addRenderableWidget(actionButton("gui.mmcr.terminal.build", footer + 148, TerminalAction.BUILD));
        dismantleButton = addRenderableWidget(actionButton("gui.mmcr.terminal.dismantle", footer + 222, TerminalAction.DEMOLISH));
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
        LevelView view = levelView(levelTypes(), data.selectedLevels());
        if (!view.slotStack().isEmpty()) {
            int slotX = left() + 194;
            int slotY = top() + 55;
            graphics.item(view.slotStack(), slotX, slotY, 0);
            if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                graphics.setComponentTooltipForNextFrame(font, getTooltipFromItem(Minecraft.getInstance(), view.slotStack()), mouseX, mouseY);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.getMainHandItem().is(ModItems.TERMINAL.get())) minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void applyState(TerminalData data, boolean controllerAvailable, boolean storageAvailable,
            List<Integer> stages, String statusKey) {
        this.data = data;
        this.controllerAvailable = controllerAvailable;
        this.storageAvailable = storageAvailable;
        this.stages = List.copyOf(stages);
        this.statusKey = statusKey;
        updateWidgets();
    }

    static LevelView levelView(List<LevelType> types, Map<Identifier, Identifier> selectedLevels) {
        if (types.isEmpty() || selectedLevels.isEmpty()) return new LevelView(false, false, null, null, ItemStack.EMPTY);
        Identifier typeId = selectedLevels.keySet().stream().filter(id -> MachineLevelRegistry.getType(id) != null)
                .findFirst().orElse(null);
        if (typeId == null) return new LevelView(false, false, null, null, ItemStack.EMPTY);
        Identifier levelId = selectedLevels.get(typeId);
        MachineLevel level = MachineLevelRegistry.getLevel(levelId);
        return new LevelView(true, level != null, typeId, levelId, level == null ? ItemStack.EMPTY : level.representative());
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
        return Button.builder(Component.translatable(key), button -> send(action,
                action == TerminalAction.SET_PREVIEW_ENABLED && !data.previewEnabled() ? 1 : 0, null, null))
                .bounds(x, top() + 128, 70, 20).build();
    }

    private void send(TerminalAction action, int value, Identifier firstId, Identifier secondId) {
        ClientPacketDistributor.sendToServer(new PktTerminalActionPayload(action, value, firstId, secondId));
        if (closesAfter(action)) Minecraft.getInstance().setScreen(null);
    }

    private void updateWidgets() {
        if (typeButton == null) return;
        LevelView view = levelView(levelTypes(), data.selectedLevels());
        typeButton.active = controllerAvailable && view.typeButtonActive();
        levelButton.active = controllerAvailable && view.levelButtonActive();
        typeButton.setMessage(view.typeId() == null ? Component.translatable("gui.mmcr.terminal.level_type")
                : MachineLevelRegistry.getType(view.typeId()).displayName());
        levelButton.setMessage(view.levelId() == null ? Component.translatable("gui.mmcr.terminal.level")
                : Component.literal(view.levelId().getPath()));
        previewButton.active = controllerAvailable;
        checkButton.active = controllerAvailable;
        buildButton.active = controllerAvailable && storageAvailable;
        dismantleButton.active = controllerAvailable && storageAvailable;
    }

    private TerminalInventoryMode nextInventoryMode() {
        return data.inventoryMode() == TerminalInventoryMode.INVENTORY
                ? TerminalInventoryMode.CONTAINER : TerminalInventoryMode.INVENTORY;
    }

    private List<LevelType> levelTypes() {
        return MachineLevelRegistry.types().stream().filter(type -> data.selectedLevels().containsKey(type.id())).toList();
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

    private int nextStage(Button button) {
        if (stages.isEmpty()) return data.stage();
        int delta = minecraftShiftDown() ? -1 : 1;
        int index = stages.indexOf(data.stage());
        return stages.get(Math.floorMod(index + delta, stages.size()));
    }

    private boolean minecraftShiftDown() {
        return Minecraft.getInstance().hasShiftDown();
    }

    private List<Integer> layers() {
        return List.of();
    }

    private int previousLayer(int current, List<Integer> layers) {
        if (layers.isEmpty()) return resetLayer();
        int index = layers.indexOf(current);
        return layers.get(index <= 0 ? layers.size() - 1 : index - 1);
    }

    private Component machineLabel() {
        if (data.controller() == null) return Component.translatable("gui.mmcr.terminal.no_controller");
        return Component.translatable("gui.mmcr.terminal.machine", data.controller().dimension().identifier(),
                data.controller().pos().toShortString());
    }

    private Component layerLabel() {
        return data.previewLayer() == Integer.MAX_VALUE ? Component.translatable("gui.mmcr.terminal.all")
                : Component.literal(Integer.toString(data.previewLayer()));
    }

    private void text(GuiGraphicsExtractor graphics, Component text, int x, int y) {
        graphics.text(font, text, left() + x, top() + y, TEXT_COLOR, false);
    }

    private int left() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (height - PANEL_HEIGHT) / 2;
    }

    record LevelView(boolean typeButtonActive, boolean levelButtonActive, Identifier typeId, Identifier levelId,
            ItemStack slotStack) {}
}
