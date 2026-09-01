package cn.howxu.mmcr.test.capability;

import cn.howxu.mmcr.api.capability.facet.ExchangeFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Exchange fixture with signed requests and no resource identity assumption.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class TestExchangeFacet implements ExchangeFacet {
    private final LongValueStorage storage = new LongValueStorage(10L, 10L, null);

    @Override
    public double potential() {
        return storage.amount();
    }

    @Override
    public double capacity() {
        return storage.capacity();
    }

    @Override
    public double conductance() {
        return 1D;
    }

    @Override
    public CapabilityOperation prepareExchange(double requested) {
        return transaction -> commit(requested, transaction);
    }

    private CapabilityResult commit(double requested, TransactionContext transaction) {
        long amount = (long) Math.abs(requested);
        long moved = requested >= 0D ? storage.insert(amount, transaction) : storage.extract(amount, transaction);
        if (moved != amount) throw new IllegalStateException("exchange request exceeds available capacity");
        return CapabilityResult.successful();
    }
}
