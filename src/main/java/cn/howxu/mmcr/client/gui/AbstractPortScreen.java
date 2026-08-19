package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.internal.autoio.AutoIOAction;
import cn.howxu.mmcr.internal.menu.AbstractMachineMenu;
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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
abstract class AbstractPortScreen<M extends AbstractMachineMenu> extends AbstractContainerScreen<M> {
    private static final int HIDDEN_INVENTORY_LABEL_Y = -1000;
    private static final int HIDDEN_SLOT_X = -1000;
    private static final int HIDDEN_SLOT_Y = -1000;
    private static final int AUTO_IO_SIDE_GRID_X = 12;
    private static final int AUTO_IO_SIDE_GRID_Y = 6;
    private static final int AUTO_IO_SIDE_GRID_STEP = 24;
    private static final int AUTO_IO_SIDE_BUTTON_SIZE = 20;
    private static final int AUTO_IO_TOGGLE_BUTTON_WIDTH = 69;
    private static final int AUTO_IO_TOGGLE_BUTTON_HEIGHT = 20;
    private static final float AUTO_IO_TOGGLE_TEXT_SCALE = 0.85F;

    protected boolean autoIOPage;
    private Button autoIOPageButton;
    private Button autoIOToggleButton;
    private Button ejectButton;
    private final EnumMap<Direction, Button> autoIOSideButtons = new EnumMap<>(Direction.class);
    private final List<HiddenSlotPosition> hiddenSlotPositions = new ArrayList<>();

    protected AbstractPortScreen(M menu, Inventory inventory, Component title, int imageHeight) {
        super(menu, inventory, title, 176, imageHeight);
        inventoryLabelY = HIDDEN_INVENTORY_LABEL_Y;
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
    protected final void slotClicked(Slot slot, int slotIndex, int mouseButton, ContainerInput clickType) {
        if (menu instanceof ItemBusMenu itemBus && hidesSlotOnAutoIOPage(itemBus, autoIOPage, slot, slotIndex)) return;
        super.slotClicked(slot, slotIndex, mouseButton, clickType);
    }

    static boolean hidesSlotOnAutoIOPage(ItemBusMenu menu, boolean autoIOPage, Slot slot, int slotIndex) {
        return autoIOPage && slot != null && slotIndex >= 0 && slotIndex < menu.busSlotCount();
    }

    static boolean isOutputPort(IOType resolvedIOType, IOType ownerIOType) {
        return (resolvedIOType == null ? ownerIOType : resolvedIOType) == IOType.OUTPUT;
    }

    private void initAutoIOButtons() {
        autoIOPageButton = addRenderableWidget(Button.builder(Component.literal("⇄"), button -> {
            autoIOPage = !autoIOPage;
            updateAutoIOWidgets();
            button.setFocused(false);
        }).bounds(leftPos + imageWidth - 16, topPos + 4, 12, 12)
                .tooltip(Tooltip.create(Component.translatable("mmcr.auto_io.control"))).build());

        autoIOToggleButton = addRenderableWidget(new AutoIOToggleButton(leftPos + autoIOSideButtonX(2) + AUTO_IO_SIDE_BUTTON_SIZE + 6,
                topPos + autoIOSideButtonY(2), autoIOToggleLabel(), button -> {
            IOPortBlockEntity port = portEntity();
            boolean enabled = port == null || !port.autoIOConfig().enabled();
            ClientPacketDistributor.sendToServer(new PktAutoIOConfigPayload(portPos(), AutoIOAction.SET_ENABLED, null, enabled));
            button.setFocused(false);
        }));

        ejectButton = addRenderableWidget(new EjectButton(leftPos + autoIOSideButtonX(2) + AUTO_IO_SIDE_BUTTON_SIZE + 6,
                Component.translatable("mmcr.auto_io.eject_contents"), button -> {
            ClientPacketDistributor.sendToServer(new PktEjectPortContentsPayload(portPos()));
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
                                ClientPacketDistributor.sendToServer(new PktAutoIOConfigPayload(portPos(), AutoIOAction.SET_ALL_SIDES, null, false));
                                return;
                            }
                            boolean enabled = port == null || !port.autoIOConfig().isSideEnabled(side);
                            ClientPacketDistributor.sendToServer(new PktAutoIOConfigPayload(portPos(), AutoIOAction.SET_SIDE, side, enabled));
                        }, () -> portEntity() != null && portEntity().autoIOConfig().isSideEnabled(side), this::portEntity));
                autoIOSideButtons.put(side, button);
            }
        }
        updateAutoIOWidgets();
    }

    private void updateAutoIOWidgets() {
        updateAutoIOSlotVisibility();
        if (autoIOPageButton != null) autoIOPageButton.setMessage(Component.literal(autoIOPage ? "←" : "⇄"));
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
        if (!(menu instanceof ItemBusMenu itemBus)) return;
        if (autoIOPage) {
            if (!hiddenSlotPositions.isEmpty()) return;
            for (int slotIndex = 0; slotIndex < itemBus.busSlotCount(); slotIndex++) {
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

    private IOPortBlockEntity portEntity() {
        if (Minecraft.getInstance().level == null) return null;
        return Minecraft.getInstance().level.getBlockEntity(portPos()) instanceof IOPortBlockEntity port ? port : null;
    }

    private boolean isAutoIOEnabled() {
        IOPortBlockEntity port = portEntity();
        return port != null && port.autoIOConfig().enabled();
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
                Component.translatable(port != null && port.autoIOConfig().isSideEnabled(side) ? "mmcr.auto_io.enabled" : "mmcr.auto_io.disabled"));
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
