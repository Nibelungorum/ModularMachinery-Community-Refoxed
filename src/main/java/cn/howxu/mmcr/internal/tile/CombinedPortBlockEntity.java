package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.port.CombinedPortSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Ordinary combined item and fluid storage host.
 *
 * @author howxu <dev@howxu.cn>
 */
public class CombinedPortBlockEntity extends IOPortBlockEntity {
    private static final long FLUID_CAPACITY = 256_000L;

    private final ItemBusBlockEntity.StorageItemHandler itemHandler;
    private final LongFluidStorage fluidStorage;
    private final IOPortKind kind;
    private CapabilitySnapshot capabilitySnapshot;

    public CombinedPortBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, kindFromState(state, fallback(state)));
    }

    private CombinedPortBlockEntity(BlockPos pos, BlockState state, IOPortKind kind) {
        super(typeForKind(kind), pos, state);
        CombinedPortSize size = kind.combinedPortSize()
                .orElseThrow(() -> new IllegalStateException("Combined port missing size: " + kind.id()));
        this.kind = kind;
        this.itemHandler = new ItemBusBlockEntity.StorageItemHandler(size.itemTypes(), ignored -> markStorageChanged());
        this.fluidStorage = new LongFluidStorage(size.fluidTypes(), FLUID_CAPACITY, this::markStorageChanged);
    }

    @Override
    public IOType ioType() {
        return kind.ioType();
    }

    @Override
    public IOPortKind kind() {
        return kind;
    }

    public IItemHandler getItemHandler(Direction side) {
        return itemHandler;
    }

    public ItemStackHandler getItemStackHandler(Direction side) {
        return itemHandler;
    }

    @Override
    public ResourceStorage<ItemResource> itemStorage() {
        return ItemBusBlockEntity.asResourceStorage(itemHandler);
    }

    public ResourceHandler<FluidResource> getResourceHandler(Direction side) {
        return fluidStorage;
    }

    @Override
    public LongFluidStorage fluidStorage() {
        return fluidStorage;
    }

    @Override
    public void dropContents() {
        ItemBusBlockEntity.dropItemStacks(level, worldPosition, itemHandler);
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
        itemHandler.serialize(output.child("inventory"));
        saveFluids(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        itemHandler.deserialize(input.childOrEmpty("inventory"));
        loadFluids(input);
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
        notifyStorageChanged();
        notifyControllerOfInputChange();
    }

    private static IOPortKind fallback(BlockState state) {
        if (state.getBlock() instanceof IOPortBlock port && port.kind().ioType() == IOType.OUTPUT) {
            return PortKinds.COMBINED_OUTPUT;
        }
        return PortKinds.COMBINED_INPUT;
    }
}
