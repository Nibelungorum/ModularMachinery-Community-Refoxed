package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.autoio.AutoIOCapabilityType;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.storage.BulkItemStorage;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.function.IntConsumer;

public abstract class ItemBusBlockEntity extends IOPortBlockEntity {

    private final ItemStackHandler handler;
    private Boolean inventoryEmpty;

    protected ItemBusBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, IOPortKind kind) {
        super(type, pos, state);
        int slots = kind.itemBusSize()
                .orElseThrow(() -> new IllegalStateException("Item bus missing item size: " + kind.id()))
                .slots();
        this.handler = new StorageItemHandler(slots, this::onContentsChanged);
    }

    private void onContentsChanged(int slot) {
        inventoryEmpty = null;
        markAutoIOCacheDirty();
        setChanged();
        notifyControllerOfInputChange();
    }

    public IItemHandler getItemHandler(Direction side) { return handler; }

    public ItemStackHandler getItemStackHandler(Direction side) { return handler; }

    public void dropContents() {
        if (level == null || level.isClientSide()) return;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            Block.popResource(level, worldPosition, stack);
            handler.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    public void clearContents() {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            handler.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        dropContents();
    }

    public boolean isInventoryEmpty() {
        if (inventoryEmpty == null) {
            inventoryEmpty = true;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (!handler.getStackInSlot(slot).isEmpty()) {
                    inventoryEmpty = false;
                    break;
                }
            }
        }
        return inventoryEmpty;
    }

    @Override
    protected boolean hasAutoIOTransferWork() {
        if (ioType() == IOType.OUTPUT) return !isInventoryEmpty();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getCount() < Math.min(handler.getSlotLimit(slot), stack.getMaxStackSize())) return true;
        }
        return false;
    }

    private void notifyControllerOfInputChange() {
        if (ioType() != IOType.INPUT || level == null || level.isClientSide() || linkedControllerPos() == null) return;
        if (level.getBlockEntity(linkedControllerPos()) instanceof MachineControllerBlockEntity controller) {
            controller.onRecipeInputsChanged();
        }
    }

    @Override
    public abstract IOType ioType();

    @Override
    public abstract IOPortKind kind();

    @Override
    public AutoIOCapabilityType autoIOCapabilityType() {
        return AutoIOCapabilityType.ITEM;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        normalizeItemHolders();
        handler.serialize(output.child("inventory"));
    }

    private void normalizeItemHolders() {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || stack.typeHolder().unwrapKey().isPresent()) continue;

            ItemStack normalized = new ItemStack(stack.getItem(), stack.getCount());
            normalized.applyComponents(stack.getComponentsPatch());
            handler.setStackInSlot(slot, normalized);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        handler.deserialize(input.childOrEmpty("inventory"));
    }

    /**
     * Legacy slot API view backed by long resource storage.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class StorageItemHandler extends ItemStackHandler {
        private final BulkItemStorage[] storages;
        private final IntConsumer onChange;
        private boolean suppressChanges;

        private StorageItemHandler(int slots, IntConsumer onChange) {
            super(slots);
            this.onChange = onChange;
            this.storages = new BulkItemStorage[slots];
            for (int slot = 0; slot < slots; slot++) {
                int changedSlot = slot;
                storages[slot] = new BulkItemStorage(Item.ABSOLUTE_MAX_STACK_SIZE,
                        () -> storageChanged(changedSlot));
            }
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            validateSlotIndex(slot);
            boolean wasSuppressingChanges = suppressChanges;
            suppressChanges = true;
            boolean replaced;
            try {
                replaced = replace(slot, stack);
            } finally {
                suppressChanges = wasSuppressingChanges;
            }
            if (!replaced) {
                throw new IllegalArgumentException("Item stack cannot fit in slot " + slot);
            }
            if (!wasSuppressingChanges) onChange.accept(slot);
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            validateSlotIndex(slot);
            BulkItemStorage storage = storages[slot];
            if (storage.resource(0).isEmpty()) return ItemStack.EMPTY;
            return storage.resource(0).toStack((int) Math.min(storage.amount(0), Integer.MAX_VALUE));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;
            validateSlotIndex(slot);
            if (!isItemValid(slot, stack)) return stack;
            ItemResource resource = ItemResource.of(stack);
            long limit = Math.min((long) getSlotLimit(slot), stack.getMaxStackSize());
            long available = limit - storages[slot].amount(0);
            if (available <= 0L) return stack;
            long inserted = storages[slot].insert(resource, Math.min(stack.getCount(), available), simulate);
            return inserted == stack.getCount()
                    ? ItemStack.EMPTY
                    : stack.copyWithCount(stack.getCount() - (int) inserted);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) return ItemStack.EMPTY;
            validateSlotIndex(slot);
            ItemResource resource = storages[slot].resource(0);
            if (resource.isEmpty()) return ItemStack.EMPTY;
            long extracted = storages[slot].extract(resource, Math.min(amount, resource.getMaxStackSize()), simulate);
            return extracted == 0L ? ItemStack.EMPTY : resource.toStack((int) extracted);
        }

        @Override
        public int getSlotLimit(int slot) {
            validateSlotIndex(slot);
            return (int) Math.min(storages[slot].capacity(0, null), Integer.MAX_VALUE);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            validateSlotIndex(slot);
            return true;
        }

        @Override
        public void serialize(ValueOutput output) {
            ValueOutput.TypedOutputList<ItemStackWithSlot> itemList = output.list("Items", ItemStackWithSlot.CODEC);
            for (int slot = 0; slot < getSlots(); slot++) {
                ItemStack stack = getStackInSlot(slot);
                if (!stack.isEmpty()) itemList.add(new ItemStackWithSlot(slot, stack));
            }
            output.putInt("Size", getSlots());
        }

        @Override
        public void deserialize(ValueInput input) {
            suppressChanges = true;
            try {
                for (int slot = 0; slot < getSlots(); slot++) replace(slot, ItemStack.EMPTY);
                input.listOrEmpty("Items", ItemStackWithSlot.CODEC).forEach(slot -> {
                    if (slot.isValidInContainer(getSlots())) replace(slot.slot(), slot.stack());
                });
            } finally {
                suppressChanges = false;
            }
        }

        private boolean replace(int slot, ItemStack stack) {
            if (stack == null) return false;
            ItemResource incoming = stack.isEmpty() ? ItemResource.EMPTY : ItemResource.of(stack);
            long requested = stack.isEmpty() ? 0L : stack.getCount();
            if (!incoming.isEmpty() && requested > storages[slot].capacity(0, null)) return false;
            if (!stack.isEmpty() && incoming.isEmpty()) return false;

            ItemResource current = storages[slot].resource(0);
            long currentAmount = storages[slot].amount(0);
            if (!current.isEmpty()) storages[slot].extract(current, Long.MAX_VALUE, false);
            if (requested == 0L) return true;

            long inserted = storages[slot].insert(incoming, requested, false);
            if (inserted == requested) return true;

            if (inserted > 0L) storages[slot].extract(incoming, inserted, false);
            if (currentAmount > 0L) storages[slot].insert(current, currentAmount, false);
            return false;
        }

        private void storageChanged(int slot) {
            if (!suppressChanges) onChange.accept(slot);
        }
    }
}
