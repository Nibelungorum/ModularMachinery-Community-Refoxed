package cn.howxu.mmcr.test.capability;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.ScalarFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Map;
import java.util.Set;

/**
 * Scalar fixture whose requested delta is applied only when its transaction commits.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class TestScalarFacet implements MachineCapability, ScalarFacet {
    private final LongValueStorage storage = new LongValueStorage(10L, 10L, null);
    private final IOType ioType;
    private final CapabilityType type;
    private final CapabilityView view = new CapabilityView() {
        @Override
        public CapabilityType type() {
            return TestScalarFacet.this.type();
        }

        @Override
        public IOType ioType() {
            return TestScalarFacet.this.ioType();
        }

        @Override
        public Set<Class<? extends CapabilityFacet>> facets() {
            return Set.of(ScalarFacet.class);
        }
    };

    public TestScalarFacet() {
        this(IOType.OUTPUT);
    }

    public TestScalarFacet(IOType ioType) {
        if (ioType == null) throw new IllegalArgumentException("ioType must not be null");
        this.ioType = ioType;
        this.type = new CapabilityType(Identifier.fromNamespaceAndPath("mmcr_test", "scalar_" + ioType.name().toLowerCase()));
    }

    public long amount() {
        return storage.amount();
    }

    public long insert(long amount, boolean simulate) {
        return storage.insert(amount, simulate);
    }

    public void setAmount(long amount) {
        storage.setAmount(amount);
    }

    @Override
    public CapabilityOperation prepareScalar(CapabilityRequest request) {
        if (request == null || request.ioType() != ioType) return ignored -> failed("scalar_direction");
        return transaction -> commit(request.parallelism(), transaction);
    }

    private CapabilityResult commit(long delta, TransactionContext transaction) {
        if (delta <= 0L) return failed("scalar_delta");
        long moved = ioType == IOType.OUTPUT ? storage.insert(delta, true) : storage.extract(delta, true);
        if (moved != delta) return failed("scalar_capacity");
        if (ioType == IOType.OUTPUT) storage.insert(delta, transaction);
        else storage.extract(delta, transaction);
        return CapabilityResult.successful();
    }

    @Override
    public CapabilityType type() {
        return type;
    }

    @Override
    public IOType ioType() {
        return ioType;
    }

    @Override
    public CapabilityView view() {
        return view;
    }

    @Override
    public CapabilityOperation prepare(CapabilityRequest request) {
        return prepareScalar(request);
    }

    private static CapabilityResult failed(String reason) {
        return CapabilityResult.failure(new ExecutionStatus(Identifier.fromNamespaceAndPath("mmcr_test", reason),
                StatusSeverity.BLOCKED, Identifier.fromNamespaceAndPath("mmcr_test", "scalar"), Map.of()));
    }
}
