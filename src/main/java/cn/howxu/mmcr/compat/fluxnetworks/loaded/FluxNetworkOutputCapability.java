package cn.howxu.mmcr.compat.fluxnetworks.loaded;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.EnergyOutputAdmissionFacet;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.ScalarFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.NativeCapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.util.IOType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Plans recipe output against the native Plug's non-linear buffer admission.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluxNetworkOutputCapability implements MachineCapability, ScalarFacet, ValueFacet<LongValueStorage>,
        OperationFacet, PresentationFacet, EnergyOutputAdmissionFacet {
    private static final int BUFFER_SLOT = 0;
    private static final String ENERGY_KEY = "energy";
    private final FluxNetworkPlugHandler handler;
    private final CapabilityView view;

    public FluxNetworkOutputCapability(FluxNetworkPlugHandler handler) {
        this.handler = handler;
        view = CapabilityFactories.view(type(), CapabilityDirections.output(), Set.of(ScalarFacet.class,
                ValueFacet.class, OperationFacet.class, PresentationFacet.class, EnergyOutputAdmissionFacet.class));
    }

    @Override public CapabilityType type() { return BuiltinCapabilityDefinitions.ENERGY_TYPE; }
    @Override public CapabilityDirections directions() { return CapabilityDirections.output(); }
    @Override public CapabilityView view() { return view; }
    @Override public int outputPriority() { return Integer.MAX_VALUE; }
    @Override public boolean supportsSplitOutput() { return true; }
    @Override public LongValueStorage storage() { return handler.storage(); }
    @Override public CapabilityOperation prepare(CapabilityRequest request) { return CapabilityFactories.operation(this, request); }
    @Override public CapabilityOperation prepareScalar(CapabilityRequest request) { return prepareOperation(request); }
    @Override public List<CapabilityDisplay> displays(CapabilityView ignored) {
        return List.of(new CapabilityDisplay("energy", Long.toString(handler.getBuffer()), "FE", Optional.empty()));
    }

    @Override
    public long outputCapacity(PlanningReservations reservations) {
        return handler.admissionAt(reservations.nativeAmount(handler, BUFFER_SLOT, handler.getBuffer()));
    }

    @Override
    public OutputPlan planOutput(long amount, PlanningReservations reservations, boolean materialize) {
        if (amount <= 0L || outputCapacity(reservations) < amount
                || !reservations.reserveNativeInsert(handler, BUFFER_SLOT, ENERGY_KEY, ENERGY_KEY,
                        handler.getBuffer(), Long.MAX_VALUE, amount)) return new OutputPlan(0L, null);
        return new OutputPlan(amount, materialize ? (NativeCapabilityOperation) () -> commit(amount) : null);
    }

    @Override
    public CapabilityOperation prepareOperation(CapabilityRequest request) {
        if (!(request instanceof CapabilityRequests.ValueRequest value) || !value.insert()
                || value.ioType() != IOType.OUTPUT) {
            return (NativeCapabilityOperation) () -> failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        return (NativeCapabilityOperation) () -> commit(value.amount());
    }

    private CapabilityResult commit(long amount) {
        return handler.acceptRecipeEnergy(amount) ? CapabilityResult.successful() : failure(BuiltinFailureReasons.MISSING_OUTPUT);
    }

    private CapabilityResult failure(FailureReason reason) {
        return CapabilityResult.failure(ExecutionStatus.blocked(type().id(), type().id(),
                FailureOccurrence.at(reason, type().id(), FailurePhase.CAPABILITY_COMMIT, null, null, Map.of())));
    }
}
