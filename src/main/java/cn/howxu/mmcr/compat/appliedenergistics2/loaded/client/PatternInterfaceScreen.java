package cn.howxu.mmcr.compat.appliedenergistics2.loaded.client;

import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import cn.howxu.mmcr.compat.appliedenergistics2.InterfaceScreenTitles;
import cn.howxu.mmcr.internal.menu.InputIsolationMenuState;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.PatternInterfaceMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.Inventory;

/**
 * MMCR title override for the native pattern-provider controls and layouts.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PatternInterfaceScreen extends PatternProviderScreen<PatternInterfaceMenu> {
    private final InputIsolationButton isolationButton;
    private final PatternRefundButton refundButton;
    private int toolbarGestureButton = -1;
    public PatternInterfaceScreen(PatternInterfaceMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        isolationButton = addToLeftToolbar(new InputIsolationButton(enabled -> {
            if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                    enabled ? InputIsolationMenuState.ENABLE : InputIsolationMenuState.DISABLE);
            setFocused(null);
        }));
        refundButton = addToLeftToolbar(new PatternRefundButton(() -> {
            if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PatternInterfaceMenu.REFUND_HOSTED);
            setFocused(null);
        }));
        refundButton.updateState(menu.hasHostedMaterials());
        isolationButton.updateState(menu.inputIsolation());
        setTextContent(TEXT_ID_DIALOG_TITLE, InterfaceScreenTitles.titleFor(menu.getTarget(), title));
    }

    @Override
    protected void init() {
        toolbarGestureButton = -1;
        super.init();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        toolbarGestureButton = -1;
        boolean refund = refundButton.isMouseOver(event.x(), event.y());
        boolean toolbar = refund || (event.button() == 0 || event.button() == 1)
                && isolationButton.isMouseOver(event.x(), event.y());
        // AE normally translates a right click to a left click. Only primary clicks request refunds.
        boolean handled = refund && event.button() != 0 || super.mouseClicked(event, doubleClick);
        if (toolbar && handled) {
            toolbarGestureButton = event.button();
            setFocused(null);
            setDragging(false);
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == toolbarGestureButton) return true;
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == toolbarGestureButton) {
            toolbarGestureButton = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        refundButton.updateState(menu.hasHostedMaterials());
        isolationButton.updateState(menu.inputIsolation());
    }
}
