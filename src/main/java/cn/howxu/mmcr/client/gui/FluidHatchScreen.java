package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.client.render.FluidGuiRenderer;
import cn.howxu.mmcr.internal.menu.FluidHatchMenu;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.util.ReadableNumber;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * Fluid hatch screen.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluidHatchScreen extends AbstractPortScreen<FluidHatchMenu> {
    private static final ResourceLocation TEXTURE = MMCR.id("textures/gui/guitank.png");
    private static final ResourceLocation AUTO_IO_TEXTURE = MMCR.id("textures/gui/guismartinterface.png");
    private static final int GUI_TEXTURE_SIZE = 256;
    private static final int TANK_X = 15;
    private static final int TANK_Y = 10;
    private static final int TANK_W = 20;
    private static final int TANK_H = 61;
    private static final int TITLE_COLOR = ControllerTextLine.DEFAULT_COLOR;

    public FluidHatchScreen(FluidHatchMenu menu, Inventory inventory, Component title) {
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
        if (autoIOPage) return;
        graphics.drawString(font, title, titleLabelX, titleLabelY, TITLE_COLOR, false);
        FluidStack fluid = fluidStack();
        if (!fluid.isEmpty()) {
            Component fluidName = fluid.getHoverName();
            graphics.drawString(font, fluidName, titleLabelX, titleLabelY + 10, TITLE_COLOR, false);
            addTooltip(leftPos + titleLabelX, topPos + titleLabelY + 10, font.width(fluidName), 10,
                    tooltipLines(menu.fluidAmount(), menu.fluidCapacity(), fluidName));
        }
        if (menu.fluidCapacity() > 0) {
            int textY = titleLabelY + (fluid.isEmpty() ? 12 : 19);
            CapabilityDisplay display = menu.displayEntries().stream().findFirst()
                    .orElse(new CapabilityDisplay("fluid", "0", "mB", Optional.empty()));
            Component amount = Component.literal(ReadableNumber.format(menu.fluidAmount()) + " / "
                    + ReadableNumber.format(menu.fluidCapacity()) + " " + display.unit());
            graphics.drawString(font, amount, titleLabelX, textY, TITLE_COLOR, false);
            addTooltip(leftPos + titleLabelX, topPos + textY, font.width(amount), 10,
                    tooltipLines(menu.fluidAmount(), menu.fluidCapacity(), fluid.isEmpty() ? null : fluid.getHoverName(), display.unit()));
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(texture(autoIOPage), leftPos, topPos, 0, 0,
                imageWidth, imageHeight, GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
        if (autoIOPage || menu.fluidCapacity() <= 0) return;
        FluidStack fluid = fluidStack();
        int filled = FluidGuiRenderer.fillHeight(menu.fluidAmount(), menu.fluidCapacity(), TANK_H);
        if (!fluid.isEmpty() && filled > 0) {
            FluidGuiRenderer.drawFluid(graphics, fluid, leftPos + TANK_X, topPos + TANK_Y + TANK_H - filled, TANK_W, filled);
        }
        graphics.blit(TEXTURE, leftPos + TANK_X, topPos + TANK_Y, 176, 0, TANK_W, TANK_H,
                GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
    }

    static List<Component> tooltipLines(long stored, long capacity, Component resourceName) {
        return tooltipLines(stored, capacity, resourceName, "mB");
    }

    static List<Component> tooltipLines(long stored, long capacity, Component resourceName, String unit) {
        return resourceName == null
                ? List.of(Component.literal(ReadableNumber.formatExact(stored) + " / "
                        + ReadableNumber.formatExact(capacity) + " " + unit))
                : List.of(resourceName, Component.literal(ReadableNumber.formatExact(stored) + " / "
                        + ReadableNumber.formatExact(capacity) + " " + unit));
    }

    private FluidStack fluidStack() {
        var storage = menu.storage();
        return storage == null ? FluidStack.EMPTY : storage.getFluidInTank(0).copy();
    }
}
