package cn.howxu.mmcr.compat.appliedflux.loaded;

import appeng.api.networking.ticking.TickRateModulation;
import cn.howxu.mmcr.compat.appliedflux.loaded.capability.FluxEnergyOutputCapability;
import cn.howxu.mmcr.compat.appliedflux.loaded.storage.FluxEnergyBuffer;
import org.jetbrains.annotations.Nullable;

/**
 * Shared, deterministic ticker behavior for AppFlux energy interface nodes.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluxEnergyTicker {
    private FluxEnergyTicker() {
    }

    public static TickRateModulation input(boolean active, int ticksSinceLastCall, FluxEnergyBuffer buffer,
                                            @Nullable FluxEnergyNetwork network) {
        if (!active) return TickRateModulation.SLEEP;
        for (int tick = 0; tick < Math.max(1, ticksSinceLastCall); tick++) buffer.advanceIdle();
        if (!buffer.isIdleReady()) {
            return buffer.idleExcess() > FluxEnergyBuffer.IDLE_SOFT_LIMIT
                    ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
        }
        if (network == null) return TickRateModulation.SLOWER;
        long returned = network.returnIdle(buffer);
        if (!buffer.isIdleReady()) return TickRateModulation.SLEEP;
        return returned > 0L ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }

    public static TickRateModulation output(boolean active, FluxEnergyBuffer pending,
                                             FluxEnergyOutputCapability capability,
                                             @Nullable FluxEnergyNetwork network) {
        if (!active || network == null) {
            capability.refreshAdmissionBudget(0L);
            return pending.amount() > 0L ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
        }
        long drained = network.drainPending(pending);
        capability.refreshAdmissionBudget(network.admissionCapacity());
        if (pending.amount() <= 0L) return TickRateModulation.SLEEP;
        return drained > 0L ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }
}
