package cn.howxu.mmcr.api.capability.facet;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;

/**
 * Prepares opaque operations for capability requests.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface OperationFacet extends CapabilityFacet {
    CapabilityOperation prepareOperation(CapabilityRequest request);
}
