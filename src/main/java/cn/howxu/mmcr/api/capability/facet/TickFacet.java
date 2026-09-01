package cn.howxu.mmcr.api.capability.facet;

import cn.howxu.mmcr.api.capability.tick.CapabilityTickContext;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickResult;

/**
 * Optionally plans capability work for a server tick phase. Implementations must only prepare operations;
 * they must not mutate capability storage while planning.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface TickFacet extends CapabilityFacet {
    CapabilityTickResult plan(CapabilityTickContext context);
}
