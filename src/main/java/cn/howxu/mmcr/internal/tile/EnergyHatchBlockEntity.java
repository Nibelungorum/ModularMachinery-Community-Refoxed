package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.facet.PersistenceFacet;
import cn.howxu.mmcr.internal.port.ExtendedEnergyHatchSize;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

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

    public EnergyHandler getEnergyHandler(Direction side) {
        return storage;
    }

    public LongEnergyStorage energyStorage() {
        return storage;
    }

    @Override
    public LongValueStorage getEnergyStorage() {
        return storage.storage();
    }

    @Override
    public CapabilitySnapshot capabilitySnapshot() {
        if (capabilitySnapshot == null) {
            capabilitySnapshot = new CapabilitySnapshot(kind().definition().bindings().stream()
                    .map(this::createCapability)
                    .toList(), java.util.List.of(new EnergyPersistenceFacet()));
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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        capabilitySnapshot().facets(PersistenceFacet.class)
                .forEach(facet -> facet.save(output.child(facet.stateKey())));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("energy").ifPresent(child -> capabilitySnapshot().facets(PersistenceFacet.class)
                .forEach(facet -> facet.load(child)));
    }

    private final class EnergyPersistenceFacet implements PersistenceFacet {
        @Override
        public String stateKey() {
            return "energy";
        }

        @Override
        public void save(ValueOutput output) {
            output.putLong("amount", storage.getAmountAsLong());
        }

        @Override
        public void load(ValueInput input) {
            storage.setAmount(input.getLong("amount").orElse(0L));
        }
    }
}
