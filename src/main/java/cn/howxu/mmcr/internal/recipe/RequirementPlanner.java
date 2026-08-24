package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                    new PlanningContext(context.requestedParallelism(), requirementIndexes.get(index),
                            context.allowPartialOutputs(), context.reservations()));
            if (!requirementPlan.successful()) return new PlanningResult(null, requirementPlan.failure());
            parallelism = Math.min(parallelism, requirementPlan.maxParallelism());
            plans.add(requirementPlan.preparedAt(context.requestedParallelism()));
        }
        if (parallelism <= 0) {
            return new PlanningResult(null, failure(requirements.isEmpty() ? null : requirements.getFirst()));
        }
        int selectedParallelism = 0;
        ExecutionStatus reservationFailure = null;
        for (int candidate = parallelism; candidate > 0; candidate--) {
            PlanningReservations reservations = context.reservations().copy();
            boolean reservationsAvailable = true;
            for (int index = 0; index < plans.size(); index++) {
                ExecutionStatus failure = plans.get(index).reserve(candidate, reservations);
                if (failure != null) {
                    reservationFailure = failure;
                    reservationsAvailable = false;
                    break;
                }
            }
            if (reservationsAvailable) {
                selectedParallelism = candidate;
                break;
            }
        }
        if (selectedParallelism <= 0) return new PlanningResult(null, reservationFailure);

        PlanningReservations materializationReservations = context.reservations().copy();
        List<RequirementPlan> materialized = new ArrayList<>(plans.size());
        Map<Integer, RecipeModifier.IOType> directions = new LinkedHashMap<>();
        for (int index = 0; index < plans.size(); index++) {
            RequirementPlan plan = plans.get(index);
            RequirementPlan resolved = plan.materialize(selectedParallelism, materializationReservations,
                    failure(requirements.get(index), "unsafe_operation_parallelism"));
            if (!resolved.successful()) return new PlanningResult(null, resolved.failure());
            materialized.add(resolved);
            directions.put(requirementIndexes.get(index), requirements.get(index).io());
        }
        return new PlanningResult(new CraftingPlan(materialized, selectedParallelism, directions), null);
    }

    private static List<MachineCapability> matchingCapabilities(MachineRequirement requirement,
                                                                  List<MachineCapability> capabilities) {
        CapabilityType type = new CapabilityType(requirement.type().id());
        IOType direction = IOType.valueOf(requirement.io().name());
        return capabilities.stream()
                .filter(capability -> type.equals(capability.view().type()))
                .filter(capability -> direction == capability.view().ioType())
                .filter(capability -> requirement.tags().isEmpty()
                        || requirement.tags().stream().anyMatch(capability.view()::matchesTag))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static RequirementHandler<MachineRequirement> handler(RequirementType<?> type) {
        RequirementHandler<?> handler = RequirementHandlerRegistry.handlerFor(type);
        if (handler == null) throw new IllegalArgumentException("No requirement handler for " + type.id());
        return (RequirementHandler<MachineRequirement>) handler;
    }

    private static @org.jetbrains.annotations.Nullable ExecutionStatus failure(MachineRequirement requirement) {
        return failure(requirement, null);
    }

    private static @org.jetbrains.annotations.Nullable ExecutionStatus failure(MachineRequirement requirement, String reason) {
        if (requirement == null) return null;
        return new ExecutionStatus(requirement.type().id(),
                cn.howxu.mmcr.api.capability.status.StatusSeverity.BLOCKED,
                requirement.type().id(), reason == null ? java.util.Map.of() : java.util.Map.of("reason", reason));
    }
}
