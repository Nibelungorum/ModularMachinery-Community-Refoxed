package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.EnergyHatchMenu;
import cn.howxu.mmcr.internal.menu.FluidHatchMenu;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;

import java.text.NumberFormat;
import java.util.Optional;

/** 一个屏幕入口,按具体菜单类型分派纹理 / 尺寸 / 自定义渲染。 */
public class MachineMenuScreen extends AbstractContainerScreen<AbstractContainerMenu> implements MenuAccess<AbstractContainerMenu> {

    private static final Identifier ITEM_BUS_TEXTURE    = MMCR.id("textures/gui/inventory_normal.png");
    private static final Identifier TANK_TEXTURE        = MMCR.id("textures/gui/guitank.png");
    private static final Identifier CONTROLLER_TEXTURE  = MMCR.id("textures/gui/guicontroller_large.png");
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
    public MachineMenuScreen(AbstractContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title,
                menu instanceof MachineControllerMenu ? 176 : 176,
                menu instanceof MachineControllerMenu ? 213 : 166);
        boolean fluidMenu = menu instanceof FluidHatchMenu;
        boolean energyMenu = menu instanceof EnergyHatchMenu;
        boolean itemBusMenu = menu instanceof ItemBusMenu;
        this.titleLabelX = titleX(titleLabelX, fluidMenu, energyMenu, itemBusMenu);
        this.titleLabelY = titleY(titleLabelY, fluidMenu || energyMenu, itemBusMenu);
        this.inventoryLabelY = hiddenInventoryLabelY();
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
        if (fluidMenu) return baseX + FLUID_TITLE_OFFSET_X;
        if (energyMenu) return baseX + ENERGY_TITLE_OFFSET_X;
        if (itemBusMenu) return baseX + ITEM_BUS_TITLE_OFFSET_X;
        return baseX + TITLE_OFFSET_X;
    }

    static int titleY(int baseY) {
        return titleY(baseY, false);
    }

    static int titleY(int baseY, boolean tankMenu) {
        return titleY(baseY, tankMenu, false);
    }

    static int titleY(int baseY, boolean tankMenu, boolean itemBusMenu) {
        if (tankMenu) return baseY + TANK_TITLE_OFFSET_Y;
        if (itemBusMenu) return baseY + ITEM_BUS_TITLE_OFFSET_Y;
        return baseY + TITLE_OFFSET_Y;
    }

    static int hiddenInventoryLabelY() {
        return HIDDEN_INVENTORY_LABEL_Y;
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

    static int storageTextX(int titleX) {
        return titleX;
    }

    static int storageTextY(int titleY) {
        return titleY + STORAGE_TEXT_OFFSET_Y;
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
        extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, textureFor(menu), x, y, 0, 0, imageWidth, imageHeight, 256, 256);

        if (menu instanceof FluidHatchMenu fh)        renderFluidTank(graphics, fh, x, y);
        else if (menu instanceof EnergyHatchMenu eh)  renderEnergyBar(graphics, eh, x, y);
    }

    private static Identifier textureFor(AbstractContainerMenu menu) {
        if (menu instanceof ItemBusMenu)             return ITEM_BUS_TEXTURE;
        if (menu instanceof FluidHatchMenu)         return TANK_TEXTURE;
        if (menu instanceof EnergyHatchMenu)        return TANK_TEXTURE;
        if (menu instanceof MachineControllerMenu)  return CONTROLLER_TEXTURE;
        return ITEM_BUS_TEXTURE;
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
        renderAmountText(g, x, y, amountText(amount, capacity, "mB"));
    }

    private void renderEnergyBar(GuiGraphicsExtractor g, EnergyHatchMenu menu, int x, int y) {
        int stored = menu.storedEnergy();
        int capacity = menu.energyCapacity();
        if (capacity <= 0) return;
        int filled = stored <= 0 ? 0 : Math.max(1, (int) Math.ceil((double) stored * ENERGY_H / capacity));
        int drawY = y + ENERGY_Y + (ENERGY_H - filled);
        if (filled > 0) {
            g.fill(x + ENERGY_X, drawY, x + ENERGY_X + ENERGY_W, y + ENERGY_Y + ENERGY_H, 0xFFE03B27);
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
        MachineControllerBlockEntity owner = menu.resolvedOwner();
        int textY = y + controllerStatusY(titleLabelY);
        int textX = x + controllerStatusX(titleLabelX);
        boolean active = menu.hasActiveRecipe();

        if (owner != null && owner.getMachine() != null) {
            g.text(font, Component.translatable("gui.mmcr.controller.machine", owner.getMachine().localizedName()),
                    textX, textY, PROGRESS_STATUS_COLOR, true);
            textY += 12;
        }
        renderControllerStatusLine(g, textX, textY, Component.translatable(controllerStatusKey(menu.isFormed(), active)),
                controllerStatusColor(menu.isFormed(), active));
        textY += 12;

        if (active) {
            int percent = progressPercent(menu.activeRecipeTick(), menu.activeRecipeTotalTick());
            g.text(font, Component.translatable("gui.mmcr.controller.progress", percent + "%" + progressDots(percent)), textX, textY, PROGRESS_STATUS_COLOR, true);
        }
    }

    private void renderControllerStatusLine(GuiGraphicsExtractor g, int x, int y, Component value, int valueColor) {
        Component label = Component.translatable("gui.mmcr.controller.status_label");
        g.text(font, label, x, y, STATUS_LABEL_COLOR, true);
        g.text(font, value, x + font.width(label) + 4, y, valueColor, true);
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
    }
}
