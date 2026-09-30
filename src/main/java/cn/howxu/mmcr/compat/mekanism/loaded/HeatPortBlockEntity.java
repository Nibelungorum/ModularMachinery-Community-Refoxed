package cn.howxu.mmcr.compat.mekanism.loaded;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.facet.PersistenceFacet;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import mekanism.api.IContentsListener;
import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.heat.IHeatHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import mekanism.common.capabilities.heat.ITileHeatHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Base block entity for loaded Mekanism heat ports.
 *
 * @author howxu <dev@howxu.cn>
 */
public abstract class HeatPortBlockEntity extends IOPortBlockEntity implements ITileHeatHandler {
    private static final double OUTPUT_INVERSE_CONDUCTION = Double.MAX_VALUE;

    private final BasicHeatCapacitor heatCapacitor;
    private final IHeatHandler externalHeatHandler = new IHeatHandler() {
        @Override
        public int getHeatCapacitorCount() {
            return 1;
        }

        @Override
        public double getTemperature(int capacitor) {
            return heatCapacitor.getTemperature();
        }

        @Override
        public double getInverseConduction(int capacitor) {
            return ioType() == IOType.OUTPUT ? OUTPUT_INVERSE_CONDUCTION : heatCapacitor.getInverseConduction();
        }

        @Override
        public double getHeatCapacity(int capacitor) {
            return heatCapacitor.getHeatCapacity();
        }

        @Override
        public void handleHeat(int capacitor, double transfer) {
            if (ioType() == IOType.INPUT && transfer < 0D || ioType() == IOType.OUTPUT && transfer > 0D) return;
            heatCapacitor.handleHeat(transfer);
        }
    };
    private CapabilitySnapshot capabilitySnapshot;

    protected HeatPortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, IOPortKind kind) {
        super(type, pos, state);
        this.heatCapacitor = BasicHeatCapacitor.create(MekanismPortSizes.HEAT_CAPACITY,
                () -> HeatAPI.getAmbientTemp(getLevel(), getBlockPos()), this::markHeatChanged);
    }

    public static BasicHeatCapacitor heatCapacitor(Level level, BlockPos pos, IContentsListener listener) {
        return BasicHeatCapacitor.create(MekanismPortSizes.HEAT_CAPACITY,
                () -> HeatAPI.getAmbientTemp(level, pos), listener);
    }

    public BasicHeatCapacitor heatCapacitor() {
        return heatCapacitor;
    }

    public IHeatHandler heatHandler() {
        return externalHeatHandler;
    }

    public IHeatHandler externalHeatHandler() {
        return externalHeatHandler;
    }

    @Override
    public List<IHeatCapacitor> getHeatCapacitors(@Nullable Direction side) {
        if (side == null) return List.of(heatCapacitor);
        boolean exposed = kind().definition().bindings().stream()
                .filter(binding -> binding.type().id().equals(MekanismRecipeTypes.HEAT))
                .anyMatch(binding -> isNativeSideExposed(binding, side));
        return exposed ? List.of(heatCapacitor) : List.of();
    }

    @Override
    public @Nullable IHeatHandler getAdjacent(Direction side) {
        if (level == null) return null;
        return level.getCapability(Capabilities.HEAT, worldPosition.relative(side), side.getOpposite());
    }

    @Override
    public double getAmbientTemperature(Direction side) {
        return HeatAPI.getAmbientTemp(level, worldPosition.relative(side));
    }

    @Override
    public HeatAPI.HeatTransfer simulate() {
        double adjacent = ioType() == IOType.OUTPUT ? simulateAdjacent() : 0D;
        double environment = 0D;
        for (Direction side : Direction.values()) {
            List<IHeatCapacitor> capacitors = getHeatCapacitors(side);
            if (capacitors.isEmpty()) continue;
            IHeatCapacitor capacitor = capacitors.getFirst();
            double temperatureDifference = capacitor.getTemperature() - getAmbientTemperature(side);
            if (temperatureDifference <= 0D) continue;
            double inverseConduction = HeatAPI.AIR_INVERSE_COEFFICIENT
                    + capacitor.getInverseInsulation() + capacitor.getInverseConduction();
            double temperatureTransfer = temperatureDifference / inverseConduction;
            capacitor.handleHeat(-temperatureTransfer * capacitor.getHeatCapacity());
            environment += temperatureTransfer;
        }
        return new HeatAPI.HeatTransfer(adjacent, environment);
    }

    @Override
    protected void tick() {
        super.tick();
        if (level == null || level.isClientSide()) return;
        simulate();
        updateHeatCapacitors(null);
    }

    @Override
    public CapabilitySnapshot capabilitySnapshot() {
        if (capabilitySnapshot == null) {
            capabilitySnapshot = new CapabilitySnapshot(kind().definition().bindings().stream()
                    .map(this::createCapability)
                    .toList(), List.of(new HeatPersistenceFacet()));
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

    private void markHeatChanged() {
        markAutoIOCacheDirty();
        notifyStorageChanged();
        notifyControllerOfInputChange();
    }

    @Override
    public void onContentsChanged() {
        markHeatChanged();
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

    private final class HeatPersistenceFacet implements PersistenceFacet {
        @Override
        public String stateKey() {
            return "heat";
        }

        @Override
        public void save(CompoundTag output, HolderLookup.Provider registries) {
            output.putDouble("stored_heat", heatCapacitor.getHeat());
        }

        @Override
        public void load(CompoundTag input, HolderLookup.Provider registries) {
            if (input.contains("stored_heat")) heatCapacitor.setHeat(input.getDouble("stored_heat"));
        }
    }
}
