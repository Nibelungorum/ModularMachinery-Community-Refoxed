package cn.howxu.mmcr.test.capability;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.NetworkParticipantFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Set;

/**
 * Network membership fixture exposing immutable snapshots after topology changes.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class TestNetworkParticipantFacet implements MachineCapability, NetworkParticipantFacet {
    private boolean attached;
    private long topologyVersion;
    private CapabilitySnapshot snapshot = new CapabilitySnapshot(List.of());
    private final CapabilityType type = new CapabilityType(Identifier.fromNamespaceAndPath("mmcr_test", "network"));
    private final CapabilityView view = new CapabilityView() {
        @Override
        public CapabilityType type() {
            return TestNetworkParticipantFacet.this.type();
        }

        @Override
        public IOType ioType() {
            return TestNetworkParticipantFacet.this.ioType();
        }

        @Override
        public Set<Class<? extends CapabilityFacet>> facets() {
            return Set.of(NetworkParticipantFacet.class);
        }
    };

    @Override
    public void attach() {
        if (!attached) {
            attached = true;
            topologyVersion++;
            snapshot = new CapabilitySnapshot(List.of(this));
        }
    }

    @Override
    public void detach() {
        if (attached) {
            attached = false;
            topologyVersion++;
            snapshot = new CapabilitySnapshot(List.of());
        }
    }

    public long topologyVersion() {
        return topologyVersion;
    }

    @Override
    public CapabilitySnapshot networkSnapshot() {
        return snapshot;
    }

    @Override
    public CapabilityType type() {
        return type;
    }

    @Override
    public IOType ioType() {
        return IOType.INPUT;
    }

    @Override
    public CapabilityView view() {
        return view;
    }

    @Override
    public CapabilityOperation prepare(CapabilityRequest request) {
        throw new UnsupportedOperationException("network participant has no scalar operation");
    }
}
