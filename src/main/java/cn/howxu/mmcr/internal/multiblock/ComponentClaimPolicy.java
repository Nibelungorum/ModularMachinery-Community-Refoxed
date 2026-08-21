package cn.howxu.mmcr.internal.multiblock;

/**
 * Ownership behavior for a stateful multiblock component.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum ComponentClaimPolicy {
    EXCLUSIVE,
    SHARED_SERIALIZED,
    SHARED_CAPACITY
}
