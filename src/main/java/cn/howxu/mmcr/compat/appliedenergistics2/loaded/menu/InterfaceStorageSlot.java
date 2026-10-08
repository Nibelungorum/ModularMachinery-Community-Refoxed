package cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu;

import appeng.api.stacks.GenericStack;
import appeng.menu.slot.AppEngSlot;
import appeng.util.ConfigMenuInventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Protects interface mirrors and output caches while preserving client display amounts.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class InterfaceStorageSlot extends AppEngSlot {
    private final ConfigMenuInventory inventory;
    private final boolean readOnly;

    public InterfaceStorageSlot(ConfigMenuInventory inventory, int slot, boolean readOnly) {
        super(inventory, slot);
        this.inventory = inventory;
        this.readOnly = readOnly;
        if (readOnly) setNotDraggable();
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return !readOnly && super.mayPickup(player);
    }

    @Override
    public ItemStack remove(int amount) {
        return readOnly ? ItemStack.EMPTY : super.remove(amount);
    }

    @Override
    public void initialize(ItemStack stack) {
        if (isRemote()) syncDisplay(stack);
    }

    @Override
    public void set(ItemStack stack) {
        if (isRemote()) {
            syncDisplay(stack);
            setChanged();
            return;
        }
        if (readOnly) return;
        GenericStack current = inventory.getDelegate().getStack(getContainerSlot());
        GenericStack next = GenericStack.unwrapItemStack(stack);
        if (next == null && !stack.isEmpty()) next = inventory.convertToSuitableStack(stack);
        boolean removal = stack.isEmpty()
                || current != null && next != null && current.what().equals(next.what())
                && next.amount() >= 0L && next.amount() <= current.amount();
        if (removal) super.set(stack);
    }

    @Override
    public void clearStack() {
        if (isRemote()) {
            syncDisplay(ItemStack.EMPTY);
            setChanged();
        }
    }

    private void syncDisplay(ItemStack stack) {
        GenericStack next = GenericStack.unwrapItemStack(stack);
        if (next == null && !stack.isEmpty()) next = inventory.convertToSuitableStack(stack);
        var delegate = inventory.getDelegate();
        delegate.beginBatch();
        try {
            delegate.setStack(getContainerSlot(), next);
        } finally {
            delegate.endBatchSuppressed();
        }
    }
}
