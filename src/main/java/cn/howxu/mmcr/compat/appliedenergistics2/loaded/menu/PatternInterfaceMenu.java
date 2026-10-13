package cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu;

import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.SlotSemantic;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.util.ConfigMenuInventory;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternReturnInventory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

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

    @Override
    protected Slot addSlot(Slot slot, SlotSemantic semantic) {
        if (slot instanceof AppEngSlot aeSlot && aeSlot.getInventory() instanceof ConfigMenuInventory wrapper
                && wrapper.getDelegate() instanceof PatternReturnInventory) {
            slot = new PatternReturnSlot(wrapper, aeSlot.getSlotIndex());
        }
        return super.addSlot(slot, semantic);
    }
}
