package cn.howxu.mmcr.api.capability.tick;

import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Capability work planned for one tick phase.
 *
 * @author howxu <dev@howxu.cn>
 */
public record CapabilityTickResult(List<CapabilityOperation> operations, @Nullable ExecutionStatus failure,
                                   boolean stateChanged) {
    public CapabilityTickResult {
        operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
    }

    public static CapabilityTickResult empty() {
        return new CapabilityTickResult(List.of(), null, false);
    }
}
