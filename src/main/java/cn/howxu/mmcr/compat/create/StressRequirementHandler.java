package cn.howxu.mmcr.compat.create;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.compat.create.CreateFailureReasons;
import cn.howxu.mmcr.api.compat.create.StressFacet;
import cn.howxu.mmcr.api.compat.create.StressState;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Plans persistent base contributions against shared actual network budgets.
 * @author howxu <dev@howxu.cn>
 */
public final class StressRequirementHandler implements RequirementHandler<StressRequirement> {
    @Override
    public StressRequirement applyModifiers(StressRequirement requirement, List<RecipeModifier> modifiers) {
        return new StressRequirement(requirement.io(), RecipeModifier.applyModifiers(modifiers, "create:stress",
                requirement.io(), requirement.stress(), false), requirement.minRpm(), requirement.rpm(), requirement.tags());
    }

    @Override
    public StressRequirement applyLevelModifiers(StressRequirement requirement, double energyMultiplier,
                                                  double outputMultiplier) {
        double multiplier = requirement.io() == RecipeModifier.IOType.INPUT ? energyMultiplier : outputMultiplier;
        return new StressRequirement(requirement.io(), requirement.stress() * multiplier,
                requirement.minRpm(), requirement.rpm(), requirement.tags());
    }

    @Override
    public List<ResourceWakeup> resourceWakeups(StressRequirement requirement) {
        return List.of(new ResourceWakeup(Set.of(CreateFailureReasons.MISSING_ROTATION.id(),
                CreateFailureReasons.INSUFFICIENT_RPM.id(), CreateFailureReasons.INSUFFICIENT_STRESS.id(),
                CreateFailureReasons.INCOMPATIBLE_OUTPUT_RPM.id()), WakeupReason.ENERGY_AVAILABLE,
                new CapabilityType(CreateRecipeTypes.STRESS)::equals));
    }

    @Override
    public RequirementPlan plan(StressRequirement requirement, List<MachineCapability> capabilities,
                                PlanningContext context) {
        if (!CreateBridge.get().available()) return RequirementHandlerSupport.blockedPlan(
                requirement, context, CreateFailureReasons.CREATE_UNAVAILABLE);
        List<StressFacet> facets = new ArrayList<>();
        for (MachineCapability capability : capabilities) {
            capability.facet(StressFacet.class).ifPresent(facet -> {
                if (facets.stream().noneMatch(existing -> existing == facet)) facets.add(facet);
            });
        }
        StressSession session = context.reservationOwner() instanceof StressSession owner ? owner : null;
        AllocationResult minimum = allocate(requirement, facets, context, session, 1L, context.reservations().copy());
        if (minimum.failure() != null) return RequirementHandlerSupport.blockedPlan(requirement, context, minimum.failure());
        long lower = 1L;
        long upper = context.requestedParallelism();
        while (lower < upper) {
            long distance = upper - lower;
            long candidate = lower + (distance >>> 1) + (distance & 1L);
            if (allocate(requirement, facets, context, session, candidate, context.reservations().copy()).failure() == null) {
                lower = candidate;
            } else upper = candidate - 1L;
        }
        RequirementPlan.OperationFactory factory = (parallelism, reservations) -> {
            AllocationResult allocation = allocate(requirement, facets, context, session, parallelism, reservations);
            if (allocation.failure() != null) return new RequirementPlan.OperationPlan(List.of(),
                    blocked(requirement, context, allocation.failure()));
            return new RequirementPlan.OperationPlan(List.of(() -> commit(requirement, context, session,
                    allocation.allocations())), null);
        };
        return RequirementHandlerSupport.deferredPlan(context, lower, factory,
                RequirementHandlerSupport.reservationFactory(factory));
    }

