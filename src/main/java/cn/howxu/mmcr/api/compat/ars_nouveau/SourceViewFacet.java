package cn.howxu.mmcr.api.compat.ars_nouveau;

import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;

/**
 * Neutral view of real source storage and its physical query identity.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface SourceViewFacet extends CapabilityFacet {
    long amount();

    long capacity();

    Object queryIdentity();
}
