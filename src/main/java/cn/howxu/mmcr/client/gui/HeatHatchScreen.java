package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.render.FluidGuiRenderer;
import cn.howxu.mmcr.compat.mekanism.loaded.HeatPortMenu;
import cn.howxu.mmcr.compat.mekanism.loaded.MekanismTemperatureDisplay;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.util.ReadableNumber;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;

/**
 * Heat hatch screen with read-only heat and temperature information.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class HeatHatchScreen extends AbstractPortScreen<HeatPortMenu> {
    private static final ResourceLocation TEXTURE = MMCR.id("textures/gui/mekanism/gui_heatport.png");
    private static final ResourceLocation AUTO_IO_TEXTURE = MMCR.id("textures/gui/guismartinterface.png");
    private static final ResourceLocation BAR_TEXTURE = MMCR.id("textures/gui/mekanism/gui_heatport.png");
    private static final int GUI_TEXTURE_SIZE = 256;
    private static final int HEAT_X = 15;
    private static final int HEAT_Y = 10;
    private static final int HEAT_W = 20;
    private static final int HEAT_H = 61;
    private static final int TITLE_COLOR = ControllerTextLine.DEFAULT_COLOR;

    public HeatHatchScreen(HeatPortMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 166);
        titleLabelX += 32;
        titleLabelY += 3;
    }

    @Override protected BlockPos portPos() { return menu.pos(); }
    @Override protected IOType ownerIOType() { return menu.owner() == null ? null : menu.owner().ioType(); }
    @Override protected int portSlotCount() { return 0; }
    @Override protected ResourceLocation texture(boolean autoIOPage) { return autoIOPage ? AUTO_IO_TEXTURE : TEXTURE; }
    @Override protected boolean supportsAutoIOControlPage() { return false; }

    static List<Component> displayLines(double heat, double capacity) {
        long safeHeat = safeWhole(heat);
        long safeCapacity = safeWhole(capacity);
        TemperatureUnit unit = MekanismTemperatureDisplay.configuredUnit();
        double temperature = safeCapacity <= 0L ? 0D
                : MekanismTemperatureDisplay.fromKelvin(safeHeat / (double) safeCapacity, unit);
        return List.of(
                Component.translatable("gui.mmcr.heat.tooltip.amount", ReadableNumber.formatExact(safeHeat),
                        Component.translatable("mmcr.unit.heat")),
                Component.translatable("gui.mmcr.heat.tooltip.capacity", ReadableNumber.formatExact(safeCapacity),
                        Component.translatable("mmcr.unit.heat_capacity")),
                Component.translatable("gui.mmcr.heat.tooltip.temperature",
                        String.format(Locale.ROOT, "%.1f", temperature),
                        Component.literal(MekanismTemperatureDisplay.symbol(unit))));
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        clearTooltipEntries();
        if (autoIOPage) return;
        graphics.drawString(font, title, titleLabelX, titleLabelY, TITLE_COLOR, false);
        long heat = menu.heatAmount();
        long capacity = menu.heatCapacity();
        graphics.drawString(font, Component.translatable("gui.mmcr.heat.amount", ReadableNumber.format(heat),
                Component.translatable("mmcr.unit.heat")), titleLabelX, titleLabelY + 10, TITLE_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.mmcr.heat.capacity", ReadableNumber.format(capacity),
                Component.translatable("mmcr.unit.heat_capacity")), titleLabelX, titleLabelY + 20, TITLE_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.mmcr.heat.temperature",
                String.format(Locale.ROOT, "%.1f", menu.temperature()),
                Component.literal(MekanismTemperatureDisplay.symbol(MekanismTemperatureDisplay.configuredUnit()))),
                titleLabelX, titleLabelY + 30, TITLE_COLOR, false);
        List<Component> details = displayLines(heat, capacity);
        int tooltipWidth = details.stream().mapToInt(font::width).max().orElse(0);
        addTooltip(leftPos + titleLabelX, topPos + titleLabelY + 10, tooltipWidth, 30, details);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(texture(autoIOPage), leftPos, topPos, 0, 0,
                imageWidth, imageHeight, GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
        if (autoIOPage) return;
        int filled = FluidGuiRenderer.fillHeight(menu.heatAmount(), menu.heatCapacity(), HEAT_H);
        if (filled > 0) {
            graphics.blit(BAR_TEXTURE, leftPos + HEAT_X,
                    topPos + HEAT_Y + HEAT_H - filled, 196, HEAT_H - filled, HEAT_W, filled,
                    GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
        }
    }

    private static long safeWhole(double value) {
        if (!Double.isFinite(value) || value <= 0D) return 0L;
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(value);
    }
}
