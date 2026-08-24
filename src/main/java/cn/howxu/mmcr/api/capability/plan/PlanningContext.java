package cn.howxu.mmcr.api.capability.plan;

/**
 * Provides recipe position and requested parallelism to a requirement handler.
 *
 * @param requestedParallelism the requested recipe parallelism
 * @param requirementIndex the requirement's position in the recipe
 * @param allowPartialOutputs whether output handlers may accept partial capacity
 * @param reservations shared reservations for one planning pass
 * @author howxu <dev@howxu.cn>
 */
public record PlanningContext(int requestedParallelism, int requirementIndex, boolean allowPartialOutputs,
                              PlanningReservations reservations) {
    public PlanningContext(int requestedParallelism, int requirementIndex) {
        this(requestedParallelism, requirementIndex, false, new PlanningReservations());
    }

    public PlanningContext(int requestedParallelism, int requirementIndex, boolean allowPartialOutputs) {
        this(requestedParallelism, requirementIndex, allowPartialOutputs, new PlanningReservations());
    }

    public PlanningContext {
        if (requestedParallelism <= 0) throw new IllegalArgumentException("requested parallelism must be positive");
        if (reservations == null) throw new IllegalArgumentException("reservations must not be null");
    }
}
