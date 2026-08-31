package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.storage.FloatValueStorage;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;

import java.util.List;

/**
 * Plans built-in smart-interface requirements.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SmartInterfaceRequirementHandler implements RequirementHandler<SmartInterfaceRequirement> {
    @Override
    public RequirementPlan plan(SmartInterfaceRequirement requirement, List<MachineCapability> capabilities,
                                PlanningContext context) {
        for (MachineCapability capability : capabilities) {
            if (!(capability.storage() instanceof FloatValueStorage storage)) continue;
            if (requirement.io() == RecipeModifier.IOType.INPUT) {
                if (storage.value(requirement.interfaceType())
                        .filter(value -> value >= requirement.minValue() && value <= requirement.maxValue()).isPresent()) {
                    return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
                }
                continue;
            }
            if (storage.value(requirement.interfaceType()).isPresent()) {
                return RequirementHandlerSupport.deferredPlan(context, context.requestedParallelism(),
                        (parallelism, reservations) -> new RequirementPlan.OperationPlan(List.of(
                                capability.prepare(new CapabilityRequests.SmartValueRequest(
                                        capability.view().type(), capability.view().ioType(), parallelism,
                                        requirement.interfaceType(), requirement.minValue()))), null));
            }
        }
        return RequirementHandlerSupport.blockedPlan(requirement, context, "missing_smart_interface");
    }
}
