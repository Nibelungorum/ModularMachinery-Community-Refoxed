package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.client.render.FluidGuiRenderer;
import cn.howxu.mmcr.internal.autoio.AutoIOAction;
import cn.howxu.mmcr.internal.menu.EnergyHatchMenu;
import cn.howxu.mmcr.internal.menu.FactorySchedulerMenu;
import cn.howxu.mmcr.internal.menu.FluidHatchMenu;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.network.PktAutoIOConfigPayload;
import cn.howxu.mmcr.internal.network.PktRecipeLockPayload;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.util.ReadableNumber;

import cn.howxu.mmcr.internal.port.ItemBusSize;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.client.gui.Font;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** 一个屏幕入口,按具体菜单类型分派纹理 / 尺寸 / 自定义渲染。 */
public class MachineMenuScreen extends AbstractContainerScreen<AbstractContainerMenu> implements MenuAccess<AbstractContainerMenu> {

    public static final int GUI_TEXTURE_SIZE = 256;

    private static final Identifier TANK_TEXTURE        = MMCR.id("textures/gui/guitank.png");
    private static final Identifier GUI_BAR_TEXTURE     = MMCR.id("textures/gui/guibar.png");
    private static final Identifier CONTROLLER_TEXTURE  = MMCR.id("textures/gui/guicontroller_large.png");
    private static final Identifier SMART_INTERFACE_TEXTURE = MMCR.id("textures/gui/guismartinterface.png");
    private static final int FLUID_BAR_OVERLAY_SOURCE_X = 176;
    private static final int ENERGY_BAR_SOURCE_X = 196;
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance();
    static final int TITLE_COLOR = -12566464;
    static final int CONTROLLER_TITLE_COLOR = 0xFFE8E8E8;
    static final int STATUS_LABEL_COLOR = CONTROLLER_TITLE_COLOR;
    static final int FORMED_STATUS_COLOR = 0xFF55FF55;
    static final int UNFORMED_STATUS_COLOR = 0xFFFF5555;
    static final int IDLE_STATUS_COLOR = 0xFFFFAA00;
    static final int PROGRESS_STATUS_COLOR = -1;
    static final int TITLE_OFFSET_X = 2;
    static final int TITLE_OFFSET_Y = 4;
    static final int FLUID_TITLE_OFFSET_X = 32;
    static final int ENERGY_TITLE_OFFSET_X = 32;
    static final int TANK_TITLE_OFFSET_Y = 3;
    static final int ITEM_BUS_TITLE_OFFSET_X = -4;
    static final int ITEM_BUS_TITLE_OFFSET_Y = -2;
    static final int CONTROLLER_STATUS_OFFSET_Y = 10;
    private static final float CONTROLLER_DETAIL_SCALE = 0.85F;
    private static final int CONTROLLER_DETAIL_LINE_SPACING = 10;
    static final int STORAGE_TEXT_OFFSET_Y = 12;
    static final int FLUID_INFO_OFFSET_Y = 10;
    static final int HIDDEN_INVENTORY_LABEL_Y = -1000;

    private static final int TANK_X = 15;
    static final int TANK_Y = 10;
    private static final int TANK_W = 20;
    private static final int TANK_H = 61;

    private static final int ENERGY_X = 15;
    static final int ENERGY_Y = 10;
    private static final int ENERGY_W = 20;
    private static final int ENERGY_H = 61;
    private static final int AUTO_IO_SIDE_GRID_X = 12;
    private static final int AUTO_IO_SIDE_GRID_Y = 6;
    private static final int AUTO_IO_SIDE_GRID_STEP = 24;
    private static final int AUTO_IO_SIDE_BUTTON_SIZE = 20;
    private static final int AUTO_IO_TOGGLE_BUTTON_WIDTH = 69;
    private static final int AUTO_IO_TOGGLE_BUTTON_HEIGHT = 20;
    private static final float AUTO_IO_TOGGLE_TEXT_SCALE = 0.85F;
    private static final int RECIPE_LOCK_BUTTON_SIZE = 20;
    private static final int PLAYER_INVENTORY_HEIGHT_WITH_HOTBAR = 82;
    private static final int RECIPE_LOCK_ENABLED_BG_COLOR = 0xFF66BB6A;
    private static final int HIDDEN_SLOT_X = -1000;
    private static final int HIDDEN_SLOT_Y = -1000;
    private boolean autoIOPage;
    private Button autoIOPageButton;
    private Button autoIOToggleButton;
    private Button recipeLockButton;
    private final EnumMap<Direction, Button> autoIOSideButtons = new EnumMap<>(Direction.class);
    private final List<HiddenSlotPosition> hiddenSlotPositions = new ArrayList<>();

