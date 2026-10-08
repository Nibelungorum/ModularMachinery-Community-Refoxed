package cn.howxu.mmcr.compat.appliedenergistics2.loaded.client;

import appeng.client.gui.widgets.IconButton;
import appeng.core.localization.ButtonToolTips;
import appeng.client.gui.Icon;

/**
 * Native stock-amount control shared by the MMCR interface screens.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class InterfaceAmountButton extends IconButton {
    public InterfaceAmountButton(OnPress onPress) {
        super(onPress);
        setDisableBackground(true);
        setMessage(ButtonToolTips.InterfaceSetStockAmount.text());
    }

    @Override
    protected Icon getIcon() {
        return isHoveredOrFocused() ? Icon.COG : Icon.COG_DISABLED;
    }
}
