package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * Single-slot storage for thread dispersers.
 *
 * @author howxu <dev@howxu.cn>
 */
public class FactorySchedulerBlockEntity extends LinkedAppearanceBlockEntity {

    private final LongResourceStorage<ItemResource> storage = new LongResourceStorage<>(ItemResource.class, 1, Long.MAX_VALUE,
            ItemResource::isEmpty, this::onContentsChanged) {
        @Override
        public boolean isValid(int slot, ItemResource resource) {
            return resource.toStack(1).is(ModItems.THREAD_DISPERSER.get()) && super.isValid(slot, resource);
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
        long count = 1L + storage.amount(0);
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    public ResourceStorage<ItemResource> itemStorage() {
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
        ItemResource resource = storage.resource(0);
        output.putBoolean("itemHasResource", resource != null && !resource.isEmpty());
        if (resource != null && !resource.isEmpty()) {
            output.store("itemResource", ItemResource.OPTIONAL_CODEC, resource);
            output.putLong("itemAmount", storage.amount(0));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        if (input.getBooleanOr("itemHasResource", false)) {
            ItemResource resource = input.read("itemResource", ItemResource.OPTIONAL_CODEC)
                    .orElse(ItemResource.EMPTY);
            storage.setContents(0, resource, input.getLong("itemAmount").orElse(0L));
        } else {
            storage.setContents(0, ItemResource.EMPTY, 0L);
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
