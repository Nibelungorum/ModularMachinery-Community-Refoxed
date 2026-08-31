package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

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

    /** Projects a built-in input to the legacy accessor shape when one exists. */
    default MachineIngredient legacyInput(R requirement) {
        return null;
    }

    /** Projects an item output requirement to the legacy accessor shape when one exists. */
    default ItemStack legacyItemOutput(R requirement) {
        return null;
    }

    /** Projects a fluid output requirement to the legacy accessor shape when one exists. */
    default FluidStack legacyFluidOutput(R requirement) {
        return null;
    }

    /** Projects an energy output requirement to the legacy accessor shape when one exists. */
    default Integer legacyEnergyOutput(R requirement) {
        return null;
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
