package cn.howxu.mmcr.compat.mekanism.loaded;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.facet.PersistenceFacet;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.chemical.attribute.ChemicalAttributeValidator;
import mekanism.api.radiation.IRadiationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Base block entity for loaded Mekanism chemical ports.
 *
 * @author howxu <dev@howxu.cn>
 */
public abstract class ChemicalPortBlockEntity extends IOPortBlockEntity {
    private final boolean radioactive;
    private final IChemicalTank chemicalTank;
    private CapabilitySnapshot capabilitySnapshot;

    protected ChemicalPortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                      IOPortKind kind, long capacity, boolean radioactive) {
        super(type, pos, state);
        this.radioactive = radioactive;
        this.chemicalTank = chemicalTank(capacity, radioactive, kind.ioType(), this::markChemicalChanged);
    }

    public boolean isRadioactive() {
        return radioactive;
    }

    private static IChemicalTank chemicalTank(long capacity, boolean radioactive, IOType ioType,
                                              IContentsListener listener) {
        return BasicChemicalTank.createModern(capacity,
                (stack, automation) -> automation != AutomationType.EXTERNAL || ioType != IOType.INPUT,
                (stack, automation) -> automation != AutomationType.EXTERNAL || ioType != IOType.OUTPUT,
                stack -> stack.isRadioactive() == radioactive, ChemicalAttributeValidator.ALWAYS_ALLOW, listener);
    }

    public IChemicalTank chemicalTank() {
        return chemicalTank;
    }

    public IChemicalTank chemicalHandler(Direction side) {
        return chemicalTank;
    }

    @Override
    public CapabilitySnapshot capabilitySnapshot() {
        if (capabilitySnapshot == null) {
            capabilitySnapshot = new CapabilitySnapshot(kind().definition().bindings().stream()
                    .map(this::createCapability)
                    .toList(), List.of(new ChemicalPersistenceFacet()));
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
    public void onBlockRemoved() {
        if (level != null && !level.isClientSide() && IRadiationManager.INSTANCE.isRadiationEnabled()) {
            IRadiationManager.INSTANCE.dumpRadiation(level, worldPosition, chemicalTank.getStack());
        }
        super.onBlockRemoved();
    }

    private void markChemicalChanged() {
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

    private final class ChemicalPersistenceFacet implements PersistenceFacet {
        @Override
        public String stateKey() {
            return "chemical";
        }

        @Override
        public void save(CompoundTag output, HolderLookup.Provider registries) {
            output.merge(chemicalTank.serializeNBT(registries));
        }

        @Override
        public void load(CompoundTag input, HolderLookup.Provider registries) {
            chemicalTank.deserializeNBT(registries, input);
        }
    }
}
