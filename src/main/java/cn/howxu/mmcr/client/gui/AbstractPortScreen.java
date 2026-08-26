package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.internal.autoio.AutoIOConfig;
import cn.howxu.mmcr.internal.autoio.AutoIOAction;
import cn.howxu.mmcr.internal.menu.AbstractMachineMenu;
import cn.howxu.mmcr.internal.menu.CombinedPortMenu;
import cn.howxu.mmcr.internal.menu.EnergyHatchMenu;
import cn.howxu.mmcr.internal.menu.ExtendedCombinedMenu;
import cn.howxu.mmcr.internal.menu.ExtendedFluidMenu;
import cn.howxu.mmcr.internal.menu.ExtendedItemMenu;
import cn.howxu.mmcr.internal.menu.FluidHatchMenu;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.network.PktAutoIOConfigPayload;
import cn.howxu.mmcr.internal.network.PktEjectPortContentsPayload;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Shared Auto IO controls for machine port screens.
 *
 * @author howxu <dev@howxu.cn>
 */
abstract class AbstractPortScreen<M extends AbstractMachineMenu> extends AbstractScrollableTextScreen<M> {
    private static final int HIDDEN_INVENTORY_LABEL_Y = -1000;
    private static final int HIDDEN_SLOT_X = -1000;
    private static final int HIDDEN_SLOT_Y = -1000;
    private static final int TEXT_UI_PLAYER_SLOT_Y_OFFSET = 47;
    private static final int AUTO_IO_SIDE_GRID_X = 12;
    private static final int AUTO_IO_SIDE_GRID_Y = 6;
    private static final int AUTO_IO_SIDE_GRID_STEP = 24;
    private static final int AUTO_IO_SIDE_BUTTON_SIZE = 20;
    private static final int AUTO_IO_TOGGLE_BUTTON_WIDTH = 69;
    private static final int AUTO_IO_TOGGLE_BUTTON_HEIGHT = 20;
    private static final float AUTO_IO_TOGGLE_TEXT_SCALE = 0.85F;
    private static final int TEXT_UI_PAGE_BUTTON_OFFSET_X = -6;
    private static final int TEXT_UI_PAGE_BUTTON_OFFSET_Y = 5;
    private static final int AUTO_IO_PAGE_BUTTON_STEP = 14;
    protected static final float TEXT_DETAIL_SCALE = 0.85F;
    protected static final int TEXT_DETAIL_LINE_SPACING = 10;
    protected static final int TEXT_VIEW_X = 9;
    protected static final int TEXT_VIEW_Y = 24;
    protected static final int TEXT_VIEW_RIGHT = 168;
    protected static final int TEXT_VIEW_BOTTOM = 123;

    protected boolean autoIOPage;
    private Identifier selectedCapabilityId;
    private Button autoIOPageButton;
    private Button secondaryAutoIOPageButton;
    private Button autoIOToggleButton;
    private Button ejectButton;
    private final EnumMap<Direction, Button> autoIOSideButtons = new EnumMap<>(Direction.class);
    private final List<HiddenSlotPosition> hiddenSlotPositions = new ArrayList<>();
    private final List<RelocatedSlotPosition> relocatedPlayerSlotPositions = new ArrayList<>();
    private final List<TooltipEntry> tooltipEntries = new ArrayList<>();

    protected AbstractPortScreen(M menu, Inventory inventory, Component title, int imageHeight) {
        super(menu, inventory, title, 176, imageHeight);
        inventoryLabelY = HIDDEN_INVENTORY_LABEL_Y;
    }

    @Override
    protected final TextViewport scrollableTextViewport() {
        return new TextViewport(TEXT_VIEW_X, TEXT_VIEW_Y,
                TEXT_VIEW_RIGHT - TEXT_VIEW_X + 1,
                Math.min(TEXT_VIEW_BOTTOM, imageHeight) - TEXT_VIEW_Y + 1,
                TEXT_DETAIL_SCALE, TEXT_DETAIL_LINE_SPACING);
    }

    @Override
    protected int scrollableTextLineCount() {
        return 0;
    }

    protected abstract BlockPos portPos();

    protected abstract IOType ownerIOType();

    protected abstract int portSlotCount();

    protected abstract Identifier texture(boolean autoIOPage);

    @Override
    protected final void init() {
        super.init();
        initAutoIOButtons();
    }

