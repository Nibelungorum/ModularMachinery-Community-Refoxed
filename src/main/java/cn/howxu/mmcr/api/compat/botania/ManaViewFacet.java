package cn.howxu.mmcr.api.compat.botania;

import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;

/**
 * Neutral view of real mana storage and its physical query identity.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface ManaViewFacet extends CapabilityFacet {
    long amount();

    long capacity();

    Object queryIdentity();
}
