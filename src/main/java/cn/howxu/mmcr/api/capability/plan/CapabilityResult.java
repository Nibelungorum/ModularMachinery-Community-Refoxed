package cn.howxu.mmcr.api.capability.plan;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;

import java.util.Objects;

/**
 * Reports whether a capability operation succeeded and, when it failed, why.
 *
 * @param success whether the operation succeeded
 * @param status the failure status, or {@code null} for a successful operation
 * @author howxu <dev@howxu.cn>
 */
public record CapabilityResult(boolean success, ExecutionStatus status) {
    /**
     * Creates a successful result.
     *
     * @return a successful result
     */
    public static CapabilityResult successful() {
        return new CapabilityResult(true, null);
    }

    /**
     * Creates a failed result.
     *
     * @param status the failure status
     * @return a failed result
     */
    public static CapabilityResult failure(ExecutionStatus status) {
        return new CapabilityResult(false, Objects.requireNonNull(status));
    }
}
