package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.facet.PersistenceFacet;
import cn.howxu.mmcr.internal.port.ExtendedEnergyHatchSize;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.util.IOType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class EnergyHatchBlockEntity extends IOPortBlockEntity {

    private final LongEnergyStorage storage;
    private CapabilitySnapshot capabilitySnapshot;

    protected EnergyHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, IOPortKind kind) {
        super(type, pos, state);
        if (kind.energyHatchSize().isPresent()) {
            EnergyHatchSize size = kind.energyHatchSize().get();
            this.storage = new LongEnergyStorage(size.capacity(), size.transfer(), this::markEnergyChanged);
        } else {
            ExtendedEnergyHatchSize size = kind.extendedEnergyHatchSize()
                    .orElseThrow(() -> new IllegalStateException("Energy hatch missing energy size: " + kind.id()));
            this.storage = new LongEnergyStorage(size.capacity(), size.transfer(), this::markEnergyChanged);
        }
    }

    public LongEnergyStorage getEnergyHandler(Direction side) {
        return storage;
    }

    public LongEnergyStorage energyStorage() {
        return storage;
    }

    @Override
    public LongEnergyStorage nativeEnergyStorage() {
        return storage;
    }

    @Override
    public CapabilitySnapshot capabilitySnapshot() {
        if (capabilitySnapshot == null) {
            capabilitySnapshot = new CapabilitySnapshot(kind().definition().bindings().stream()
                    .map(this::createCapability)
                    .toList(), List.of(new EnergyPersistenceFacet()));
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

    private void markEnergyChanged() {
        markAutoIOCacheDirty();
        notifyStorageChanged();
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

    private final class EnergyPersistenceFacet implements PersistenceFacet {
        @Override
        public String stateKey() {
            return "energy";
        }

        @Override
        public void save(CompoundTag output, HolderLookup.Provider registries) {
            output.putLong("amount", storage.getAmountAsLong());
        }

        @Override
        public void load(CompoundTag input, HolderLookup.Provider registries) {
            storage.setAmount(input.getLong("amount"));
        }
    }
}
