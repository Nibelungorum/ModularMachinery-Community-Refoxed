package cn.howxu.mmcr.api.capability.facet;

import cn.howxu.mmcr.api.capability.tick.CapabilityTickContext;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickResult;

/**
 * Optionally plans capability work for a server tick phase.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface TickFacet extends CapabilityFacet {
    CapabilityTickResult plan(CapabilityTickContext context);
}
