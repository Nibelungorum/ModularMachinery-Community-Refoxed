package cn.howxu.mmcr.compat.appliedflux.loaded.tile;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.core.settings.TickRates;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.compat.appliedflux.loaded.FluxEnergyNetwork;
import cn.howxu.mmcr.compat.appliedflux.loaded.FluxEnergyTicker;
import cn.howxu.mmcr.compat.appliedflux.loaded.capability.FluxEnergyInputCapability;
import cn.howxu.mmcr.compat.appliedflux.loaded.storage.FluxEnergyBuffer;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.util.IOType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * AE2 input port that prefetches exact AppFlux energy into a persistent local cache.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluxEnergyInputInterfaceBlockEntity extends FluxInterfaceBlockEntity {
    private final FluxEnergyBuffer buffer = new FluxEnergyBuffer(this::onEnergyChanged);
    private final FluxEnergyInputCapability capability = new FluxEnergyInputCapability(buffer,
            "appflux_energy_input", requested -> {
                FluxEnergyNetwork network = network();
                return network == null ? java.util.Optional.empty() : network.planExactExtract(requested);
            }, () -> network() != null);
    private final CapabilitySnapshot capabilitySnapshot = new CapabilitySnapshot(List.of(capability));
    private final InputTicker ticker = new InputTicker();
    private Level alertedLevel;
    private IGrid alertedGrid;
    private IGridNode alertedNode;
    private long alertedTick;
    private long wakeGeneration;

    public FluxEnergyInputInterfaceBlockEntity(BlockPos pos, BlockState state, IOPortKind kind) {
        super(pos, state, kind);
        mainNode.addService(IGridTickable.class, ticker);
    }

    @Override
    public IOType ioType() {
        return IOType.INPUT;
    }

    @Override
    public CapabilitySnapshot capabilitySnapshot() {
        return capabilitySnapshot;
    }

    @Override
    public LongValueStorage getEnergyStorage() {
        return buffer.storage();
    }

    @Override
    protected FluxEnergyBuffer energyBuffer() {
        return buffer;
    }

    @Override
    protected void onNetworkChanged() {
        resetPendingWake();
        wakeNode();
    }

    private void onEnergyChanged() {
        notifyStorageChanged();
        notifyControllerOfInputChange();
        wakeForEnergyChange();
    }

    private void wakeForEnergyChange() {
        Level currentLevel = getLevel();
        if (currentLevel == null) {
            wakeNode();
            return;
        }
        long gameTime = currentLevel.getGameTime();
        mainNode.ifPresent((grid, node) -> {
            if (alertedLevel == currentLevel && alertedTick == gameTime && alertedGrid == grid && alertedNode == node) return;
            long generation = wakeGeneration;
            if (grid.getTickManager().alertDevice(node) && generation == wakeGeneration && getLevel() == currentLevel) {
                alertedLevel = currentLevel;
                alertedTick = gameTime;
                alertedGrid = grid;
                alertedNode = node;
            }
        });
    }

    @Override
    protected void resetPendingWake() {
        wakeGeneration++;
        alertedLevel = null;
        alertedGrid = null;
        alertedNode = null;
    }

    private final class InputTicker implements IGridTickable {
        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(TickRates.Interface, buffer.idleExcess() <= FluxEnergyBuffer.IDLE_SOFT_LIMIT);
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            resetPendingWake();
            return FluxEnergyTicker.input(node.isActive(), ticksSinceLastCall, buffer, network());
        }
    }
}
