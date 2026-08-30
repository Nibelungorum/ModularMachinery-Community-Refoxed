package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import java.util.Objects;
import java.util.Map;
import java.util.Optional;

/**
 * Context supplied to a direct server-tick machine behavior.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class TickBehaviorContext extends MachineBehaviorContext {
    private final CapabilitySnapshot capabilitySnapshot;
    private final int factoryThreadCount;
    private final long parallelism;

    public TickBehaviorContext(MachineBehaviorContext base, CapabilitySnapshot snapshot) {
        this(base, snapshot, 1, 1L);
    }

    public TickBehaviorContext(MachineBehaviorContext base, CapabilitySnapshot snapshot,
                               int factoryThreadCount, long parallelism) {
        super(base.controller(), base.level(), base.controllerPos(), base.machineId(), base.gameTime(),
                base.screenText(), base.dataStorage(), base.ioView(), base.upgradeItems());
        capabilitySnapshot = Objects.requireNonNull(snapshot, "snapshot");
        if (factoryThreadCount < 1) throw new IllegalArgumentException("factoryThreadCount must be positive");
        if (parallelism < 1L) throw new IllegalArgumentException("parallelism must be positive");
        this.factoryThreadCount = factoryThreadCount;
        this.parallelism = parallelism;
    }

    public int factoryThreadCount() {
        return factoryThreadCount;
    }

    public long parallelism() {
        return parallelism;
    }

    public Optional<Float> smartInterfaceValue(String name) {
        return ioView().smartInterfaceValue(name);
    }

    public Map<String, Float> smartInterfaceValues() {
        return ioView().smartInterfaceValues();
    }

    public MachineIoPlan ioPlan() {
        return new MachineIoPlan(capabilitySnapshot);
    }
}
