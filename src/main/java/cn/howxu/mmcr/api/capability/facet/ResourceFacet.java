package cn.howxu.mmcr.api.capability.facet;

import cn.howxu.mmcr.api.capability.storage.ResourceStorage;

/**
 * Exposes typed resource storage for a machine capability.
 *
 * @param <R> resource type
 * @author howxu <dev@howxu.cn>
 */
public interface ResourceFacet<R> extends CapabilityFacet {
    Class<R> resourceType();

    ResourceStorage<R> storage();
}
