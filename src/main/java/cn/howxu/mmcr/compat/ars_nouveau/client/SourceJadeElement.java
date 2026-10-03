package cn.howxu.mmcr.compat.ars_nouveau.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.ui.Element;

/**
 * Renders the native source sprite in a Jade recipe output line.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SourceJadeElement extends Element {
    @Override
    public Vec2 getSize() {
        return new Vec2(10, 8);
    }

    @Override
    public void render(GuiGraphics graphics, float x, float y, float maxX, float maxY) {
        SourceGuiRenderer.drawSource(graphics, (int) x, (int) y, 10, 8);
    }
}
