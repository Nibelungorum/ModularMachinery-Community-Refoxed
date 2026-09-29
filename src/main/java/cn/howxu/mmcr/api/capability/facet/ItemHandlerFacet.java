package cn.howxu.mmcr.api.capability.facet;

import net.neoforged.neoforge.items.IItemHandler;

/**
 * Exposes a native item handler for a machine capability.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface ItemHandlerFacet extends CapabilityFacet {
    IItemHandler itemHandler();
}
