package cn.howxu.mmcr.api.capability;

import java.util.List;
import java.util.Objects;

/**
 * An immutable snapshot of the capabilities hosted by a machine.
 *
 * @param capabilities the capabilities in this snapshot
 * @author howxu <dev@howxu.cn>
 */
public record CapabilitySnapshot(List<MachineCapability> capabilities) {
    public CapabilitySnapshot {
        capabilities = List.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
    }
}
