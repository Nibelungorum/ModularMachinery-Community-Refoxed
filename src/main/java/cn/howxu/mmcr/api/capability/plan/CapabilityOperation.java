package cn.howxu.mmcr.api.capability.plan;

import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Applies a prepared capability operation within a transaction.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface CapabilityOperation {
    /**
     * Applies this operation without participating in a Transfer transaction.
     * Built-in native item, fluid, and energy operations use this entry point.
     */
    default CapabilityResult commit() {
        return commit(null);
    }

    /**
     * Legacy compatibility bridge for capability integrations that still require Transfer.
     *
     * @deprecated Task 7/8 will replace compatibility operations with native handlers.
     */
    @Deprecated(forRemoval = true)
    CapabilityResult commit(TransactionContext transaction);

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
