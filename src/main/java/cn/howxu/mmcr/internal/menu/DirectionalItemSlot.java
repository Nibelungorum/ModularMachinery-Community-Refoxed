package cn.howxu.mmcr.internal.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.items.IItemHandler;

public class DirectionalItemSlot extends Slot {

    private final IItemHandler storage;

    public DirectionalItemSlot(IItemHandler storage, int index, int xPosition, int yPosition) {
        super(new SimpleContainer(0), index, xPosition, yPosition);
        this.storage = storage;
    }

    @Override
    public ItemStack getItem() {
        return storage.getStackInSlot(getContainerSlot()).copy();
    }

    @Override
    public void set(ItemStack stack) {
        if (!stack.isEmpty() && (!storage.isItemValid(getContainerSlot(), stack)
                || stack.getCount() > storage.getSlotLimit(getContainerSlot()))) return;
        storage.extractItem(getContainerSlot(), Integer.MAX_VALUE, false);
        if (!stack.isEmpty()) storage.insertItem(getContainerSlot(), stack.copy(), false);
    }

    @Override
    public ItemStack remove(int amount) {
        return amount <= 0 ? ItemStack.EMPTY : storage.extractItem(getContainerSlot(), amount, false);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !stack.isEmpty() && storage.isItemValid(getContainerSlot(), stack);
    }

    @Override
    public boolean mayPickup(Player playerIn) {
        return true;
    }

    @Override
    public ItemStack safeInsert(ItemStack inputStack, int inputAmount) {
        if (inputStack.isEmpty() || !mayPlace(inputStack)) return inputStack;

        int transferable = Math.min(Math.min(inputAmount, inputStack.getCount()),
                getMaxStackSize(inputStack) - getItem().getCount());
        if (transferable <= 0) return inputStack;

        ItemStack remainder = storage.insertItem(getContainerSlot(), inputStack.copyWithCount(transferable), false);
        return shrink(inputStack, transferable - remainder.getCount());
    }

    @Override
    public int getMaxStackSize() {
        ItemStack resource = storage.getStackInSlot(getContainerSlot());
        return resource.isEmpty() ? super.getMaxStackSize() : resource.getMaxStackSize();
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        if (stack.isEmpty()) return super.getMaxStackSize(stack);
        return Math.min(storage.getSlotLimit(getContainerSlot()), stack.getMaxStackSize());
    }

    private static ItemStack shrink(ItemStack stack, int amount) {
        stack.shrink(amount);
        return stack;
    }

}