    private static AllocationResult allocate(StressRequirement requirement, List<StressFacet> facets,
                                               PlanningContext context, StressSession session, long parallelism,
                                               PlanningReservations reservations) {
        boolean input = requirement.io() == RecipeModifier.IOType.INPUT;
        if (!input && !validOutputRpm(requirement.rpm())) return failed(CreateFailureReasons.INVALID_OUTPUT_RPM);
        if (facets.isEmpty()) return failed(input ? CreateFailureReasons.MISSING_ROTATION
                : CreateFailureReasons.INCOMPATIBLE_OUTPUT_RPM);
        double remaining = requirement.stress() * parallelism;
        if (!Double.isFinite(remaining) || remaining <= 0D) return failed(CreateFailureReasons.INSUFFICIENT_STRESS);
        Map<Object, Double> credits = input ? ownedCredits(session, context.requirementIndex()) : Map.of();
        if (input && session != null) {
            for (var credit : credits.entrySet()) {
                if (!reservations.creditStress(credit.getKey(), session, context.requirementIndex(), credit.getValue())) {
                    return failed(CreateFailureReasons.INSUFFICIENT_STRESS);
                }
            }
        }
        List<Allocation> allocations = new ArrayList<>();
        FailureReason failure = input ? CreateFailureReasons.MISSING_ROTATION : CreateFailureReasons.INCOMPATIBLE_OUTPUT_RPM;
        for (StressFacet facet : facets) {
            StressState state = facet.state();
            Object network = facet.networkIdentity();
            if (input) {
                FailureReason rotation = rotationFailure(state, network, requirement.minRpm());
                if (rotation != null) {
                    failure = rotation;
                    continue;
                }
            } else if (!facet.acceptsGeneratedRpm(session, context.requirementIndex(), requirement.rpm())
                    || !reservations.acceptsGeneratedRpm(facet, requirement.rpm())) {
                continue;
            }
            double rpm = input ? state.theoreticalRpm() : requirement.rpm();
            double ownedBase = session == null ? 0D : facet.ownedBaseStress(session, context.requirementIndex());
            if (!validOwnedBase(state, ownedBase) || session != null
                    && !reservations.creditStress(facet, session, context.requirementIndex(), ownedBase)) {
                failure = CreateFailureReasons.INSUFFICIENT_STRESS;
                continue;
            }
            // Facet reservations/credits are base units; real network keys remain actual stress units.
            double otherBase = Math.max(0D, state.baseContribution() - reservations.creditedStress(facet));
            double availableBase = nativeBaseLimit(rpm) - otherBase;
            double reservedBase = reservations.reservedStress(facet);
            double base = Math.min(remaining, Math.max(0D, availableBase - reservedBase));
            double availableActual = 0D;
            if (input) {
                if (!validNetwork(state)) {
                    failure = CreateFailureReasons.INSUFFICIENT_STRESS;
                    continue;
                }
                // Disabled stress ignores capacity, but native network float sums must still stay finite.
                double networkLimit = facet.stressEnabled() ? state.networkCapacity() : Float.MAX_VALUE;
                availableActual = networkLimit - state.networkStress() + reservations.creditedStress(network);
                base = Math.min(base, Math.max(0D, availableActual - reservations.reservedStress(network))
                        / Math.abs(state.theoreticalRpm()));
            }
            double nextReserved = reservedBase + base;
            if (!StressContributions.positiveFloat(base) || nextReserved > availableBase
                    || !validNativeAggregate(otherBase + nextReserved, rpm)) {
                failure = CreateFailureReasons.INSUFFICIENT_STRESS;
                continue;
            }
            if (input && !reservations.reserveStress(network, base * Math.abs(rpm), availableActual)) {
                failure = CreateFailureReasons.INSUFFICIENT_STRESS;
                continue;
            }
            reservations.reserveStress(facet, base, availableBase);
            if (!input) reservations.reserveGeneratedRpm(facet, requirement.rpm());
            allocations.add(new Allocation(facet, network, base));
            remaining -= base;
            if (remaining <= 0D) return new AllocationResult(List.copyOf(allocations), null);
        }
        return failed(failure);
    }