    public MachineMenuScreen(AbstractContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title,
                menu instanceof MachineControllerMenu ? 176 : 176,
                imageHeightFor(menu));
        boolean fluidMenu = menu instanceof FluidHatchMenu;
        boolean energyMenu = menu instanceof EnergyHatchMenu;
        boolean itemBusMenu = menu instanceof ItemBusMenu;
        boolean factoryMenu = menu instanceof FactorySchedulerMenu;
        boolean showTitle = showsPortTitle(fluidMenu, energyMenu, itemBusMenu)
                && (!(menu instanceof ItemBusMenu itemBus) || itemBus.showsTitle());
        this.titleLabelX = titleX(titleLabelX, fluidMenu, energyMenu, itemBusMenu, factoryMenu);
        this.titleLabelY = showTitle ? titleY(titleLabelY, fluidMenu || energyMenu, itemBusMenu, factoryMenu) : hiddenInventoryLabelY();
        this.inventoryLabelY = hiddenInventoryLabelY();
    }

    static int imageHeightFor(AbstractContainerMenu menu) {
        if (menu instanceof ItemBusMenu itemBus) return itemBus.imageHeight();
        return menu instanceof MachineControllerMenu ? 213 : 166;
    }

    static int titleX(int baseX) {
        return titleX(baseX, false);
    }

    static int titleX(int baseX, boolean tankMenu) {
        return titleX(baseX, tankMenu, false);
    }

    static int titleX(int baseX, boolean tankMenu, boolean itemBusMenu) {
        return titleX(baseX, tankMenu, false, itemBusMenu);
    }

    static int titleX(int baseX, boolean fluidMenu, boolean energyMenu, boolean itemBusMenu) {
        return titleX(baseX, fluidMenu, energyMenu, itemBusMenu, false);
    }

    static int titleX(int baseX, boolean fluidMenu, boolean energyMenu, boolean itemBusMenu, boolean factoryMenu) {
        if (fluidMenu) return baseX + FLUID_TITLE_OFFSET_X;
        if (energyMenu) return baseX + ENERGY_TITLE_OFFSET_X;
        if (itemBusMenu || factoryMenu) return baseX + ITEM_BUS_TITLE_OFFSET_X;
        return baseX + TITLE_OFFSET_X;
    }

    static int titleY(int baseY) {
        return titleY(baseY, false);
    }

    static int titleY(int baseY, boolean tankMenu) {
        return titleY(baseY, tankMenu, false);
    }

    static int titleY(int baseY, boolean tankMenu, boolean itemBusMenu) {
        return titleY(baseY, tankMenu, itemBusMenu, false);
    }

    static int titleY(int baseY, boolean tankMenu, boolean itemBusMenu, boolean factoryMenu) {
        if (tankMenu) return baseY + TANK_TITLE_OFFSET_Y;
        if (itemBusMenu || factoryMenu) return baseY + ITEM_BUS_TITLE_OFFSET_Y;
        return baseY + TITLE_OFFSET_Y;
    }

    static int hiddenInventoryLabelY() {
        return HIDDEN_INVENTORY_LABEL_Y;
    }

    static boolean showsPortTitle(boolean fluidMenu, boolean energyMenu, boolean itemBusMenu) {
        return !itemBusMenu;
    }

    static boolean shouldRenderTitle(boolean fluidMenu, boolean energyMenu, boolean itemBusMenu, boolean autoIOPage) {
        return !autoIOPage && showsPortTitle(fluidMenu, energyMenu, itemBusMenu);
    }

    static Direction autoIODirectionAt(int x, int y) {
        if (x == 1 && y == 0) return Direction.UP;
        if (x == 0 && y == 1) return Direction.WEST;
        if (x == 1 && y == 1) return Direction.NORTH;
        if (x == 2 && y == 1) return Direction.EAST;
        if (x == 1 && y == 2) return Direction.DOWN;
        if (x == 2 && y == 2) return Direction.SOUTH;
        return null;
    }

    static boolean isAutoIOShiftAllSidesCell(int x, int y) {
        return x == 1 && y == 1;
    }

    static boolean isPortSlotIndex(int slotIndex, int portSlotCount) {
        return slotIndex >= 0 && slotIndex < portSlotCount;
    }

    static int autoIOPageButtonSize() {
        return 12;
    }

    static int autoIOPageButtonX(int leftPos, int imageWidth) {
        return leftPos + imageWidth - 4 - autoIOPageButtonSize();
    }

    static boolean hidesSlotOnAutoIOPage(AbstractContainerMenu menu, boolean autoIOPage, Slot slot, int slotIdx, int portSlotCount) {
        return autoIOPage && menu instanceof ItemBusMenu && slot != null && isPortSlotIndex(slotIdx, portSlotCount);
    }

    static int autoIOSideButtonX(int gridX) {
        return AUTO_IO_SIDE_GRID_X + gridX * AUTO_IO_SIDE_GRID_STEP;
    }

    static int autoIOSideButtonY(int gridY) {
        return AUTO_IO_SIDE_GRID_Y + gridY * AUTO_IO_SIDE_GRID_STEP;
    }

    static int autoIOSideButtonSize() {
        return AUTO_IO_SIDE_BUTTON_SIZE;
    }

    static int autoIOToggleButtonX() {
        return autoIOSideButtonX(2) + autoIOSideButtonSize() + 6;
    }

    static int autoIOToggleButtonY() {
        return autoIOSideButtonY(2);
    }

    static int autoIOToggleButtonWidth() {
        return AUTO_IO_TOGGLE_BUTTON_WIDTH;
    }

    static int autoIOToggleButtonHeight() {
        return AUTO_IO_TOGGLE_BUTTON_HEIGHT;
    }

    static float autoIOToggleTextScale() {
        return AUTO_IO_TOGGLE_TEXT_SCALE;
    }

    static int titleColor(boolean controllerMenu) {
        return controllerMenu ? CONTROLLER_TITLE_COLOR : TITLE_COLOR;
    }

    static int controllerStatusX(int titleX) {
        return titleX;
    }

    static int controllerStatusY(int titleY) {
        return titleY + CONTROLLER_STATUS_OFFSET_Y;
    }

    static float controllerDetailScale() {
        return CONTROLLER_DETAIL_SCALE;
    }

    static int nextControllerDetailY(int y) {
        return y + CONTROLLER_DETAIL_LINE_SPACING;
    }

    static int storageTextX(int titleX) {
        return titleX;
    }

    static int storageTextY(int titleY) {
        return titleY + STORAGE_TEXT_OFFSET_Y;
    }

    static int storageTextY(int titleY, boolean tankMenu) {
        int visibleTitleY = tankMenu && titleY == hiddenInventoryLabelY() ? titleY(6, true) : titleY;
        return storageTextY(visibleTitleY);
    }

    static int fluidInfoTextY(int titleY) {
        return titleY + FLUID_INFO_OFFSET_Y;
    }

    static int fluidStorageTextY(int titleY) {
        return fluidInfoTextY(titleY) + 9;
    }

    static int fluidStorageTextY(int titleY, boolean hasFluidName) {
        return hasFluidName ? fluidStorageTextY(titleY) : storageTextY(titleY, true);
    }

    static int fluidBarOverlaySourceX() {
        return FLUID_BAR_OVERLAY_SOURCE_X;
    }

    static Identifier fluidBarOverlayTexture() {
        return TANK_TEXTURE;
    }

    static int energyBarSourceX() {
        return ENERGY_BAR_SOURCE_X;
    }

    static int energyBarSourceY(int filled) {
        return ENERGY_H - filled;
    }

    static Rect recipeLockButtonRect(int left, int top, int width, int height) {
        return new Rect(left + width - RECIPE_LOCK_BUTTON_SIZE - 12,
                top + height - PLAYER_INVENTORY_HEIGHT_WITH_HOTBAR - RECIPE_LOCK_BUTTON_SIZE - 12,
                RECIPE_LOCK_BUTTON_SIZE, RECIPE_LOCK_BUTTON_SIZE);
    }

    static List<Component> recipeLockTooltip(boolean locked, String recipeId) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(locked
                ? "gui.mmcr.controller.recipe_lock.enabled"
                : "gui.mmcr.controller.recipe_lock.disabled"));
        if (locked && !recipeId.isEmpty()) {
            lines.add(Component.translatable("gui.mmcr.controller.recipe_lock.recipe", Component.literal(recipeId)));
        }
        return lines;
    }

    @Override
    protected void init() {
        super.init();
        if (isPortMenu()) initAutoIOButtons();
        if (menu instanceof MachineControllerMenu controller) {
            Rect rect = recipeLockButtonRect(leftPos, topPos, imageWidth, imageHeight);
            recipeLockButton = addRenderableWidget(Button.builder(Component.empty(),
                    button -> {
                        ClientPacketDistributor.sendToServer(new PktRecipeLockPayload(controller.controllerPos(), 0));
                        clearRecipeLockButtonFocus(button);
                    }).bounds(rect.left(), rect.top(), rect.width(), rect.height()).build());
            updateRecipeLockTooltip(controller);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateAutoIOWidgets();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (shouldRenderTitle(menu instanceof FluidHatchMenu, menu instanceof EnergyHatchMenu, menu instanceof ItemBusMenu, autoIOPage)) {
            if (menu instanceof MachineControllerMenu) {
                float scale = controllerDetailScale();
                graphics.pose().pushMatrix();
                graphics.pose().scale(scale, scale);
                graphics.text(font, title, (int) (titleLabelX / scale), (int) (titleLabelY / scale),
                        titleColor(true), false);
                graphics.pose().popMatrix();
            } else {
                graphics.text(font, title, titleLabelX, titleLabelY, titleColor(false), false);
            }
        }
        if (!autoIOPage && menu instanceof FluidHatchMenu fluidHatch) {
            FluidStack fluid = fluidStack(fluidHatch);
            if (shouldRenderFluidInfo(fluid)) {
                graphics.text(font, fluidInfoLine(fluid), titleLabelX, fluidInfoTextY(titleLabelY), TITLE_COLOR, false);
            }
        }
        if (menu instanceof MachineControllerMenu mc) renderControllerStatus(graphics, mc, 0, 0);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        extractBackground(graphics, mouseX, mouseY, partialTicks);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        if (menu instanceof MachineControllerMenu controller) {
            updateRecipeLockTooltip(controller);
            renderRecipeLockButtonIcon(graphics);
        }
        extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void slotClicked(Slot slot, int slotIdx, int mouseButton, ContainerInput clickType) {
        if (isAutoIOPortSlot(slot, slotIdx)) return;
        super.slotClicked(slot, slotIdx, mouseButton, clickType);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        if (autoIOPage && isPortMenu()) {
            BackgroundBlit blit = backgroundBlit(0, 0, imageWidth, Math.min(imageHeight, BASE_BUS_BACKGROUND_HEIGHT));
            graphics.blit(RenderPipelines.GUI_TEXTURED, textureFor(menu, true),
                    x, y, 0, 0, blit.width(), blit.height(),
                    blit.sourceWidth(), blit.sourceHeight());
        } else if (menu instanceof ItemBusMenu) {
            for (BackgroundBlit blit : itemBusBackgroundBlits(imageHeight)) {
                BackgroundBlit drawBlit = backgroundBlit(blit.destY(), blit.sourceY(), imageWidth, blit.height());
                graphics.blit(RenderPipelines.GUI_TEXTURED, textureFor(menu, false),
                        x, y + drawBlit.destY(), 0, drawBlit.sourceY(), drawBlit.width(), drawBlit.height(),
                        drawBlit.sourceWidth(), drawBlit.sourceHeight());
            }
        } else {
            BackgroundBlit blit = backgroundBlit(0, 0, imageWidth, imageHeight);
            graphics.blit(RenderPipelines.GUI_TEXTURED, textureFor(menu, autoIOPage), x, y, 0, 0, blit.width(), blit.height(),
                    blit.sourceWidth(), blit.sourceHeight());
        }

        if (!autoIOPage && menu instanceof FluidHatchMenu fh)        renderFluidTank(graphics, fh, x, y);
        else if (!autoIOPage && menu instanceof EnergyHatchMenu eh)  renderEnergyBar(graphics, eh, x, y);
    }

    private boolean isPortMenu() {
        return menu instanceof ItemBusMenu || menu instanceof FluidHatchMenu || menu instanceof EnergyHatchMenu;
    }

    private BlockPos portPos() {
        if (menu instanceof ItemBusMenu itemBus) return itemBus.pos();
        if (menu instanceof FluidHatchMenu fluidHatch) return fluidHatch.pos();
        if (menu instanceof EnergyHatchMenu energyHatch) return energyHatch.pos();
        return BlockPos.ZERO;
    }

    private IOPortBlockEntity portEntity() {
        if (Minecraft.getInstance().level == null) return null;
        return Minecraft.getInstance().level.getBlockEntity(portPos()) instanceof IOPortBlockEntity port ? port : null;
    }

    private int portSlotCount() {
        return menu instanceof ItemBusMenu itemBus ? itemBus.busSlotCount() : 0;
    }

    private boolean isAutoIOPortSlot(Slot slot, int slotIdx) {
        return hidesSlotOnAutoIOPage(menu, autoIOPage, slot, slotIdx, portSlotCount());
    }

    private void initAutoIOButtons() {
        autoIOPageButton = addRenderableWidget(Button.builder(Component.literal("⇄"), button -> {
            autoIOPage = !autoIOPage;
            updateAutoIOWidgets();
        }).bounds(autoIOPageButtonX(leftPos, imageWidth), topPos + 4, autoIOPageButtonSize(), autoIOPageButtonSize()).tooltip(Tooltip.create(Component.translatable("mmcr.auto_io.control"))).build());

        autoIOToggleButton = addRenderableWidget(new AutoIOToggleButton(leftPos + autoIOToggleButtonX(), topPos + autoIOToggleButtonY(), autoIOToggleLabel(), button -> {
            IOPortBlockEntity port = portEntity();
            boolean enabled = port == null || !port.autoIOConfig().enabled();
            ClientPacketDistributor.sendToServer(new PktAutoIOConfigPayload(portPos(), AutoIOAction.SET_ENABLED, null, enabled));
        }));

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                Direction side = autoIODirectionAt(x, y);
                if (side == null) continue;
                boolean shiftAllSidesCell = isAutoIOShiftAllSidesCell(x, y);
                Button button = addRenderableWidget(new AutoIOSideButton(leftPos + autoIOSideButtonX(x), topPos + autoIOSideButtonY(y), side,
                        clicked -> {
                            IOPortBlockEntity port = portEntity();
                            if (shiftAllSidesCell && Minecraft.getInstance().hasShiftDown()) {
                                ClientPacketDistributor.sendToServer(new PktAutoIOConfigPayload(portPos(), AutoIOAction.SET_ALL_SIDES, null, false));
                                return;
                            }
                            boolean enabled = port == null || !port.autoIOConfig().isSideEnabled(side);
                            ClientPacketDistributor.sendToServer(new PktAutoIOConfigPayload(portPos(), AutoIOAction.SET_SIDE, side, enabled));
                        },
                        () -> portEntity() != null && portEntity().autoIOConfig().isSideEnabled(side), this::portEntity));
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
            for (int slotIdx = 0; slotIdx < itemBus.busSlotCount(); slotIdx++) {
                Slot slot = menu.getSlot(slotIdx);
                Slot hiddenSlot = new Slot(slot.container, slot.getContainerSlot(), HIDDEN_SLOT_X, HIDDEN_SLOT_Y);
                hiddenSlot.index = slot.index;
                hiddenSlotPositions.add(new HiddenSlotPosition(slotIdx, slot));
                menu.slots.set(slotIdx, hiddenSlot);
            }
            return;
        }
        for (HiddenSlotPosition position : hiddenSlotPositions) {
            menu.slots.set(position.index(), position.slot());
        }
        hiddenSlotPositions.clear();
    }

    static Component autoIOToggleLabel(boolean enabled, boolean outputPort) {
        return autoIOToggleTypeLabel(outputPort).copy()
                .append(Component.literal(": "))
                .append(autoIOToggleStateLabel(enabled));
    }

    static Component autoIOToggleTypeLabel(boolean outputPort) {
        return Component.translatable(outputPort ? "mmcr.auto_io.auto_output" : "mmcr.auto_io.auto_input");
    }

    static Component autoIOToggleStateLabel(boolean enabled) {
        return Component.translatable(enabled ? "mmcr.auto_io.state.enabled" : "mmcr.auto_io.state.disabled")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private Component autoIOToggleLabel() {
        return autoIOToggleLabel(isAutoIOEnabled(), isOutputPort());
    }

    private boolean isAutoIOEnabled() {
        IOPortBlockEntity port = portEntity();
        return port != null && port.autoIOConfig().enabled();
    }

    private boolean isOutputPort() {
        IOPortBlockEntity port = portEntity();
        return isOutputPort(port == null ? null : port.ioType(), ownerIOType());
    }

    static boolean isOutputPort(IOType resolvedIOType, IOType ownerIOType) {
        return (resolvedIOType == null ? ownerIOType : resolvedIOType) == IOType.OUTPUT;
    }

    private IOType ownerIOType() {
        if (menu instanceof ItemBusMenu itemBus && itemBus.owner() != null) return itemBus.owner().ioType();
        if (menu instanceof FluidHatchMenu fluidHatch && fluidHatch.owner() != null) return fluidHatch.owner().ioType();
        if (menu instanceof EnergyHatchMenu energyHatch && energyHatch.owner() != null) return energyHatch.owner().ioType();
        return null;
    }

    private Component autoIOSideTooltip(Direction side) {
        IOPortBlockEntity port = portEntity();
        boolean enabled = port != null && port.autoIOConfig().isSideEnabled(side);
        return autoIOSideTooltip(side, enabled);
    }

    static Component autoIOSideTooltip(Direction side, boolean enabled) {
        return Component.translatable("mmcr.auto_io.side", Component.translatable("mmcr.direction." + side.name().toLowerCase(Locale.ROOT)),
                Component.translatable(enabled ? "mmcr.auto_io.enabled" : "mmcr.auto_io.disabled"));
    }

    static Component autoIOSideLine(Direction side, Component blockName) {
        return Component.translatable("mmcr.auto_io.side_block",
                Component.translatable("mmcr.direction." + side.name().toLowerCase(Locale.ROOT)), blockName);
    }

    static ItemStack autoIOSideIcon(IOPortBlockEntity port, Direction side) {
        return port == null ? ItemStack.EMPTY : port.adjacentSide(side).icon();
    }

    /**
     * 一个分段的 item bus 背景贴图指令。源区域(sourceY, height)必须在 {@link #GUI_TEXTURE_SIZE} 范围内,
     * 因为 {@code inventory_normal.png} 高度为 {@value #GUI_TEXTURE_SIZE}px。
     */
    public record BackgroundBlit(int destY, int sourceY, int width, int height, int sourceWidth, int sourceHeight) {
        public BackgroundBlit(int destY, int sourceY, int height) {
            this(destY, sourceY, 0, height, GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
        }
    }

    public static BackgroundBlit backgroundBlit(int destY, int sourceY, int width, int height) {
        return new BackgroundBlit(destY, sourceY, width, height, GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
    }

    /**
     * 把 item bus 背景拆为多段绘制,避免一次 blit 时 sourceY 越界。
     * 第一段使用真实的 0-166 顶部区域,后续每段高 {@link #SLOT_SIZE},sourceY 退回到纹理底部可用像素,
     * 保证每段 sourceY + height ≤ {@value #GUI_TEXTURE_SIZE}。
     */
    public static List<BackgroundBlit> itemBusBackgroundBlits(int imageHeight) {
        List<BackgroundBlit> blits = new ArrayList<>();
        int topHeight = Math.min(BASE_BUS_BACKGROUND_HEIGHT, imageHeight);
        blits.add(new BackgroundBlit(0, 0, topHeight));
        int destY = topHeight;
        while (destY < imageHeight) {
            int height = Math.min(SLOT_SIZE, imageHeight - destY);
            int sourceY = Math.max(0, GUI_TEXTURE_SIZE - height);
            blits.add(new BackgroundBlit(destY, sourceY, height));
            destY += height;
        }
        return blits;
    }

    static final int BASE_BUS_BACKGROUND_HEIGHT = 166;

    static final int SLOT_SIZE = 18;

    private static Identifier textureFor(AbstractContainerMenu menu) {
        return textureFor(menu, false);
    }

    static Identifier textureFor(AbstractContainerMenu menu, boolean autoIOPage) {
        if (autoIOPage && (menu instanceof ItemBusMenu || menu instanceof FluidHatchMenu || menu instanceof EnergyHatchMenu)) return SMART_INTERFACE_TEXTURE;
        if (menu instanceof ItemBusMenu itemBus)     return MMCR.id(itemBus.texturePath());
        if (menu instanceof FluidHatchMenu)         return TANK_TEXTURE;
        if (menu instanceof EnergyHatchMenu)        return TANK_TEXTURE;
        if (menu instanceof MachineControllerMenu)  return CONTROLLER_TEXTURE;
        if (menu instanceof FactorySchedulerMenu factory) return MMCR.id(factory.texturePath());
        return MMCR.id(ItemBusMenu.texturePathForSize(ItemBusSize.NORMAL));
    }

    private void renderFluidTank(GuiGraphicsExtractor g, FluidHatchMenu menu, int x, int y) {
        long amount = menu.fluidAmount();
        long capacity = menu.fluidCapacity();
        if (capacity <= 0) return;

        FluidStack fluid = fluidStack(menu);
        int filled = FluidGuiRenderer.fillHeight(amount, capacity, TANK_H);
        if (shouldRenderFluidInfo(fluid) && filled > 0) {
            FluidGuiRenderer.drawFluid(g, fluid, x + TANK_X, y + TANK_Y + TANK_H - filled, TANK_W, filled);
        }
        g.blit(RenderPipelines.GUI_TEXTURED, fluidBarOverlayTexture(),
                x + TANK_X, y + TANK_Y,
                fluidBarOverlaySourceX(), 0, TANK_W, TANK_H,
                GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
        renderAmountText(g, x, y, amountText(amount, capacity, "mB"), shouldRenderFluidInfo(fluid));
    }

    private void renderEnergyBar(GuiGraphicsExtractor g, EnergyHatchMenu menu, int x, int y) {
        long stored = menu.storedEnergy();
        long capacity = menu.energyCapacity();
        if (capacity <= 0) return;
        int filled = stored <= 0 ? 0 : Math.min(ENERGY_H, Math.max(1, (int) Math.min((long) ENERGY_H, Math.ceilDiv(stored * ENERGY_H, capacity))));
        int drawY = y + ENERGY_Y + (ENERGY_H - filled);
        if (filled > 0) {
            g.blit(RenderPipelines.GUI_TEXTURED, GUI_BAR_TEXTURE,
                    x + ENERGY_X, drawY,
                    energyBarSourceX(), energyBarSourceY(filled), ENERGY_W, filled,
                    GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
        }
        renderAmountText(g, x, y, amountText(stored, capacity, "FE"), false);
    }

    private void renderAmountText(GuiGraphicsExtractor g, int x, int y, String text, boolean hasFluidName) {
        int textY = fluidStorageTextY(titleLabelY, hasFluidName);
        g.text(font, Component.literal(text), x + storageTextX(titleLabelX), y + textY, TITLE_COLOR, false);
    }

    private static String amountText(long amount, long capacity, String unit) {
        return ReadableNumber.format(amount) + " / " + ReadableNumber.format(capacity) + " " + unit;
    }

    static boolean shouldRenderFluidInfo(FluidStack fluid) {
        return fluid != null && !fluid.isEmpty();
    }

    static Component fluidInfoLine(FluidStack fluid) {
        return Component.translatable("gui.mmcr.fluid", fluid.getHoverName());
    }

    private static FluidStack fluidStack(FluidHatchMenu menu) {
        var storage = menu.storage();
        if (storage == null || storage.getResource(0).isEmpty()) return FluidStack.EMPTY;
        return storage.getResource(0).toStack(Math.min(storage.getAmountAsInt(0), Integer.MAX_VALUE));
    }

    private void renderControllerStatus(GuiGraphicsExtractor g, MachineControllerMenu menu, int x, int y) {
        int textY = y + controllerStatusY(titleLabelY);
        int textX = x + controllerStatusX(titleLabelX);
        boolean active = menu.hasActiveRecipe();
        String failure = menu.lastFailureMessage();
        var owner = menu.resolvedOwner();

        final float scale = controllerDetailScale();
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        int scaledX = (int) (textX / scale);
        int scaledY = (int) (textY / scale);
        int scaledWidth = (int) (imageWidth * scale);

        renderControllerStatusLineScaled(g, scaledX, scaledY,
                Component.translatable(controllerStatusKey(menu.isFormed(), active)),
                controllerStatusColor(menu.isFormed(), active));
        scaledY = nextControllerDetailY(scaledY);

        if (owner != null) {
            for (MachineLevel level : owner.getFoundLevels().values()) {
                scaledY = renderScaledWrappedLine(g, levelLine(level), scaledX, scaledY, scaledWidth, STATUS_LABEL_COLOR);
            }
        }

        if (failure != null) {
            Component failureLine = Component.translatable("gui.mmcr.controller.last_failure", Component.translatable(failure));
            scaledY = renderScaledWrappedLine(g, failureLine, scaledX, scaledY, scaledWidth, STATUS_LABEL_COLOR);
        }

        if (owner != null && owner.getMachine() != null) {
            scaledY = renderScaledWrappedLine(g, Component.translatable("gui.mmcr.controller.machine", owner.getMachine().displayName()),
                    scaledX, scaledY, scaledWidth, PROGRESS_STATUS_COLOR);
        }

        for (ControllerStatusLine line : moduleStatusLines(menu.isHostController(), menu.isModuleController(),
                menu.installedModuleCount(), menu.connectedHostId())) {
            scaledY = renderScaledWrappedLine(g, line.text(), scaledX, scaledY, scaledWidth, line.color());
        }

        if (menu.isFormed()) {
            int parallelSlots = menu.parallelControllerCount();
            if (parallelSlots > 0) {
                scaledY = renderScaledWrappedLine(g, parallelSlotLine(parallelSlots),
                        scaledX, scaledY, scaledWidth, STATUS_LABEL_COLOR);
            }
            Component workLine = parallelLine(menu.currentParallelism(), menu.maxParallelism());
            scaledY = renderScaledWrappedLine(g, workLine,
                    scaledX, scaledY, scaledWidth, STATUS_LABEL_COLOR);
        }

        if (menu.isRedstonePaused()) {
            Component redstoneLine = Component.translatable("gui.mmcr.controller.redstone_stopped");
            scaledY = renderScaledWrappedLine(g, redstoneLine, scaledX, scaledY, scaledWidth, STATUS_LABEL_COLOR);
        }

        long stored = menu.totalStoredEnergy();
        long capacity = menu.totalCapacityEnergy();
        if (stored > 0 || capacity > 0) {
            Component energyLine = Component.translatable("gui.mmcr.controller.energy",
                    Component.literal(NUMBER_FORMAT.format(stored)),
                    Component.literal(NUMBER_FORMAT.format(capacity)));
            scaledY = renderScaledWrappedLine(g, energyLine, scaledX, scaledY, scaledWidth, STATUS_LABEL_COLOR);
        }

        FluidStack inputFluid = menu.primaryFluid();
        if (!inputFluid.isEmpty()) {
            Component fluidLine = Component.translatable("gui.mmcr.controller.fluid_input",
                    Component.literal(inputFluid.getFluid().toString()),
                    Component.literal(NUMBER_FORMAT.format(inputFluid.getAmount())));
            scaledY = renderScaledWrappedLine(g, fluidLine, scaledX, scaledY, scaledWidth, STATUS_LABEL_COLOR);
        }

        FluidStack outputFluid = menu.primaryOutputFluid();
        if (!outputFluid.isEmpty()) {
            Component fluidLine = Component.translatable("gui.mmcr.controller.fluid_output",
                    Component.literal(outputFluid.getFluid().toString()),
                    Component.literal(NUMBER_FORMAT.format(outputFluid.getAmount())));
            scaledY = renderScaledWrappedLine(g, fluidLine, scaledX, scaledY, scaledWidth, STATUS_LABEL_COLOR);
        }

        if (active) {
            int percent = progressPercent(menu.activeRecipeTick(), menu.activeRecipeTotalTick());
            Component progressLine = Component.translatable("gui.mmcr.controller.progress", percent + "%" + progressDots(percent));
            renderScaledWrappedLine(g, progressLine, scaledX, scaledY, scaledWidth, PROGRESS_STATUS_COLOR);
        }

        g.pose().popMatrix();
    }

    static Component levelLine(MachineLevel level) {
        var type = MachineLevelRegistry.getType(level.typeId());
        if (type == null || !(level.statePredicate() instanceof BlockPredicate.OfBlockState predicate)) return Component.empty();
        return Component.translatable("gui.mmcr.controller.level", type.displayName(), predicate.state().getBlock().getName());
    }

    static Component parallelLine(int parallelism, int maxParallelism) {
        return Component.translatable("gui.mmcr.controller.parallel",
                Component.literal(NUMBER_FORMAT.format(parallelism)),
                Component.literal(NUMBER_FORMAT.format(maxParallelism)));
    }

    static Component controllerWorkLine(int parallelism, int maxParallelism,
                                        boolean hasFactoryController, int factoryActiveThreadCount, int factoryThreadCount) {
        return hasFactoryController
                ? factoryThreadLine(factoryActiveThreadCount, factoryThreadCount)
                : parallelLine(parallelism, maxParallelism);
    }

    static Component factoryThreadLine(int activeThreadCount, int threadCount) {
        return Component.translatable("gui.mmcr.controller.threads",
                Component.literal(NUMBER_FORMAT.format(activeThreadCount)),
                Component.literal(NUMBER_FORMAT.format(threadCount)));
    }

    static Component parallelSlotLine(int parallelSlots) {
        return Component.translatable("gui.mmcr.controller.parallel_slots",
                Component.literal(NUMBER_FORMAT.format(parallelSlots)));
    }

    static Component installedModuleCountLine(int installedModuleCount) {
        return Component.translatable("gui.mmcr.controller.installed_modules",
                Component.literal(NUMBER_FORMAT.format(installedModuleCount)));
    }

    static List<ControllerStatusLine> moduleStatusLines(boolean hostController, boolean moduleController,
                                                        int installedModuleCount, Optional<Identifier> connectedHostId) {
        if (hostController) return List.of(new ControllerStatusLine(installedModuleCountLine(installedModuleCount), STATUS_LABEL_COLOR));
        if (!moduleController) return List.of();
        return List.of(new ControllerStatusLine(moduleConnectionLine(connectedHostId),
                connectedHostId.isPresent() ? STATUS_LABEL_COLOR : UNFORMED_STATUS_COLOR));
    }

    static Component moduleConnectionLine(Optional<Identifier> hostId) {
        if (hostId.isEmpty()) return Component.translatable("gui.mmcr.controller.module_unconnected");
        var host = MachineRegistry.getMachine(hostId.get());
        Component hostName = host == null ? Component.literal(hostId.get().toString()) : host.displayName();
        return Component.translatable("gui.mmcr.controller.module_connected", hostName);
    }

    private void renderControllerStatusLineScaled(GuiGraphicsExtractor g, int x, int y, Component value, int valueColor) {
        Component label = Component.translatable("gui.mmcr.controller.status_label");
        g.text(font, label, x, y, STATUS_LABEL_COLOR, true);
        g.text(font, value, x + font.width(label) + 4, y, valueColor, true);
    }

    private int renderScaledWrappedLine(GuiGraphicsExtractor g, Component text, int x, int y, int maxWidth, int color) {
        if (font.width(text) <= maxWidth) {
            g.text(font, text, x, y, color, true);
            return nextControllerDetailY(y);
        }
        List<FormattedCharSequence> lines = font.split(text, maxWidth);
        int cy = y;
        for (FormattedCharSequence line : lines) {
            g.text(font, line, x, cy, color, true);
            cy = nextControllerDetailY(cy);
        }
        return cy;
    }

    static String controllerStatusKey(boolean formed, boolean active) {
        if (!formed) return "gui.mmcr.controller.unformed";
        return active ? "gui.mmcr.controller.running" : "gui.mmcr.controller.idle";
    }

    static int progressPercent(int current, int total) {
        if (total <= 0) return 0;
        return Math.min(total, current) * 100 / total;
    }

    static String progressDots(int percent) {
        return ".".repeat((Math.max(0, Math.min(100, percent)) / 5) % 5);
    }

    static int controllerStatusColor(boolean formed, boolean active) {
        if (!formed) return UNFORMED_STATUS_COLOR;
        return active ? FORMED_STATUS_COLOR : IDLE_STATUS_COLOR;
    }

    private void updateRecipeLockTooltip(MachineControllerMenu controller) {
        if (recipeLockButton == null) return;
        String lockedRecipeId = controller.lockedRecipeId();
        recipeLockButton.setTooltip(Tooltip.create(tooltipComponent(recipeLockTooltip(controller.recipeLocked(), lockedRecipeId == null ? "" : lockedRecipeId))));
    }

    private void renderRecipeLockButtonIcon(GuiGraphicsExtractor graphics) {
        if (recipeLockButton == null || !recipeLockButton.visible) return;
        int x = recipeLockButton.getX();
        int y = recipeLockButton.getY();
        int width = recipeLockButton.getWidth();
        int height = recipeLockButton.getHeight();
        if (menu instanceof MachineControllerMenu controller && controller.recipeLocked()) {
            graphics.fill(x, y, x + width, y + height, RECIPE_LOCK_ENABLED_BG_COLOR);
        }
        graphics.item(recipeLockIcon(), x + (width - 16) / 2,
                y + (height - 16) / 2, 0);
    }

    private static ItemStack recipeLockIcon() {
        return new ItemStack(Items.KNOWLEDGE_BOOK);
    }

    static void clearRecipeLockButtonFocus(Button button) {
        button.setFocused(false);
    }

    private static Component tooltipComponent(List<Component> lines) {
        Component component = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) component = component.copy().append("\n");
            component = component.copy().append(lines.get(i));
        }
        return component;
    }

    public record Rect(int left, int top, int width, int height) {
        int right() { return left + width; }
        int bottom() { return top + height; }
        boolean overlaps(Rect other) {
            return left < other.right() && right() > other.left && top < other.bottom() && bottom() > other.top;
        }
    }

    record ControllerStatusLine(Component text, int color) {
    }

    private record HiddenSlotPosition(int index, Slot slot) {
    }

    /** 把同一个 {@link MachineMenuScreen} 注册到所有 MMCR 菜单类型(根据具体菜单类型分派渲染)。 */
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModUIs.ITEM_BUS.get(),          MachineMenuScreen::new);
        event.register(ModUIs.FLUID_HATCH.get(),       MachineMenuScreen::new);
        event.register(ModUIs.ENERGY_HATCH.get(),      MachineMenuScreen::new);
        event.register(ModUIs.MACHINE_CONTROLLER.get(), MachineMenuScreen::new);
        event.register(ModUIs.FACTORY_SCHEDULER.get(),  MachineMenuScreen::new);
    }

    private static class AutoIOToggleButton extends Button {
        private Component typeLine;
        private Component stateLine;

        AutoIOToggleButton(int x, int y, Component message, OnPress onPress) {
            super(x, y, autoIOToggleButtonWidth(), autoIOToggleButtonHeight(), message, onPress, Button.DEFAULT_NARRATION);
            this.typeLine = message;
            this.stateLine = Component.empty();
        }

        void setLines(Component typeLine, Component stateLine) {
            this.typeLine = typeLine;
            this.stateLine = stateLine;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int baseColor = active ? 0xFF6B6B6B : 0xFF3F3F3F;
            int hoverColor = isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFAAAAAA;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), hoverColor);
            graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, baseColor);

            var font = Minecraft.getInstance().font;
            float scale = autoIOToggleTextScale();
            graphics.pose().pushMatrix();
            graphics.pose().scale(scale, scale);
            renderCenteredLine(graphics, font, typeLine, getY() + 2, scale);
            renderCenteredLine(graphics, font, stateLine, getY() + 11, scale);
            graphics.pose().popMatrix();
        }

        private void renderCenteredLine(GuiGraphicsExtractor graphics, Font font, Component text, int y, float scale) {
            int textWidth = font.width(text);
            int textX = (int) ((getX() + (getWidth() - textWidth * scale) / 2.0F) / scale);
            graphics.text(font, text, textX, (int) (y / scale), 0xFFFFFFFF, false);
        }
    }

    private static class AutoIOSideButton extends Button {
        private static final int SELECTED_COLOR = 0xFF2E7D32;
        private static final int SELECTED_BORDER_COLOR = 0xFF66BB6A;
        private final Direction side;
        private final BooleanSupplier selected;
        private final Supplier<IOPortBlockEntity> portSupplier;

        AutoIOSideButton(int x, int y, Direction side, OnPress onPress, BooleanSupplier selected, Supplier<IOPortBlockEntity> portSupplier) {
            super(x, y, autoIOSideButtonSize(), autoIOSideButtonSize(), Component.empty(), onPress, Button.DEFAULT_NARRATION);
            this.side = side;
            this.selected = selected;
            this.portSupplier = portSupplier;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int baseColor = active ? 0xFF6B6B6B : 0xFF3F3F3F;
            int hoverColor = isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFAAAAAA;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), hoverColor);
            graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, baseColor);
            if (selected.getAsBoolean()) {
                graphics.fill(getX() + 3, getY() + 3, getX() + getWidth() - 3, getY() + getHeight() - 3, SELECTED_COLOR);
                graphics.fill(getX() + 2, getY() + 2, getX() + getWidth() - 2, getY() + 3, SELECTED_BORDER_COLOR);
                graphics.fill(getX() + 2, getY() + getHeight() - 3, getX() + getWidth() - 2, getY() + getHeight() - 2, SELECTED_BORDER_COLOR);
                graphics.fill(getX() + 2, getY() + 2, getX() + 3, getY() + getHeight() - 2, SELECTED_BORDER_COLOR);
                graphics.fill(getX() + getWidth() - 3, getY() + 2, getX() + getWidth() - 2, getY() + getHeight() - 2, SELECTED_BORDER_COLOR);
            }
            ItemStack icon = autoIOSideIcon(portSupplier.get(), side);
            if (!icon.isEmpty()) {
                graphics.item(icon, getX() + 2, getY() + 2, 0);
            }
        }
    }
}
