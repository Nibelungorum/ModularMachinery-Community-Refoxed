package cn.howxu.mmcr.compat.appliedflux.loaded;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.MEStorage;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.NativeCapabilityOperation;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.compat.appliedflux.loaded.capability.FluxEnergyInputCapability.ExactPrefetchBridge;
import cn.howxu.mmcr.compat.appliedflux.loaded.storage.FluxEnergyBuffer;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import java.util.Map;
import java.util.Optional;

/**
 * Adapts the AppFlux FE key in an AE2 storage service to MMCR's local energy caches.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluxEnergyNetwork implements ExactPrefetchBridge {
    private static final FluxKey FLUX_KEY = FluxKey.of(EnergyType.FE);

    private final IStorageService storageService;
    private final MEStorage storage;
    private final IActionSource source;
    private final EnergyStorage energy;

    public FluxEnergyNetwork(IStorageService storageService, IActionSource source) {
        this.storageService = storageService;
        this.storage = storageService == null ? null : storageService.getInventory();
        this.source = source;
        energy = storage == null ? null : new StorageAdapter();
    }

    /** Test-only constructor that keeps transaction behavior independent of a live grid. */
    public FluxEnergyNetwork(EnergyStorage energy) {
        storageService = null;
        storage = null;
        source = null;
        this.energy = energy;
    }

    @Override
    public Optional<CapabilityOperation> planExactExtract(long requestedAmount) {
        if (requestedAmount <= 0L || energy == null || energy.extract(requestedAmount, true) != requestedAmount) {
            return Optional.empty();
        }
        return Optional.of((NativeCapabilityOperation) () -> extractExactly(requestedAmount));
    }

    public long drainPending(FluxEnergyBuffer pending) {
        if (energy == null || pending.amount() <= 0L) return 0L;
        long accepted = energy.insert(pending.amount(), false);
        if (accepted <= 0L) return 0L;
        if (accepted > pending.amount()) throw new IllegalStateException("network accepted more energy than requested");
        if (pending.extract(accepted) != accepted) throw new IllegalStateException("pending energy changed during network drain");
        return accepted;
    }

    public long returnIdle(FluxEnergyBuffer buffer) {
        if (energy == null || !buffer.isIdleReady()) return 0L;
        long accepted = energy.insert(buffer.idleExcess(), false);
        if (accepted <= 0L) return 0L;
        if (accepted > buffer.idleExcess()) throw new IllegalStateException("network accepted more energy than requested");
        if (buffer.returnIdle(accepted) != accepted) throw new IllegalStateException("idle energy changed during network return");
        return accepted;
    }

    public long admissionCapacity() {
        return energy == null ? 0L : energy.insert(Long.MAX_VALUE, true);
    }

    public boolean available() {
        return energy != null;
    }

    private CapabilityResult extractExactly(long requestedAmount) {
        long simulated = energy.extract(requestedAmount, true);
        if (simulated != requestedAmount) return missingInput();
        long extracted = energy.extract(requestedAmount, false);
        if (extracted == requestedAmount) return CapabilityResult.successful();
        return missingInput();
    }

    private static CapabilityResult missingInput() {
        var energyType = BuiltinCapabilityDefinitions.ENERGY_TYPE.id();
        return CapabilityResult.failure(ExecutionStatus.blocked(energyType, energyType,
                FailureOccurrence.at(BuiltinFailureReasons.MISSING_INPUT, energyType,
                        FailurePhase.CAPABILITY_COMMIT, null, null, Map.of())));
    }

    /** Narrow storage seam used by unit tests and by the live MEStorage adapter. */
    public interface EnergyStorage {
        long extract(long requestedAmount, boolean simulate);

        long insert(long requestedAmount, boolean simulate);
    }

    private final class StorageAdapter implements EnergyStorage {
        @Override
        public long extract(long requestedAmount, boolean simulate) {
            return storage.extract(FLUX_KEY, requestedAmount, simulate ? Actionable.SIMULATE : Actionable.MODULATE, source);
        }

        @Override
        public long insert(long requestedAmount, boolean simulate) {
            return storage.insert(FLUX_KEY, requestedAmount, simulate ? Actionable.SIMULATE : Actionable.MODULATE, source);
        }
    }
}
