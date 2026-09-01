package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
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
public final class SmartInterfaceCapability implements MachineCapability, OperationFacet, PresentationFacet {
    private static final CapabilityType TYPE = new CapabilityType(MMCR.id("smart_interface"));
    private final FloatValueStorage storage;
    private final IOType ioType;
    private final CapabilityView view;

    public SmartInterfaceCapability(FloatValueStorage storage, IOType ioType) {
        if (storage == null) throw new IllegalArgumentException("storage must not be null");
        if (ioType == null) throw new IllegalArgumentException("ioType must not be null");
        this.storage = storage;
        this.ioType = ioType;
        this.view = CapabilityFactories.view(TYPE, ioType, Set.of(OperationFacet.class, PresentationFacet.class));
    }

    @Override
    public CapabilityType type() {
        return TYPE;
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
    public FloatValueStorage storage() {
        return storage;
    }

    @Override
    public CapabilityOperation prepare(CapabilityRequest request) {
        if (!(request instanceof CapabilityRequests.SmartValueRequest)
                || !TYPE.equals(request.type())
                || request.ioType() != ioType) {
            return ignored -> failure("unsupported_request");
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
            return ignored -> failure("unsupported_request");
        }
        return transaction -> storage.set(smart.interfaceType(), smart.value(), transaction)
                ? CapabilityResult.successful()
                : failure("smart_value");
    }

    private CapabilityResult failure(String reason) {
        return CapabilityResult.failure(new ExecutionStatus(TYPE.id(), StatusSeverity.BLOCKED,
                TYPE.id(), Map.of("reason", reason)));
    }
}
