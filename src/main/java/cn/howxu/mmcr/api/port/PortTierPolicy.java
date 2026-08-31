package cn.howxu.mmcr.api.port;

import cn.howxu.mmcr.api.capability.type.CapabilityBinding;

/**
 * Decides whether a capability binding is supported by a port tier.
 *
 * @author howxu <dev@howxu.cn>
 */
@FunctionalInterface
public interface PortTierPolicy {
    boolean supports(CapabilityBinding binding, int tier);

    static PortTierPolicy always() {
        return (binding, tier) -> true;
    }

    static PortTierPolicy atLeast(int minimumTier) {
        if (minimumTier < 0) throw new IllegalArgumentException("minimumTier must be >= 0");
        return (binding, tier) -> tier >= minimumTier;
    }
}
