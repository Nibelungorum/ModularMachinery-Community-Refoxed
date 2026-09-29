package cn.howxu.mmcr.api.compat.mekanism;

import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;

/**
 * Mekanism-neutral read access to a heat capability.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface HeatViewFacet extends CapabilityFacet {
    double heat();

    double temperature();

    double heatCapacity();
}
