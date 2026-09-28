package cn.howxu.mmcr.internal.sync;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.status.FailureReasonRegistry;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Converts the pre-Task 6 string failure fields at their persistence boundaries.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FailureStatusMigration {
    private static final Identifier CRAFTING_SOURCE = MMCR.id("crafting_runtime");

    private FailureStatusMigration() {
    }

    public static @Nullable ExecutionStatus craftingFailure(@Nullable String legacyReason,
                                                             @Nullable Identifier recipeId) {
        Resolved resolved = resolve(legacyReason);
        if (resolved == null) return null;
        Map<String, String> details = resolved.rawReasonId() == null
                ? Map.of() : Map.of("raw_reason_id", resolved.rawReasonId());
        return ExecutionStatus.blocked(CRAFTING_SOURCE, CRAFTING_SOURCE,
                FailureOccurrence.at(resolved.reason(), CRAFTING_SOURCE, resolved.phase(), recipeId, null, details));
    }

    public static @Nullable Identifier factoryReasonId(@Nullable String legacyReason) {
        Resolved resolved = resolve(legacyReason);
        if (resolved == null || BuiltinFailureReasons.UNKNOWN.id().equals(resolved.reason().id())) return null;
        return resolved.reason().id();
    }

    private static @Nullable Resolved resolve(@Nullable String legacyReason) {
        if (legacyReason == null || legacyReason.isEmpty()) return null;
        Resolved known = known(legacyReason);
        if (known != null) return known;

        Identifier reasonId;
        try {
            reasonId = legacyReason.contains(":")
                    ? Identifier.parse(legacyReason)
                    : MMCR.id(legacyReason);
        } catch (RuntimeException exception) {
            return null;
        }
        FailureReason registered = FailureReasonRegistry.find(reasonId);
        if (registered != null) return new Resolved(registered, phaseFor(registered), null);
        FailureReason builtin = builtin(reasonId);
        if (builtin != null) return new Resolved(builtin, phaseFor(builtin), null);
        return new Resolved(BuiltinFailureReasons.UNKNOWN, FailurePhase.UNKNOWN, reasonId.toString());
    }

    private static @Nullable Resolved known(String legacyReason) {
        return switch (legacyReason) {
            case "unknown" -> resolved(BuiltinFailureReasons.UNKNOWN, FailurePhase.UNKNOWN);
            case "module_connection" -> resolved(BuiltinFailureReasons.MODULE_CONNECTION, FailurePhase.RECIPE_START);
            case "no_output_capacity" -> resolved(BuiltinFailureReasons.MISSING_OUTPUT, FailurePhase.FINISH);
            case "insufficient_resource" -> resolved(BuiltinFailureReasons.MISSING_INPUT, FailurePhase.REQUIREMENT_PLAN);
            case "insufficient_energy" -> resolved(BuiltinFailureReasons.MISSING_ENERGY, FailurePhase.REQUIREMENT_PLAN);
            case "level_insufficient" -> resolved(BuiltinFailureReasons.LEVEL_INSUFFICIENT, FailurePhase.LEVEL_CHECK);
            case "version_invalidated" -> resolved(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RUNTIME);
            case "smart_interface_changed" -> resolved(BuiltinFailureReasons.SMART_INTERFACE_CHANGED, FailurePhase.RUNTIME);
            case "recipe_search", "recipe_search_exception" -> resolved(
                    legacyReason.equals("recipe_search")
                            ? BuiltinFailureReasons.RECIPE_SEARCH : BuiltinFailureReasons.RECIPE_SEARCH_EXCEPTION,
                    FailurePhase.RECIPE_SEARCH);
            case "recipe_load" -> resolved(BuiltinFailureReasons.RECIPE_LOAD, FailurePhase.RECIPE_LOAD);
            case "invalid_start", "start" -> resolved(BuiltinFailureReasons.RECIPE_START, FailurePhase.RECIPE_START);
            case "recipe_behavior" -> resolved(BuiltinFailureReasons.RECIPE_BEHAVIOR, FailurePhase.UNKNOWN);
            case "behavior_before_start" -> resolved(BuiltinFailureReasons.RECIPE_BEHAVIOR, FailurePhase.RECIPE_START);
            case "per_tick" -> resolved(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK);
            case "behavior_before_finish" -> resolved(BuiltinFailureReasons.BEHAVIOR_BEFORE_FINISH, FailurePhase.FINISH);
            case "behavior_before_finish_cancelled" -> resolved(
                    BuiltinFailureReasons.BEHAVIOR_BEFORE_FINISH_CANCELLED, FailurePhase.FINISH);
            case "invalid_outputs" -> resolved(BuiltinFailureReasons.INVALID_OUTPUTS, FailurePhase.FINISH);
            case "finish" -> resolved(BuiltinFailureReasons.FINISH, FailurePhase.FINISH);
            default -> null;
        };
    }

    private static Resolved resolved(FailureReason reason, FailurePhase phase) {
        return new Resolved(reason, phase, null);
    }

    private static FailurePhase phaseFor(FailureReason reason) {
        if (BuiltinFailureReasons.LEVEL_INSUFFICIENT.id().equals(reason.id())) return FailurePhase.LEVEL_CHECK;
        if (BuiltinFailureReasons.RECIPE_SEARCH.id().equals(reason.id())
                || BuiltinFailureReasons.RECIPE_SEARCH_EXCEPTION.id().equals(reason.id())) {
            return FailurePhase.RECIPE_SEARCH;
        }
        if (BuiltinFailureReasons.RECIPE_LOAD.id().equals(reason.id())) return FailurePhase.RECIPE_LOAD;
        if (BuiltinFailureReasons.RECIPE_START.id().equals(reason.id())) return FailurePhase.RECIPE_START;
        if (BuiltinFailureReasons.PER_TICK.id().equals(reason.id())) return FailurePhase.PER_TICK;
        if (BuiltinFailureReasons.FINISH.id().equals(reason.id())
                || BuiltinFailureReasons.MISSING_OUTPUT.id().equals(reason.id())) return FailurePhase.FINISH;
        if (BuiltinFailureReasons.VERSION_INVALIDATED.id().equals(reason.id())
                || BuiltinFailureReasons.SMART_INTERFACE_CHANGED.id().equals(reason.id())) return FailurePhase.RUNTIME;
        return FailurePhase.UNKNOWN;
    }

    private static @Nullable FailureReason builtin(Identifier id) {
        for (FailureReason reason : new FailureReason[] {
                BuiltinFailureReasons.UNKNOWN, BuiltinFailureReasons.MISSING_INPUT,
                BuiltinFailureReasons.MISSING_OUTPUT, BuiltinFailureReasons.MISSING_ENERGY,
                BuiltinFailureReasons.LEVEL_INSUFFICIENT, BuiltinFailureReasons.MODULE_CONNECTION,
                BuiltinFailureReasons.VERSION_INVALIDATED, BuiltinFailureReasons.SMART_INTERFACE_CHANGED,
                BuiltinFailureReasons.RECIPE_SEARCH, BuiltinFailureReasons.RECIPE_SEARCH_EXCEPTION,
                BuiltinFailureReasons.RECIPE_LOAD, BuiltinFailureReasons.RECIPE_BEHAVIOR,
                BuiltinFailureReasons.RECIPE_START, BuiltinFailureReasons.PER_TICK,
                BuiltinFailureReasons.FINISH, BuiltinFailureReasons.INVALID_OUTPUTS,
                BuiltinFailureReasons.BEHAVIOR_BEFORE_FINISH,
                BuiltinFailureReasons.BEHAVIOR_BEFORE_FINISH_CANCELLED}) {
            if (reason.id().equals(id)) return reason;
        }
        return null;
    }

    private record Resolved(FailureReason reason, FailurePhase phase, @Nullable String rawReasonId) {
    }
}
