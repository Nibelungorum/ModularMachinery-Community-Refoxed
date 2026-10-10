package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.render.ChemicalGuiRenderer;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalPortMenu;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.util.ReadableNumber;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.Optional;

/**
 * Chemical hatch screen. This class is loaded only after the optional menu has been registered.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ChemicalHatchScreen extends AbstractPortScreen<ChemicalPortMenu> {
    private static final ResourceLocation TEXTURE = MMCR.id("textures/gui/guitank.png");
    private static final ResourceLocation AUTO_IO_TEXTURE = MMCR.id("textures/gui/guismartinterface.png");
    private static final int GUI_TEXTURE_SIZE = 256;
    private static final int TANK_X = 15;
    private static final int TANK_Y = 10;
    private static final int TANK_W = 20;
    private static final int TANK_H = 61;
    private static final int TITLE_COLOR = ControllerTextLine.DEFAULT_COLOR;

    public ChemicalHatchScreen(ChemicalPortMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 166);
        titleLabelX += 32;
        titleLabelY += 3;
    }

    @Override protected BlockPos portPos() { return menu.pos(); }
    @Override protected IOType ownerIOType() { return menu.owner() == null ? null : menu.owner().ioType(); }
    @Override protected int portSlotCount() { return 0; }
    @Override protected ResourceLocation texture(boolean autoIOPage) { return autoIOPage ? AUTO_IO_TEXTURE : TEXTURE; }

    @Override
    protected int containerTankIndexAt(double relativeX, double relativeY) {
        return singleTankIndexAt(relativeX, relativeY);
    }

    static int singleTankIndexAt(double x, double y) {
        return x >= TANK_X && x < TANK_X + TANK_W
                && y >= TANK_Y && y < TANK_Y + TANK_H ? 0 : -1;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        clearTooltipEntries();
        if (autoIOPage) return;
        graphics.drawString(font, title, titleLabelX, titleLabelY, TITLE_COLOR, false);
        Component chemicalName = menu.chemicalName();
        boolean hasChemical = menu.chemicalResourceLocation() != null;
        if (hasChemical) {
            graphics.drawString(font, chemicalName, titleLabelX, titleLabelY + 10, TITLE_COLOR, false);
        }
        if (menu.chemicalCapacity() <= 0) return;
        CapabilityDisplay display = menu.displayEntries().stream().findFirst()
                .orElse(new CapabilityDisplay("chemical", "0", "mB", Optional.empty()));
        Component amount = Component.translatable("gui.mmcr.chemical.amount",
                ReadableNumber.format(menu.chemicalAmount()),
                ReadableNumber.format(menu.chemicalCapacity()),
                Component.translatable("mmcr.unit.chemical"));
        int textY = titleLabelY + (hasChemical ? 19 : 12);
        graphics.drawString(font, amount, titleLabelX, textY, TITLE_COLOR, false);
        addTooltip(leftPos + titleLabelX, topPos + textY, font.width(amount), 10,
                FluidHatchScreen.tooltipLines(menu.chemicalAmount(), menu.chemicalCapacity(),
                        hasChemical ? chemicalName : null, display.unit()));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(texture(autoIOPage), leftPos, topPos, 0, 0,
                imageWidth, imageHeight, GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
        if (autoIOPage || menu.chemicalCapacity() <= 0) return;
        ChemicalGuiRenderer.ChemicalRenderState state = renderState(menu, TANK_H);
        ChemicalGuiRenderer.drawChemical(graphics, state, leftPos + TANK_X, topPos + TANK_Y,
                TANK_W, TANK_H);
        graphics.blit(TEXTURE, leftPos + TANK_X, topPos + TANK_Y,
                176, 0, TANK_W, TANK_H, GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
    }

    static ChemicalGuiRenderer.ChemicalRenderState renderState(ChemicalPortMenu menu, int height) {
        return ChemicalGuiRenderer.state(menu.chemicalResourceLocation(), menu.chemicalTint(),
                menu.chemicalAmount(), menu.chemicalCapacity(), height);
    }
}
