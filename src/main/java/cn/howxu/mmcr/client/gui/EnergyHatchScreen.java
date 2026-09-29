package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.internal.menu.EnergyHatchMenu;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.util.ReadableNumber;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.math.BigInteger;
import java.util.List;

/**
 * Energy hatch screen.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class EnergyHatchScreen extends AbstractPortScreen<EnergyHatchMenu> {
    private static final ResourceLocation TEXTURE = MMCR.id("textures/gui/guibar.png");
    private static final ResourceLocation AUTO_IO_TEXTURE = MMCR.id("textures/gui/guismartinterface.png");
    private static final ResourceLocation BAR_TEXTURE = MMCR.id("textures/gui/guibar.png");
    private static final int GUI_TEXTURE_SIZE = 256;
    private static final int ENERGY_X = 15;
    private static final int ENERGY_Y = 10;
    private static final int ENERGY_W = 20;
    private static final int ENERGY_H = 61;
    private static final int TITLE_COLOR = ControllerTextLine.DEFAULT_COLOR;

    public EnergyHatchScreen(EnergyHatchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 166);
        titleLabelX += 32;
        titleLabelY += 3;
    }

    @Override protected BlockPos portPos() { return menu.pos(); }
    @Override protected IOType ownerIOType() { return menu.owner() == null ? null : menu.owner().ioType(); }
    @Override protected int portSlotCount() { return 0; }
    @Override protected ResourceLocation texture(boolean autoIOPage) { return autoIOPage ? AUTO_IO_TEXTURE : TEXTURE; }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        clearTooltipEntries();
        if (!autoIOPage) graphics.drawString(font, title, titleLabelX, titleLabelY, TITLE_COLOR, false);
        if (!autoIOPage && menu.energyCapacity() > 0) {
            CapabilityDisplay display = menu.displayEntries().stream().findFirst()
                    .orElse(new CapabilityDisplay("energy", "0", "FE", Optional.empty()));
            Component amount = Component.literal(ReadableNumber.format(menu.storedEnergy()) + " / "
                    + ReadableNumber.format(menu.energyCapacity()) + " " + display.unit());
            int x = leftPos + titleLabelX;
            int y = topPos + titleLabelY + 12;
            graphics.drawString(font, amount, titleLabelX, titleLabelY + 12, TITLE_COLOR, false);
            addTooltip(x, y, font.width(amount), 10, tooltipLines(menu.storedEnergy(), menu.energyCapacity(), display.unit()));
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(texture(autoIOPage), leftPos, topPos, 0, 0,
                imageWidth, imageHeight, GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
        long capacity = menu.energyCapacity();
        if (autoIOPage || capacity <= 0) return;
        long stored = menu.storedEnergy();
        int filled = filledHeight(stored, capacity);
        if (filled > 0) graphics.blit(BAR_TEXTURE, leftPos + ENERGY_X,
                topPos + ENERGY_Y + ENERGY_H - filled, 196, ENERGY_H - filled, ENERGY_W, filled,
                GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
    }

    static List<Component> tooltipLines(long stored, long capacity) {
        return tooltipLines(stored, capacity, "FE");
    }

    static List<Component> tooltipLines(long stored, long capacity, String unit) {
        return List.of(Component.literal(ReadableNumber.formatExact(stored) + " / "
                + ReadableNumber.formatExact(capacity) + " " + unit));
    }

    static int filledHeight(long stored, long capacity) {
        if (stored <= 0L || capacity <= 0L) return 0;
        if (stored >= capacity) return ENERGY_H;
        BigInteger height = BigInteger.valueOf(stored)
                .multiply(BigInteger.valueOf(ENERGY_H))
                .add(BigInteger.valueOf(capacity - 1L))
                .divide(BigInteger.valueOf(capacity));
        return Math.max(1, height.intValue());
    }
}
