package cn.howxu.mmcr.test.capability;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.NetworkParticipantFacet;

import java.util.List;

/**
 * Network membership fixture exposing immutable snapshots after topology changes.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class TestNetworkParticipantFacet implements NetworkParticipantFacet {
    private boolean attached;
    private long topologyVersion;

    @Override
    public void attach() {
        if (!attached) {
            attached = true;
            topologyVersion++;
        }
    }

    @Override
    public void detach() {
        if (attached) {
            attached = false;
            topologyVersion++;
        }
    }

    public long topologyVersion() {
        return topologyVersion;
    }

    @Override
    public CapabilitySnapshot networkSnapshot() {
        return new CapabilitySnapshot(List.<MachineCapability>of());
    }
}
