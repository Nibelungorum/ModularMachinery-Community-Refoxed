package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.runtime.ui.ControllerUiServerSession;
import org.jetbrains.annotations.Nullable;

/** Common menu metadata; client session ownership belongs to the client event bridge.
 * @author howxu <dev@howxu.cn>
 */
public interface ControllerUiMenu {
    ControllerMenuOpenData uiOpenData();
    @Nullable ControllerUiServerSession uiServerSession();
    boolean playerInventoryVisible();
    void setPlayerInventoryVisible(boolean visible);
}
