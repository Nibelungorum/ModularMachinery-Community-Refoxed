package cn.howxu.mmcr.api.capability.facet;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;

/**
 * Exposes scalar operations without imposing a value representation or unit.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface ScalarFacet extends CapabilityFacet {
    CapabilityOperation prepareScalar(CapabilityRequest request);
}
