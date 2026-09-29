package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.OutputFit;
import cn.howxu.mmcr.api.capability.plan.OutputSimulation;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.util.SaturatingLong;
import cn.howxu.mmcr.util.IOType;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared mechanics for the built-in requirement handlers.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RequirementHandlerSupport {
    private RequirementHandlerSupport() {
    }

    public static ExecutionStatus blocked(MachineRequirement requirement, PlanningContext context,
                                          FailureReason reason) {
        return blocked(requirement, context, reason, Map.of());
    }

    public static ExecutionStatus blocked(MachineRequirement requirement, PlanningContext context,
                                          FailureReason reason, Map<String, String> details) {
        FailureOccurrence occurrence = FailureOccurrence.at(reason, requirement.type().id(),
                FailurePhase.REQUIREMENT_PLAN, null, context.requirementIndex(), details);
        return ExecutionStatus.blocked(requirement.type().id(), requirement.type().id(), occurrence);
    }

    public static ExecutionStatus blocked(MachineRequirement requirement, FailureReason reason) {
        return blocked(requirement, reason, Map.of());
    }

    public static ExecutionStatus blocked(MachineRequirement requirement, FailureReason reason,
                                         Map<String, String> details) {
        FailureOccurrence occurrence = FailureOccurrence.at(reason, requirement.type().id(),
                FailurePhase.REQUIREMENT_PLAN, null, null, details);
        return ExecutionStatus.blocked(requirement.type().id(), requirement.type().id(), occurrence);
    }

    public static RequirementPlan blockedPlan(MachineRequirement requirement, PlanningContext context,
                                              FailureReason reason) {
        return blockedPlan(requirement, context, reason, Map.of());
    }

    public static RequirementPlan blockedPlan(MachineRequirement requirement, PlanningContext context,
                                              FailureReason reason, Map<String, String> details) {
        return new RequirementPlan(context.requirementIndex(), 0L, List.of(),
                blocked(requirement, context, reason, details));
    }

    public static RequirementPlan blockedOutputPlan(MachineRequirement requirement, PlanningContext context,
                                                    FailureReason reason, long requested) {
        return blockedOutputPlan(requirement, context, reason, requested, Map.of());
    }

    public static RequirementPlan blockedOutputPlan(MachineRequirement requirement, PlanningContext context,
                                                    FailureReason reason, long requested,
                                                    Map<String, String> details) {
        return RequirementPlan.withOutputSimulation(context.requirementIndex(), 0, List.of(),
                blocked(requirement, context, reason, details), new OutputSimulation(requested, 0L, OutputFit.NONE));
    }

    public static RequirementPlan deferredPlan(PlanningContext context, long maxParallelism,
                                               RequirementPlan.OperationFactory factory) {
        return new RequirementPlan(context.requirementIndex(), maxParallelism, List.of(), null, factory);
    }

    public static RequirementPlan deferredPlan(PlanningContext context, long maxParallelism,
                                               RequirementPlan.OperationFactory factory,
                                               RequirementPlan.ReservationFactory reservationFactory) {
        return new RequirementPlan(context.requirementIndex(), maxParallelism, List.of(), null,
                factory, reservationFactory);
    }

    public static long scaled(long amount, long parallelism) {
        return SaturatingLong.multiply(amount, parallelism);
    }

    public static List<MachineCapability> prioritizedOutputCapabilities(List<MachineCapability> capabilities) {
        return capabilities.stream()
                .sorted(Comparator.comparingInt(MachineCapability::outputPriority).reversed())
                .toList();
    }

    public static OutputSimulation outputSimulation(long requested, long accepted) {
        if (requested <= 0L) return null;
        OutputFit fit = accepted == 0L ? OutputFit.NONE
                : accepted == requested ? OutputFit.FULL : OutputFit.PARTIAL;
        return new OutputSimulation(requested, accepted, fit);
    }

    public static RequirementPlan.ReservationFactory reservationFactory(RequirementPlan.OperationFactory operationFactory) {
        return new RequirementPlan.ReservationFactory() {
            @Override
            public ExecutionStatus reserve(long parallelism, PlanningReservations reservations) {
                return operationFactory.create(parallelism, reservations).failure();
            }

            @Override
            public RequirementPlan.ReservationResult reserveResult(long parallelism,
                                                                    PlanningReservations reservations) {
                RequirementPlan.OperationPlan operationPlan = operationFactory.create(parallelism, reservations);
                return new RequirementPlan.ReservationResult(operationPlan.failure(),
                        operationPlan.outputSimulation());
            }
        };
    }

    public static long saturatingAdd(long first, long second) {
        return SaturatingLong.add(first, second);
    }

    public static boolean shouldProduce(float chance) {
        return chance >= 1F || chance > 0F && Math.random() < chance;
    }

    public static ConsumeProfile consumeProfile(float chance, long parallelism) {
        if (parallelism <= 1_024L) {
            boolean[] decisions = new boolean[(int) parallelism];
            for (int index = 0; index < decisions.length; index++) {
                decisions[index] = chance >= 1F || Math.random() < chance;
            }
            return new ConsumeProfile(decisions, chance);
        }
        return new ConsumeProfile(null, chance);
    }

    public record ConsumeProfile(boolean[] decisions, float chance) {
        public ConsumeProfile {
            decisions = decisions == null ? null : decisions.clone();
        }
        public boolean[] decisions() {
            return decisions == null ? null : decisions.clone();
        }
        public long consumedBatches(long parallelism) {
            if (decisions == null) return Math.round(parallelism * (double) chance);
            long consumed = 0L;
            int limit = (int) Math.min(parallelism, decisions.length);
            for (int index = 0; index < limit; index++) {
                if (decisions[index]) consumed++;
            }
            return consumed;
        }
    }

}
