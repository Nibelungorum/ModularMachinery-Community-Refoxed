package cn.howxu.mmcr.api.capability.facet;

import cn.howxu.mmcr.api.capability.storage.CapabilityStorage;

/**
 * Exposes a typed backing store for scalar or named-value capability state.
 *
 * @param <S> backing storage type
 * @author howxu <dev@howxu.cn>
 */
public interface ValueFacet<S extends CapabilityStorage> extends CapabilityFacet {
    S storage();

    /**
     * Returns whether this storage has no state that must survive a world reload.
     */
    default boolean isStateless() {
        return false;
    }
}
