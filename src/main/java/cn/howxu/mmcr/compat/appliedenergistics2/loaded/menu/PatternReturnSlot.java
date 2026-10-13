package cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu;

import appeng.api.stacks.GenericStack;
import appeng.menu.slot.AppEngSlot;
import appeng.util.ConfigMenuInventory;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternReturnInventory;
import net.minecraft.world.item.ItemStack;

/**
 * Limits physical menu insertion before taking items from the player.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PatternReturnSlot extends AppEngSlot {
    private final ConfigMenuInventory inventory;

    public PatternReturnSlot(ConfigMenuInventory inventory, int slot) {
        super(inventory, slot);
        this.inventory = inventory;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return super.mayPlace(stack) && getMaxStackSize(stack) > 0;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        GenericStack resource = inventory.convertToSuitableStack(stack);
        if (resource == null) return 0;
        long capacity = PatternReturnInventory.slotCapacity(inventory.getDelegate(), getSlotIndex(),
                resource.what(), resource.what().getType());
        return (int) Math.min(super.getMaxStackSize(stack), capacity);
    }
}
