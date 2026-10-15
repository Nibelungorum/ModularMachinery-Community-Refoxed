package cn.howxu.mmcr.compat.appliedenergistics2.loaded.client;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.client.gui.implementations.UpgradeableScreen;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface controls with MMCR-local stock configuration policy.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class AE2InterfaceScreen extends UpgradeableScreen<AE2InterfaceMenu> {
    private final SettingToggleButton<FuzzyMode> fuzzyMode;
    private final List<InterfaceAmountButton> amountButtons = new ArrayList<>();
    private final InputIsolationButton isolationButton;
    private int isolationGestureButton = -1;

    public AE2InterfaceScreen(AE2InterfaceMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
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
            int configSlot = configSlots.get(i).getContainerSlot();
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
        fuzzyMode.set(menu.getFuzzyMode());
        fuzzyMode.setVisibility(menu.hasUpgrade(AEItems.FUZZY_CARD));
        var configSlots = menu.getSlots(SlotSemantics.CONFIG);
        for (int i = 0; i < amountButtons.size(); i++) {
            Slot slot = configSlots.get(i);
            amountButtons.get(i).visible = InterfaceMenuSlots.canSetAmount(menu.getHost(), slot.getContainerSlot())
                    && slot.isActive() && !slot.getItem().isEmpty();
        }
    }
}
