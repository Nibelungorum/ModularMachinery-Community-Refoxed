package cn.howxu.mmcr.compat.pneumaticcraft.client;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.gui.ControllerTextLine;
import cn.howxu.mmcr.compat.pneumaticcraft.loaded.AirPortMenu;
import me.desht.pneumaticcraft.client.render.pressure_gauge.PressureGaugeRenderer2D;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

/** Smart-interface background with the native PNC gauge and read-only information.
 * @author howxu <dev@howxu.cn>
 */
public final class AirPortScreen extends AbstractContainerScreen<AirPortMenu> {
    private static final ResourceLocation TEXTURE = MMCR.id("textures/gui/guismartinterface.png");
    private static final int GAUGE_X = 37;
    private static final int GAUGE_Y = 42;
    private static final int TEXT_X = 77;
    private static final int TITLE_Y = 9;
    private static final int INFO_Y = 19;
    private static final int LINE_HEIGHT = 10;
    private static final int TEXT_WIDTH = 89;
    private static final float TEXT_SCALE = 0.8F;
    private static final float TITLE_SCALE = TEXT_SCALE * 1.1F;

    public AirPortScreen(AirPortMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = -1000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Flush the GUI background before PNC draws directly with its tesselator.
        graphics.flush();
        if (menu.criticalPressure() > 0F) {
            PressureGaugeRenderer2D.drawPressureGauge(graphics, font, -1F, menu.criticalPressure(),
                    menu.dangerPressure(), 0F, menu.pressure(), GAUGE_X, GAUGE_Y);
        }
        renderLine(graphics, title, TITLE_Y, TITLE_SCALE);
        renderLine(graphics, Component.translatable("mmcr.pneumaticcraft.gui.pressure", pressure(menu.pressure())), INFO_Y);
        renderLine(graphics, Component.translatable("mmcr.pneumaticcraft.gui.air", menu.air()), INFO_Y + LINE_HEIGHT);
        renderLine(graphics, Component.translatable("mmcr.pneumaticcraft.gui.volume", menu.volume()), INFO_Y + LINE_HEIGHT * 2);
    }

    private void renderLine(GuiGraphics graphics, Component text, int y) {
        renderLine(graphics, text, y, TEXT_SCALE);
    }

    private void renderLine(GuiGraphics graphics, Component text, int y, float textScale) {
        float scale = Math.min(textScale, (float) TEXT_WIDTH / Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(TEXT_X, y, 0);
        graphics.pose().scale(scale, scale, 1F);
        graphics.drawString(font, text, 0, 0, ControllerTextLine.DEFAULT_COLOR, false);
        graphics.pose().popPose();
    }

    private static String pressure(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
