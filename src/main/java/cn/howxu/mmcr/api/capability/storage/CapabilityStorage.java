package cn.howxu.mmcr.api.capability.storage;

/**
 * Marker for storage protocols exposed by machine capabilities.
 * Consumers should obtain a typed storage through the capability facet that declares it.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface CapabilityStorage {
    /**
     * Returns the stable content representation used when publishing capability state.
     * Mutable storage implementations must include all content that affects capability behavior.
     */
    Object contentFingerprint();
}
