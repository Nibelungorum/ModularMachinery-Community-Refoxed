package cn.howxu.mmcr.api.capability.plan;

import java.util.Map;
import cn.howxu.mmcr.api.capability.facet.InputRoutingFacet;
import cn.howxu.mmcr.api.recipe.InputScope;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport.Sampling;
import org.jetbrains.annotations.Nullable;

/**
 * Provides recipe position and requested parallelism to a requirement handler.
 *
 * @param requestedParallelism the requested recipe parallelism
 * @param requirementIndex the requirement's position in the recipe
 * @param allowPartialOutputs whether output handlers may accept partial capacity
 * @param reservations shared reservations for one planning pass
 * @param outputPolicies output policies keyed by recipe requirement index
 * @param reservationOwner optional persistent owner of recipe-scoped reservations
 * @author howxu <dev@howxu.cn>
 */
public record PlanningContext(long requestedParallelism, int requirementIndex, boolean allowPartialOutputs,
                               PlanningReservations reservations,
                               Map<Integer, OutputPolicy> outputPolicies,
                               @Nullable InputRoutingFacet inputRouting, @Nullable InputScope inputScope,
                               @Nullable Sampling sampling, @Nullable Object reservationOwner) {
    public PlanningContext(long requestedParallelism, int requirementIndex, boolean allowPartialOutputs,
                           PlanningReservations reservations, Map<Integer, OutputPolicy> outputPolicies) {
        this(requestedParallelism, requirementIndex, allowPartialOutputs, reservations, outputPolicies,
                null, null, null, null);
    }

    public PlanningContext(long requestedParallelism, int requirementIndex, boolean allowPartialOutputs,
                           PlanningReservations reservations, Map<Integer, OutputPolicy> outputPolicies,
                           Object reservationOwner) {
        this(requestedParallelism, requirementIndex, allowPartialOutputs, reservations, outputPolicies,
                null, null, null, reservationOwner);
    }

    public PlanningContext(long requestedParallelism, int requirementIndex, boolean allowPartialOutputs,
                           PlanningReservations reservations, Map<Integer, OutputPolicy> outputPolicies,
                           @Nullable InputRoutingFacet inputRouting, @Nullable InputScope inputScope,
                           @Nullable Sampling sampling) {
        this(requestedParallelism, requirementIndex, allowPartialOutputs, reservations, outputPolicies,
                inputRouting, inputScope, sampling, null);
    }

    public PlanningContext forRequirement(int index, PlanningReservations reservations) {
        return new PlanningContext(requestedParallelism, index, allowPartialOutputs, reservations,
                outputPolicies, inputRouting, inputScope, sampling, reservationOwner);
    }

    public PlanningContext forScope(InputScope scope, @Nullable Sampling sampling) {
        return new PlanningContext(requestedParallelism, requirementIndex, allowPartialOutputs,
                reservations.copy(), outputPolicies, inputRouting, scope, sampling, reservationOwner);
    }

    public PlanningContext(long requestedParallelism, int requirementIndex) {
        this(requestedParallelism, requirementIndex, false, new PlanningReservations(), Map.of());
    }

    public PlanningContext(long requestedParallelism, int requirementIndex, boolean allowPartialOutputs) {
        this(requestedParallelism, requirementIndex, allowPartialOutputs, new PlanningReservations(), Map.of());
    }

    public PlanningContext(long requestedParallelism, int requirementIndex,
                           Map<Integer, OutputPolicy> outputPolicies) {
        this(requestedParallelism, requirementIndex, false, new PlanningReservations(), outputPolicies);
    }

    public PlanningContext(long requestedParallelism, int requirementIndex, boolean allowPartialOutputs,
                           PlanningReservations reservations) {
        this(requestedParallelism, requirementIndex, allowPartialOutputs, reservations, Map.of());
    }

    public OutputPolicy outputPolicy() {
        return outputPolicies.getOrDefault(requirementIndex,
                allowPartialOutputs ? OutputPolicy.ALLOW_PARTIAL : OutputPolicy.REQUIRE_FULL);
    }

    public PlanningContext {
        if (requestedParallelism <= 0) throw new IllegalArgumentException("requested parallelism must be positive");
        if (reservations == null) throw new IllegalArgumentException("reservations must not be null");
        outputPolicies = Map.copyOf(outputPolicies == null ? Map.of() : outputPolicies);
    }
}
