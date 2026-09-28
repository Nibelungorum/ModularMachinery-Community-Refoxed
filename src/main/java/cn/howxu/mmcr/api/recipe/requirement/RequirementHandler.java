package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.MMCR;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Plans execution for one machine requirement type.
 *
 * @param <R> the requirement handled by this handler
 * @author howxu <dev@howxu.cn>
 */
public interface RequirementHandler<R extends MachineRequirement> {
    RequirementPlan plan(R requirement, List<MachineCapability> capabilities, PlanningContext context);

    /** Applies recipe modifiers to the handler-owned requirement representation. */
    default R applyModifiers(R requirement, List<RecipeModifier> modifiers) {
        return requirement;
    }

    /** Applies machine-level multipliers before ordinary recipe modifiers. */
    default R applyLevelModifiers(R requirement, double energyMultiplier, double outputMultiplier) {
        return requirement;
    }

    /** Reports input overlap without making recipe code depend on a concrete requirement type. */
    default boolean overlaps(R requirement, MachineRequirement other) {
        return false;
    }

    /**
     * Supplies resource wakeups for a failed requirement without exposing concrete requirement types to callers.
     */
    default List<ResourceWakeup> resourceWakeups(R requirement) {
        return List.of();
    }

    enum WakeupReason {
        INPUT_AVAILABLE,
        ENERGY_AVAILABLE,
        OUTPUT_CAPACITY
    }

    /**
     * Describes when a resource change can make a failed requirement eligible for another search.
     *
     * @param failureReasonIds failure reason IDs this matcher can resolve
     * @param reason generic resource notification category
     * @param matcher predicate for the changed resource
     */
    record ResourceWakeup(Set<Identifier> failureReasonIds, WakeupReason reason, Predicate<Object> matcher) {
        public ResourceWakeup {
            failureReasonIds = Set.copyOf(Objects.requireNonNull(failureReasonIds, "failureReasonIds"));
            if (failureReasonIds.isEmpty()) throw new IllegalArgumentException("failureReasonIds must not be empty");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(matcher, "matcher");
        }

        public boolean matches(@Nullable FailureReason failureReason) {
            return failureReason != null && failureReasonIds.contains(failureReason.id());
        }

        /**
         * Keeps the legacy recipe-search boundary working while its persisted failure is still a string.
         */
        public boolean matches(@Nullable String failureReason) {
            if (failureReason == null) return false;
            Identifier id;
            try {
                id = failureReason.contains(":") ? Identifier.parse(failureReason)
                        : MMCR.id(failureReason);
            } catch (IllegalArgumentException exception) {
                return false;
            }
            if (failureReasonIds.contains(id)) return true;
            return switch (failureReason) {
                case "insufficient_resource" -> failureReasonIds.contains(BuiltinFailureReasons.MISSING_INPUT.id())
                        || failureReasonIds.contains(BuiltinFailureReasons.MISSING_OUTPUT.id());
                case "insufficient_energy" -> failureReasonIds.contains(BuiltinFailureReasons.MISSING_ENERGY.id());
                case "no_output_capacity" -> failureReasonIds.contains(BuiltinFailureReasons.MISSING_OUTPUT.id());
                case "per_tick" -> failureReasonIds.contains(BuiltinFailureReasons.PER_TICK.id());
                case "finish" -> failureReasonIds.contains(BuiltinFailureReasons.FINISH.id());
                default -> false;
            };
        }
    }
}