    @Override
    protected final void containerTick() {
        super.containerTick();
        updateAutoIOWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (autoIOPage) return false;
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    protected final void slotClicked(Slot slot, int slotIndex, int mouseButton, ContainerInput clickType) {
        if (menu instanceof ItemBusMenu itemBus && hidesSlotOnAutoIOPage(itemBus, autoIOPage, slot, slotIndex)
                || menu instanceof CombinedPortMenu combined && hidesSlotOnAutoIOPage(combined, autoIOPage, slot, slotIndex)) return;
        super.slotClicked(slot, slotIndex, mouseButton, clickType);
    }

    static boolean hidesSlotOnAutoIOPage(ItemBusMenu menu, boolean autoIOPage, Slot slot, int slotIndex) {
        return autoIOPage && slot != null && slotIndex >= 0 && slotIndex < menu.busSlotCount();
    }

    static boolean hidesSlotOnAutoIOPage(CombinedPortMenu menu, boolean autoIOPage, Slot slot, int slotIndex) {
        return autoIOPage && slot != null && slotIndex >= 0 && slotIndex < menu.itemSlotCount();
    }

    static boolean isOutputPort(IOType resolvedIOType, IOType ownerIOType) {
        return (resolvedIOType == null ? ownerIOType : resolvedIOType) == IOType.OUTPUT;
    }

    protected final void selectCapability(Identifier capabilityId) {
        selectedCapabilityId = capabilityId;
    }

    static boolean contains(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    protected final void clearTooltipEntries() {
        tooltipEntries.clear();
    }

    protected final void addTooltip(int x, int y, int width, int height, List<Component> lines) {
        tooltipEntries.add(new TooltipEntry(x, y, width, height, List.copyOf(lines)));
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        for (TooltipEntry entry : tooltipEntries) {
            if (contains(entry.x(), entry.y(), entry.width(), entry.height(), mouseX, mouseY)) {
                graphics.setComponentTooltipForNextFrame(font, entry.lines(), mouseX, mouseY);
                return;
            }
        }
    }

    private void initAutoIOButtons() {
        int autoIOPageButtonX = leftPos + imageWidth - 16;
        int autoIOPageButtonY = topPos + 4;
        if (isTextUi()) {
            autoIOPageButtonX += TEXT_UI_PAGE_BUTTON_OFFSET_X;
            autoIOPageButtonY += TEXT_UI_PAGE_BUTTON_OFFSET_Y;
        }
        List<Identifier> capabilityIds = supportedCapabilityIds();
        autoIOPageButton = createAutoIOPageButton(autoIOPageButtonX, autoIOPageButtonY, capabilityIds.getFirst());
        if (capabilityIds.size() > 1) {
            secondaryAutoIOPageButton = createAutoIOPageButton(autoIOPageButtonX,
                    autoIOPageButtonY + AUTO_IO_PAGE_BUTTON_STEP, capabilityIds.get(1));
        }

        autoIOToggleButton = addRenderableWidget(new AutoIOToggleButton(leftPos + autoIOSideButtonX(2) + AUTO_IO_SIDE_BUTTON_SIZE + 6,
                topPos + autoIOSideButtonY(2), autoIOToggleLabel(), button -> {
            IOPortBlockEntity port = portEntity();
            boolean enabled = port == null || !selectedAutoIOConfig().enabled();
            ClientPacketDistributor.sendToServer(new PktAutoIOConfigPayload(portPos(), selectedCapabilityId(),
                    AutoIOAction.SET_ENABLED, null, enabled));
            button.setFocused(false);
        }));

        ejectButton = addRenderableWidget(new EjectButton(leftPos + autoIOSideButtonX(2) + AUTO_IO_SIDE_BUTTON_SIZE + 6,
                Component.translatable("mmcr.auto_io.eject_contents"), button -> {
            ClientPacketDistributor.sendToServer(new PktEjectPortContentsPayload(portPos(), selectedCapabilityId()));
                button.setFocused(false);
        }, topPos + autoIOSideButtonY(1)));

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                Direction side = autoIODirectionAt(x, y);
                if (side == null) continue;
                boolean shiftAllSidesCell = x == 1 && y == 1;
                Button button = addRenderableWidget(new AutoIOSideButton(leftPos + autoIOSideButtonX(x), topPos + autoIOSideButtonY(y), side,
                        clicked -> {
                            clicked.setFocused(false);
                            IOPortBlockEntity port = portEntity();
                            if (shiftAllSidesCell && Minecraft.getInstance().hasShiftDown()) {
                                ClientPacketDistributor.sendToServer(new PktAutoIOConfigPayload(portPos(), selectedCapabilityId(),
                                        AutoIOAction.SET_ALL_SIDES, null, false));
                                return;
                            }
                            boolean enabled = port == null || !selectedAutoIOConfig().isSideEnabled(side);
                            ClientPacketDistributor.sendToServer(new PktAutoIOConfigPayload(portPos(), selectedCapabilityId(),
                                    AutoIOAction.SET_SIDE, side, enabled));
                        }, () -> portEntity() != null && selectedAutoIOConfig().isSideEnabled(side), this::portEntity));
                autoIOSideButtons.put(side, button);
            }
        }
        updateAutoIOWidgets();
    }