    private static CapabilityResult commit(StressRequirement requirement, PlanningContext context,
                                            StressSession session, List<Allocation> allocations) {
        if (session == null) return commitFailure(requirement, context, CreateFailureReasons.MISSING_RECIPE_SCOPE);
        if (!CreateBridge.get().available()) return commitFailure(requirement, context, CreateFailureReasons.CREATE_UNAVAILABLE);
        FailureReason failure = preflight(requirement, context.requirementIndex(), session, allocations);
        if (failure != null) return commitFailure(requirement, context, failure);
        session.beginCommit(context.requirementIndex());
        try {
            for (Allocation allocation : allocations) {
                if (requirement.io() == RecipeModifier.IOType.INPUT
                        && allocation.facet().networkIdentity() != allocation.network()) {
                    session.release(context.requirementIndex());
                    return commitFailure(requirement, context, CreateFailureReasons.MISSING_ROTATION);
                }
                session.track(allocation.facet(), context.requirementIndex(), requirement.io());
                CapabilityResult result = allocation.facet().apply(session, context.requirementIndex(), allocation.baseStress(),
                        requirement.io() == RecipeModifier.IOType.OUTPUT ? requirement.rpm() : 0D);
                if (!result.success()) {
                    session.release(context.requirementIndex());
                    return result;
                }
            }
            session.retain(context.requirementIndex(), allocations.stream().map(Allocation::facet).toList());
            return CapabilityResult.successful();
        } catch (RuntimeException | Error exception) {
            session.release(context.requirementIndex());
            throw exception;
        } finally {
            session.endCommit();
        }
    }

    private static FailureReason preflight(StressRequirement requirement, int index, StressSession session,
                                            List<Allocation> allocations) {
        boolean input = requirement.io() == RecipeModifier.IOType.INPUT;
        if (!input && !validOutputRpm(requirement.rpm())) return CreateFailureReasons.INVALID_OUTPUT_RPM;
        Map<Object, Double> credits = input ? ownedCredits(session, index) : Map.of();
        if (credits.values().stream().anyMatch(value -> !Double.isFinite(value) || value < 0D)) {
            return CreateFailureReasons.INSUFFICIENT_STRESS;
        }
        Map<Object, Double> requested = new IdentityHashMap<>();
        for (Allocation allocation : allocations) {
            StressFacet facet = allocation.facet();
            StressState state = facet.state();
            Object network = facet.networkIdentity();
            if (input && network != allocation.network()) return CreateFailureReasons.MISSING_ROTATION;
            if (!StressContributions.positiveFloat(allocation.baseStress())) return CreateFailureReasons.INSUFFICIENT_STRESS;
            if (input) {
                FailureReason rotation = rotationFailure(state, network, requirement.minRpm());
                if (rotation != null) return rotation;
            } else if (!facet.acceptsGeneratedRpm(session, index, requirement.rpm())) {
                return CreateFailureReasons.INCOMPATIBLE_OUTPUT_RPM;
            }
            double ownedBase = facet.ownedBaseStress(session, index);
            double rpm = input ? state.theoreticalRpm() : requirement.rpm();
            if (!validOwnedBase(state, ownedBase) || !validNativeAggregate(
                    state.baseContribution() - ownedBase + allocation.baseStress(), rpm)) {
                return CreateFailureReasons.INSUFFICIENT_STRESS;
            }
            if (!input) {
                if (!StressContributions.positiveFloat(allocation.baseStress() * Math.abs(requirement.rpm()))) {
                    return CreateFailureReasons.INSUFFICIENT_STRESS;
                }
                continue;
            }
            double actual = allocation.baseStress() * Math.abs(state.theoreticalRpm());
            if (!StressContributions.positiveFloat(actual)) return CreateFailureReasons.INSUFFICIENT_STRESS;
            if (!validNetwork(state)) return CreateFailureReasons.INSUFFICIENT_STRESS;
            double total = requested.getOrDefault(network, 0D) + actual;
            double networkLimit = facet.stressEnabled() ? state.networkCapacity() : Float.MAX_VALUE;
            double available = networkLimit - state.networkStress() + credits.getOrDefault(network, 0D);
            if (!Double.isFinite(total) || total > available) return CreateFailureReasons.INSUFFICIENT_STRESS;
            requested.put(network, total);
        }
        return null;
    }

