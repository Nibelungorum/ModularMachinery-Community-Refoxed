package cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu;

import appeng.api.inventories.InternalInventory;
import appeng.menu.slot.FakeSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps output configuration synchronized without accepting server-side edits.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class LockedConfigSlot extends FakeSlot {
    public LockedConfigSlot(InternalInventory inventory, int slot) {
        super(inventory, slot);
        setNotDraggable();
    }

    @Override
    public boolean canSetFilterTo(ItemStack stack) {
        return false;
    }

    @Override
    public void set(ItemStack stack) {
        if (isRemote()) {
            getInventory().setItemDirect(getContainerSlot(), stack);
            setChanged();
        }
    }

    @Override
    public void initialize(ItemStack stack) {
        if (isRemote()) super.initialize(stack);
    }

    @Override
    public void increase(ItemStack stack) {
    }

    @Override
    public void decrease(ItemStack stack) {
    }

    @Override
    public void clearStack() {
        if (isRemote()) super.clearStack();
    }
}