    private Button createAutoIOPageButton(int x, int y, Identifier capabilityId) {
        return addRenderableWidget(Button.builder(Component.literal("⇄"), button -> {
            if (autoIOPage) {
                autoIOPage = false;
            } else {
                selectCapability(capabilityId);
                autoIOPage = true;
            }
            resetTextScrollOffset();
            updateAutoIOWidgets();
            button.setFocused(false);
        }).bounds(x, y, 12, 12).build());
    }

    private void updateAutoIOWidgets() {
        updateAutoIOSlotVisibility();
        if (autoIOPageButton != null) {
            autoIOPageButton.setMessage(Component.literal(autoIOPage ? "←" : "⇄"));
            autoIOPageButton.setTooltip(Tooltip.create(autoIOPage
                    ? Component.translatable("mmcr.auto_io.back")
                    : Component.translatable(autoIOControlTooltipKey(supportedCapabilityIds().getFirst(), isOutputPort()))));
        }
        if (secondaryAutoIOPageButton != null) {
            List<Identifier> capabilityIds = supportedCapabilityIds();
            secondaryAutoIOPageButton.visible = !autoIOPage && capabilityIds.size() > 1;
            secondaryAutoIOPageButton.active = secondaryAutoIOPageButton.visible;
            if (capabilityIds.size() > 1) {
                secondaryAutoIOPageButton.setTooltip(Tooltip.create(Component.translatable(
                        autoIOControlTooltipKey(capabilityIds.get(1), isOutputPort()))));
            }
        }
        if (autoIOToggleButton != null) {
            autoIOToggleButton.visible = autoIOPage;
            autoIOToggleButton.active = autoIOPage;
            autoIOToggleButton.setMessage(autoIOToggleLabel());
            if (autoIOToggleButton instanceof AutoIOToggleButton toggleButton) {
                toggleButton.setLines(autoIOToggleTypeLabel(isOutputPort()), autoIOToggleStateLabel(isAutoIOEnabled()));
            }
        }
        if (ejectButton != null) {
            boolean showEject = autoIOPage && !isOutputPort();
            ejectButton.visible = showEject;
            ejectButton.active = showEject;
        }
        for (var entry : autoIOSideButtons.entrySet()) {
            Button button = entry.getValue();
            button.visible = autoIOPage;
            button.active = autoIOPage;
            button.setTooltip(Tooltip.create(autoIOSideTooltip(entry.getKey())));
        }
    }

    private void updateAutoIOSlotVisibility() {
        updatePlayerSlotLayout();
        int hiddenSlotCount = portSlotCount();
        if (hiddenSlotCount <= 0) return;
        if (autoIOPage) {
            if (!hiddenSlotPositions.isEmpty()) return;
            for (int slotIndex = 0; slotIndex < hiddenSlotCount; slotIndex++) {
                Slot slot = menu.getSlot(slotIndex);
                Slot hiddenSlot = new Slot(slot.container, slot.getContainerSlot(), HIDDEN_SLOT_X, HIDDEN_SLOT_Y);
                hiddenSlot.index = slot.index;
                hiddenSlotPositions.add(new HiddenSlotPosition(slotIndex, slot));
                menu.slots.set(slotIndex, hiddenSlot);
            }
            return;
        }
        for (HiddenSlotPosition position : hiddenSlotPositions) {
            menu.slots.set(position.index(), position.slot());
        }
        hiddenSlotPositions.clear();
    }

    private void updatePlayerSlotLayout() {
        if (!isTextUi()) return;
        if (autoIOPage) {
            if (!relocatedPlayerSlotPositions.isEmpty()) return;
            for (int slotIndex = portSlotCount(); slotIndex < menu.slots.size(); slotIndex++) {
                Slot slot = menu.getSlot(slotIndex);
                Slot relocatedSlot = new Slot(slot.container, slot.getContainerSlot(), slot.x,
                        slot.y - TEXT_UI_PLAYER_SLOT_Y_OFFSET);
                relocatedSlot.index = slot.index;
                relocatedPlayerSlotPositions.add(new RelocatedSlotPosition(slotIndex, slot));
                menu.slots.set(slotIndex, relocatedSlot);
            }
            return;
        }
        for (RelocatedSlotPosition position : relocatedPlayerSlotPositions) {
            menu.slots.set(position.index(), position.slot());
        }
        relocatedPlayerSlotPositions.clear();
    }

