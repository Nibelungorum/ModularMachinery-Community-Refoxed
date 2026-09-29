package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Plans built-in fluid requirements through native fluid handlers.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluidRequirementHandler implements RequirementHandler<FluidRequirement> {
    @Override
    public FluidRequirement applyModifiers(FluidRequirement requirement, List<RecipeModifier> modifiers) {
        if (requirement.io() == RecipeModifier.IOType.INPUT) {
            return new FluidRequirement(requirement.io(), requirement.fluid(),
                    IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyFluidInput(modifiers, requirement.amount())),
                    requirement.stack(), IntegrationTypeHelper.applyFluidInputChance(modifiers, requirement.chance()),
                    requirement.tags(), IntegrationTypeHelper.applyFluidInputChance(modifiers, requirement.consumeChance()));
        }
        FluidStack stack = requirement.stack().copy();
        stack.setAmount(IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyFluidOutput(modifiers, stack.getAmount())));
        return new FluidRequirement(requirement.io(), requirement.fluid(), requirement.amount(), stack,
                IntegrationTypeHelper.applyFluidOutputChance(modifiers, requirement.chance()), requirement.tags(),
                requirement.consumeChance());
    }

    @Override
    public FluidRequirement applyLevelModifiers(FluidRequirement requirement, double energyMultiplier,
                                                double outputMultiplier) {
        if (requirement.io() != RecipeModifier.IOType.OUTPUT) return requirement;
        FluidStack stack = requirement.stack().copy();
        stack.setAmount(levelOutputAmount(stack.getAmount(), outputMultiplier));
        return new FluidRequirement(requirement.io(), requirement.fluid(), requirement.amount(), stack,
                requirement.chance(), requirement.tags(), requirement.consumeChance());
    }

    @Override
    public RequirementPlan plan(FluidRequirement requirement, List<MachineCapability> capabilities,
                                PlanningContext context) {
        return NativeRequirementPlanning.fluid(requirement, capabilities, context);
    }

    @Override
    public List<ResourceWakeup> resourceWakeups(FluidRequirement requirement) {
        Predicate<Object> matcher = resource -> resource instanceof FluidStack fluid && !fluid.isEmpty()
                && (requirement.io() == RecipeModifier.IOType.OUTPUT
                ? FluidStack.isSameFluidSameComponents(fluid, requirement.stack())
                : requirement.fluid() != null && requirement.fluid().test(fluid));
        return requirement.io() == RecipeModifier.IOType.INPUT
                ? List.of(new ResourceWakeup(Set.of(BuiltinFailureReasons.MISSING_INPUT.id(),
                BuiltinFailureReasons.PER_TICK.id()), WakeupReason.INPUT_AVAILABLE, matcher))
                : requirement.stack().isEmpty() ? List.of() : List.of(new ResourceWakeup(
                Set.of(BuiltinFailureReasons.MISSING_OUTPUT.id(), BuiltinFailureReasons.FINISH.id()),
                WakeupReason.OUTPUT_CAPACITY, matcher));
    }

    private static int levelOutputAmount(int original, double multiplier) {
        if (multiplier <= 0D) return 0;
        if (multiplier >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        int result = (int) Math.floor(original * multiplier);
        return original > 0 ? Math.max(1, result) : result;
    }
}
