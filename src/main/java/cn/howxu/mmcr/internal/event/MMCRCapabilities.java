package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.registry.MMCRRegistries;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public final class MMCRCapabilities {
    private MMCRCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                MMCRRegistries.ITEM_BUS_BE.get(),
                (be, side) -> be instanceof ItemBusBlockEntity ib ? new LegacyItemHandlerAdapter(ib.getItemHandler(side)) : null);
    }

    private static final class LegacyItemHandlerAdapter implements ResourceHandler<ItemResource> {
        private final IItemHandler handler;

        LegacyItemHandlerAdapter(IItemHandler handler) {
            this.handler = handler;
        }

        @Override
        public int size() {
            return handler.getSlots();
        }

        @Override
        public ItemResource getResource(int slot) {
            ItemStack stack = handler.getStackInSlot(slot);
            return stack.isEmpty() ? ItemResource.EMPTY : ItemResource.of(stack);
        }

        @Override
        public long getAmountAsLong(int slot) {
            return handler.getStackInSlot(slot).getCount();
        }

        @Override
        public long getCapacityAsLong(int slot, ItemResource resource) {
            return Math.min(handler.getSlotLimit(slot), resource.getMaxStackSize());
        }

        @Override
        public boolean isValid(int slot, ItemResource resource) {
            return handler.isItemValid(slot, resource.toStack(1));
        }

        @Override
        public int insert(int slot, ItemResource resource, int amount, TransactionContext tx) {
            ItemStack remainder = handler.insertItem(slot, resource.toStack(amount), false);
            return amount - remainder.getCount();
        }

        @Override
        public int extract(int slot, ItemResource resource, int amount, TransactionContext tx) {
            ItemStack extracted = handler.extractItem(slot, amount, false);
            return extracted.getCount();
        }
    }
}
