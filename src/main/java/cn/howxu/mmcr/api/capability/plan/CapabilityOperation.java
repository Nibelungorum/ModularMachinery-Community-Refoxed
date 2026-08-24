package cn.howxu.mmcr.api.capability.plan;

import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Applies a prepared capability operation within a transaction.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface CapabilityOperation {
    CapabilityResult commit(TransactionContext transaction);

    /**
     * Adapts an operation whose request was prepared for a larger candidate parallelism.
     *
     * @param parallelism the final plan parallelism
     * @return an operation safe for the final parallelism
     */
    default CapabilityOperation forParallelism(int parallelism) {
        if (parallelism <= 0) throw new IllegalArgumentException("parallelism must be positive");
        return this;
    }
}