    private IOPortBlockEntity portEntity() {
        if (Minecraft.getInstance().level == null) return null;
        return Minecraft.getInstance().level.getBlockEntity(portPos()) instanceof IOPortBlockEntity port ? port : null;
    }

    private boolean isAutoIOEnabled() {
        IOPortBlockEntity port = portEntity();
        return port != null && selectedAutoIOConfig().enabled();
    }

    private boolean isTextUi() {
        return menu instanceof ExtendedItemMenu || menu instanceof ExtendedFluidMenu
                || menu instanceof ExtendedCombinedMenu;
    }

    private boolean isOutputPort() {
        IOPortBlockEntity port = portEntity();
        return isOutputPort(port == null ? null : port.ioType(), ownerIOType());
    }

    private Component autoIOToggleLabel() {
        return autoIOToggleTypeLabel(isOutputPort()).copy()
                .append(Component.literal(": "))
                .append(autoIOToggleStateLabel(isAutoIOEnabled()));
    }

    private static Component autoIOToggleTypeLabel(boolean outputPort) {
        return Component.translatable(outputPort ? "mmcr.auto_io.auto_output" : "mmcr.auto_io.auto_input");
    }

    private static Component autoIOToggleStateLabel(boolean enabled) {
        return Component.translatable(enabled ? "mmcr.auto_io.state.enabled" : "mmcr.auto_io.state.disabled")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private Component autoIOSideTooltip(Direction side) {
        IOPortBlockEntity port = portEntity();
        return Component.translatable("mmcr.auto_io.side", Component.translatable("mmcr.direction." + side.name().toLowerCase(Locale.ROOT)),
                Component.translatable(port != null && selectedAutoIOConfig().isSideEnabled(side)
                        ? "mmcr.auto_io.enabled" : "mmcr.auto_io.disabled"));
    }

    private AutoIOConfig selectedAutoIOConfig() {
        IOPortBlockEntity port = portEntity();
        if (port == null) return new AutoIOConfig();
        return port.autoIOConfig(new CapabilityType(selectedCapabilityId()));
    }

    private Identifier selectedCapabilityId() {
        List<Identifier> capabilityIds = supportedCapabilityIds();
        IOPortBlockEntity port = portEntity();
        if (port != null) {
            List<Identifier> available = port.capabilitySnapshot().capabilities().stream()
                    .map(capability -> capability.type().id())
                    .toList();
            capabilityIds = capabilityIds.stream().filter(available::contains).toList();
        }
        return selectedCapabilityId(capabilityIds);
    }

    protected List<Identifier> supportedCapabilityIds() {
        if (menu instanceof CombinedPortMenu || menu instanceof ExtendedCombinedMenu) {
            return List.of(MMCR.id("item"), MMCR.id("fluid"));
        }
        if (menu instanceof FluidHatchMenu || menu instanceof ExtendedFluidMenu) {
            return List.of(MMCR.id("fluid"));
        }
        if (menu instanceof EnergyHatchMenu) return List.of(MMCR.id("energy"));
        return List.of(MMCR.id("item"));
    }

    static String autoIOControlTooltipKey(Identifier capabilityId, boolean outputPort) {
        return "mmcr.auto_io." + capabilityId.getPath() + (outputPort ? "_output" : "_input") + "_control";
    }

    protected final Identifier selectedCapabilityId(List<Identifier> capabilityIds) {
        if (selectedCapabilityId != null && capabilityIds.contains(selectedCapabilityId)) return selectedCapabilityId;
        return capabilityIds.stream().findFirst().orElse(MMCR.id("item"));
    }

    private static Direction autoIODirectionAt(int x, int y) {
        if (x == 1 && y == 0) return Direction.UP;
        if (x == 0 && y == 1) return Direction.WEST;
        if (x == 1 && y == 1) return Direction.NORTH;
        if (x == 2 && y == 1) return Direction.EAST;
        if (x == 1 && y == 2) return Direction.DOWN;
        if (x == 2 && y == 2) return Direction.SOUTH;
        return null;
    }

    private static int autoIOSideButtonX(int gridX) {
        return AUTO_IO_SIDE_GRID_X + gridX * AUTO_IO_SIDE_GRID_STEP;
    }

    private static int autoIOSideButtonY(int gridY) {
        return AUTO_IO_SIDE_GRID_Y + gridY * AUTO_IO_SIDE_GRID_STEP;
    }

    private record HiddenSlotPosition(int index, Slot slot) {
    }

    private record RelocatedSlotPosition(int index, Slot slot) {
    }

    private record TooltipEntry(int x, int y, int width, int height, List<Component> lines) {
    }

    abstract static class AutoIOStyledButton extends Button {
        AutoIOStyledButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, Button.DEFAULT_NARRATION);
        }

