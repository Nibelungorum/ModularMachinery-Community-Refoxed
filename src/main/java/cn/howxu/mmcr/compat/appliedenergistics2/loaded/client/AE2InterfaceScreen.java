package cn.howxu.mmcr.compat.appliedenergistics2.loaded.client;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantics;
import cn.howxu.mmcr.compat.appliedenergistics2.InterfaceScreenTitles;
import cn.howxu.mmcr.internal.menu.InputIsolationMenuState;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.AE2InterfaceMenu;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.InterfaceMenuSlots;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface controls with MMCR-local stock configuration policy.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class AE2InterfaceScreen extends InterfaceMarkerScreen<AE2InterfaceMenu> {
    private final SettingToggleButton<FuzzyMode> fuzzyMode;
    private final List<InterfaceAmountButton> amountButtons = new ArrayList<>();
    private final InputIsolationButton isolationButton;
    private int isolationGestureButton = -1;

    public AE2InterfaceScreen(AE2InterfaceMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style, menu.getSlots(SlotSemantics.CONFIG));
        fuzzyMode = new ServerSettingToggleButton<>(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        addToLeftToolbar(fuzzyMode);
        isolationButton = addToLeftToolbar(new InputIsolationButton(enabled -> {
            if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                    enabled ? InputIsolationMenuState.ENABLE : InputIsolationMenuState.DISABLE);
            setFocused(null);
        }));
        isolationButton.updateState(menu.inputIsolation());
        widgets.addOpenPriorityButton();
        var configSlots = menu.getSlots(SlotSemantics.CONFIG);
        for (int i = 0; i < configSlots.size(); i++) {
            int configSlot = configSlots.get(i).getSlotIndex();
            var button = new InterfaceAmountButton(ignored -> menu.openSetAmountMenu(configSlot));
            widgets.add("amtButton" + (i + 1), button);
            amountButtons.add(button);
        }
        setTextContent(TEXT_ID_DIALOG_TITLE, InterfaceScreenTitles.titleFor(menu.getTarget(), title));
    }

    @Override
    protected void init() {
        isolationGestureButton = -1;
        super.init();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        isolationGestureButton = -1;
        boolean isolationClick = (event.button() == 0 || event.button() == 1)
                && isolationButton.isMouseOver(event.x(), event.y());
        boolean handled = super.mouseClicked(event, doubleClick);
        if (isolationClick && handled) {
            isolationGestureButton = event.button();
            setFocused(null);
            setDragging(false);
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == isolationGestureButton) return true;
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == isolationGestureButton) {
            isolationGestureButton = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        isolationButton.updateState(menu.inputIsolation());
        fuzzyMode.set(menu.getFuzzyMode());
        fuzzyMode.setVisibility(menu.hasUpgrade(AEItems.FUZZY_CARD));
        var configSlots = menu.getSlots(SlotSemantics.CONFIG);
        for (int i = 0; i < amountButtons.size(); i++) {
            Slot slot = configSlots.get(i);
            amountButtons.get(i).visible = InterfaceMenuSlots.canSetAmount(menu.getHost(), slot.getSlotIndex())
                    && slot.isActive() && !slot.getItem().isEmpty();
        }
    }
}
