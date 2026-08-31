package cn.howxu.mmcr.api.capability.facet;

import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;

/**
 * Exposes a transaction-aware scalar exchange boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface ExchangeFacet extends CapabilityFacet {
    double potential();

    double capacity();

    double conductance();

    CapabilityOperation prepareExchange(double requested);
}