        protected void drawAutoIOBackground(GuiGraphicsExtractor graphics) {
            int baseColor = active ? 0xFF6B6B6B : 0xFF3F3F3F;
            int hoverColor = isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFAAAAAA;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), hoverColor);
            graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, baseColor);
        }
    }

    static class AutoIOToggleButton extends AutoIOStyledButton {
        private Component typeLine;
        private Component stateLine;

        AutoIOToggleButton(int x, int y, Component message, OnPress onPress) {
            super(x, y, AUTO_IO_TOGGLE_BUTTON_WIDTH, AUTO_IO_TOGGLE_BUTTON_HEIGHT, message, onPress);
            typeLine = message;
            stateLine = Component.empty();
        }

        void setLines(Component typeLine, Component stateLine) {
            this.typeLine = typeLine;
            this.stateLine = stateLine;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            drawAutoIOBackground(graphics);
            Font font = Minecraft.getInstance().font;
            graphics.pose().pushMatrix();
            graphics.pose().scale(AUTO_IO_TOGGLE_TEXT_SCALE, AUTO_IO_TOGGLE_TEXT_SCALE);
            renderCenteredLine(graphics, font, typeLine, getY() + 2);
            renderCenteredLine(graphics, font, stateLine, getY() + 11);
            graphics.pose().popMatrix();
        }

        private void renderCenteredLine(GuiGraphicsExtractor graphics, Font font, Component text, int y) {
            int textX = (int) ((getX() + (getWidth() - font.width(text) * AUTO_IO_TOGGLE_TEXT_SCALE) / 2.0F) / AUTO_IO_TOGGLE_TEXT_SCALE);
            graphics.text(font, text, textX, (int) (y / AUTO_IO_TOGGLE_TEXT_SCALE), 0xFFFFFFFF, false);
        }
    }

    static class EjectButton extends AutoIOStyledButton {
        EjectButton(int x, Component message, OnPress onPress, int y) {
            super(x, y, AUTO_IO_TOGGLE_BUTTON_WIDTH, AUTO_IO_TOGGLE_BUTTON_HEIGHT, message, onPress);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            drawAutoIOBackground(graphics);
            Font font = Minecraft.getInstance().font;
            graphics.text(font, getMessage(), getX() + (getWidth() - font.width(getMessage())) / 2, getY() + 6, 0xFFFFFFFF, false);
        }
    }

    static class AutoIOSideButton extends AutoIOStyledButton {
        private final Direction side;
        private final BooleanSupplier selected;
        private final Supplier<IOPortBlockEntity> portSupplier;

        AutoIOSideButton(int x, int y, Direction side, OnPress onPress, BooleanSupplier selected, Supplier<IOPortBlockEntity> portSupplier) {
            super(x, y, AUTO_IO_SIDE_BUTTON_SIZE, AUTO_IO_SIDE_BUTTON_SIZE, Component.empty(), onPress);
            this.side = side;
            this.selected = selected;
            this.portSupplier = portSupplier;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            drawAutoIOBackground(graphics);
            if (selected.getAsBoolean()) {
                graphics.fill(getX() + 3, getY() + 3, getX() + getWidth() - 3, getY() + getHeight() - 3, 0xFF2E7D32);
                graphics.fill(getX() + 2, getY() + 2, getX() + getWidth() - 2, getY() + 3, 0xFF66BB6A);
                graphics.fill(getX() + 2, getY() + getHeight() - 3, getX() + getWidth() - 2, getY() + getHeight() - 2, 0xFF66BB6A);
                graphics.fill(getX() + 2, getY() + 2, getX() + 3, getY() + getHeight() - 2, 0xFF66BB6A);
                graphics.fill(getX() + getWidth() - 3, getY() + 2, getX() + getWidth() - 2, getY() + getHeight() - 2, 0xFF66BB6A);
            }
            ItemStack icon = portSupplier.get() == null ? ItemStack.EMPTY : portSupplier.get().adjacentSide(side).icon();
            if (!icon.isEmpty()) graphics.item(icon, getX() + 2, getY() + 2, 0);
        }
    }
}
