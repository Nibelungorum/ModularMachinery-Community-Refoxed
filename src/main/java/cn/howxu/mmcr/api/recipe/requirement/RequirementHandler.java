package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;

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
     * @param failureReasons failure reasons this matcher can resolve
     * @param reason generic resource notification category
     * @param matcher predicate for the changed resource
     */
    record ResourceWakeup(Set<String> failureReasons, WakeupReason reason, Predicate<Object> matcher) {
        public ResourceWakeup {
            failureReasons = Set.copyOf(Objects.requireNonNull(failureReasons, "failureReasons"));
            if (failureReasons.isEmpty()) throw new IllegalArgumentException("failureReasons must not be empty");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(matcher, "matcher");
        }

        public boolean matches(String failureReason) {
            return failureReasons.contains(failureReason);
        }
    }
}
