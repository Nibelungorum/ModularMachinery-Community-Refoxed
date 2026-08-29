package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import java.util.Objects;

/**
 * Context supplied to a direct server-tick machine behavior.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class TickBehaviorContext extends MachineBehaviorContext {
    private final CapabilitySnapshot capabilitySnapshot;

    public TickBehaviorContext(MachineBehaviorContext base, CapabilitySnapshot snapshot) {
        super(base.controller(), base.level(), base.controllerPos(), base.machineId(), base.gameTime(),
                base.screenText(), base.dataStorages(), base.ioView());
        capabilitySnapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    public MachineIoPlan ioPlan() {
        return new MachineIoPlan(capabilitySnapshot);
    }
}
