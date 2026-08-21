package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Single-slot storage for thread dispersers.
 *
 * @author howxu <dev@howxu.cn>
 */
public class FactorySchedulerBlockEntity extends LinkedAppearanceBlockEntity {

    private final ItemStackHandler handler = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(ModItems.THREAD_DISPERSER.get());
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (owner != null) owner.invalidateFactoryCapacity();
        }
    };
    private @Nullable MachineControllerBlockEntity owner;

    public FactorySchedulerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BES.get("factory_controller").get(), pos, state);
    }

    void bindOwner(@Nullable MachineControllerBlockEntity owner) {
        this.owner = owner;
    }

    public int threadCount() {
        long count = 1L + handler.getStackInSlot(0).getCount();
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    public IItemHandler getItemHandler(Direction side) {
        return handler;
    }

    public ItemStackHandler getItemStackHandler(Direction side) {
        return handler;
    }

    public void dropContents() {
        if (level == null || level.isClientSide()) return;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            Block.popResource(level, worldPosition, stack);
            handler.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        dropContents();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        handler.serialize(output.child("inventory"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        handler.deserialize(input.childOrEmpty("inventory"));
    }

    @Override
    public void setRemoved() {
        owner = null;
        super.setRemoved();
    }
}
