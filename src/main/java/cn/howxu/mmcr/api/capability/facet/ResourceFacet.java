package cn.howxu.mmcr.api.capability.facet;

import cn.howxu.mmcr.api.capability.storage.ResourceStorage;

/**
 * Exposes typed resource storage for a machine capability.
 *
 * @param <R> resource type
 * @author howxu <dev@howxu.cn>
 */
public interface ResourceFacet<R> extends ValueFacet<ResourceStorage<R>> {
    Class<R> resourceType();

    ResourceStorage<R> storage();

    /**
     * Returns whether item-like resources may exceed their ordinary stack size in this storage.
     */
    default boolean supportsLargeStacks() {
        return false;
    }
}
