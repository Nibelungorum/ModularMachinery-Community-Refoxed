package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.autoio.AutoIOCapabilityType;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

public abstract class FluidHatchBlockEntity extends IOPortBlockEntity {

    private final LongFluidStorage storage;

    protected FluidHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, IOPortKind kind) {
        super(type, pos, state);
        long capacity = kind.fluidHatchSize()
                .orElseThrow(() -> new IllegalStateException("Fluid hatch missing fluid size: " + kind.id()))
                .capacity();
        this.storage = new LongFluidStorage(capacity, this::markFluidChanged);
    }

    public ResourceHandler<FluidResource> getResourceHandler(Direction side) {
        return storage;
    }

    public LongFluidStorage getMutableFluidStorage() {
        return storage;
    }

    public boolean isTankEmpty() {
        return storage.isEmpty();
    }

    @Override
    public int autoIoTransferLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected boolean hasAutoIOTransferWork() {
        if (ioType() == IOType.OUTPUT) return !isTankEmpty();
        return storage.getAmountAsLong() < storage.getCapacityAsLong();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void markFluidChanged() {
        markAutoIOCacheDirty();
        setChanged();
    }

    @Override
    public abstract IOType ioType();

    @Override
    public abstract IOPortKind kind();

    @Override
    public AutoIOCapabilityType autoIOCapabilityType() {
        return AutoIOCapabilityType.FLUID;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        FluidResource resource = storage.getResource();
        output.putBoolean("tankHasFluid", !resource.isEmpty());
        if (!resource.isEmpty()) {
            output.store("tankFluid", FluidResource.OPTIONAL_CODEC, resource);
            output.putLong("tankAmount", storage.getAmountAsLong());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        if (input.getBooleanOr("tankHasFluid", false)) {
            FluidResource resource = input.read("tankFluid", FluidResource.OPTIONAL_CODEC).orElse(FluidResource.EMPTY);
            long amount = input.getLong("tankAmount").orElse(0L);
            storage.setContents(resource, amount);
        } else {
            storage.clearContent();
        }
    }
}
