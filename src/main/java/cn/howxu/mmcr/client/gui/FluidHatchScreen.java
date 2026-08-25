package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.render.FluidGuiRenderer;
import cn.howxu.mmcr.internal.menu.FluidHatchMenu;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.util.ReadableNumber;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * Fluid hatch screen.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluidHatchScreen extends AbstractPortScreen<FluidHatchMenu> {
    private static final Identifier TEXTURE = MMCR.id("textures/gui/guitank.png");
    private static final Identifier AUTO_IO_TEXTURE = MMCR.id("textures/gui/guismartinterface.png");
    private static final int GUI_TEXTURE_SIZE = 256;
    private static final int TANK_X = 15;
    private static final int TANK_Y = 10;
    private static final int TANK_W = 20;
    private static final int TANK_H = 61;
    private static final int TITLE_COLOR = -12566464;

    public FluidHatchScreen(FluidHatchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 166);
        titleLabelX += 32;
        titleLabelY += 3;
    }

    @Override protected BlockPos portPos() { return menu.pos(); }
    @Override protected IOType ownerIOType() { return menu.owner() == null ? null : menu.owner().ioType(); }
    @Override protected int portSlotCount() { return 0; }
    @Override protected Identifier texture(boolean autoIOPage) { return autoIOPage ? AUTO_IO_TEXTURE : TEXTURE; }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        clearTooltipEntries();
        if (autoIOPage) return;
        graphics.text(font, title, titleLabelX, titleLabelY, TITLE_COLOR, false);
        FluidStack fluid = fluidStack();
        if (!fluid.isEmpty()) {
            Component fluidName = fluid.getHoverName();
            graphics.text(font, fluidName, titleLabelX, titleLabelY + 10, TITLE_COLOR, false);
            addTooltip(leftPos + titleLabelX, topPos + titleLabelY + 10, font.width(fluidName), 10,
                    tooltipLines(menu.fluidAmount(), menu.fluidCapacity(), fluidName));
        }
        if (menu.fluidCapacity() > 0) {
            int textY = titleLabelY + (fluid.isEmpty() ? 12 : 19);
            Component amount = Component.literal(ReadableNumber.format(menu.fluidAmount()) + " / "
                    + ReadableNumber.format(menu.fluidCapacity()) + " mB");
            graphics.text(font, amount, titleLabelX, textY, TITLE_COLOR, false);
            addTooltip(leftPos + titleLabelX, topPos + textY, font.width(amount), 10,
                    tooltipLines(menu.fluidAmount(), menu.fluidCapacity(), fluid.isEmpty() ? null : fluid.getHoverName()));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture(autoIOPage), leftPos, topPos, 0, 0,
                imageWidth, imageHeight, GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
        if (autoIOPage || menu.fluidCapacity() <= 0) return;
        FluidStack fluid = fluidStack();
        int filled = FluidGuiRenderer.fillHeight(menu.fluidAmount(), menu.fluidCapacity(), TANK_H);
        if (!fluid.isEmpty() && filled > 0) {
            FluidGuiRenderer.drawFluid(graphics, fluid, leftPos + TANK_X, topPos + TANK_Y + TANK_H - filled, TANK_W, filled);
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos + TANK_X, topPos + TANK_Y, 176, 0, TANK_W, TANK_H,
                GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
    }

    static List<Component> tooltipLines(long stored, long capacity, Component resourceName) {
        return resourceName == null
                ? List.of(Component.literal(ReadableNumber.formatExact(stored) + " / "
                        + ReadableNumber.formatExact(capacity) + " mB"))
                : List.of(resourceName, Component.literal(ReadableNumber.formatExact(stored) + " / "
                        + ReadableNumber.formatExact(capacity) + " mB"));
    }

    private FluidStack fluidStack() {
        var storage = menu.storage();
        if (storage == null || storage.getResource(0).isEmpty()) return FluidStack.EMPTY;
        return storage.getResource(0).toStack(Math.min(storage.getAmountAsInt(0), Integer.MAX_VALUE));
    }
}
