package cn.howxu.mmcr.internal.storage;

import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Native item handler backed by long per-slot quantities.
 *
 * @author howxu <dev@howxu.cn>
 */
public class LongItemStorage implements IItemHandlerModifiable {
    private final LongResourceStorage<ItemStack> storage;
    private final Predicate<ItemStack> validator;

    public LongItemStorage(int slots, long capacity, Runnable onChange) {
        this(slots, capacity, stack -> true, onChange);
    }

    public LongItemStorage(int slots, long capacity, Predicate<ItemStack> validator, Runnable onChange) {
        if (validator == null) throw new IllegalArgumentException("validator must not be null");
        this.validator = validator;
        this.storage = new LongResourceStorage<>(slots, capacity, ItemStack::isEmpty,
                stack -> stack.copyWithCount(1), ItemStack::isSameItemSameComponents, onChange);
    }

    public int size() {
        return storage.size();
    }

    public long amount(int slot) {
        return storage.amount(slot);
    }

    public long capacity(int slot) {
        return storage.capacity(slot);
    }

    public ItemStack resource(int slot) {
        ItemStack resource = storage.resource(slot);
        return resource == null ? ItemStack.EMPTY : resource;
    }

    public void setContents(int slot, ItemStack stack, long amount) {
        if (stack == null || stack.isEmpty()) {
            storage.setContents(slot, ItemStack.EMPTY, 0L);
        } else {
            if (!validator.test(stack)) throw new IllegalArgumentException("Invalid item for slot " + slot);
            storage.setContents(slot, stack, amount);
        }
    }

    public long forceInsert(ItemStack stack, long amount, boolean simulate) {
        if (stack == null || stack.isEmpty() || !validator.test(stack)) return 0L;
        return storage.insertDirect(0, stack, amount, simulate);
    }

    public long forceExtract(int slot, long amount, boolean simulate) {
        ItemStack resource = resource(slot);
        return resource.isEmpty() ? 0L : storage.extractDirect(slot, resource, amount, simulate);
    }

    @Override
    public int getSlots() {
        return storage.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        ItemStack resource = resource(slot);
        return resource.isEmpty() ? ItemStack.EMPTY
                : resource.copyWithCount((int) Math.min(storage.amount(slot), Integer.MAX_VALUE));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack == null || stack.isEmpty() || !isItemValid(slot, stack)) return stack;
        long inserted = storage.insertDirect(slot, stack, stack.getCount(), simulate);
        return inserted == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount((int) (stack.getCount() - inserted));
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        ItemStack resource = resource(slot);
        if (resource.isEmpty()) return ItemStack.EMPTY;
        int extracted = (int) storage.extractDirect(slot, resource,
                Math.min(amount, resource.getMaxStackSize()), simulate);
        return extracted == 0 ? ItemStack.EMPTY : resource.copyWithCount(extracted);
    }

    @Override
    public int getSlotLimit(int slot) {
        return (int) Math.min(storage.capacity(slot), Integer.MAX_VALUE);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return stack != null && !stack.isEmpty() && validator.test(stack) && storage.isValid(slot, stack);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        setContents(slot, stack, stack == null ? 0L : stack.getCount());
    }
}
