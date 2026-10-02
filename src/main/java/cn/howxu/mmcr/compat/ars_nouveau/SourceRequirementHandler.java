package cn.howxu.mmcr.compat.ars_nouveau;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.compat.ars_nouveau.SourceViewFacet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import cn.howxu.mmcr.util.IOType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Allocates source through shared native reservations at the final recipe parallelism.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SourceRequirementHandler implements RequirementHandler<SourceRequirement> {
    @Override
    public RequirementPlan plan(SourceRequirement requirement, List<MachineCapability> capabilities,
                                PlanningContext context) {
        boolean insert = requirement.io() == RecipeModifier.IOType.OUTPUT;
        boolean partial = insert && context.outputPolicy() == OutputPolicy.ALLOW_PARTIAL;
        List<MachineCapability> ordered = insert
                ? RequirementHandlerSupport.prioritizedOutputCapabilities(capabilities) : capabilities;
        long totalAvailable = 0L;
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (MachineCapability capability : ordered) {
            SourceViewFacet source = capability.facet(SourceViewFacet.class).orElse(null);
            if (source == null || !seen.add(source.queryIdentity())) continue;
            totalAvailable = RequirementHandlerSupport.saturatingAdd(totalAvailable,
                    available(source, context.reservations(), insert));
        }
        long maximum = partial ? context.requestedParallelism()
                : Math.min(context.requestedParallelism(), totalAvailable / requirement.amount());
        if (maximum <= 0L) {
            long requested = RequirementHandlerSupport.scaled(requirement.amount(), context.requestedParallelism());
            Map<String, String> details = details(requested, totalAvailable);
            return insert ? RequirementHandlerSupport.blockedOutputPlan(requirement, context,
                    SourceFailureReasons.OUTPUT_BLOCKED, requested, details)
                    : RequirementHandlerSupport.blockedPlan(requirement, context,
                            SourceFailureReasons.INPUT_MISSING, details);
        }
        return RequirementHandlerSupport.deferredPlan(context, maximum,
                (parallelism, reservations) -> allocate(requirement, ordered, context, parallelism,
                        insert, partial, reservations, true),
                RequirementHandlerSupport.reservationFactory((parallelism, reservations) -> allocate(
                        requirement, ordered, context, parallelism, insert, partial, reservations, false)));
    }

    @Override
    public List<ResourceWakeup> resourceWakeups(SourceRequirement requirement) {
        boolean output = requirement.io() == RecipeModifier.IOType.OUTPUT;
        return List.of(new ResourceWakeup(Set.of((output ? SourceFailureReasons.OUTPUT_BLOCKED
                : SourceFailureReasons.INPUT_MISSING).id()),
                output ? WakeupReason.OUTPUT_CAPACITY : WakeupReason.INPUT_AVAILABLE,
                ArsSourceIds.SOURCE::equals));
    }

    private long available(SourceViewFacet source, PlanningReservations reservations, boolean insert) {
        long amount = reservations.nativeAmount(source.queryIdentity(), 0, source.amount());
        return insert ? Math.max(0L, source.capacity() - amount) : amount;
    }

    private boolean reserve(SourceViewFacet source, long amount, boolean insert, PlanningReservations reservations) {
        Object storedKey = source.amount() == 0L ? null : ArsSourceIds.SOURCE;
        return insert
                ? reservations.reserveNativeInsert(source.queryIdentity(), 0, ArsSourceIds.SOURCE,
                        storedKey, source.amount(), source.capacity(), amount)
                : reservations.reserveNativeExtract(source.queryIdentity(), 0, ArsSourceIds.SOURCE,
                        storedKey, source.amount(), amount);
    }

    private RequirementPlan.OperationPlan allocate(SourceRequirement requirement, List<MachineCapability> ordered,
                                                    PlanningContext context, long parallelism, boolean insert,
                                                    boolean partial, PlanningReservations reservations,
                                                    boolean createOperations) {
        long requested = RequirementHandlerSupport.scaled(requirement.amount(), parallelism);
        long remaining = requested;
        List<CapabilityOperation> operations = new ArrayList<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (MachineCapability capability : ordered) {
            SourceViewFacet source = capability.facet(SourceViewFacet.class).orElse(null);
            if (source == null || !seen.add(source.queryIdentity())) continue;
            long allocated = Math.min(remaining, available(source, reservations, insert));
            if (allocated <= 0L) continue;
            if (!reserve(source, allocated, insert, reservations)) {
                return new RequirementPlan.OperationPlan(List.of(),
                        blocked(requirement, context, requested, requested - remaining));
            }
            if (createOperations) operations.add(capability.prepare(new CapabilityRequests.ValueRequest(
                    ArsSourceIds.TYPE, insert ? IOType.OUTPUT : IOType.INPUT, parallelism, allocated, insert)));
            remaining -= allocated;
            if (remaining == 0L) break;
        }
        if (remaining > 0L && !partial) {
            return new RequirementPlan.OperationPlan(List.of(),
                    blocked(requirement, context, requested, requested - remaining));
        }
        return new RequirementPlan.OperationPlan(operations, null,
                insert ? RequirementHandlerSupport.outputSimulation(requested, requested - remaining) : null);
    }

    private ExecutionStatus blocked(SourceRequirement requirement, PlanningContext context,
                                    long required, long available) {
        return RequirementHandlerSupport.blocked(requirement, context,
                requirement.io() == RecipeModifier.IOType.OUTPUT
                        ? SourceFailureReasons.OUTPUT_BLOCKED : SourceFailureReasons.INPUT_MISSING,
                details(required, available));
    }

    private Map<String, String> details(long required, long available) {
        return Map.of("required", Long.toString(required), "available", Long.toString(Math.max(0L, available)),
                "shortfall", Long.toString(Math.max(0L, required - available)));
    }
}
