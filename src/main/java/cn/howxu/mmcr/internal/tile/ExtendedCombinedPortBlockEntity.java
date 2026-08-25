package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.port.ExtendedCombinedPortSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Storage host for extended combined item and fluid ports.
 *
 * @author howxu <dev@howxu.cn>
 */
public class ExtendedCombinedPortBlockEntity extends IOPortBlockEntity {
    private final LongResourceStorage<ItemResource> itemStorage;
    private final LongResourceStorage<FluidResource> fluidStorage;
    private final IOPortKind kind;
    private CapabilitySnapshot capabilitySnapshot;

    public ExtendedCombinedPortBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, kindFromState(state, fallback(state)));
    }

    private ExtendedCombinedPortBlockEntity(BlockPos pos, BlockState state, IOPortKind kind) {
        super(typeForKind(kind), pos, state);
        ExtendedCombinedPortSize size = kind.extendedCombinedPortSize()
                .orElseThrow(() -> new IllegalStateException("Extended combined port missing size: " + kind.id()));
        this.kind = kind;
        this.itemStorage = new LongResourceStorage<>(ItemResource.class, size.itemTypes(), Long.MAX_VALUE,
                ItemResource::isEmpty, this::markStorageChanged);
        this.fluidStorage = new LongResourceStorage<>(FluidResource.class, size.fluidTypes(), Long.MAX_VALUE,
                FluidResource::isEmpty, this::markStorageChanged);
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
        return itemStorage;
    }

    @Override
    public ResourceStorage<FluidResource> fluidStorage() {
        return fluidStorage;
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
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        saveItems(output);
        saveFluids(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        loadItems(input);
        loadFluids(input);
    }

    private void saveItems(ValueOutput output) {
        for (int slot = 0; slot < itemStorage.size(); slot++) {
            String suffix = "_" + slot;
            ItemResource resource = itemStorage.resource(slot);
            output.putBoolean("itemHasResource" + suffix, resource != null && !resource.isEmpty());
            if (resource != null && !resource.isEmpty()) {
                output.store("itemResource" + suffix, ItemResource.OPTIONAL_CODEC, resource);
                output.putLong("itemAmount" + suffix, itemStorage.amount(slot));
            }
        }
    }

    private void loadItems(ValueInput input) {
        for (int slot = 0; slot < itemStorage.size(); slot++) {
            String suffix = "_" + slot;
            if (input.getBooleanOr("itemHasResource" + suffix, false)) {
                ItemResource resource = input.read("itemResource" + suffix, ItemResource.OPTIONAL_CODEC)
                        .orElse(ItemResource.EMPTY);
                itemStorage.setContents(slot, resource, input.getLong("itemAmount" + suffix).orElse(0L));
            } else {
                itemStorage.setContents(slot, ItemResource.EMPTY, 0L);
            }
        }
    }

    private void saveFluids(ValueOutput output) {
        for (int slot = 0; slot < fluidStorage.size(); slot++) {
            String suffix = "_" + slot;
            FluidResource resource = fluidStorage.resource(slot);
            output.putBoolean("tankHasFluid" + suffix, !resource.isEmpty());
            if (!resource.isEmpty()) {
                output.store("tankFluid" + suffix, FluidResource.OPTIONAL_CODEC, resource);
                output.putLong("tankAmount" + suffix, fluidStorage.amount(slot));
            }
        }
    }

    private void loadFluids(ValueInput input) {
        for (int slot = 0; slot < fluidStorage.size(); slot++) {
            String suffix = "_" + slot;
            if (input.getBooleanOr("tankHasFluid" + suffix, false)) {
                FluidResource resource = input.read("tankFluid" + suffix, FluidResource.OPTIONAL_CODEC)
                        .orElse(FluidResource.EMPTY);
                fluidStorage.setContents(slot, resource, input.getLong("tankAmount" + suffix).orElse(0L));
            } else {
                fluidStorage.setContents(slot, FluidResource.EMPTY, 0L);
            }
        }
    }

    private void markStorageChanged() {
        markAutoIOCacheDirty();
        setChanged();
    }

    private static IOPortKind fallback(BlockState state) {
        if (state.getBlock() instanceof IOPortBlock port && port.kind().ioType() == IOType.OUTPUT) {
            return PortKinds.EXTENDED_COMBINED_OUTPUT;
        }
        return PortKinds.EXTENDED_COMBINED_INPUT;
    }
}
