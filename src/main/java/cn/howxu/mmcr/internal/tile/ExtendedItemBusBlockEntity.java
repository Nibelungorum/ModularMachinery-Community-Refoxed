package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.port.ExtendedItemBusSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Storage host for extended item buses.
 *
 * @author howxu <dev@howxu.cn>
 */
public class ExtendedItemBusBlockEntity extends IOPortBlockEntity {
    private final LongResourceStorage<ItemResource> storage;
    private final IOPortKind kind;
    private CapabilitySnapshot capabilitySnapshot;

    public ExtendedItemBusBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, kindFromState(state, fallback(state)));
    }

    private ExtendedItemBusBlockEntity(BlockPos pos, BlockState state, IOPortKind kind) {
        super(typeForKind(kind), pos, state);
        ExtendedItemBusSize size = kind.extendedItemBusSize()
                .orElseThrow(() -> new IllegalStateException("Extended item bus missing size: " + kind.id()));
        this.kind = kind;
        this.storage = new LongResourceStorage<>(ItemResource.class, size.slots(), Long.MAX_VALUE,
                ItemResource::isEmpty, this::markItemChanged);
    }

    @Override
    public IOType ioType() {
        return kind.ioType();
    }

    @Override
    public IOPortKind kind() {
        return kind;
    }

    @Override
    public ResourceStorage<ItemResource> itemStorage() {
        return storage;
    }

    @Override
    public CapabilitySnapshot capabilitySnapshot() {
        if (capabilitySnapshot == null) {
            capabilitySnapshot = new CapabilitySnapshot(kind.capabilityFactories().stream()
                    .map(factory -> factory.create(this))
                    .toList());
        }
        return capabilitySnapshot;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
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
        super.loadAdditional(input);
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

    private void markItemChanged() {
        markAutoIOCacheDirty();
        notifyStorageChanged();
    }

    private static IOPortKind fallback(BlockState state) {
        if (state.getBlock() instanceof IOPortBlock port && port.kind().ioType() == IOType.OUTPUT) {
            return PortKinds.EXTENDED_ITEM_OUTPUT;
        }
        return PortKinds.EXTENDED_ITEM_INPUT;
    }
}
