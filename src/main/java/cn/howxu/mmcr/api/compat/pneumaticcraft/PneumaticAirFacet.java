package cn.howxu.mmcr.api.compat.pneumaticcraft;

import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;

/** Neutral access to one physical air handler, without exposing native mutable storage.
 * @author howxu <dev@howxu.cn>
 */
public interface PneumaticAirFacet extends CapabilityFacet {
    Object queryIdentity();

    AirState state();

    CapabilityResult validate(long amount, boolean insert, float minPressure);

    CapabilityResult apply(long amount, boolean insert, float minPressure);
}
