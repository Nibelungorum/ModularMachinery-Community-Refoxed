package cn.howxu.mmcr.compat.appliedflux.loaded.tile;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.core.settings.TickRates;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.compat.appliedflux.loaded.FluxEnergyNetwork;
import cn.howxu.mmcr.compat.appliedflux.loaded.FluxEnergyTicker;
import cn.howxu.mmcr.compat.appliedflux.loaded.capability.FluxEnergyOutputCapability;
import cn.howxu.mmcr.compat.appliedflux.loaded.storage.FluxEnergyBuffer;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.util.IOType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * AE2 output port that retains recipe energy until the AppFlux network accepts it.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluxEnergyOutputInterfaceBlockEntity extends FluxInterfaceBlockEntity {
    private final FluxEnergyBuffer pending = new FluxEnergyBuffer(this::onEnergyChanged);
    private final FluxEnergyOutputCapability capability = new FluxEnergyOutputCapability(pending);
    private final CapabilitySnapshot capabilitySnapshot = new CapabilitySnapshot(List.of(capability));
    private final OutputTicker ticker = new OutputTicker();

    public FluxEnergyOutputInterfaceBlockEntity(BlockPos pos, BlockState state, IOPortKind kind) {
        super(pos, state, kind);
        mainNode.addService(IGridTickable.class, ticker);
    }

    @Override
    public IOType ioType() {
        return IOType.OUTPUT;
    }

    @Override
    public CapabilitySnapshot capabilitySnapshot() {
        return capabilitySnapshot;
    }

    public LongValueStorage getEnergyStorage() {
        return pending.storage();
    }

    @Override
    protected FluxEnergyBuffer energyBuffer() {
        return pending;
    }

    @Override
    protected void onNetworkChanged() {
        if (!mainNode.isActive()) capability.refreshAdmissionBudget(0L);
        wakeNode();
    }

    private void onEnergyChanged() {
        notifyStorageChanged();
        wakeNode();
    }

    private final class OutputTicker implements IGridTickable {
        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(TickRates.Interface, pending.amount() <= 0L);
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            return FluxEnergyTicker.output(node.isActive(), pending, capability, network());
        }
    }
}
