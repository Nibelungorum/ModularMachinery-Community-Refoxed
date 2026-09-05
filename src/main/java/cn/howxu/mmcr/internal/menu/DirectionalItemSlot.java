package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

public class DirectionalItemSlot extends Slot {

    private final ResourceStorage<ItemResource> storage;

    public DirectionalItemSlot(ResourceStorage<ItemResource> storage, int index, int xPosition, int yPosition) {
        super(new SimpleContainer(0), index, xPosition, yPosition);
        this.storage = storage;
    }

    @Override
    public ItemStack getItem() {
        ItemResource resource = storage.resource(getContainerSlot());
        if (resource == null || resource.isEmpty()) return ItemStack.EMPTY;
        return resource.toStack((int) Math.min(storage.amount(getContainerSlot()), resource.getMaxStackSize()));
    }

    @Override
    public void set(ItemStack stack) {
        ItemResource current = storage.resource(getContainerSlot());
        try (Transaction transaction = Transaction.openRoot()) {
            if (current != null && !current.isEmpty()) {
                storage.extract(getContainerSlot(), current, storage.amount(getContainerSlot()), transaction);
            }
            if (!stack.isEmpty()) {
                ItemResource resource = ItemResource.of(stack);
                if (storage.insert(getContainerSlot(), resource, stack.getCount(), transaction) != stack.getCount()) return;
            }
            transaction.commit();
        }
    }

    @Override
    public ItemStack remove(int amount) {
        ItemResource resource = storage.resource(getContainerSlot());
        if (resource == null || resource.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        try (Transaction transaction = Transaction.openRoot()) {
            long extracted = storage.extract(getContainerSlot(), resource, amount, transaction);
            if (extracted == 0L) return ItemStack.EMPTY;
            transaction.commit();
            return resource.toStack((int) extracted);
        }
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !stack.isEmpty() && storage.isValid(getContainerSlot(), ItemResource.of(stack));
    }

    @Override
    public boolean mayPickup(Player playerIn) {
        return true;
    }

    @Override
    public int getMaxStackSize() {
        ItemResource resource = storage.resource(getContainerSlot());
        return resource == null || resource.isEmpty() ? super.getMaxStackSize() : resource.getMaxStackSize();
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        if (stack.isEmpty()) return super.getMaxStackSize(stack);
        return (int) Math.min(storage.capacity(getContainerSlot(), ItemResource.of(stack)), stack.getMaxStackSize());
    }

}
