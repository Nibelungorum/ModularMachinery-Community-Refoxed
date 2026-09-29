package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.EnergyOutputAdmissionFacet;
import cn.howxu.mmcr.api.capability.facet.EnergyStorageFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plans built-in energy requirements and owns their resource wakeups.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class EnergyRequirementHandler implements RequirementHandler<EnergyRequirement> {
    @Override
    public EnergyRequirement applyModifiers(EnergyRequirement requirement, List<RecipeModifier> modifiers) {
        return new EnergyRequirement(requirement.io(),
                IntegrationTypeHelper.applyEnergy(modifiers, requirement.fePerTick()),
                requirement.tags());
    }

    @Override
    public EnergyRequirement applyLevelModifiers(EnergyRequirement requirement, double energyMultiplier,
                                                 double outputMultiplier) {
        return new EnergyRequirement(requirement.io(), floorNonNegative(requirement.fePerTick() * energyMultiplier),
                requirement.tags());
    }

    private static long floorNonNegative(double value) {
        if (Double.isNaN(value) || value <= 0D) return 0L;
        return value >= Long.MAX_VALUE || Double.isInfinite(value)
                ? Long.MAX_VALUE : (long) Math.floor(value);
    }

    @Override
    public RequirementPlan plan(EnergyRequirement requirement, List<MachineCapability> capabilities,
                                PlanningContext context) {
        if (requirement.fePerTick() <= 0) {
            return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
        }
        if (capabilities.stream().anyMatch(capability -> capability.facet(EnergyStorageFacet.class).isPresent())) {
            return NativeRequirementPlanning.energy(requirement, capabilities, context);
        }
        boolean insert = requirement.io() == RecipeModifier.IOType.OUTPUT;
        List<MachineCapability> plannedCapabilities = insert
                ? RequirementHandlerSupport.prioritizedOutputCapabilities(capabilities) : capabilities;
        IOType direction = IOType.valueOf(requirement.io().name());
        boolean allowPartialOutput = insert && context.outputPolicy() == OutputPolicy.ALLOW_PARTIAL;
        long maximum = energyMaximum(requirement.fePerTick(), insert, plannedCapabilities,
                context.requestedParallelism(), allowPartialOutput, context.reservations());
        if (maximum <= 0) {
            return insert
                    ? RequirementHandlerSupport.blockedOutputPlan(requirement, context,
                    BuiltinFailureReasons.MISSING_OUTPUT,
                    requestedAmount(requirement, context.requestedParallelism()),
                    Map.of("required", Long.toString(requestedAmount(requirement, context.requestedParallelism())),
                            "available", "0", "shortfall",
                            Long.toString(requestedAmount(requirement, context.requestedParallelism()))))
                    : RequirementHandlerSupport.blockedPlan(requirement, context, BuiltinFailureReasons.MISSING_ENERGY,
                    Map.of("required", Long.toString(RequirementHandlerSupport.scaled(
                            requirement.fePerTick(), context.requestedParallelism())), "available", "0"));
        }
        return RequirementHandlerSupport.deferredPlan(context, maximum,
                (parallelism, reservations) -> planOperations(requirement, plannedCapabilities, parallelism,
                        context, reservations, insert, direction, allowPartialOutput, true),
                RequirementHandlerSupport.reservationFactory((parallelism, reservations) -> planOperations(
                        requirement, plannedCapabilities, parallelism, context, reservations, insert, direction,
                        allowPartialOutput, false)));
    }

    @Override
    public List<ResourceWakeup> resourceWakeups(EnergyRequirement requirement) {
        CapabilityType type = new CapabilityType(requirement.type().id());
        if (requirement.io() == RecipeModifier.IOType.INPUT) {
            return List.of(new ResourceWakeup(Set.of(BuiltinFailureReasons.MISSING_ENERGY.id()),
                    WakeupReason.ENERGY_AVAILABLE,
                    type::equals));
        }
        return List.of(new ResourceWakeup(Set.of(BuiltinFailureReasons.MISSING_OUTPUT.id()),
                WakeupReason.OUTPUT_CAPACITY,
                type::equals));
    }

    private static RequirementPlan.OperationPlan planOperations(EnergyRequirement requirement,
                                                                 List<MachineCapability> capabilities,
                                                                 long parallelism,
                                                                 PlanningContext context,
                                                                 PlanningReservations reservations,
                                                                 boolean insert,
                                                                 IOType direction,
                                                                  boolean allowPartialOutput,
                                                                  boolean materialize) {
        List<CapabilityOperation> operations = new ArrayList<>();
        long requested = insert ? requestedAmount(requirement, parallelism) : 0L;
        long required = RequirementHandlerSupport.scaled(requirement.fePerTick(), parallelism);
        List<EnergyAction> actions = reserveEnergy(required, parallelism, insert, capabilities, reservations,
                materialize, operations);
        long accepted = energyAmount(actions);
        if (accepted < required && (!allowPartialOutput || !insert)) {
            FailureReason reason = insert ? BuiltinFailureReasons.MISSING_OUTPUT
                    : BuiltinFailureReasons.MISSING_ENERGY;
            return new RequirementPlan.OperationPlan(List.of(), RequirementHandlerSupport.blocked(requirement,
                    context, reason, Map.of("required", Long.toString(required),
                            "available", Long.toString(accepted), "shortfall",
                            Long.toString(required - accepted))),
                    RequirementHandlerSupport.outputSimulation(requested, accepted));
        }
        if (materialize) {
            for (EnergyAction action : actions) {
                if (action.admissionOperation()) continue;
                operations.add(action.capability().prepare(new CapabilityRequests.ValueRequest(
                        action.capability().view().type(), direction,
                        parallelism, action.amount(), insert)));
            }
        }
        if (insert && accepted == 0L) {
            return new RequirementPlan.OperationPlan(List.of(),
                    RequirementHandlerSupport.blocked(requirement, context, BuiltinFailureReasons.MISSING_OUTPUT,
                            Map.of("required", Long.toString(required), "available", "0", "shortfall",
                                    Long.toString(required))),
                    RequirementHandlerSupport.outputSimulation(requested, accepted));
        }
        return new RequirementPlan.OperationPlan(operations, null,
                RequirementHandlerSupport.outputSimulation(requested, accepted));
    }

    private static long energyMaximum(long perBatch, boolean insert, List<MachineCapability> capabilities,
                                      long requested, boolean allowPartialOutput,
                                      PlanningReservations reservations) {
        if (insert && allowPartialOutput) return hasEnergyCapacity(capabilities, reservations) ? requested : 0;
        long lower = 0L;
        long upper = requested;
        while (lower < upper) {
            long distance = upper - lower;
            long candidate = lower + (distance >>> 1) + (distance & 1L);
            if (canReserveEnergyBatches(perBatch, candidate, insert, capabilities, reservations)) lower = candidate;
            else upper = candidate - 1L;
        }
        if (insert && lower == 0 && hasEnergyCapacity(capabilities, reservations)) return 1;
        return lower;
    }

    private static boolean hasEnergyCapacity(List<MachineCapability> capabilities,
                                             PlanningReservations reservations) {
        for (MachineCapability capability : capabilities) {
            EnergyOutputAdmissionFacet admission = outputAdmission(capability);
            if (admission != null) {
                if (admission.outputCapacity(reservations) > 0L) return true;
                continue;
            }
            LongValueStorage storage = energyStorage(capability);
            if (storage != null
                    && storage.transferLimit() > 0L
                    && reservations.valueAvailable(storage, true) > 0L) return true;
        }
        return false;
    }

    private static boolean canReserveEnergyBatches(long perBatch, long batches, boolean insert,
                                                   List<MachineCapability> capabilities,
                                                   PlanningReservations reservations) {
        long required = RequirementHandlerSupport.scaled(perBatch, batches);
        long available = 0L;
        for (MachineCapability capability : capabilities) {
            if (insert) {
                EnergyOutputAdmissionFacet admission = outputAdmission(capability);
                if (admission != null) {
                    available = RequirementHandlerSupport.saturatingAdd(available,
                            Math.max(0L, admission.outputCapacity(reservations)));
                    if (available >= required) return true;
                    continue;
                }
            }
            LongValueStorage storage = energyStorage(capability);
            if (storage == null) continue;
            long transferable = Math.min(reservations.valueAvailable(storage, insert),
                    RequirementHandlerSupport.scaled(storage.transferLimit(), batches));
            available = RequirementHandlerSupport.saturatingAdd(available, transferable);
            if (available >= required) return true;
        }
        return available >= required;
    }

    private static List<EnergyAction> reserveEnergy(long amount, long batches, boolean insert,
                                                    List<MachineCapability> capabilities,
                                                    PlanningReservations reservations, boolean materialize,
                                                    List<CapabilityOperation> operations) {
        long remaining = amount;
        List<EnergyAction> actions = new ArrayList<>();
        for (MachineCapability capability : capabilities) {
            if (insert) {
                EnergyOutputAdmissionFacet admission = outputAdmission(capability);
                if (admission != null) {
                    if (admission.outputCapacity(reservations) < remaining) continue;
                    EnergyOutputAdmissionFacet.OutputPlan planned = admission.planOutput(remaining, reservations,
                            materialize);
                    if (planned.accepted() != remaining) continue;
                    if (materialize && planned.operation() != null) operations.add(planned.operation());
                    actions.add(new EnergyAction(capability, remaining, true));
                    remaining = 0L;
                    break;
                }
            }
            LongValueStorage storage = energyStorage(capability);
            if (storage == null) continue;
            long available = reservations.valueAvailable(storage, insert);
            long moved = Math.min(remaining, Math.min(available,
                    RequirementHandlerSupport.scaled(storage.transferLimit(), batches)));
            if (moved <= 0L || !reservations.reserveValueTotal(storage, moved, insert)) continue;
            actions.add(new EnergyAction(capability, moved, false));
            remaining -= moved;
            if (remaining == 0L) break;
        }
        return actions;
    }

    private static LongValueStorage energyStorage(MachineCapability capability) {
        ValueFacet<?> facet = capability == null ? null : capability.facet(ValueFacet.class).orElse(null);
        return facet != null && facet.storage() instanceof LongValueStorage storage ? storage : null;
    }

    private static EnergyOutputAdmissionFacet outputAdmission(MachineCapability capability) {
        return capability == null ? null : capability.facet(EnergyOutputAdmissionFacet.class).orElse(null);
    }

    private static long energyAmount(List<EnergyAction> actions) {
        long amount = 0L;
        for (EnergyAction action : actions) amount = RequirementHandlerSupport.saturatingAdd(amount, action.amount());
        return amount;
    }

    private static long requestedAmount(EnergyRequirement requirement, long parallelism) {
        return RequirementHandlerSupport.scaled(requirement.fePerTick(), parallelism);
    }

    private record EnergyAction(MachineCapability capability, long amount, boolean admissionOperation) {
    }
}
