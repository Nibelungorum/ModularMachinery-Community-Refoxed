package cn.howxu.mmcr.compat.jei;

import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/**
 * Geometry in the native pixel coordinates of the JEI structure preview artwork.
 *
 * @author HowXu {@literal <dev@howxu.cn>}
 */
final class StructurePreviewLayout {
    static final int WIDTH = 168;
    static final ScreenRectangle TITLE = new ScreenRectangle(4, 4, 160, 16);
    static final int CONTROL_INSET = 2;
    private static final int JEI_BUTTON_SIZE = 13;

    private StructurePreviewLayout() {}

    static int heightForScale(int scale) {
        return switch (scale) {
            case 1 -> 300;
            case 2 -> 280;
            case 3 -> 220;
            default -> 150;
        };
    }

    static ScreenRectangle viewport(int height) {
        return new ScreenRectangle(3, 22, 161, height - 47);
    }

    static ScreenPosition materialOrigin(ScreenRectangle viewport) {
        return new ScreenPosition(viewport.left(), viewport.bottom() + 2);
    }

    static ScreenPosition sideButtonOrigin(ScreenRectangle viewport) {
        // JEI stacks the bookmark above the transfer button when both are visible.
        return new ScreenPosition(viewport.right() - CONTROL_INSET - JEI_BUTTON_SIZE,
                viewport.bottom() - CONTROL_INSET - JEI_BUTTON_SIZE);
    }
}
