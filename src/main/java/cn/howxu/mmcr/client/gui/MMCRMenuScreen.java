package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.internal.menu.EnergyHatchMenu;
import cn.howxu.mmcr.internal.menu.FluidHatchMenu;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/** 一个屏幕入口,按具体菜单类型分派纹理 / 尺寸 / 自定义渲染。 */
public class MMCRMenuScreen extends AbstractContainerScreen<AbstractContainerMenu> implements MenuAccess<AbstractContainerMenu> {

    private static final Identifier ITEM_BUS_TEXTURE    = MMCR.id("textures/gui/inventory_normal.png");
    private static final Identifier TANK_TEXTURE        = MMCR.id("textures/gui/guitank.png");
    private static final Identifier CONTROLLER_TEXTURE  = MMCR.id("textures/gui/guicontroller_large.png");
    static final int TITLE_COLOR = -12566464;
    static final int TITLE_OFFSET_X = 2;
    static final int TITLE_OFFSET_Y = 4;
    static final int FLUID_TITLE_OFFSET_X = 32;
    static final int ENERGY_TITLE_OFFSET_X = 32;
    static final int TANK_TITLE_OFFSET_Y = 3;
    static final int ITEM_BUS_TITLE_OFFSET_X = -4;
    static final int ITEM_BUS_TITLE_OFFSET_Y = -2;
    static final int HIDDEN_INVENTORY_LABEL_Y = -1000;

    private static final int TANK_X = 63;
    static final int TANK_Y = 17;
    private static final int TANK_W = 50;
    private static final int TANK_H = 60;

    private static final int ENERGY_X = 64;
    static final int ENERGY_Y = 18;
    private static final int ENERGY_W = 48;
    private static final int ENERGY_H = 58;

    public MMCRMenuScreen(AbstractContainerMenu menu, Inventory inventory, Component title) {
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

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, TITLE_COLOR, false);
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
        else if (menu instanceof MachineControllerMenu mc) renderControllerStatus(graphics, mc, x, y);
    }

    private static Identifier textureFor(AbstractContainerMenu menu) {
        if (menu instanceof ItemBusMenu)             return ITEM_BUS_TEXTURE;
        if (menu instanceof FluidHatchMenu)         return TANK_TEXTURE;
        if (menu instanceof EnergyHatchMenu)        return TANK_TEXTURE;
        if (menu instanceof MachineControllerMenu)  return CONTROLLER_TEXTURE;
        return ITEM_BUS_TEXTURE;
    }

    private static void renderFluidTank(GuiGraphicsExtractor g, FluidHatchMenu menu, int x, int y) {
        if (menu.tank() == null) return;
        FluidStack fluid = menu.tank().getFluid();
        int capacity = menu.tank().getCapacity();
        if (capacity <= 0) return;

        int filled = fluid.isEmpty() ? 0 : (int) Math.ceil((double) fluid.getAmount() * TANK_H / capacity);
        int drawY = y + TANK_Y + (TANK_H - filled);
        int color = fluid.isEmpty() ? 0xFF606060 : 0xFF3B7BE0;
        g.fill(x + TANK_X, drawY, x + TANK_X + TANK_W, y + TANK_Y + TANK_H, color);
    }

    private static void renderEnergyBar(GuiGraphicsExtractor g, EnergyHatchMenu menu, int x, int y) {
        if (menu.storage() == null) return;
        int stored = menu.storage().getEnergyStored();
        int capacity = menu.storage().getMaxEnergyStored();
        if (capacity <= 0) return;
        int filled = (int) Math.ceil((double) stored * ENERGY_H / capacity);
        int drawY = y + ENERGY_Y + (ENERGY_H - filled);
        g.fill(x + ENERGY_X, drawY, x + ENERGY_X + ENERGY_W, y + ENERGY_Y + ENERGY_H, 0xFFE03B27);
    }

    private void renderControllerStatus(GuiGraphicsExtractor g, MachineControllerMenu menu, int x, int y) {
        MachineControllerBlockEntity owner = menu.owner();
        if (owner == null) return;
        int textY = y + 12;
        int textX = x + 12;
        int color = 0xFFFFFF;

        if (owner.getMachine() != null) {
            g.text(font, Component.translatable("gui.mmcr.controller.machine", owner.getMachine().localizedName()),
                    textX, textY, color, true);
            textY += 12;
        }
        g.text(font, Component.translatable(owner.isFormed() ? "gui.mmcr.controller.formed" : "gui.mmcr.controller.unformed"),
                textX, textY, color, true);
        textY += 12;

        MachineRecipe active = owner.getActiveRecipe();
        if (active != null) {
            int total = active.tickTime();
            int current = Math.min(total, owner.getTickCounter());
            int percent = total > 0 ? (current * 100 / total) : 0;
            g.text(font, Component.translatable("gui.mmcr.controller.progress", percent),
                    textX, textY, color, true);
        } else {
            g.text(font, Component.translatable("gui.mmcr.controller.idle"),
                    textX, textY, color, true);
        }
    }

    /** 把同一个 {@link MMCRMenuScreen} 注册到所有 MMCR 菜单类型(根据具体菜单类型分派渲染)。 */
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModUIs.ITEM_BUS.get(),          MMCRMenuScreen::new);
        event.register(ModUIs.FLUID_HATCH.get(),       MMCRMenuScreen::new);
        event.register(ModUIs.ENERGY_HATCH.get(),      MMCRMenuScreen::new);
        event.register(ModUIs.MACHINE_CONTROLLER.get(), MMCRMenuScreen::new);
    }
}
