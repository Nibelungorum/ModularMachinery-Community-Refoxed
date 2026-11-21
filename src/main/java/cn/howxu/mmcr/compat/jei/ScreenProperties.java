package cn.howxu.mmcr.compat.jei;

import mezz.jei.api.gui.handlers.IGuiProperties;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

/** Snapshot of a standalone panel and its scaled screen for JEI layout.
 * @author HowXu {@literal <dev@howxu.cn>} */
record ScreenProperties(Class<? extends Screen> screenClass, int guiLeft, int guiTop,
                        int guiXSize, int guiYSize, int screenWidth, int screenHeight) implements IGuiProperties {
    ScreenProperties(Screen screen, Rect2i bounds) {
        this(screen.getClass(), bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(),
                screen.width, screen.height);
    }
}