    private static Map<Object, Double> ownedCredits(StressSession session, int index) {
        Map<Object, Double> credits = new IdentityHashMap<>();
        if (session == null) return credits;
        for (StressFacet facet : session.facets(index)) {
            Object network = facet.networkIdentity();
            if (network != null) credits.merge(network, facet.ownedActualStress(session, index), Double::sum);
        }
        return credits;
    }

    private static FailureReason rotationFailure(StressState state, Object network, double minimum) {
        if (network == null || !state.connected()) return CreateFailureReasons.MISSING_ROTATION;
        if (state.overstressed()) return CreateFailureReasons.INSUFFICIENT_STRESS;
        if (!Double.isFinite(state.actualRpm()) || !Double.isFinite(state.theoreticalRpm())
                || !Float.isFinite((float) state.actualRpm()) || !Float.isFinite((float) state.theoreticalRpm())
                || (float) state.actualRpm() == 0F || (float) state.theoreticalRpm() == 0F) {
            return CreateFailureReasons.MISSING_ROTATION;
        }
        return Math.abs(state.actualRpm()) < minimum ? CreateFailureReasons.INSUFFICIENT_RPM : null;
    }

    private static boolean validNetwork(StressState state) {
        return Double.isFinite(state.networkCapacity()) && state.networkCapacity() >= 0D
                && Float.isFinite((float) state.networkCapacity()) && Double.isFinite(state.networkStress())
                && state.networkStress() >= 0D && Float.isFinite((float) state.networkStress());
    }

    private static boolean validOwnedBase(StressState state, double ownedBase) {
        return Double.isFinite(state.baseContribution()) && state.baseContribution() >= 0D
                && Float.isFinite((float) state.baseContribution()) && Double.isFinite(ownedBase)
                && ownedBase >= 0D && ownedBase <= state.baseContribution();
    }

    private static boolean validNativeAggregate(double base, double rpm) {
        return StressContributions.positiveFloat(base)
                && StressContributions.positiveFloat((float) base * Math.abs((float) rpm));
    }

    /** Choose a native float endpoint whose float product is finite, including rounding at the upper limit. */
    private static double nativeBaseLimit(double rpm) {
        float speed = Math.abs((float) rpm);
        float limit = (float) Math.min((double) Float.MAX_VALUE, (double) Float.MAX_VALUE / speed);
        return Float.isFinite(limit * speed) ? limit : Math.nextDown(limit);
    }

    private static boolean validOutputRpm(double rpm) {
        double maximum = CreateBridge.get().maxRpm();
        return Double.isFinite(rpm) && rpm != 0D && Float.isFinite((float) rpm) && (float) rpm != 0F
                && Double.isFinite(maximum) && maximum > 0D && Math.abs(rpm) <= maximum;
    }

    private static ExecutionStatus blocked(StressRequirement requirement, PlanningContext context, FailureReason reason) {
        return RequirementHandlerSupport.blocked(requirement, context, reason);
    }

    private static CapabilityResult commitFailure(StressRequirement requirement, PlanningContext context, FailureReason reason) {
        return CapabilityResult.failure(ExecutionStatus.blocked(requirement.type().id(), requirement.type().id(),
                FailureOccurrence.at(reason, requirement.type().id(), FailurePhase.CAPABILITY_COMMIT,
                        null, context.requirementIndex(), Map.of())));
    }

    private static AllocationResult failed(FailureReason reason) {
        return new AllocationResult(List.of(), reason);
    }

    /** Prepared local allocation retains the real network identity for commit validation.
     * @author howxu <dev@howxu.cn>
     */
    private record Allocation(StressFacet facet, Object network, double baseStress) {
    }

    /** Reservation passes only produce values; no native contributions are written.
     * @author howxu <dev@howxu.cn>
     */
    private record AllocationResult(List<Allocation> allocations, FailureReason failure) {
    }
}
