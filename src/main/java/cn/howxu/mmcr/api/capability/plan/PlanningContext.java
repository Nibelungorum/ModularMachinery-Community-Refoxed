package cn.howxu.mmcr.api.capability.plan;

/**
 * Provides recipe position and requested parallelism to a requirement handler.
 *
 * @param requestedParallelism the requested recipe parallelism
 * @param requirementIndex the requirement's position in the recipe
 * @author howxu <dev@howxu.cn>
 */
public record PlanningContext(int requestedParallelism, int requirementIndex) {
}
