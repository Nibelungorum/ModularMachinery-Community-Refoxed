package cn.howxu.mmcr.api.capability.status;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import cn.howxu.mmcr.MMCR;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Describes the status of a capability operation.
 *
 * @param id the status ResourceLocation
 * @param severity the status severity
 * @param source the source that produced the status
 * @param failure structured failure details, or {@code null} for a non-failure status
 * @author howxu <dev@howxu.cn>
 */
public record ExecutionStatus(
        ResourceLocation id,
        StatusSeverity severity,
        ResourceLocation source,
        @Nullable FailureOccurrence failure) {

    /**
     * Compatibility boundary for existing serialized and producer status data.
     */
    public ExecutionStatus(ResourceLocation id, StatusSeverity severity, ResourceLocation source,
                           Map<String, String> details) {
        this(id, severity, source, legacyFailure(source, details));
    }

    public FailureReason reason() {
        return failure == null ? null : failure.reason();
    }

    public Map<String, String> details() {
        return failure == null ? Map.of() : failure.details();
    }

    public static ExecutionStatus blocked(ResourceLocation id, ResourceLocation source, FailureOccurrence failure) {
        return new ExecutionStatus(id, StatusSeverity.BLOCKED, source, failure);
    }

    private static @Nullable FailureOccurrence legacyFailure(ResourceLocation source, Map<String, String> details) {
        Objects.requireNonNull(details, "details");
        Map<String, String> copied = new HashMap<>(details);
        if (copied.isEmpty()) return null;

        String rawReason = copied.get("reason");
        if (rawReason == null || rawReason.isBlank()) {
            return FailureOccurrence.at(null, source, FailurePhase.UNKNOWN, null, null, copied);
        }

        FailureReason reason;
        try {
            ResourceLocation reasonId = rawReason.contains(":")
                    ? ResourceLocation.parse(rawReason)
                    : MMCR.id(rawReason);
            reason = FailureReasonRegistry.find(reasonId);
        } catch (IllegalArgumentException exception) {
            reason = null;
        }
        return FailureOccurrence.at(reason, source, FailurePhase.UNKNOWN, null, null, copied);
    }
}
