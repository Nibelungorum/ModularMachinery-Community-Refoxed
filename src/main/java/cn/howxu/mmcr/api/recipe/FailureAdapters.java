package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Adapts legacy recipe failure values to the typed failure model.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FailureAdapters {
    private FailureAdapters() {
    }

    public static FailureReason reason(RequirementFailure.Kind kind) {
        return switch (Objects.requireNonNull(kind, "kind")) {
            case MISSING_INPUT -> BuiltinFailureReasons.MISSING_INPUT;
            case MISSING_OUTPUT -> BuiltinFailureReasons.MISSING_OUTPUT;
            case MISSING_ENERGY -> BuiltinFailureReasons.MISSING_ENERGY;
            case COMMIT_LOST_INPUT -> BuiltinFailureReasons.COMMIT_LOST_INPUT;
            case COMMIT_LOST_OUTPUT -> BuiltinFailureReasons.COMMIT_LOST_OUTPUT;
            case TAG_MISMATCH -> BuiltinFailureReasons.TAG_MISMATCH;
        };
    }

    public static FailureOccurrence occurrence(RequirementFailure failure,
                                                ResourceLocation source,
                                                FailurePhase phase) {
        Objects.requireNonNull(failure, "failure");
        Map<String, String> details = new LinkedHashMap<>();
        details.put("required", Long.toString(failure.required()));
        details.put("available", Long.toString(failure.available()));
        details.put("short_amount", Long.toString(failure.shortAmount()));
        return FailureOccurrence.at(reason(failure.kind()), source, phase, null,
                failure.requirementIndex(), details);
    }

    public static FailureOccurrence legacyMessage(String message,
                                                   ResourceLocation source,
                                                   FailurePhase phase) {
        return FailureOccurrence.at(BuiltinFailureReasons.UNKNOWN, source, phase,
                null, null, Map.of("legacy_message", Objects.requireNonNull(message, "message")));
    }
}
