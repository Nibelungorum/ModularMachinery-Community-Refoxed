package cn.howxu.mmcr.api.capability.facet;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;

/**
 * Exposes network membership without treating topology refresh as local storage.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface NetworkParticipantFacet extends CapabilityFacet {
    void attach();

    void detach();

    CapabilitySnapshot networkSnapshot();
}
