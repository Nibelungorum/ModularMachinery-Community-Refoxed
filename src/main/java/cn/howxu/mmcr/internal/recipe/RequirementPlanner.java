package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.CraftingPlan;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.capability.plan.OutputSimulation;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.compat.mekanism.MekanismPortFamilies;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.util.IOType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves requirements through the handler registry and capability protocol.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RequirementPlanner {
    public PlanningResult plan(List<MachineRequirement> requirements,
                               List<MachineCapability> capabilities,
                               PlanningContext context) {
        List<Integer> requirementIndexes = new ArrayList<>(requirements == null ? 0 : requirements.size());
        if (requirements != null) {
            for (int index = 0; index < requirements.size(); index++) requirementIndexes.add(index);
        }
        return plan(requirements, capabilities, context,
                requirementIndexes);
    }

    public PlanningResult plan(List<MachineRequirement> requirements,
                               List<MachineCapability> capabilities,
                               PlanningContext context,
                               List<Integer> requirementIndexes) {
        return plan(requirements, capabilities, context, requirementIndexes, false);
    }

    /** Plans an already active batch without searching for a smaller candidate. */
    public PlanningResult planExact(List<MachineRequirement> requirements,
                                    List<MachineCapability> capabilities,
                                    PlanningContext context,
                                    List<Integer> requirementIndexes) {
        return plan(requirements, capabilities, context, requirementIndexes, true);
    }

    private PlanningResult plan(List<MachineRequirement> requirements,
                                List<MachineCapability> capabilities,
                                PlanningContext context,
                                List<Integer> requirementIndexes, boolean exactParallelism) {
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
        long parallelism = context.requestedParallelism();
        ExecutionStatus parallelismFailure = null;
        Integer parallelismFailureIndex = null;
        for (int index = 0; index < requirements.size(); index++) {
            MachineRequirement requirement = requirements.get(index);
            RequirementHandler<MachineRequirement> handler = handler(requirement.type());
            List<MachineCapability> matching = matchingCapabilities(requirement, capabilities);
            RequirementPlan requirementPlan = handler.plan(requirement, matching,
                    new PlanningContext(context.requestedParallelism(), requirementIndexes.get(index),
                            context.allowPartialOutputs(), context.reservations(), context.outputPolicies(),
                            context.reservationOwner()));
            if (!requirementPlan.successful()) {
                return failed(requirementPlan.failure(), requirementIndexes.get(index), plans, requirementPlan);
            }
            if (parallelismFailure == null && requirementPlan.maxParallelism() <= 0) {
                parallelismFailure = failure(requirement, requirementIndexes.get(index));
                parallelismFailureIndex = requirementIndexes.get(index);
            }
            parallelism = Math.min(parallelism, requirementPlan.maxParallelism());
            plans.add(requirementPlan.preparedAt(context.requestedParallelism()));
        }
        if (parallelism <= 0) {
            return new PlanningResult(null, parallelismFailure, outputSimulations(plans), parallelismFailureIndex);
        }
        long lower = 0L;
        long upper = parallelism;
        ReservationAttempt lastFailure = null;
        if (exactParallelism) {
            ReservationAttempt attempt = reserveCandidate(plans, requirementIndexes,
                    context.requestedParallelism(), context.reservations());
            if (!attempt.successful()) {
                return new PlanningResult(null, attempt.failure(), attempt.outputSimulations(), attempt.failureIndex());
            }
            for (int index = 0; index < plans.size(); index++) {
                if (plans.get(index).maxParallelism() < context.requestedParallelism()) {
                    return new PlanningResult(null, failure(requirements.get(index), requirementIndexes.get(index)),
                            attempt.outputSimulations(), requirementIndexes.get(index));
                }
            }
            lower = context.requestedParallelism();
        }
        while (lower < upper) {
            long distance = upper - lower;
            long candidate = lower + (distance >>> 1) + (distance & 1L);
            ReservationAttempt attempt = reserveCandidate(plans, requirementIndexes, candidate,
                    context.reservations());
            if (attempt.successful()) {
                lower = candidate;
            } else {
                lastFailure = attempt;
                upper = candidate - 1L;
            }
        }
        if (lower <= 0L) {
            return new PlanningResult(null, lastFailure == null ? null : lastFailure.failure(),
                    lastFailure == null ? List.of() : lastFailure.outputSimulations(),
                    lastFailure == null ? null : lastFailure.failureIndex());
        }
        long selectedParallelism = lower;

        PlanningReservations materializationReservations = context.reservations().copy();
        List<RequirementPlan> materialized = new ArrayList<>(plans.size());
        Map<Integer, RecipeModifier.IOType> directions = new LinkedHashMap<>();
        for (int index = 0; index < plans.size(); index++) {
            RequirementPlan plan = plans.get(index);
            RequirementPlan resolved = plan.materialize(selectedParallelism, materializationReservations,
                    failure(requirements.get(index), requirementIndexes.get(index),
                            BuiltinFailureReasons.UNSAFE_OPERATION_PARALLELISM));
            if (!resolved.successful()) {
                return failed(resolved.failure(), requirementIndexes.get(index), materialized, resolved);
            }
            materialized.add(resolved);
            directions.put(requirementIndexes.get(index), requirements.get(index).io());
        }
        return new PlanningResult(new CraftingPlan(materialized, selectedParallelism, directions), null);
    }

    private static ReservationAttempt reserveCandidate(List<RequirementPlan> plans,
                                                        List<Integer> requirementIndexes,
                                                        long candidate,
                                                        PlanningReservations baseReservations) {
        PlanningReservations reservations = baseReservations.copy();
        List<OutputSimulation> candidateOutputSimulations = new ArrayList<>(outputSimulations(plans));
        for (int index = 0; index < plans.size(); index++) {
            RequirementPlan.ReservationResult reservation = plans.get(index)
                    .reservationResult(candidate, reservations);
            if (reservation.outputSimulation() != null) {
                candidateOutputSimulations.add(reservation.outputSimulation());
            }
            ExecutionStatus failure = reservation.failure();
            if (failure != null) {
                return new ReservationAttempt(false, failure, requirementIndexes.get(index),
                        candidateOutputSimulations);
            }
        }
        return new ReservationAttempt(true, null, null, candidateOutputSimulations);
    }

    private record ReservationAttempt(boolean successful, ExecutionStatus failure, Integer failureIndex,
                                      List<OutputSimulation> outputSimulations) {
    }

    private static PlanningResult failed(ExecutionStatus failure, int failureRequirementIndex,
                                         List<RequirementPlan> completed, RequirementPlan failed) {
        List<RequirementPlan> plans = new ArrayList<>(completed);
        plans.add(failed);
        return new PlanningResult(null, failure, outputSimulations(plans), failureRequirementIndex);
    }

    private static List<OutputSimulation> outputSimulations(List<RequirementPlan> plans) {
        return plans.stream()
                .map(RequirementPlan::outputSimulation)
                .filter(Objects::nonNull)
                .toList();
    }

    public static List<MachineCapability> matchingCapabilities(MachineRequirement requirement,
                                                                  List<MachineCapability> capabilities) {
        var capabilityIds = requirement.type().capabilityIds();
        IOType direction = IOType.valueOf(requirement.io().name());
        return capabilities.stream()
                .filter(capability -> capabilityIds.contains(capability.view().type().id())
                        || MekanismPortFamilies.HEAT_TEMPERATURE.equals(requirement.type().id())
                        && MekanismPortFamilies.HEAT.equals(capability.view().type().id()))
                .filter(capability -> capability.view().directions().supports(direction))
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

    private static @Nullable ExecutionStatus failure(MachineRequirement requirement, int requirementIndex) {
        if (requirement == null) return null;
        FailureReason reason = requirement.io() == RecipeModifier.IOType.OUTPUT
                ? BuiltinFailureReasons.MISSING_OUTPUT
                : requirement instanceof EnergyRequirement
                ? BuiltinFailureReasons.MISSING_ENERGY : BuiltinFailureReasons.MISSING_INPUT;
        return failure(requirement, requirementIndex, reason);
    }

    private static @Nullable ExecutionStatus failure(MachineRequirement requirement, int requirementIndex,
                                                     FailureReason reason) {
        if (requirement == null) return null;
        FailureOccurrence occurrence = FailureOccurrence.at(reason, requirement.type().id(),
                FailurePhase.REQUIREMENT_PLAN, null, requirementIndex, Map.of());
        return ExecutionStatus.blocked(requirement.type().id(), requirement.type().id(), occurrence);
    }
}
