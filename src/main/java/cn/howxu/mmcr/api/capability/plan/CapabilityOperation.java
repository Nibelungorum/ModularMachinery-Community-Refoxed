package cn.howxu.mmcr.api.capability.plan;

/**
 * Applies a prepared native capability operation.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface CapabilityOperation {
    CapabilityResult commit();

    /**
     * Adapts an operation whose request was prepared for a larger candidate parallelism.
     *
     * @param parallelism the final plan parallelism
     * @return an operation safe for the final parallelism
     */
    default CapabilityOperation forParallelism(long parallelism) {
        if (parallelism <= 0) throw new IllegalArgumentException("parallelism must be positive");
        return this;
    }
}
