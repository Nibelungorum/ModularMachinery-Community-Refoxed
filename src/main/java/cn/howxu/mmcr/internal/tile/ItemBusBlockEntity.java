package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.facet.PersistenceFacet;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.function.Consumer;

public abstract class ItemBusBlockEntity extends IOPortBlockEntity {
    private static final int MAX_DROPPED_STACKS_PER_SLOT = 1024;

    private final LongResourceStorage<ItemResource> storage;
    private CapabilitySnapshot capabilitySnapshot;

    protected ItemBusBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots, long capacity) {
        super(type, pos, state);
        long storageCapacity = capacity;
        this.storage = new LongResourceStorage<>(ItemResource.class, slots, storageCapacity,
                ItemResource::isEmpty, this::markItemChanged) {
            @Override
            public long capacity(int slot, ItemResource resource) {
                long slotCapacity = super.capacity(slot, resource);
                return storageCapacity == Long.MAX_VALUE || resource == null
                        ? slotCapacity : Math.min(slotCapacity, resource.getMaxStackSize());
            }
        };
    }

    private void markItemChanged() {
        markAutoIOCacheDirty();
        notifyStorageChanged();
        notifyControllerOfInputChange();
    }

    @Override
    public ResourceStorage<ItemResource> itemStorage() {
        return storage;
    }

    @Override
    public CapabilitySnapshot capabilitySnapshot() {
        if (capabilitySnapshot == null) {
            capabilitySnapshot = new CapabilitySnapshot(kind().definition().bindings().stream()
                    .map(this::createCapability)
                    .toList(), java.util.List.of(new ItemPersistenceFacet()));
        }
        return capabilitySnapshot;
    }

    public void dropContents() {
        dropItemResources(level, worldPosition, storage);
    }

    static void dropItemResources(Level level, BlockPos pos, ResourceStorage<ItemResource> storage) {
        if (level == null || level.isClientSide()) return;
        dropItemResources(storage, stack -> Block.popResource(level, pos, stack));
    }

    static void dropItemResources(ResourceStorage<ItemResource> storage, Consumer<ItemStack> drop) {
        if (storage == null || drop == null) throw new IllegalArgumentException("Storage and drop action are required");
        for (int slot = 0; slot < storage.size(); slot++) {
            ItemResource resource = storage.resource(slot);
            long amount = storage.amount(slot);
            if (resource == null || resource.isEmpty() || amount <= 0L) continue;

            int stackLimit = resource.getMaxStackSize();
            long physicalLimit = (long) stackLimit * MAX_DROPPED_STACKS_PER_SLOT;
            long droppedAmount = Math.min(amount, physicalLimit);
            try {
                long remaining = droppedAmount;
                while (remaining > 0L) {
                    int count = (int) Math.min(remaining, stackLimit);
                    drop.accept(resource.toStack(count));
                    remaining -= count;
                }
                if (amount > droppedAmount) {
                    MMCR.LOG.warn("Discarding {} item(s) from {} after bounded drop", amount - droppedAmount,
                            resource);
                }
            } finally {
                try (Transaction transaction = Transaction.openRoot()) {
                    storage.extract(slot, resource, amount, transaction);
                    transaction.commit();
                }
            }
        }
    }

    @Override
    public abstract IOType ioType();

    @Override
    public abstract IOPortKind kind();

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        capabilitySnapshot().facets(PersistenceFacet.class)
                .forEach(facet -> facet.save(output.child(facet.stateKey())));
    }

    private void saveItems(ValueOutput output) {
        for (int slot = 0; slot < storage.size(); slot++) {
            String suffix = "_" + slot;
            ItemResource resource = storage.resource(slot);
            output.putBoolean("itemHasResource" + suffix, resource != null && !resource.isEmpty());
            if (resource != null && !resource.isEmpty()) {
                output.store("itemResource" + suffix, ItemResource.OPTIONAL_CODEC, resource);
                output.putLong("itemAmount" + suffix, storage.amount(slot));
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        beginLoadingAdditional();
        try {
            super.loadAdditional(input);
            input.child("item").ifPresent(child -> capabilitySnapshot().facets(PersistenceFacet.class)
                    .forEach(facet -> facet.load(child)));
        } finally {
            endLoadingAdditional();
        }
    }

    private void loadItems(ValueInput input) {
        for (int slot = 0; slot < storage.size(); slot++) {
            String suffix = "_" + slot;
            if (input.getBooleanOr("itemHasResource" + suffix, false)) {
                ItemResource resource = input.read("itemResource" + suffix, ItemResource.OPTIONAL_CODEC)
                        .orElse(ItemResource.EMPTY);
                long amount = input.getLong("itemAmount" + suffix).orElse(0L);
                storage.setContents(slot, resource, amount);
            } else {
                storage.setContents(slot, ItemResource.EMPTY, 0L);
            }
        }
    }

    private final class ItemPersistenceFacet implements PersistenceFacet {
        @Override
        public String stateKey() {
            return "item";
        }

        @Override
        public void save(ValueOutput output) {
            saveItems(output);
        }

        @Override
        public void load(ValueInput input) {
            loadItems(input);
        }
    }
}
