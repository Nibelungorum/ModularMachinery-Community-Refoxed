package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.internal.autoio.AutoIOAction;
import cn.howxu.mmcr.internal.menu.EnergyHatchMenu;
import cn.howxu.mmcr.internal.menu.FactorySchedulerMenu;
import cn.howxu.mmcr.internal.menu.FluidHatchMenu;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.network.PktAutoIOConfigPayload;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.fluids.FluidStack;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;

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
    static final int CONTROLLER_STATUS_OFFSET_Y = 12;
    private static final float CONTROLLER_DETAIL_SCALE = 1.0F;
    private static final int CONTROLLER_DETAIL_LINE_SPACING = 14;
    static final int STORAGE_TEXT_OFFSET_Y = 12;
    static final int HIDDEN_INVENTORY_LABEL_Y = -1000;

    private static final int TANK_X = 15;
    static final int TANK_Y = 10;
    private static final int TANK_W = 20;
    private static final int TANK_H = 61;

    private static final int ENERGY_X = 15;
    static final int ENERGY_Y = 10;
    private static final int ENERGY_W = 20;
    private static final int ENERGY_H = 61;
    private static final int AUTO_IO_SIDE_ROW_Y = 28;
    private static final int AUTO_IO_SIDE_ROW_H = 18;
    private static final int AUTO_IO_SIDE_TEXT_X = 54;
    private boolean autoIOPage;
    private Button autoIOPageButton;
    private Button autoIOToggleButton;
    private final EnumMap<Direction, Button> autoIOSideButtons = new EnumMap<>(Direction.class);

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
        return !(fluidMenu || energyMenu || itemBusMenu);
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

    static boolean isPortSlotIndex(int slotIndex, int portSlotCount) {
        return slotIndex >= 0 && slotIndex < portSlotCount;
    }

    static boolean hidesSlotOnAutoIOPage(AbstractContainerMenu menu, boolean autoIOPage, Slot slot, int slotIdx, int portSlotCount) {
        return autoIOPage && menu instanceof ItemBusMenu && slot != null && isPortSlotIndex(slotIdx, portSlotCount);
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

    @Override
    protected void init() {
        super.init();
        if (isPortMenu()) initAutoIOButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateAutoIOWidgets();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, titleColor(menu instanceof MachineControllerMenu), false);
        if (menu instanceof MachineControllerMenu mc) renderControllerStatus(graphics, mc, 0, 0);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        extractBackground(graphics, mouseX, mouseY, partialTicks);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        if (autoIOPage && isPortMenu()) renderAutoIOSideRows(graphics, (width - imageWidth) / 2, (height - imageHeight) / 2);
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
        if (autoIOPage && menu instanceof ItemBusMenu) {
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
        }).bounds(leftPos + 4, topPos + 4, 20, 20).tooltip(Tooltip.create(Component.translatable("mmcr.auto_io.control"))).build());

        autoIOToggleButton = addRenderableWidget(Button.builder(autoIOToggleLabel(), button -> {
            IOPortBlockEntity port = portEntity();
            boolean enabled = port == null || !port.autoIOConfig().enabled();
            ClientPacketDistributor.sendToServer(new PktAutoIOConfigPayload(portPos(), AutoIOAction.SET_ENABLED, null, enabled));
        })
                .bounds(leftPos + 53, topPos + imageHeight - 72, 70, 20).build());

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                Direction side = autoIODirectionAt(x, y);
                if (side == null) continue;
                Button button = addRenderableWidget(new AutoIOSideButton(leftPos + 8, topPos + AUTO_IO_SIDE_ROW_Y + side.ordinal() * AUTO_IO_SIDE_ROW_H,
                        clicked -> {
                            IOPortBlockEntity port = portEntity();
                            boolean enabled = port == null || !port.autoIOConfig().isSideEnabled(side);
                            ClientPacketDistributor.sendToServer(new PktAutoIOConfigPayload(portPos(), AutoIOAction.SET_SIDE, side, enabled));
                        },
                        () -> portEntity() != null && portEntity().autoIOConfig().isSideEnabled(side)));
                autoIOSideButtons.put(side, button);
            }
        }
        updateAutoIOWidgets();
    }

    private void updateAutoIOWidgets() {
        if (autoIOPageButton != null) autoIOPageButton.setMessage(Component.literal(autoIOPage ? "←" : "⇄"));
        if (autoIOToggleButton != null) {
            autoIOToggleButton.visible = autoIOPage;
            autoIOToggleButton.active = autoIOPage;
            autoIOToggleButton.setMessage(autoIOToggleLabel());
            autoIOToggleButton.setTooltip(Tooltip.create(autoIOToggleTooltip()));
        }
        for (var entry : autoIOSideButtons.entrySet()) {
            Button button = entry.getValue();
            button.visible = autoIOPage;
            button.active = autoIOPage;
            button.setTooltip(Tooltip.create(autoIOSideTooltip(entry.getKey())));
        }
    }

    private Component autoIOToggleLabel() {
        return Component.translatable(isOutputPort() ? "mmcr.auto_io.auto_output" : "mmcr.auto_io.auto_input");
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

    private Component autoIOToggleTooltip() {
        IOPortBlockEntity port = portEntity();
        boolean enabled = port != null && port.autoIOConfig().enabled();
        return Component.translatable(enabled ? "mmcr.auto_io.enabled" : "mmcr.auto_io.disabled");
    }

    private Component autoIOSideTooltip(Direction side) {
        IOPortBlockEntity port = portEntity();
        boolean enabled = port != null && port.autoIOConfig().isSideEnabled(side);
        return Component.translatable("mmcr.auto_io.side", Component.translatable("mmcr.direction." + side.name().toLowerCase(Locale.ROOT)),
                Component.translatable(enabled ? "mmcr.auto_io.enabled" : "mmcr.auto_io.disabled"));
    }

    static Component autoIOSideLine(Direction side, Component blockName) {
        return Component.translatable("mmcr.auto_io.side_block",
                Component.translatable("mmcr.direction." + side.name().toLowerCase(Locale.ROOT)), blockName);
    }

    private void renderAutoIOSideRows(GuiGraphicsExtractor g, int x, int y) {
        IOPortBlockEntity port = portEntity();
        int row = 0;
        for (Direction side : Direction.values()) {
            IOPortBlockEntity.AdjacentSide adjacent = port == null ? null : port.adjacentSide(side);
            Component blockName = adjacent == null ? Component.translatable("block.minecraft.air") : adjacent.name();
            int rowY = y + AUTO_IO_SIDE_ROW_Y + row * AUTO_IO_SIDE_ROW_H;
            g.text(font, autoIOSideLine(side, blockName), x + AUTO_IO_SIDE_TEXT_X, rowY, TITLE_COLOR, false);
            row++;
        }
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
        if (autoIOPage && menu instanceof ItemBusMenu) return SMART_INTERFACE_TEXTURE;
        if (menu instanceof ItemBusMenu itemBus)     return MMCR.id(itemBus.texturePath());
        if (menu instanceof FluidHatchMenu)         return TANK_TEXTURE;
        if (menu instanceof EnergyHatchMenu)        return TANK_TEXTURE;
        if (menu instanceof MachineControllerMenu)  return CONTROLLER_TEXTURE;
        if (menu instanceof FactorySchedulerMenu factory) return MMCR.id(factory.texturePath());
        return MMCR.id(ItemBusMenu.texturePathForSize(cn.howxu.mmcr.internal.port.ItemBusSize.NORMAL));
    }

    private void renderFluidTank(GuiGraphicsExtractor g, FluidHatchMenu menu, int x, int y) {
        int amount = menu.fluidAmount();
        int capacity = menu.fluidCapacity();
        if (capacity <= 0) return;

        FluidStack fluid = menu.tank() == null ? FluidStack.EMPTY : menu.tank().getFluid();
        int filled = amount <= 0 ? 0 : Math.max(1, (int) Math.ceil((double) amount * TANK_H / capacity));
        if (!fluid.isEmpty() && filled > 0) {
            drawFluid(g, fluid, x + TANK_X, y + TANK_Y, TANK_W, TANK_H, Math.min(filled, TANK_H));
        }
        g.blit(RenderPipelines.GUI_TEXTURED, fluidBarOverlayTexture(),
                x + TANK_X, y + TANK_Y,
                fluidBarOverlaySourceX(), 0, TANK_W, TANK_H,
                GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
        renderAmountText(g, x, y, amountText(amount, capacity, "mB"));
    }

    private void renderEnergyBar(GuiGraphicsExtractor g, EnergyHatchMenu menu, int x, int y) {
        int stored = menu.storedEnergy();
        int capacity = menu.energyCapacity();
        if (capacity <= 0) return;
        int filled = stored <= 0 ? 0 : Math.min(ENERGY_H, Math.max(1, (int) Math.ceil((double) stored * ENERGY_H / capacity)));
        int drawY = y + ENERGY_Y + (ENERGY_H - filled);
        if (filled > 0) {
            g.blit(RenderPipelines.GUI_TEXTURED, GUI_BAR_TEXTURE,
                    x + ENERGY_X, drawY,
                    energyBarSourceX(), energyBarSourceY(filled), ENERGY_W, filled,
                    GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
        }
        renderAmountText(g, x, y, amountText(stored, capacity, "FE"));
    }

    private void renderAmountText(GuiGraphicsExtractor g, int x, int y, String text) {
        g.text(font, Component.literal(text), x + storageTextX(titleLabelX), y + storageTextY(titleLabelY), TITLE_COLOR, false);
    }

    private static String amountText(int amount, int capacity, String unit) {
        return NUMBER_FORMAT.format(amount) + " / " + NUMBER_FORMAT.format(capacity) + " " + unit;
    }

    private static void drawFluid(GuiGraphicsExtractor g, FluidStack fluid, int x, int y, int width, int height, int filled) {
        Optional<TextureAtlasSprite> sprite = stillFluidSprite(fluid);
        if (sprite.isEmpty()) return;

        int color = fluidColor(fluid);
        int drawY = y + height - filled;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite.get(), x, drawY, width, filled, color);
    }

    private static Optional<TextureAtlasSprite> stillFluidSprite(FluidStack stack) {
        Fluid fluid = stack.getFluid();
        ModelManager modelManager = Minecraft.getInstance().getModelManager();
        FluidStateModelSet modelSet = modelManager.getFluidStateModelSet();
        FluidModel model = modelSet.get(fluid.defaultFluidState());
        TextureAtlasSprite sprite = model.stillMaterial().sprite();
        return Optional.ofNullable(sprite).filter(s -> s.atlasLocation() != MissingTextureAtlasSprite.getLocation());
    }

    private static int fluidColor(FluidStack stack) {
        Fluid fluid = stack.getFluid();
        ModelManager modelManager = Minecraft.getInstance().getModelManager();
        FluidStateModelSet modelSet = modelManager.getFluidStateModelSet();
        FluidModel model = modelSet.get(fluid.defaultFluidState());
        FluidTintSource tintSource = model.fluidTintSource();
        return tintSource == null ? 0xFFFFFFFF : tintSource.colorAsStack(stack);
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

    /** 把同一个 {@link MachineMenuScreen} 注册到所有 MMCR 菜单类型(根据具体菜单类型分派渲染)。 */
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModUIs.ITEM_BUS.get(),          MachineMenuScreen::new);
        event.register(ModUIs.FLUID_HATCH.get(),       MachineMenuScreen::new);
        event.register(ModUIs.ENERGY_HATCH.get(),      MachineMenuScreen::new);
        event.register(ModUIs.MACHINE_CONTROLLER.get(), MachineMenuScreen::new);
        event.register(ModUIs.FACTORY_SCHEDULER.get(),  MachineMenuScreen::new);
    }

    private static class AutoIOSideButton extends Button {
        private static final int SELECTED_COLOR = 0xFF2E7D32;
        private static final int SELECTED_BORDER_COLOR = 0xFF66BB6A;
        private final BooleanSupplier selected;

        AutoIOSideButton(int x, int y, OnPress onPress, BooleanSupplier selected) {
            super(x, y, 20, 20, Component.empty(), onPress, Button.DEFAULT_NARRATION);
            this.selected = selected;
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
        }
    }
}
