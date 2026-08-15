package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.autoio.AutoIOCapabilityType;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public abstract class ItemBusBlockEntity extends IOPortBlockEntity {

    private final ItemStackHandler handler;
    private Boolean inventoryEmpty;

    protected ItemBusBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, IOPortKind kind) {
        super(type, pos, state);
        int slots = kind.itemBusSize()
                .orElseThrow(() -> new IllegalStateException("Item bus missing item size: " + kind.id()))
                .slots();
        this.handler = new ItemStackHandler(slots) {
            @Override
            protected void onContentsChanged(int slot) {
                inventoryEmpty = null;
                markAutoIOCacheDirty();
                setChanged();
                notifyControllerOfInputChange();
            }
        };
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
}
