package cn.howxu.mmcr.api.capability.plan;

import java.util.Map;

/**
 * Provides recipe position and requested parallelism to a requirement handler.
 *
 * @param requestedParallelism the requested recipe parallelism
 * @param requirementIndex the requirement's position in the recipe
 * @param allowPartialOutputs whether output handlers may accept partial capacity
 * @param reservations shared reservations for one planning pass
 * @param outputPolicies output policies keyed by recipe requirement index
 * @author howxu <dev@howxu.cn>
 */
public record PlanningContext(long requestedParallelism, int requirementIndex, boolean allowPartialOutputs,
                               PlanningReservations reservations,
                               Map<Integer, OutputPolicy> outputPolicies) {
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
