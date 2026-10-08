package cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu;

import appeng.helpers.InterfaceLogicHost;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.AppEngSlot;
import appeng.util.ConfigMenuInventory;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBaseBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.StockingInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.util.InterfaceMenuPolicy;
import net.minecraft.world.inventory.Slot;

/**
 * Applies MMCR interface policies only to their actual configuration and storage inventories.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class InterfaceMenuSlots {
    private InterfaceMenuSlots() {
    }

    public static Slot adapt(Object target, Slot slot) {
        if (!(target instanceof InterfaceLogicHost host)
                || !(slot instanceof AppEngSlot aeSlot)
                || !(aeSlot.getInventory() instanceof ConfigMenuInventory wrapper)) return slot;
        var logic = host.getInterfaceLogic();
        if (target instanceof OutputInterfaceBaseBlockEntity
                && wrapper.getDelegate() == logic.getConfig()) {
            return new LockedConfigSlot(wrapper, aeSlot.getContainerSlot());
        }
        if (wrapper.getDelegate() == logic.getStorage()
                && (target instanceof StockingInterfaceBlockEntity
                || target instanceof OutputInterfaceBaseBlockEntity)) {
            return new InterfaceStorageSlot(wrapper, aeSlot.getContainerSlot(),
                    !(target instanceof OutputInterfaceBlockEntity));
        }
        return slot;
    }

    public static boolean allowsAction(Object target, InventoryAction action, Slot slot) {
        if (!slot.isActive() || slot instanceof LockedConfigSlot) return false;
        if (!(slot instanceof AppEngSlot aeSlot)
                || !(aeSlot.getInventory() instanceof ConfigMenuInventory wrapper)
                || !(target instanceof InterfaceLogicHost host)
                || wrapper.getDelegate() != host.getInterfaceLogic().getStorage()) return true;
        if (target instanceof StockingInterfaceBlockEntity) return false;
        if (target instanceof OutputInterfaceBaseBlockEntity) {
            if (!InterfaceMenuPolicy.isExtractableOutputStorageSlot(target, wrapper)) return false;
            return switch (action) {
                case FILL_ITEM, FILL_ITEM_MOVE_TO_PLAYER, FILL_ENTIRE_ITEM,
                        FILL_ENTIRE_ITEM_MOVE_TO_PLAYER, MOVE_REGION -> true;
                default -> false;
            };
        }
        return true;
    }

    public static boolean canSetAmount(InterfaceLogicHost host, int configSlot) {
        return host instanceof InputInterfaceBlockEntity
                && configSlot >= 0 && configSlot < host.getConfig().size();
    }
}
