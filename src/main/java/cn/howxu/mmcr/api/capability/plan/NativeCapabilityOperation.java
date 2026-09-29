package cn.howxu.mmcr.api.capability.plan;

import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A capability operation that is safe for the built-in no-transaction runtime path.
 *
 * @author howxu <dev@howxu.cn>
 */
@FunctionalInterface
public interface NativeCapabilityOperation extends CapabilityOperation {
    @Override
    CapabilityResult commit();

    @Override
    default CapabilityResult commit(TransactionContext transaction) {
        return commit();
    }

    @Override
    default boolean supportsNativeExecution() {
        return true;
    }
}
