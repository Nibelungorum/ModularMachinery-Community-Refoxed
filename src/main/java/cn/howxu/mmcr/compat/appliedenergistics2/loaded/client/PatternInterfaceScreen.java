package cn.howxu.mmcr.compat.appliedenergistics2.loaded.client;

import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import cn.howxu.mmcr.compat.appliedenergistics2.InterfaceScreenTitles;
import cn.howxu.mmcr.internal.menu.InputIsolationMenuState;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.PatternInterfaceMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * MMCR title override for the native pattern-provider controls and layouts.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PatternInterfaceScreen extends PatternProviderScreen<PatternInterfaceMenu> {
    private final InputIsolationButton isolationButton;
    private int isolationGestureButton = -1;
    public PatternInterfaceScreen(PatternInterfaceMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        isolationButton = addToLeftToolbar(new InputIsolationButton(enabled -> {
            if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                    enabled ? InputIsolationMenuState.ENABLE : InputIsolationMenuState.DISABLE);
            setFocused(null);
        }));
        isolationButton.updateState(menu.inputIsolation());
        setTextContent(TEXT_ID_DIALOG_TITLE, InterfaceScreenTitles.titleFor(menu.getTarget(), title));
    }

    @Override
    protected void init() {
        isolationGestureButton = -1;
        super.init();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        isolationGestureButton = -1;
        boolean isolationClick = (button == 0 || button == 1)
                && isolationButton.isMouseOver(mouseX, mouseY);
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (isolationClick && handled) {
            isolationGestureButton = button;
            setFocused(null);
            setDragging(false);
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == isolationGestureButton) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == isolationGestureButton) {
            isolationGestureButton = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        isolationButton.updateState(menu.inputIsolation());
    }
}
