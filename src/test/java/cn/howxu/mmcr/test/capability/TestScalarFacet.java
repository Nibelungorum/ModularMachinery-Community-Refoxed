package cn.howxu.mmcr.test.capability;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.facet.ScalarFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Scalar fixture whose requested delta is applied only when its transaction commits.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class TestScalarFacet implements ScalarFacet {
    private final LongValueStorage storage = new LongValueStorage(10L, 10L, null);

    public long amount() {
        return storage.amount();
    }

    public long insert(long amount, boolean simulate) {
        return storage.insert(amount, simulate);
    }

    @Override
    public CapabilityOperation prepareScalar(CapabilityRequest request) {
        return transaction -> commit(request.parallelism(), transaction);
    }

    private CapabilityResult commit(long delta, TransactionContext transaction) {
        if (storage.insert(delta, transaction) != delta) throw new IllegalStateException("scalar request exceeds capacity");
        return CapabilityResult.successful();
    }
}
