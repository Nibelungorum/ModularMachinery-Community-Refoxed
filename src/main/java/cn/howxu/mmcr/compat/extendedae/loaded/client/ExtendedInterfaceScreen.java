package cn.howxu.mmcr.compat.extendedae.loaded.client;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.core.definitions.AEItems;
import appeng.client.gui.Icon;
import cn.howxu.mmcr.compat.appliedenergistics2.InterfaceScreenTitles;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.client.InterfaceAmountButton;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.InterfaceMenuSlots;
import cn.howxu.mmcr.compat.extendedae.loaded.menu.ExtendedInterfaceMenu;
import com.glodblock.github.extendedae.client.button.ActionEPPButton;
import com.glodblock.github.extendedae.network.EAENetworkHandler;
import com.glodblock.github.extendedae.network.packet.CUpdatePage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * Extended and oversize interface controls with native paging and upgrades.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ExtendedInterfaceScreen extends UpgradeableScreen<ExtendedInterfaceMenu> {
    private final SettingToggleButton<FuzzyMode> fuzzyMode;
    private final List<InterfaceAmountButton> amountButtons = new ArrayList<>();
    private final ActionEPPButton nextPage;
    private final ActionEPPButton previousPage;

    public ExtendedInterfaceScreen(ExtendedInterfaceMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        fuzzyMode = new ServerSettingToggleButton<>(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        nextPage = new ActionEPPButton(ignored ->
                EAENetworkHandler.INSTANCE.sendToServer(new CUpdatePage(1)), Icon.ARROW_RIGHT);
        previousPage = new ActionEPPButton(ignored ->
                EAENetworkHandler.INSTANCE.sendToServer(new CUpdatePage(0)), Icon.ARROW_LEFT);
        nextPage.setMessage(Component.translatable("gui.extendedae.ex_interface.next"));
        previousPage.setMessage(Component.translatable("gui.extendedae.ex_interface.pre"));
        addToLeftToolbar(fuzzyMode);
        addToLeftToolbar(nextPage);
        addToLeftToolbar(previousPage);
        widgets.addOpenPriorityButton();
        var configSlots = menu.getConfigSlots();
        for (int i = 0; i < configSlots.size(); i++) {
            int configSlot = configSlots.get(i).getContainerSlot();
            var button = new InterfaceAmountButton(ignored -> menu.openSetAmountMenu(configSlot));
            widgets.add("amtButton" + (i + 1), button);
            amountButtons.add(button);
        }
        setTextContent(TEXT_ID_DIALOG_TITLE, InterfaceScreenTitles.titleFor(menu.getTarget(), title));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        menu.showPage(menu.getPage());
        fuzzyMode.set(menu.getFuzzyMode());
        fuzzyMode.setVisibility(menu.hasUpgrade(AEItems.FUZZY_CARD));
        nextPage.setVisibility(menu.getPage() == 0);
        previousPage.setVisibility(menu.getPage() == 1);
        var configSlots = menu.getConfigSlots();
        for (int i = 0; i < amountButtons.size(); i++) {
            Slot slot = configSlots.get(i);
            amountButtons.get(i).visible = InterfaceMenuSlots.canSetAmount(menu.getHost(), slot.getContainerSlot())
                    && slot.isActive() && !slot.getItem().isEmpty();
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        graphics.drawString(font, Component.translatable("gui.extendedae.ex_interface.config", menu.getPage() + 1),
                8, 24, style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB(), false);
    }
}
