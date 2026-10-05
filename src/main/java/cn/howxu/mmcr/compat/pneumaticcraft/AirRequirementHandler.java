package cn.howxu.mmcr.compat.pneumaticcraft;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.OutputFit;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.OutputSimulation;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.compat.pneumaticcraft.AirState;
import cn.howxu.mmcr.api.compat.pneumaticcraft.PneumaticAirFacet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import cn.howxu.mmcr.util.IOType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Plans signed air against the shared scalar budget, with whole-split preflight.
 * @author howxu <dev@howxu.cn>
 */
public final class AirRequirementHandler implements RequirementHandler<AirRequirement> {
    @Override
    public AirRequirement applyModifiers(AirRequirement requirement, List<RecipeModifier> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) return requirement;
        BigDecimal add = BigDecimal.ZERO;
        BigDecimal mul = BigDecimal.ONE;
        BigDecimal divisor = BigDecimal.ONE;
        for (RecipeModifier modifier : modifiers) {
            if (!modifier.getTarget().isEmpty() && !modifier.getTarget().equals(PneumaticIds.AIR.toString())
                    || modifier.getIOTarget() != requirement.io() || modifier.affectsChance()) continue;
            if (!Float.isFinite(modifier.getModifier())) {
                return withRate(requirement, floorNonNegative(RecipeModifier.applyModifiers(modifiers,
                        PneumaticIds.AIR.toString(), requirement.io(), (double) requirement.airPerTick(), false)));
            }
            BigDecimal value = new BigDecimal((double) modifier.getModifier());
            switch (modifier.getOperation()) {
                case ADD -> add = add.add(value);
                case SUBTRACT -> add = add.subtract(value);
                case MULTIPLY -> mul = mul.multiply(value);
                case DIVIDE -> {
                    if (value.signum() != 0) divisor = divisor.multiply(value);
                }
            }
        }
        return adjustedRate(requirement, add, mul, divisor);
    }

    @Override
    public AirRequirement applyLevelModifiers(AirRequirement requirement, double energyMultiplier,
                                               double outputMultiplier) {
        double multiplier = requirement.io() == RecipeModifier.IOType.INPUT ? energyMultiplier : outputMultiplier;
        if (!Double.isFinite(multiplier)) {
            return withRate(requirement, floorNonNegative(requirement.airPerTick() * multiplier));
        }
        return adjustedRate(requirement, BigDecimal.ZERO, BigDecimal.valueOf(multiplier), BigDecimal.ONE);
    }

    private static AirRequirement adjustedRate(AirRequirement requirement, BigDecimal add, BigDecimal mul,
                                               BigDecimal divisor) {
        if (add.signum() == 0 && mul.compareTo(divisor) == 0) return requirement;
        // Keep the helper's grouping; retain finite divisors until the final integer quotient.
        BigDecimal numerator = BigDecimal.valueOf(requirement.airPerTick()).add(add).multiply(mul);
        if (divisor.signum() < 0) {
            numerator = numerator.negate();
            divisor = divisor.negate();
        }
        long rate = numerator.signum() <= 0 ? 0L
                : numerator.compareTo(divisor.multiply(BigDecimal.valueOf(Long.MAX_VALUE))) >= 0 ? Long.MAX_VALUE
                : numerator.divideToIntegralValue(divisor).longValueExact();
        return withRate(requirement, rate);
    }

    private static AirRequirement withRate(AirRequirement requirement, long rate) {
        if (rate == requirement.airPerTick()) return requirement;
        return new AirRequirement(requirement.io(), rate,
                requirement.minPressure(), requirement.tags());
    }

    private static long floorNonNegative(double value) {
        if (Double.isNaN(value) || value <= 0D) return 0L;
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.floor(value);
    }

    @Override
    public List<ResourceWakeup> resourceWakeups(AirRequirement requirement) {
        boolean input = requirement.io() == RecipeModifier.IOType.INPUT;
        return List.of(new ResourceWakeup(input
                ? Set.of(AirFailureReasons.MISSING_INTERFACE.id(), AirFailureReasons.INSUFFICIENT_PRESSURE.id(),
                AirFailureReasons.INSUFFICIENT_AIR.id())
                : Set.of(AirFailureReasons.MISSING_INTERFACE.id(), AirFailureReasons.OUTPUT_BLOCKED.id()),
                input ? WakeupReason.INPUT_AVAILABLE : WakeupReason.OUTPUT_CAPACITY,
                PneumaticIds.AIR::equals));
    }

    @Override
    public RequirementPlan plan(AirRequirement requirement, List<MachineCapability> capabilities,
                                PlanningContext context) {
        if (!PneumaticCraftBridge.get().available()) {
            return blockedPlan(requirement, context, AirFailureReasons.UNAVAILABLE);
        }
        List<PneumaticAirFacet> facets = matchingFacets(requirement, capabilities);
        if (facets.isEmpty()) return blockedPlan(requirement, context, AirFailureReasons.MISSING_INTERFACE);

        AllocationResult minimum = allocate(requirement, facets, context, 1L, context.reservations().copy());
        if (minimum.failure() != null) {
            AllocationResult requested = allocate(requirement, facets, context, context.requestedParallelism(),
                    context.reservations().copy());
            return RequirementPlan.withOutputSimulation(context.requirementIndex(), 0L, List.of(),
                    RequirementHandlerSupport.blocked(requirement, context, minimum.failure()), requested.simulation());
        }
        long lower = 1L;
        long upper = context.requestedParallelism();
        while (lower < upper) {
            long distance = upper - lower;
            long candidate = lower + (distance >>> 1) + (distance & 1L);
            if (allocate(requirement, facets, context, candidate, context.reservations().copy()).failure() == null) {
                lower = candidate;
            } else upper = candidate - 1L;
        }
        RequirementPlan.OperationFactory factory = (parallelism, reservations) -> {
            AllocationResult allocation = allocate(requirement, facets, context, parallelism, reservations);
            if (allocation.failure() != null) {
                return new RequirementPlan.OperationPlan(List.of(), RequirementHandlerSupport.blocked(
                        requirement, context, allocation.failure()), allocation.simulation());
            }
            return new RequirementPlan.OperationPlan(List.of(() -> commit(requirement, context,
                    allocation.allocations())), null, allocation.simulation());
        };
        return RequirementHandlerSupport.deferredPlan(context, lower, factory,
                RequirementHandlerSupport.reservationFactory(factory));
    }

    /** Reallocates a startup input at the chosen parallelism and validates its split without applying it. */
    public static CapabilityResult preflightStart(AirRequirement requirement, List<MachineCapability> capabilities,
                                                 PlanningContext context) {
        if (!PneumaticCraftBridge.get().available()) return commitFailure(context, AirFailureReasons.UNAVAILABLE);
        List<PneumaticAirFacet> facets = matchingFacets(requirement, capabilities);
        if (facets.isEmpty()) return commitFailure(context, AirFailureReasons.MISSING_INTERFACE);
        AllocationResult allocation = allocate(requirement, facets, context, context.requestedParallelism(),
                context.reservations());
        if (allocation.failure() != null) return commitFailure(context, allocation.failure());
        return preflight(requirement, context, allocation.allocations());
    }

    private static List<PneumaticAirFacet> matchingFacets(AirRequirement requirement,
                                                        List<MachineCapability> capabilities) {
        List<PneumaticAirFacet> facets = new ArrayList<>();
        Map<Object, Boolean> identities = new IdentityHashMap<>();
        List<MachineCapability> ordered = requirement.io() == RecipeModifier.IOType.OUTPUT
                ? RequirementHandlerSupport.prioritizedOutputCapabilities(capabilities) : capabilities;
        for (MachineCapability capability : ordered) {
            if (!capability.view().directions().supports(IOType.valueOf(requirement.io().name()))
                    || !requirement.tags().isEmpty()
                    && requirement.tags().stream().noneMatch(capability.view()::matchesTag)) continue;
            capability.facet(PneumaticAirFacet.class).ifPresent(facet -> {
                Object identity = facet.queryIdentity();
                if (identity != null && identities.put(identity, Boolean.TRUE) == null) facets.add(facet);
            });
        }
        return facets;
    }

    private static RequirementPlan blockedPlan(AirRequirement requirement, PlanningContext context,
                                                FailureReason reason) {
        return RequirementPlan.withOutputSimulation(context.requirementIndex(), 0L, List.of(),
                RequirementHandlerSupport.blocked(requirement, context, reason),
                simulation(requirement, RequirementHandlerSupport.scaled(requirement.airPerTick(),
                        context.requestedParallelism()), 0L));
    }

    private static AllocationResult allocate(AirRequirement requirement, List<PneumaticAirFacet> facets,
                                               PlanningContext context, long parallelism,
                                               PlanningReservations reservations) {
        boolean insert = requirement.io() == RecipeModifier.IOType.OUTPUT;
        long requested = RequirementHandlerSupport.scaled(requirement.airPerTick(), parallelism);
        long remaining = requested;
        boolean pressureMet = false;
        List<Allocation> allocations = new ArrayList<>();
        for (PneumaticAirFacet facet : facets) {
            AirState state = facet.state();
            Object identity = facet.queryIdentity();
            long capacity = Math.min(Integer.MAX_VALUE,
                    (long) Math.floor((double) state.dangerPressure() * state.volume()));
            long available = reservations.valueAvailable(identity, capacity, state.air(), insert);
            if (!insert) {
                long virtualAir = available;
                if (virtualAir == 0L) {
                    // Extraction availability clamps negatives; insertion headroom retains their sign.
                    virtualAir = Math.min(0L, capacity - reservations.valueAvailable(
                            identity, capacity, state.air(), true));
                }
                if ((float) virtualAir / state.volume() < requirement.minPressure()) continue;
                pressureMet = true;
            }
            long moved = Math.min(Integer.MAX_VALUE, Math.min(remaining, available));
            if (requested == 0L) {
                allocations.add(new Allocation(facet, identity, 0L));
                break;
            }
            // Native addAir floors at -volume; an insertion must reach that floor without clamping.
            if (insert && capacity - available + moved < -(long) state.volume()) continue;
            if (moved <= 0L || !reservations.reserveValueTotal(identity, capacity, state.air(), moved, insert)) continue;
            allocations.add(new Allocation(facet, identity, moved));
            remaining -= moved;
            if (remaining == 0L) break;
        }
        FailureReason failure = null;
        if (allocations.isEmpty() || remaining > 0L
                && !(insert && context.outputPolicy() == OutputPolicy.ALLOW_PARTIAL)) {
            failure = insert ? AirFailureReasons.OUTPUT_BLOCKED
                    : pressureMet ? AirFailureReasons.INSUFFICIENT_AIR : AirFailureReasons.INSUFFICIENT_PRESSURE;
        }
        return new AllocationResult(List.copyOf(allocations), failure,
                simulation(requirement, requested, requested - remaining));
    }

    private static OutputSimulation simulation(AirRequirement requirement, long requested, long accepted) {
        if (requirement.io() != RecipeModifier.IOType.OUTPUT) return null;
        return new OutputSimulation(requested, accepted, accepted == requested ? OutputFit.FULL
                : accepted == 0L ? OutputFit.NONE : OutputFit.PARTIAL);
    }

    private static CapabilityResult commit(AirRequirement requirement, PlanningContext context,
                                            List<Allocation> allocations) {
        CapabilityResult validation = preflight(requirement, context, allocations);
        if (!validation.success()) return validation;
        boolean insert = requirement.io() == RecipeModifier.IOType.OUTPUT;
        for (Allocation allocation : allocations) {
            CapabilityResult result = allocation.facet().apply(allocation.amount(), insert, requirement.minPressure());
            if (!result.success()) return result;
        }
        return CapabilityResult.successful();
    }

    private static CapabilityResult preflight(AirRequirement requirement, PlanningContext context,
                                              List<Allocation> allocations) {
        if (!PneumaticCraftBridge.get().available()) {
            return commitFailure(context, AirFailureReasons.UNAVAILABLE);
        }
        boolean insert = requirement.io() == RecipeModifier.IOType.OUTPUT;
        for (Allocation allocation : allocations) {
            if (allocation.facet().queryIdentity() != allocation.identity()) {
                return commitFailure(context, AirFailureReasons.MISSING_INTERFACE);
            }
            CapabilityResult result = allocation.facet().validate(allocation.amount(), insert, requirement.minPressure());
            if (!result.success()) return result;
        }
        return CapabilityResult.successful();
    }

    private static CapabilityResult commitFailure(PlanningContext context, FailureReason reason) {
        return CapabilityResult.failure(ExecutionStatus.blocked(PneumaticIds.AIR, PneumaticIds.AIR,
                FailureOccurrence.at(reason, PneumaticIds.AIR, FailurePhase.CAPABILITY_COMMIT,
                        null, context.requirementIndex(), Map.of())));
    }

    /** One allocation per physical handler.
     * @author howxu <dev@howxu.cn>
     */
    private record Allocation(PneumaticAirFacet facet, Object identity, long amount) {
    }

    /** Shared candidate and materialization result.
     * @author howxu <dev@howxu.cn>
     */
    private record AllocationResult(List<Allocation> allocations, FailureReason failure, OutputSimulation simulation) {
    }
}
