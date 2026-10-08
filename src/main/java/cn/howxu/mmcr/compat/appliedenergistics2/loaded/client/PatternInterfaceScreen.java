package cn.howxu.mmcr.compat.appliedenergistics2.loaded.client;

import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import cn.howxu.mmcr.compat.appliedenergistics2.InterfaceScreenTitles;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.PatternInterfaceMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * MMCR title override for the native pattern-provider controls and layouts.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PatternInterfaceScreen extends PatternProviderScreen<PatternInterfaceMenu> {
    public PatternInterfaceScreen(PatternInterfaceMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        setTextContent(TEXT_ID_DIALOG_TITLE, InterfaceScreenTitles.titleFor(menu.getTarget(), title));
    }
}
