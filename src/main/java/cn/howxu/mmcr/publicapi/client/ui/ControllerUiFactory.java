package cn.howxu.mmcr.publicapi.client.ui;

import net.minecraft.client.gui.screens.Screen;

/** Creates a complete screen retaining the opening menu via MenuAccess.
 * @author howxu <dev@howxu.cn> */
@FunctionalInterface
public interface ControllerUiFactory {
    Screen create(ControllerUiOpenContext context);
}
