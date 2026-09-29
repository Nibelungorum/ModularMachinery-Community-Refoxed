package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.storage.FloatValueStorage;
import cn.howxu.mmcr.util.IOType;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Bidirectional named-float capability backed by a smart interface.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SmartInterfaceCapability implements MachineCapability, ValueFacet<FloatValueStorage>,
        OperationFacet, PresentationFacet {
    private static final CapabilityType TYPE = new CapabilityType(MMCR.id("smart_interface"));
    private final FloatValueStorage storage;
    private final IOType ioType;
    private final CapabilityView view;

    public SmartInterfaceCapability(FloatValueStorage storage, IOType ioType) {
        if (storage == null) throw new IllegalArgumentException("storage must not be null");
        if (ioType == null) throw new IllegalArgumentException("ioType must not be null");
        this.storage = storage;
        this.ioType = ioType;
        this.view = CapabilityFactories.view(TYPE, directions(),
                Set.of(ValueFacet.class, OperationFacet.class, PresentationFacet.class));
    }

    @Override
    public CapabilityType type() {
        return TYPE;
    }

    @Override
    public CapabilityDirections directions() {
        return CapabilityDirections.of(ioType);
    }

    @Override
    public CapabilityView view() {
        return view;
    }

    @Override
    public FloatValueStorage storage() {
        return storage;
    }

    @Override
    public CapabilityOperation prepare(CapabilityRequest request) {
        if (!(request instanceof CapabilityRequests.SmartValueRequest)
                || !TYPE.equals(request.type())
                || !directions().supports(request.ioType())) {
            return () -> failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        return CapabilityFactories.operation(this, request);
    }

    @Override
    public List<CapabilityDisplay> displays(CapabilityView ignored) {
        return storage.values().entrySet().stream()
                .map(entry -> new CapabilityDisplay(entry.getKey(), Float.toString(entry.getValue()), "value", Optional.empty()))
                .toList();
    }

    @Override
    public CapabilityOperation prepareOperation(CapabilityRequest request) {
        if (!(request instanceof CapabilityRequests.SmartValueRequest smart)) {
            return () -> failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        return () -> storage.setExisting(smart.interfaceType(), smart.value())
                ? CapabilityResult.successful()
                : failure(BuiltinFailureReasons.SMART_VALUE);
    }

    private CapabilityResult failure(FailureReason reason) {
        FailureOccurrence occurrence = FailureOccurrence.at(reason, TYPE.id(), FailurePhase.CAPABILITY_COMMIT,
                null, null, Map.of());
        return CapabilityResult.failure(ExecutionStatus.blocked(TYPE.id(), TYPE.id(), occurrence));
    }
}
