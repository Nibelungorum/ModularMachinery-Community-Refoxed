package cn.howxu.mmcr.api.capability.plan;

import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Applies a prepared capability operation within a transaction.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface CapabilityOperation {
    CapabilityResult commit(TransactionContext transaction);
}
