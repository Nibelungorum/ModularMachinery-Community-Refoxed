package cn.howxu.mmcr.compat.appliedenergistics2.loaded.client;

import appeng.client.gui.widgets.ToggleButton;
import appeng.client.gui.Icon;
import cn.howxu.mmcr.client.gui.IsolationToggleButton;
import cn.howxu.mmcr.internal.menu.InputIsolationMenuState;

import java.util.List;

/**
 * Native AE toolbar toggle for the server-confirmed interface isolation state.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class InputIsolationButton extends ToggleButton {
    public InputIsolationButton(Listener listener) {
        super(Icon.LOCKED, Icon.UNLOCKED, listener);
    }

    public void updateState(InputIsolationMenuState state) {
        setVisibility(state.supported());
        setState(state.enabled());
        var tooltip = List.of(IsolationToggleButton.tooltip(state, false));
        setTooltipOn(tooltip);
        setTooltipOff(tooltip);
    }
}
