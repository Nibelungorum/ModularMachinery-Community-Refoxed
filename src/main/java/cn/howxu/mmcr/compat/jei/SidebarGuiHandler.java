package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.client.gui.AbstractScrollableTextScreen;
import cn.howxu.mmcr.client.gui.StyledButton;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import net.minecraft.client.renderer.Rect2i;

import java.util.ArrayList;
import java.util.List;

/** Keeps JEI clear of the visible controller and port sidebar buttons.
 * @author HowXu {@literal <dev@howxu.cn>} */
final class SidebarGuiHandler implements IGuiContainerHandler<AbstractScrollableTextScreen<?>> {
    @Override
    public List<Rect2i> getGuiExtraAreas(AbstractScrollableTextScreen<?> screen) {
        int left = screen.getGuiLeft();
        int right = left + screen.getXSize();
        List<Rect2i> areas = new ArrayList<>();
        for (var child : screen.children()) {
            if (child instanceof StyledButton button && button.visible
                    && (button.getX() < left || button.getRight() > right)) {
                areas.add(new Rect2i(button.getX(), button.getY(), button.getWidth(), button.getHeight()));
            }
        }
        return areas;
    }
}
