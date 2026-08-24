package cn.howxu.mmcr.api.capability;

import java.util.List;

/**
 * An immutable snapshot of the capabilities hosted by a machine.
 *
 * @param capabilities the capabilities in this snapshot
 * @author howxu <dev@howxu.cn>
 */
public record CapabilitySnapshot(List<MachineCapability> capabilities) {
    public CapabilitySnapshot {
        capabilities = List.copyOf(capabilities);
    }
}
