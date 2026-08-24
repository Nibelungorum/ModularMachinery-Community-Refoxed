package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.CraftingPlan;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.util.IOType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Resolves requirements through the handler registry and capability protocol.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RequirementPlanner {
    public PlanningResult plan(List<MachineRequirement> requirements,
                               List<MachineCapability> capabilities,
                               PlanningContext context) {
        return plan(requirements, capabilities, context,
                IntStream.range(0, requirements == null ? 0 : requirements.size()).boxed().toList());
    }

    public PlanningResult plan(List<MachineRequirement> requirements,
                               List<MachineCapability> capabilities,
                               PlanningContext context,
                               List<Integer> requirementIndexes) {
        if (requirements == null || capabilities == null || context == null) {
            throw new IllegalArgumentException("requirements, capabilities and context must not be null");
        }
        if (requirementIndexes == null || requirementIndexes.size() != requirements.size()) {
            throw new IllegalArgumentException("requirement indexes must match requirements");
        }
        if (context.requestedParallelism() <= 0) {
            throw new IllegalArgumentException("requested parallelism must be positive");
        }

        List<RequirementPlan> plans = new ArrayList<>(requirements.size());
        int parallelism = context.requestedParallelism();
        for (int index = 0; index < requirements.size(); index++) {
            MachineRequirement requirement = requirements.get(index);
            RequirementHandler<MachineRequirement> handler = handler(requirement.type());
            List<MachineCapability> matching = matchingCapabilities(requirement, capabilities);
            RequirementPlan requirementPlan = handler.plan(requirement, matching,
                    new PlanningContext(parallelism, requirementIndexes.get(index), context.allowPartialOutputs()));
            if (!requirementPlan.successful()) return new PlanningResult(null, requirementPlan.failure());
            parallelism = Math.min(parallelism, requirementPlan.maxParallelism());
            plans.add(requirementPlan);
        }
        if (parallelism <= 0) {
            return new PlanningResult(null, failure(requirements.isEmpty() ? null : requirements.getFirst()));
        }
        return new PlanningResult(new CraftingPlan(plans, parallelism), null);
    }

    private static List<MachineCapability> matchingCapabilities(MachineRequirement requirement,
                                                                  List<MachineCapability> capabilities) {
        CapabilityType type = new CapabilityType(requirement.type().id());
        IOType direction = IOType.valueOf(requirement.io().name());
        return capabilities.stream()
                .filter(capability -> type.equals(capability.view().type()))
                .filter(capability -> direction == capability.view().ioType())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static RequirementHandler<MachineRequirement> handler(RequirementType<?> type) {
        RequirementHandler<?> handler = RequirementHandlerRegistry.handlerFor(type);
        if (handler == null) throw new IllegalArgumentException("No requirement handler for " + type.id());
        return (RequirementHandler<MachineRequirement>) handler;
    }

    private static @org.jetbrains.annotations.Nullable ExecutionStatus failure(MachineRequirement requirement) {
        if (requirement == null) return null;
        return new ExecutionStatus(requirement.type().id(),
                cn.howxu.mmcr.api.capability.status.StatusSeverity.BLOCKED,
                requirement.type().id(), java.util.Map.of());
    }
}
