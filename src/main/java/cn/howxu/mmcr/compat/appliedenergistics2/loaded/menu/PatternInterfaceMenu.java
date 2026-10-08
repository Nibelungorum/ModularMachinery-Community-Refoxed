package cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu;

import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.implementations.PatternProviderMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/**
 * Retains native pattern-provider behavior under an MMCR menu type.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PatternInterfaceMenu extends PatternProviderMenu {
    public PatternInterfaceMenu(MenuType<? extends PatternInterfaceMenu> type, int id,
                                Inventory inventory, PatternProviderLogicHost host) {
        super(type, id, inventory, host);
    }
}
