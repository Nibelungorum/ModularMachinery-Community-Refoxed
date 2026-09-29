package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.facet.PersistenceFacet;
import cn.howxu.mmcr.internal.port.ExtendedFluidHatchSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.util.IOType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

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

    /** Native fluid handler; the Transfer-backed port accessor is migrated by the capability task. */
    public LongFluidStorage fluidHandler(Direction side) {
        return storage;
    }

    @Override
    public LongFluidStorage nativeFluidHandler() {
        return storage;
    }

    @Override
    public CapabilitySnapshot capabilitySnapshot() {
        if (capabilitySnapshot == null) {
            capabilitySnapshot = new CapabilitySnapshot(kind().definition().bindings().stream()
                    .map(this::createCapability)
                    .toList(), List.of(new FluidPersistenceFacet()));
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
        notifyStorageChanged();
        notifyControllerOfInputChange();
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

    private void saveFluids(CompoundTag output, HolderLookup.Provider registries) {
        for (int slot = 0; slot < storage.size(); slot++) {
            String suffix = slot == 0 ? "" : "_" + slot;
            FluidStack resource = storage.resource(slot);
            output.putBoolean("tankHasFluid" + suffix, !resource.isEmpty());
            if (!resource.isEmpty()) {
                output.put("tankFluid" + suffix, resource.saveOptional(registries));
                output.putLong("tankAmount" + suffix, storage.amount(slot));
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

    private void loadFluids(CompoundTag input, HolderLookup.Provider registries) {
        for (int slot = 0; slot < storage.size(); slot++) {
            String suffix = slot == 0 ? "" : "_" + slot;
            if (input.getBoolean("tankHasFluid" + suffix)) {
                FluidStack resource = FluidStack.parseOptional(registries, input.getCompound("tankFluid" + suffix));
                long amount = input.getLong("tankAmount" + suffix);
                storage.setContents(slot, resource, amount);
            } else {
                storage.setContents(slot, FluidStack.EMPTY, 0L);
            }
        }
    }

    private final class FluidPersistenceFacet implements PersistenceFacet {
        @Override
        public String stateKey() {
            return "fluid";
        }

        @Override
        public void save(CompoundTag output, HolderLookup.Provider registries) {
            saveFluids(output, registries);
        }

        @Override
        public void load(CompoundTag input, HolderLookup.Provider registries) {
            loadFluids(input, registries);
        }
    }
}
