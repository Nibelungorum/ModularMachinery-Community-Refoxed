package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.internal.recipe.InputValidationOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.compat.botania.ManaViewFacet;
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
 * Allocates mana through shared native reservations at the final recipe parallelism.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ManaRequirementHandler implements RequirementHandler<ManaRequirement> {
    @Override
    public RequirementPlan plan(ManaRequirement requirement, List<MachineCapability> capabilities,
                                PlanningContext context) {
        boolean insert = requirement.io() == RecipeModifier.IOType.OUTPUT;
        if (insert && !RequirementHandlerSupport.shouldProduce(requirement.chance(), context)) {
            return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
        }
        RequirementHandlerSupport.ConsumeProfile consumption = insert ? null
                : RequirementHandlerSupport.consumeProfile(requirement.consumeChance(), context.requestedParallelism(), context);
        boolean partial = insert && context.outputPolicy() == OutputPolicy.ALLOW_PARTIAL;
        List<MachineCapability> ordered = insert
                ? RequirementHandlerSupport.prioritizedOutputCapabilities(capabilities) : capabilities;
        long totalAvailable = 0L;
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (MachineCapability capability : ordered) {
            ManaViewFacet mana = capability.facet(ManaViewFacet.class).orElse(null);
            if (mana == null || !seen.add(mana.queryIdentity())) continue;
            totalAvailable = RequirementHandlerSupport.saturatingAdd(totalAvailable,
                    available(mana, context.reservations(), insert));
        }
        long maximum = partial ? context.requestedParallelism()
                : Math.min(context.requestedParallelism(), totalAvailable / requirement.amount());
        if (maximum <= 0L) {
            long requested = RequirementHandlerSupport.scaled(requirement.amount(), context.requestedParallelism());
            Map<String, String> details = details(requested, totalAvailable);
            return insert ? RequirementHandlerSupport.blockedOutputPlan(requirement, context,
                    ManaFailureReasons.OUTPUT_BLOCKED, requested, details)
                    : RequirementHandlerSupport.blockedPlan(requirement, context,
                            ManaFailureReasons.INPUT_MISSING, details);
        }
        return RequirementHandlerSupport.deferredPlan(context, maximum,
                (parallelism, reservations) -> allocate(requirement, ordered, context, parallelism,
                        insert, partial, reservations, true, consumption),
                RequirementHandlerSupport.reservationFactory((parallelism, reservations) -> allocate(
                        requirement, ordered, context, parallelism, insert, partial, reservations, false, consumption)));
    }

    @Override
    public List<ResourceWakeup> resourceWakeups(ManaRequirement requirement) {
        boolean output = requirement.io() == RecipeModifier.IOType.OUTPUT;
        return List.of(new ResourceWakeup(Set.of((output ? ManaFailureReasons.OUTPUT_BLOCKED
                : ManaFailureReasons.INPUT_MISSING).id()),
                output ? WakeupReason.OUTPUT_CAPACITY : WakeupReason.INPUT_AVAILABLE,
                BotaniaManaIds.MANA::equals));
    }

    private long available(ManaViewFacet mana, PlanningReservations reservations, boolean insert) {
        long amount = reservations.nativeAmount(mana.queryIdentity(), 0, mana.amount());
        return insert ? Math.max(0L, mana.capacity() - amount) : amount;
    }

    private boolean reserve(ManaViewFacet mana, long amount, boolean insert, PlanningReservations reservations) {
        Object storedKey = mana.amount() == 0L ? null : BotaniaManaIds.MANA;
        return insert
                ? reservations.reserveNativeInsert(mana.queryIdentity(), 0, BotaniaManaIds.MANA,
                        storedKey, mana.amount(), mana.capacity(), amount)
                : reservations.reserveNativeExtract(mana.queryIdentity(), 0, BotaniaManaIds.MANA,
                        storedKey, mana.amount(), amount);
    }

    private RequirementPlan.OperationPlan allocate(ManaRequirement requirement, List<MachineCapability> ordered,
                                                    PlanningContext context, long parallelism, boolean insert,
                                                    boolean partial, PlanningReservations reservations,
                                                    boolean createOperations,
                                                    RequirementHandlerSupport.ConsumeProfile consumption) {
        long requested = RequirementHandlerSupport.scaled(requirement.amount(), parallelism);
        if (!insert) {
            // A retained batch still has to match the full input before reserving only its consumed share.
            long totalAvailable = 0L;
            Set<Object> identities = Collections.newSetFromMap(new IdentityHashMap<>());
            for (MachineCapability capability : ordered) {
                ManaViewFacet mana = capability.facet(ManaViewFacet.class).orElse(null);
                if (mana != null && identities.add(mana.queryIdentity())) {
                    totalAvailable = RequirementHandlerSupport.saturatingAdd(totalAvailable,
                            available(mana, reservations, false));
                }
            }
            if (totalAvailable < requested) {
                return new RequirementPlan.OperationPlan(List.of(), blocked(requirement, context, requested, totalAvailable));
            }
            long consumedBatches = requirement.consumeChance() == 1F ? parallelism : consumption.consumedBatches(parallelism);
            requested = RequirementHandlerSupport.scaled(requirement.amount(), consumedBatches);
        }
        long remaining = requested;
        List<CapabilityOperation> operations = new ArrayList<>();
        if (!insert && createOperations && requirement.consumeChance() < 1F) {
            operations.add(inputValidation(requirement, ordered, context,
                    RequirementHandlerSupport.scaled(requirement.amount(), parallelism)));
        }
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (MachineCapability capability : ordered) {
            ManaViewFacet mana = capability.facet(ManaViewFacet.class).orElse(null);
            if (mana == null || !seen.add(mana.queryIdentity())) continue;
            long allocated = Math.min(remaining, available(mana, reservations, insert));
            if (allocated <= 0L) continue;
            if (!reserve(mana, allocated, insert, reservations)) {
                return new RequirementPlan.OperationPlan(List.of(),
                        blocked(requirement, context, requested, requested - remaining));
            }
            if (createOperations) operations.add(capability.prepare(new CapabilityRequests.ValueRequest(
                    BotaniaManaIds.TYPE, insert ? IOType.OUTPUT : IOType.INPUT, parallelism, allocated, insert)));
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

    private CapabilityOperation inputValidation(ManaRequirement requirement, List<MachineCapability> capabilities,
                                                PlanningContext context, long required) {
        return new InputValidationOperation(() -> {
                long available = 0L;
                Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
                for (MachineCapability capability : capabilities) {
                    ManaViewFacet mana = capability.facet(ManaViewFacet.class).orElse(null);
                    if (mana != null && seen.add(mana.queryIdentity())) {
                        available = RequirementHandlerSupport.saturatingAdd(available, mana.amount());
                    }
                }
                return available >= required ? CapabilityResult.successful()
                        : CapabilityResult.failure(ExecutionStatus.blocked(requirement.type().id(), requirement.type().id(),
                                FailureOccurrence.at(ManaFailureReasons.INPUT_MISSING, requirement.type().id(),
                                        FailurePhase.CAPABILITY_COMMIT, null, context.requirementIndex(),
                                        details(required, available))));
        });
    }

    private ExecutionStatus blocked(ManaRequirement requirement, PlanningContext context,
                                    long required, long available) {
        return RequirementHandlerSupport.blocked(requirement, context,
                requirement.io() == RecipeModifier.IOType.OUTPUT
                        ? ManaFailureReasons.OUTPUT_BLOCKED : ManaFailureReasons.INPUT_MISSING,
                details(required, available));
    }

    private Map<String, String> details(long required, long available) {
        return Map.of("required", Long.toString(required), "available", Long.toString(Math.max(0L, available)),
                "shortfall", Long.toString(Math.max(0L, required - available)));
    }
}
