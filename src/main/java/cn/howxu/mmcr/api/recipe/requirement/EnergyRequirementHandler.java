package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.MachineIngredient;

import java.util.ArrayList;
import java.util.List;
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
                IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyEnergy(modifiers, requirement.fePerTick())),
                requirement.tags());
    }

    @Override
    public EnergyRequirement applyLevelModifiers(EnergyRequirement requirement, double energyMultiplier,
                                                 double outputMultiplier) {
        return new EnergyRequirement(requirement.io(), floorNonNegative(requirement.fePerTick() * energyMultiplier),
                requirement.tags());
    }

    @Override
    public MachineIngredient legacyInput(EnergyRequirement requirement) {
        return requirement.io() == RecipeModifier.IOType.INPUT
                ? new MachineIngredient.EnergyIngredient(requirement.fePerTick()) : null;
    }

    @Override
    public Integer legacyEnergyOutput(EnergyRequirement requirement) {
        return requirement.io() == RecipeModifier.IOType.OUTPUT ? requirement.fePerTick() : null;
    }

    private static int floorNonNegative(double value) {
        if (value <= 0D) return 0;
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.floor(value);
    }

    @Override
    public RequirementPlan plan(EnergyRequirement requirement, List<MachineCapability> capabilities,
                                PlanningContext context) {
        if (requirement.fePerTick() <= 0) {
            return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
        }
        boolean insert = requirement.io() == RecipeModifier.IOType.OUTPUT;
        boolean allowPartialOutput = insert && context.outputPolicy() == OutputPolicy.ALLOW_PARTIAL;
        long maximum = energyMaximum(requirement.fePerTick(), insert, capabilities,
                context.requestedParallelism(), allowPartialOutput);
        if (maximum <= 0) {
            return insert
                    ? RequirementHandlerSupport.blockedOutputPlan(requirement, context, "no_output_capacity",
                    requestedAmount(requirement, context.requestedParallelism()))
                    : RequirementHandlerSupport.blockedPlan(requirement, context, "insufficient_energy");
        }
        return RequirementHandlerSupport.deferredPlan(context, maximum,
                (parallelism, reservations) -> planOperations(requirement, capabilities, parallelism,
                        reservations, insert, allowPartialOutput, true),
                RequirementHandlerSupport.reservationFactory((parallelism, reservations) -> planOperations(
                        requirement, capabilities, parallelism, reservations, insert, allowPartialOutput, false)));
    }

    @Override
    public List<ResourceWakeup> resourceWakeups(EnergyRequirement requirement) {
        CapabilityType type = new CapabilityType(requirement.type().id());
        if (requirement.io() == RecipeModifier.IOType.INPUT) {
            return List.of(new ResourceWakeup(Set.of("insufficient_energy"), WakeupReason.ENERGY_AVAILABLE,
                    type::equals));
        }
        return List.of(new ResourceWakeup(Set.of("no_output_capacity"), WakeupReason.OUTPUT_CAPACITY,
                type::equals));
    }

    private static RequirementPlan.OperationPlan planOperations(EnergyRequirement requirement,
                                                                List<MachineCapability> capabilities,
                                                                long parallelism,
                                                                PlanningReservations reservations,
                                                                boolean insert,
                                                                boolean allowPartialOutput,
                                                                boolean materialize) {
        List<CapabilityOperation> operations = new ArrayList<>();
        long requested = insert ? requestedAmount(requirement, parallelism) : 0L;
        long required = RequirementHandlerSupport.scaled(requirement.fePerTick(), parallelism);
        List<EnergyAction> actions = reserveEnergy(required, parallelism, insert, capabilities, reservations);
        long accepted = energyAmount(actions);
        if (accepted < required && (!allowPartialOutput || !insert)) {
            return new RequirementPlan.OperationPlan(List.of(), RequirementHandlerSupport.blocked(requirement,
                    accepted == 0L && insert ? "no_output_capacity" :
                            insert ? "insufficient_resource" : "insufficient_energy"),
                    RequirementHandlerSupport.outputSimulation(requested, accepted));
        }
        if (materialize) {
            for (EnergyAction action : actions) {
                operations.add(action.capability().prepare(new CapabilityRequests.ValueRequest(
                        action.capability().view().type(), action.capability().view().ioType(),
                        parallelism, action.amount(), insert)));
            }
        }
        if (insert && accepted == 0L) {
            return new RequirementPlan.OperationPlan(List.of(),
                    RequirementHandlerSupport.blocked(requirement, "no_output_capacity"),
                    RequirementHandlerSupport.outputSimulation(requested, accepted));
        }
        return new RequirementPlan.OperationPlan(operations, null,
                RequirementHandlerSupport.outputSimulation(requested, accepted));
    }

    private static long energyMaximum(long perBatch, boolean insert, List<MachineCapability> capabilities,
                                      long requested, boolean allowPartialOutput) {
        if (insert && allowPartialOutput) return hasEnergyCapacity(capabilities) ? requested : 0;
        PlanningReservations reservations = new PlanningReservations();
        long lower = 0L;
        long upper = requested;
        while (lower < upper) {
            long distance = upper - lower;
            long candidate = lower + (distance >>> 1) + (distance & 1L);
            if (canReserveEnergyBatches(perBatch, candidate, insert, capabilities, reservations)) lower = candidate;
            else upper = candidate - 1L;
        }
        if (insert && lower == 0 && hasEnergyCapacity(capabilities)) return 1;
        return lower;
    }

    private static boolean hasEnergyCapacity(List<MachineCapability> capabilities) {
        PlanningReservations reservations = new PlanningReservations();
        for (MachineCapability capability : capabilities) {
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
                                                    PlanningReservations reservations) {
        long remaining = amount;
        List<EnergyAction> actions = new ArrayList<>();
        for (MachineCapability capability : capabilities) {
            LongValueStorage storage = energyStorage(capability);
            if (storage == null) continue;
            long available = reservations.valueAvailable(storage, insert);
            long moved = Math.min(remaining, Math.min(available,
                    RequirementHandlerSupport.scaled(storage.transferLimit(), batches)));
            if (moved <= 0L || !reservations.reserveValueTotal(storage, moved, insert)) continue;
            actions.add(new EnergyAction(capability, moved));
            remaining -= moved;
            if (remaining == 0L) break;
        }
        return actions;
    }

    private static LongValueStorage energyStorage(MachineCapability capability) {
        ValueFacet<?> facet = capability == null ? null : capability.facet(ValueFacet.class).orElse(null);
        return facet != null && facet.storage() instanceof LongValueStorage storage ? storage : null;
    }

    private static long energyAmount(List<EnergyAction> actions) {
        long amount = 0L;
        for (EnergyAction action : actions) amount = RequirementHandlerSupport.saturatingAdd(amount, action.amount());
        return amount;
    }

    private static long requestedAmount(EnergyRequirement requirement, long parallelism) {
        return RequirementHandlerSupport.scaled(requirement.fePerTick(), parallelism);
    }

    private record EnergyAction(MachineCapability capability, long amount) {
    }
}
