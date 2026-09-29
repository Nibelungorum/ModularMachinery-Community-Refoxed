package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Single-slot storage for thread dispersers.
 *
 * @author howxu <dev@howxu.cn>
 */
public class FactorySchedulerBlockEntity extends LinkedAppearanceBlockEntity {

    private final LongItemStorage storage = new LongItemStorage(1, Long.MAX_VALUE,
            stack -> stack.is(ModItems.THREAD_DISPERSER.get()), this::onContentsChanged);
    private @Nullable MachineControllerBlockEntity owner;

    public FactorySchedulerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BES.get("factory_controller").get(), pos, state);
    }

    void bindOwner(@Nullable MachineControllerBlockEntity owner) {
        this.owner = owner;
    }

    public int threadCount() {
        long count = 1L + storage.amount(0);
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    public LongItemStorage itemHandler() {
        return storage;
    }

    public void dropContents() {
        ItemBusBlockEntity.dropItemResources(level, worldPosition, storage);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        dropContents();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ItemStack resource = storage.resource(0);
        output.putBoolean("itemHasResource", !resource.isEmpty());
        if (!resource.isEmpty()) {
            output.store("itemResource", ItemStack.CODEC, resource);
            output.putLong("itemAmount", storage.amount(0));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        if (input.getBooleanOr("itemHasResource", false)) {
            ItemStack resource = input.read("itemResource", ItemStack.CODEC)
                    .orElse(ItemStack.EMPTY);
            storage.setContents(0, resource, input.getLong("itemAmount").orElse(0L));
        } else {
            storage.setContents(0, ItemStack.EMPTY, 0L);
        }
    }

    @Override
    public void setRemoved() {
        owner = null;
        super.setRemoved();
    }

    private void onContentsChanged() {
        setChanged();
        if (owner != null) owner.invalidateFactoryCapacity();
    }
}
