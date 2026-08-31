package cn.howxu.mmcr.api.capability.transfer;

import cn.howxu.mmcr.api.capability.CapabilityType;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Startup registry for capability-specific automatic transfer strategies.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class TransferStrategyRegistry {
    private static final Map<CapabilityType, TransferPolicy> POLICIES = new ConcurrentHashMap<>();
    private static volatile boolean FROZEN;

    private TransferStrategyRegistry() {
    }

    public static void register(CapabilityType type, TransferPolicy policy) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(policy, "policy");
        if (FROZEN) throw new IllegalStateException("Transfer strategy registry is frozen");
        if (POLICIES.putIfAbsent(type, policy) != null) {
            throw new IllegalArgumentException("Duplicate transfer policy: " + type.id());
        }
    }

    public static Optional<TransferPolicy> policyFor(CapabilityType type) {
        return type == null ? Optional.empty() : Optional.ofNullable(POLICIES.get(type));
    }

    public static void freeze() {
        FROZEN = true;
    }
}
