package cn.howxu.mmcr.test.capability;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.ExchangeFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Map;
import java.util.Set;

/**
 * Exchange fixture with signed requests and no resource identity assumption.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class TestExchangeFacet implements MachineCapability, ExchangeFacet {
    private final LongValueStorage storage = new LongValueStorage(10L, 10L, null);
    private final CapabilityType type = new CapabilityType(ResourceLocation.fromNamespaceAndPath("mmcr_test", "exchange"));
    private final CapabilityView view = new CapabilityView() {
        @Override
        public CapabilityType type() {
            return TestExchangeFacet.this.type();
        }

        @Override
        public CapabilityDirections directions() {
            return TestExchangeFacet.this.directions();
        }

        @Override
        public Set<Class<? extends CapabilityFacet>> facets() {
            return Set.of(ExchangeFacet.class);
        }
    };

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
    public CapabilityType type() {
        return type;
    }

    @Override
    public CapabilityDirections directions() {
        return CapabilityDirections.input();
    }

    @Override
    public CapabilityView view() {
        return view;
    }

    @Override
    public CapabilityOperation prepare(CapabilityRequest request) {
        throw new UnsupportedOperationException("exchange fixture has no generic operation");
    }

    @Override
    public CapabilityOperation prepareExchange(double requested) {
        return transaction -> commit(requested, transaction);
    }

    private CapabilityResult commit(double requested, TransactionContext transaction) {
        if (!Double.isFinite(requested) || requested != Math.rint(requested)) return failed("exchange_delta");
        long amount = (long) Math.abs(requested);
        long moved = requested >= 0D ? storage.insert(amount, true) : storage.extract(amount, true);
        if (moved != amount) return failed("exchange_capacity");
        if (requested >= 0D) storage.insert(amount, transaction);
        else storage.extract(amount, transaction);
        return CapabilityResult.successful();
    }

    private static CapabilityResult failed(String reason) {
        ResourceLocation source = ResourceLocation.fromNamespaceAndPath("mmcr_test", "exchange");
        return CapabilityResult.failure(ExecutionStatus.blocked(
                ResourceLocation.fromNamespaceAndPath("mmcr_test", reason), source,
                FailureOccurrence.at(BuiltinFailureReasons.UNKNOWN, source, FailurePhase.CAPABILITY_COMMIT,
                        null, null, Map.of())));
    }
}
