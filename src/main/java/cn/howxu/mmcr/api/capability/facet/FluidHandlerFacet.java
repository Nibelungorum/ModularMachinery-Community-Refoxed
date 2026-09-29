package cn.howxu.mmcr.api.capability.facet;

import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Exposes a native fluid handler for a machine capability.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface FluidHandlerFacet extends CapabilityFacet {
    IFluidHandler fluidHandler();
}
