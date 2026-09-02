package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import cn.howxu.mmcr.internal.item.MultiblockDetectorItem;
import cn.howxu.mmcr.internal.item.MultiblockDetectorSelection;
import cn.howxu.mmcr.internal.network.PktMultiblockDetectorExportPayload;
import cn.howxu.mmcr.internal.network.PktMultiblockDetectorUpdatePayload;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.OptionalInt;

/**
 * Direct client screen for editing and exporting a multiblock detector selection.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MultiblockDetectorScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 200;
    private static final int CONTENT_X = 10;
    private static final int TITLE_ROW = 8;
    private static final int CONTROLLER_ROW = 26;
    private static final int FIRST_LABEL_ROW = 47;
    private static final int FIRST_INPUT_ROW = 60;
    private static final int FIRST_ADJUST_ROW = 80;
    private static final int SECOND_LABEL_ROW = 105;
    private static final int SECOND_INPUT_ROW = 118;
    private static final int SECOND_ADJUST_ROW = 138;
    private static final int FOOTER_ROW = 172;
    private static final int INPUT_WIDTH = 48;
    private static final int INPUT_HEIGHT = 18;
    private static final int AXIS_START_X = 126;
    private static final int AXIS_COLUMN_WIDTH = 72;
    private static final int AXIS_LABEL_WIDTH = 12;
    private static final int ADJUST_BUTTON_WIDTH = 22;
    private static final int ADJUST_BUTTON_HEIGHT = 18;
    private static final int ADJUST_BUTTON_GAP = 4;
    private static final int FOOTER_BUTTON_WIDTH = 84;
    private static final int FOOTER_BUTTON_HEIGHT = 20;
    private static final int FOOTER_BUTTON_GAP = 4;
    private static final int TEXT_COLOR = 0xFF202020;
    private static final int PANEL_COLOR = 0xFFB6E4F2;
    private static final int OUTER_BORDER_COLOR = 0xFF40798B;
    private static final int INNER_BORDER_COLOR = 0xFFDDF4FA;
    private static final Axis[] AXES = {Axis.X, Axis.Y, Axis.Z};

    private MultiblockDetectorSelection selection;
    private boolean maskEnabled;
    private final EditBox[] coordinateInputs = new EditBox[6];
    private final Button[] decrementButtons = new Button[6];
    private final Button[] incrementButtons = new Button[6];
    private Button javaExportButton;
    private Button kubeJsExportButton;
    private Button maskButton;
    private boolean updatingWidgets;

    public MultiblockDetectorScreen(Component title) {
        super(title);
        ItemStack detector = mainHandDetector();
        selection = detector.isEmpty() ? MultiblockDetectorSelection.EMPTY : MultiblockDetectorItem.selection(detector);
        maskEnabled = !detector.isEmpty() && Boolean.TRUE.equals(detector.get(ModDataComponents.MULTIBLOCK_DETECTOR_MASK.get()));
    }

    @Override
    protected void init() {
        super.init();
        for (Point point : Point.values()) {
            for (Axis axis : AXES) {
                int index = inputIndex(point, axis);
                coordinateInputs[index] = addRenderableWidget(new EditBox(font, panelLeft() + inputX(axis),
                        panelTop() + inputRow(point), INPUT_WIDTH, INPUT_HEIGHT,
                        Component.translatable("item.mmcr.multiblock_detector")));
                coordinateInputs[index].setMaxLength(11);
                coordinateInputs[index].setFilter(MultiblockDetectorScreen::acceptsCoordinateCandidate);
                coordinateInputs[index].setResponder(value -> coordinateChanged(point, axis, value));

                decrementButtons[index] = addRenderableWidget(axisButton(point, axis, -1,
                        panelLeft() + inputX(axis), panelTop() + adjustRow(point)));
                incrementButtons[index] = addRenderableWidget(axisButton(point, axis, 1,
                        panelLeft() + inputX(axis) + ADJUST_BUTTON_WIDTH + ADJUST_BUTTON_GAP,
                        panelTop() + adjustRow(point)));
            }
        }

        int footerX = panelLeft() + (PANEL_WIDTH - 4 * FOOTER_BUTTON_WIDTH - 3 * FOOTER_BUTTON_GAP) / 2;
        javaExportButton = addRenderableWidget(Button.builder(Component.literal("Java"), button -> {
            ClientPacketDistributor.sendToServer(new PktMultiblockDetectorExportPayload(false));
            setFocused(null);
        }).bounds(footerX, panelTop() + FOOTER_ROW, FOOTER_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT).build());
        kubeJsExportButton = addRenderableWidget(Button.builder(Component.literal("KubeJS"), button -> {
            ClientPacketDistributor.sendToServer(new PktMultiblockDetectorExportPayload(true));
            setFocused(null);
        }).bounds(footerX + FOOTER_BUTTON_WIDTH + FOOTER_BUTTON_GAP, panelTop() + FOOTER_ROW,
                FOOTER_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT).build());
        maskButton = addRenderableWidget(Button.builder(maskLabel(), button -> {
            maskEnabled = !maskEnabled;
            button.setMessage(maskLabel());
            writeLocalStateAndSync();
            setFocused(null);
        }).bounds(footerX + 2 * (FOOTER_BUTTON_WIDTH + FOOTER_BUTTON_GAP), panelTop() + FOOTER_ROW,
                FOOTER_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.mmcr.multiblock_detector.clear_selection"), button -> {
                    selection = MultiblockDetectorSelection.EMPTY;
                    updateWidgets();
                    writeLocalStateAndSync();
                    setFocused(null);
                }).bounds(footerX + 3 * (FOOTER_BUTTON_WIDTH + FOOTER_BUTTON_GAP), panelTop() + FOOTER_ROW,
                        FOOTER_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT).build());
        updateWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        int left = panelLeft();
        int top = panelTop();
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;
        graphics.fill(left, top, right, bottom, PANEL_COLOR);
        graphics.fill(left, top, right, top + 1, OUTER_BORDER_COLOR);
        graphics.fill(left, bottom - 1, right, bottom, OUTER_BORDER_COLOR);
        graphics.fill(left, top, left + 1, bottom, OUTER_BORDER_COLOR);
        graphics.fill(right - 1, top, right, bottom, OUTER_BORDER_COLOR);
        graphics.fill(left + 1, top + 1, right - 1, top + 2, INNER_BORDER_COLOR);
        graphics.fill(left + 1, bottom - 2, right - 1, bottom - 1, INNER_BORDER_COLOR);
        graphics.fill(left + 1, top + 1, left + 2, bottom - 1, INNER_BORDER_COLOR);
        graphics.fill(right - 2, top + 1, right - 1, bottom - 1, INNER_BORDER_COLOR);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);

        renderLabel(graphics, title, CONTENT_X, TITLE_ROW);
        renderLabel(graphics, controllerLabel(), CONTENT_X, CONTROLLER_ROW);
        renderLabel(graphics, pointLabel(Point.FIRST), CONTENT_X, FIRST_LABEL_ROW);
        renderLabel(graphics, pointLabel(Point.SECOND), CONTENT_X, SECOND_LABEL_ROW);
        renderPointRow(graphics, Point.FIRST, FIRST_INPUT_ROW);
        renderPointRow(graphics, Point.SECOND, SECOND_INPUT_ROW);
    }

    @Override
    public void tick() {
        super.tick();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.getMainHandItem().is(ModItems.MULTIBLOCK_DETECTOR.get())) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    static OptionalInt parseCoordinate(String value) {
        if (!acceptsCoordinateCandidate(value)) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }

    static boolean acceptsCoordinateCandidate(String value) {
        return SmartInterfaceScreen.acceptsInputCandidate(value, SmartInterfaceType.ValueType.INTEGER);
    }

    static boolean canSyncCoordinate(boolean updatingWidgets, BlockPos point, String value) {
        return !updatingWidgets && point != null && parseCoordinate(value).isPresent();
    }

    static BlockPos withAxis(BlockPos original, Axis axis, int value) {
        return switch (axis) {
            case X -> new BlockPos(value, original.getY(), original.getZ());
            case Y -> new BlockPos(original.getX(), value, original.getZ());
            case Z -> new BlockPos(original.getX(), original.getY(), value);
        };
    }

    private void coordinateChanged(Point point, Axis axis, String value) {
        BlockPos original = point.value(selection);
        if (!canSyncCoordinate(updatingWidgets, original, value)) return;
        OptionalInt parsed = parseCoordinate(value);
        selection = point.replace(selection, withAxis(original, axis, parsed.getAsInt()));
        writeLocalStateAndSync();
    }

    private Button axisButton(Point point, Axis axis, int delta, int x, int y) {
        return Button.builder(Component.literal(delta < 0 ? "-" : "+"), button -> {
            adjust(point, axis, delta);
            setFocused(null);
        }).bounds(x, y, ADJUST_BUTTON_WIDTH, ADJUST_BUTTON_HEIGHT).build();
    }

    private void adjust(Point point, Axis axis, int delta) {
        BlockPos original = point.value(selection);
        if (original == null) return;
        selection = point.replace(selection, withAxis(original, axis, adjustedCoordinate(axisValue(original, axis), delta)));
        updateWidgets();
        writeLocalStateAndSync();
    }

    static int adjustedCoordinate(int current, int delta) {
        if (delta > 0 && current == Integer.MAX_VALUE || delta < 0 && current == Integer.MIN_VALUE) return current;
        return current + delta;
    }

    private void writeLocalStateAndSync() {
        ItemStack detector = mainHandDetector();
        if (detector.isEmpty()) return;
        detector.set(ModDataComponents.MULTIBLOCK_DETECTOR_SELECTION.get(), selection);
        if (maskEnabled) detector.set(ModDataComponents.MULTIBLOCK_DETECTOR_MASK.get(), true);
        else detector.remove(ModDataComponents.MULTIBLOCK_DETECTOR_MASK.get());
        ClientPacketDistributor.sendToServer(new PktMultiblockDetectorUpdatePayload(selection, maskEnabled));
    }

    private void updateWidgets() {
        if (coordinateInputs[0] == null) return;
        updatingWidgets = true;
        try {
            for (Point point : Point.values()) {
                BlockPos value = point.value(selection);
                for (Axis axis : AXES) {
                    int index = inputIndex(point, axis);
                    coordinateInputs[index].setValue(value == null ? "" : Integer.toString(axisValue(value, axis)));
                    coordinateInputs[index].visible = true;
                    coordinateInputs[index].active = value != null;
                    decrementButtons[index].visible = true;
                    decrementButtons[index].active = value != null;
                    incrementButtons[index].visible = true;
                    incrementButtons[index].active = value != null;
                }
            }
            maskButton.setMessage(maskLabel());
        } finally {
            updatingWidgets = false;
        }
    }

    private void renderPointRow(GuiGraphicsExtractor graphics, Point point, int row) {
        BlockPos value = point.value(selection);
        graphics.text(font, blockName(value), panelLeft() + CONTENT_X, panelTop() + row + 5, TEXT_COLOR, false);
        for (Axis axis : AXES) {
            graphics.text(font, Component.literal(axis.name() + "="), panelLeft() + inputX(axis) - AXIS_LABEL_WIDTH,
                    panelTop() + row + 5, TEXT_COLOR, false);
        }
    }

    private void renderLabel(GuiGraphicsExtractor graphics, Component label, int x, int y) {
        graphics.text(font, label, panelLeft() + x, panelTop() + y, TEXT_COLOR, false);
    }

    private Component controllerLabel() {
        BlockPos position = selection.controllerPos();
        Component value = position == null ? Component.translatable("tooltip.mmcr.multiblock_detector.not_set")
                : positionLabel(position, selection.controllerFace());
        return Component.translatable("tooltip.mmcr.multiblock_detector.controller", value);
    }

    private Component pointLabel(Point point) {
        return Component.translatable(point == Point.FIRST
                ? "tooltip.mmcr.multiblock_detector.first" : "tooltip.mmcr.multiblock_detector.second",
                pointLabelValue(point.value(selection)));
    }

    private Component pointLabelValue(BlockPos position) {
        return position == null ? Component.translatable("tooltip.mmcr.multiblock_detector.not_set")
                : positionLabel(position, null);
    }

    private Component positionLabel(BlockPos position, Direction face) {
        Component facing = face == null ? Component.empty()
                : Component.translatable("tooltip.mmcr.multiblock_detector.face", face.getSerializedName());
        return Component.translatable("tooltip.mmcr.multiblock_detector.position", blockName(position),
                position.toShortString(), facing);
    }

    private Component blockName(BlockPos position) {
        if (position == null) return Component.translatable("tooltip.mmcr.multiblock_detector.not_set");
        Level level = Minecraft.getInstance().level;
        if (level == null || !level.hasChunkAt(position)) {
            return Component.translatable("tooltip.mmcr.multiblock_detector.unknown_block");
        }
        return level.getBlockState(position).getBlock().getName();
    }

    private Component maskLabel() {
        return Component.literal(maskEnabled ? "Mask: ON" : "Mask: OFF");
    }

    private ItemStack mainHandDetector() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return ItemStack.EMPTY;
        ItemStack item = minecraft.player.getMainHandItem();
        return item.is(ModItems.MULTIBLOCK_DETECTOR.get()) ? item : ItemStack.EMPTY;
    }

    private int panelLeft() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int panelTop() {
        return (height - PANEL_HEIGHT) / 2;
    }

    private static int inputIndex(Point point, Axis axis) {
        return point.ordinal() * AXES.length + axis.ordinal();
    }

    private static int inputX(Axis axis) {
        return AXIS_START_X + axis.ordinal() * AXIS_COLUMN_WIDTH + AXIS_LABEL_WIDTH;
    }

    private static int inputRow(Point point) {
        return point == Point.FIRST ? FIRST_INPUT_ROW : SECOND_INPUT_ROW;
    }

    private static int adjustRow(Point point) {
        return point == Point.FIRST ? FIRST_ADJUST_ROW : SECOND_ADJUST_ROW;
    }

    private static int axisValue(BlockPos position, Axis axis) {
        return switch (axis) {
            case X -> position.getX();
            case Y -> position.getY();
            case Z -> position.getZ();
        };
    }

    private enum Point {
        FIRST {
            @Override
            BlockPos value(MultiblockDetectorSelection selection) {
                return selection.firstPos();
            }

            @Override
            MultiblockDetectorSelection replace(MultiblockDetectorSelection selection, BlockPos value) {
                return selection.withFirst(value);
            }
        },
        SECOND {
            @Override
            BlockPos value(MultiblockDetectorSelection selection) {
                return selection.secondPos();
            }

            @Override
            MultiblockDetectorSelection replace(MultiblockDetectorSelection selection, BlockPos value) {
                return selection.withSecond(value);
            }
        };

        abstract BlockPos value(MultiblockDetectorSelection selection);

        abstract MultiblockDetectorSelection replace(MultiblockDetectorSelection selection, BlockPos value);
    }
}
