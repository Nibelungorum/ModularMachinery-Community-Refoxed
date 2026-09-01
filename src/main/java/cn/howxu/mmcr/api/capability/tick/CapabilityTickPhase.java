package cn.howxu.mmcr.api.capability.tick;

/**
 * The fixed lifecycle phases for capability tick work.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum CapabilityTickPhase {
    BEFORE_RECIPE,
    AFTER_INPUTS,
    AFTER_RECIPE,
    IDLE
}
