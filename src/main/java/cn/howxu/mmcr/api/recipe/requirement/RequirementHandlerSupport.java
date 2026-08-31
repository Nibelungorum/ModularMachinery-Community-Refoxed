package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.OutputFit;
import cn.howxu.mmcr.api.capability.plan.OutputSimulation;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared mechanics for the built-in requirement handlers.
 *
 * @author howxu <dev@howxu.cn>
 */
final class RequirementHandlerSupport {
    private RequirementHandlerSupport() {
    }

    static ExecutionStatus blocked(MachineRequirement requirement, String reason) {
        return new ExecutionStatus(requirement.type().id(), StatusSeverity.BLOCKED,
                requirement.type().id(), Map.of("reason", reason));
    }

    static RequirementPlan blockedPlan(MachineRequirement requirement, PlanningContext context, String reason) {
        return new RequirementPlan(context.requirementIndex(), 0, List.of(), blocked(requirement, reason));
    }

    static RequirementPlan blockedOutputPlan(MachineRequirement requirement, PlanningContext context,
                                             String reason, long requested) {
        return RequirementPlan.withOutputSimulation(context.requirementIndex(), 0, List.of(),
                blocked(requirement, reason), new OutputSimulation(requested, 0L, OutputFit.NONE));
    }

    static RequirementPlan deferredPlan(PlanningContext context, long maxParallelism,
                                       RequirementPlan.OperationFactory factory) {
        return new RequirementPlan(context.requirementIndex(), maxParallelism, List.of(), null, factory);
    }

    static RequirementPlan deferredPlan(PlanningContext context, long maxParallelism,
                                       RequirementPlan.OperationFactory factory,
                                       RequirementPlan.ReservationFactory reservationFactory) {
        return new RequirementPlan(context.requirementIndex(), maxParallelism, List.of(), null,
                factory, reservationFactory);
    }

    static ResourceStorage<?> resourceStorage(MachineCapability capability, Class<?> resourceType) {
        return capability.storage() instanceof ResourceStorage<?> storage
                && storage.resourceType().equals(resourceType) ? storage : null;
    }

    static long scaled(long amount, long parallelism) {
        try {
            return Math.multiplyExact(amount, parallelism);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    static OutputSimulation outputSimulation(long requested, long accepted) {
        if (requested <= 0L) return null;
        OutputFit fit = accepted == 0L ? OutputFit.NONE
                : accepted == requested ? OutputFit.FULL : OutputFit.PARTIAL;
        return new OutputSimulation(requested, accepted, fit);
    }

    static RequirementPlan.ReservationFactory reservationFactory(RequirementPlan.OperationFactory operationFactory) {
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

    static <R> RequirementPlan.OperationPlan resourceOperations(
            Map<MachineCapability, List<CapabilityRequests.ResourceAction<R>>> actionMap,
            long parallelism, boolean materialize, OutputSimulation outputSimulation) {
        List<CapabilityOperation> operations = materialize
                ? actionMap.entrySet().stream()
                .map(entry -> entry.getKey().prepare(new CapabilityRequests.ResourceRequest<>(
                        entry.getKey().view().type(), entry.getKey().view().ioType(), parallelism, entry.getValue())))
                .toList()
                : List.of();
        return new RequirementPlan.OperationPlan(operations, null, outputSimulation);
    }

    static long saturatingAdd(long first, long second) {
        if (second > 0 && first > Long.MAX_VALUE - second) return Long.MAX_VALUE;
        return first + second;
    }

    static boolean shouldProduce(float chance) {
        return chance >= 1F || chance > 0F && Math.random() < chance;
    }

    static ConsumeProfile consumeProfile(float chance, long parallelism) {
        if (parallelism <= 1_024L) {
            boolean[] decisions = new boolean[(int) parallelism];
            for (int index = 0; index < decisions.length; index++) {
                decisions[index] = chance >= 1F || Math.random() < chance;
            }
            return new ConsumeProfile(decisions, chance);
        }
        return new ConsumeProfile(null, chance);
    }

    record ConsumeProfile(boolean[] decisions, float chance) {
        long consumedBatches(long parallelism) {
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
