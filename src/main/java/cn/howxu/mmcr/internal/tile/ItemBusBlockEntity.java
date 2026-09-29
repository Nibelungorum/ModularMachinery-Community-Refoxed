package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.facet.PersistenceFacet;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.util.IOType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

public abstract class ItemBusBlockEntity extends IOPortBlockEntity {
    private static final int MAX_DROPPED_STACKS_PER_SLOT = 1024;

    private final LongItemStorage storage;
    private CapabilitySnapshot capabilitySnapshot;

    protected ItemBusBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots, long capacity) {
        super(type, pos, state);
        this.storage = new LongItemStorage(slots, capacity, this::markItemChanged);
    }

    private void markItemChanged() {
        markAutoIOCacheDirty();
        notifyStorageChanged();
        notifyControllerOfInputChange();
    }

    /** Native item handler; the Transfer-backed port accessor is migrated by the capability task. */
    public LongItemStorage itemHandler() {
        return storage;
    }

    @Override
    public LongItemStorage nativeItemHandler() {
        return storage;
    }

    @Override
    public CapabilitySnapshot capabilitySnapshot() {
        if (capabilitySnapshot == null) {
            capabilitySnapshot = new CapabilitySnapshot(kind().definition().bindings().stream()
                    .map(this::createCapability)
                    .toList(), List.of(new ItemPersistenceFacet()));
        }
        return capabilitySnapshot;
    }

    public void dropContents() {
        dropItemResources(level, worldPosition, storage);
    }

    static void dropItemResources(Level level, BlockPos pos, LongItemStorage storage) {
        if (level == null || level.isClientSide()) return;
        dropItemResources(storage, stack -> Block.popResource(level, pos, stack));
    }

    static void dropItemResources(LongItemStorage storage, Consumer<ItemStack> drop) {
        if (storage == null || drop == null) throw new IllegalArgumentException("Storage and drop action are required");
        for (int slot = 0; slot < storage.size(); slot++) {
            ItemStack resource = storage.resource(slot);
            long amount = storage.amount(slot);
            if (resource.isEmpty() || amount <= 0L) continue;

            int stackLimit = resource.getMaxStackSize();
            long physicalLimit = (long) stackLimit * MAX_DROPPED_STACKS_PER_SLOT;
            long droppedAmount = Math.min(amount, physicalLimit);
            try {
                long remaining = droppedAmount;
                while (remaining > 0L) {
                    int count = (int) Math.min(remaining, stackLimit);
                    drop.accept(resource.copyWithCount(count));
                    remaining -= count;
                }
                if (amount > droppedAmount) {
                    MMCR.LOG.warn("Discarding {} item(s) from {} after bounded drop", amount - droppedAmount,
                            resource);
                }
            } finally {
                storage.forceExtract(slot, amount, false);
            }
        }
    }

    @Override
    public abstract IOType ioType();

    @Override
    public abstract IOPortKind kind();

    @Override
    protected void saveAdditional(CompoundTag output, HolderLookup.Provider registries) {
        super.saveAdditional(output, registries);
        capabilitySnapshot().facets(PersistenceFacet.class).forEach(facet -> {
            CompoundTag state = new CompoundTag();
            facet.save(state, registries);
            output.put(facet.stateKey(), state);
        });
    }

    private void saveItems(CompoundTag output, HolderLookup.Provider registries) {
        for (int slot = 0; slot < storage.size(); slot++) {
            String suffix = "_" + slot;
            ItemStack resource = storage.resource(slot);
            output.putBoolean("itemHasResource" + suffix, !resource.isEmpty());
            if (!resource.isEmpty()) {
                output.put("itemResource" + suffix, resource.save(registries));
                output.putLong("itemAmount" + suffix, storage.amount(slot));
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag input, HolderLookup.Provider registries) {
        beginLoadingAdditional();
        try {
            super.loadAdditional(input, registries);
            capabilitySnapshot().facets(PersistenceFacet.class)
                    .forEach(facet -> facet.load(input.getCompound(facet.stateKey()), registries));
        } finally {
            endLoadingAdditional();
        }
    }

    private void loadItems(CompoundTag input, HolderLookup.Provider registries) {
        for (int slot = 0; slot < storage.size(); slot++) {
            String suffix = "_" + slot;
            if (input.getBoolean("itemHasResource" + suffix)) {
                ItemStack resource = ItemStack.parseOptional(registries, input.getCompound("itemResource" + suffix));
                long amount = input.getLong("itemAmount" + suffix);
                storage.setContents(slot, resource, amount);
            } else {
                storage.setContents(slot, ItemStack.EMPTY, 0L);
            }
        }
    }

    private final class ItemPersistenceFacet implements PersistenceFacet {
        @Override
        public String stateKey() {
            return "item";
        }

        @Override
        public void save(CompoundTag output, HolderLookup.Provider registries) {
            saveItems(output, registries);
        }

        @Override
        public void load(CompoundTag input, HolderLookup.Provider registries) {
            loadItems(input, registries);
        }
    }
}
