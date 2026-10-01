package cn.howxu.mmcr.api.network.view;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Objects;

/** Server-created public reference to an active machine network interface.
 * @author howxu <dev@howxu.cn>
 */
public final class NetworkInterfaceReference {
    private final cn.howxu.mmcr.api.network.NetworkInterfaceReference reference;

    NetworkInterfaceReference(cn.howxu.mmcr.api.network.NetworkInterfaceReference reference) {
        this.reference = Objects.requireNonNull(reference, "reference");
    }

    public BlockPos position() { return reference.position(); }

    public List<MachineReference> connections() {
        return reference.connections().stream().map(MachineReference::fromInternal).toList();
    }

    /** Internal bridge value for MMCR adapters. */
    public Object bridgeValue() { return reference; }
}
