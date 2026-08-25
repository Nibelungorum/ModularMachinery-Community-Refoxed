package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.internal.port.ExtendedFluidHatchSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
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
    private CapabilitySnapshot capabilitySnapshot;

    protected FluidHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, IOPortKind kind) {
        super(type, pos, state);
        if (kind.fluidHatchSize().isPresent()) {
            FluidHatchSize size = kind.fluidHatchSize().get();
            this.storage = new LongFluidStorage(size.capacity(), this::markFluidChanged);
        } else {
            ExtendedFluidHatchSize size = kind.extendedFluidHatchSize()
                    .orElseThrow(() -> new IllegalStateException("Fluid hatch missing fluid size: " + kind.id()));
            this.storage = new LongFluidStorage(size.slots(), Long.MAX_VALUE, this::markFluidChanged);
        }
    }

    public ResourceHandler<FluidResource> getResourceHandler(Direction side) {
        return storage;
    }

    @Override
    public LongFluidStorage fluidStorage() {
        return storage;
    }

    @Override
    public CapabilitySnapshot capabilitySnapshot() {
        if (capabilitySnapshot == null) {
            capabilitySnapshot = new CapabilitySnapshot(kind().capabilityFactories().stream()
                    .map(factory -> factory.create(this))
                    .toList());
        }
        return capabilitySnapshot;
    }

    public boolean isTankEmpty() {
        for (int slot = 0; slot < storage.size(); slot++) {
            if (storage.amount(slot) > 0L && !storage.resource(slot).isEmpty()) return false;
        }
        return true;
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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (int slot = 0; slot < storage.size(); slot++) {
            String suffix = slot == 0 ? "" : "_" + slot;
            FluidResource resource = storage.resource(slot);
            output.putBoolean("tankHasFluid" + suffix, !resource.isEmpty());
            if (!resource.isEmpty()) {
                output.store("tankFluid" + suffix, FluidResource.OPTIONAL_CODEC, resource);
                output.putLong("tankAmount" + suffix, storage.amount(slot));
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (int slot = 0; slot < storage.size(); slot++) {
            String suffix = slot == 0 ? "" : "_" + slot;
            if (input.getBooleanOr("tankHasFluid" + suffix, false)) {
                FluidResource resource = input.read("tankFluid" + suffix, FluidResource.OPTIONAL_CODEC)
                        .orElse(FluidResource.EMPTY);
                long amount = input.getLong("tankAmount" + suffix).orElse(0L);
                storage.setContents(slot, resource, amount);
            } else {
                storage.setContents(slot, FluidResource.EMPTY, 0L);
            }
        }
    }
}
