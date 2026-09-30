package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;

/**
 * Plans built-in item requirements through native item handlers.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ItemRequirementHandler implements RequirementHandler<ItemRequirement> {
    @Override
    public ItemRequirement applyModifiers(ItemRequirement requirement, List<RecipeModifier> modifiers) {
        if (requirement.io() == RecipeModifier.IOType.INPUT) {
            int count = IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyItemInput(modifiers, requirement.count()));
            float consumeChance = IntegrationTypeHelper.applyItemInputChance(modifiers, requirement.consumeChance());
            return new ItemRequirement(requirement.io(), requirement.item(), count, requirement.stack(),
                    requirement.chance(), requirement.tags(), requirement.components(), consumeChance);
        }
        ItemStack stack = requirement.stack().copy();
        stack.setCount(IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyItemOutput(modifiers, stack.getCount())));
        return new ItemRequirement(requirement.io(), requirement.item(), requirement.count(), stack,
                IntegrationTypeHelper.applyItemOutputChance(modifiers, requirement.chance()), requirement.tags(),
                requirement.components(), requirement.consumeChance());
    }

    @Override
    public ItemRequirement applyLevelModifiers(ItemRequirement requirement, double energyMultiplier,
                                               double outputMultiplier) {
        if (requirement.io() != RecipeModifier.IOType.OUTPUT) return requirement;
        ItemStack stack = requirement.stack().copy();
        stack.setCount(levelOutputCount(stack.getCount(), outputMultiplier));
        return new ItemRequirement(requirement.io(), requirement.item(), requirement.count(), stack,
                requirement.chance(), requirement.tags(), requirement.components(), requirement.consumeChance());
    }

    @Override
    public boolean overlaps(ItemRequirement requirement, MachineRequirement other) {
        if (!(other instanceof ItemRequirement right)
                || requirement.io() != RecipeModifier.IOType.INPUT || right.io() != RecipeModifier.IOType.INPUT
                || requirement.item() == null || right.item() == null) return false;
        try {
            return Arrays.stream(requirement.item().getItems()).map(ItemStack::getItem)
                    .anyMatch(left -> Arrays.stream(right.item().getItems())
                            .map(ItemStack::getItem).anyMatch(left::equals));
        } catch (UnsupportedOperationException ignored) {
            return true;
        }
    }

    @Override
    public RequirementPlan plan(ItemRequirement requirement, List<MachineCapability> capabilities,
                                PlanningContext context) {
        if (requirement.io() == RecipeModifier.IOType.INPUT && requirement.item() == null) {
            return RequirementHandlerSupport.blockedPlan(requirement, context, BuiltinFailureReasons.MISSING_INPUT);
        }
        return NativeRequirementPlanning.item(requirement, capabilities, context);
    }

    @Override
    public List<ResourceWakeup> resourceWakeups(ItemRequirement requirement) {
        if (requirement.io() == RecipeModifier.IOType.INPUT) {
            return List.of(new ResourceWakeup(Set.of(BuiltinFailureReasons.MISSING_INPUT.id(),
                    BuiltinFailureReasons.PER_TICK.id()), WakeupReason.INPUT_AVAILABLE, itemMatcher(requirement)));
        }
        ItemStack stack = requirement.stack(null);
        return stack.isEmpty() ? List.of() : List.of(new ResourceWakeup(
                Set.of(BuiltinFailureReasons.MISSING_OUTPUT.id(), BuiltinFailureReasons.FINISH.id()),
                WakeupReason.OUTPUT_CAPACITY, resource -> resource instanceof ItemStack item
                        && ItemStack.isSameItemSameComponents(item, stack)));
    }

    private static int levelOutputCount(int original, double multiplier) {
        if (multiplier <= 0D) return 0;
        if (multiplier >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        int result = (int) Math.floor(original * multiplier);
        return original > 0 ? Math.max(1, result) : result;
    }

    private static Predicate<Object> itemMatcher(ItemRequirement requirement) {
        return resource -> resource instanceof ItemStack item && !item.isEmpty() && requirement.item() != null
                && requirement.item().test(item) && requirement.components().matches(item);
    }
}
