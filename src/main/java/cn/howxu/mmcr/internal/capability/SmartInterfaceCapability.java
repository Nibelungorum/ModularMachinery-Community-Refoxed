package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.capability.storage.FloatValueStorage;
import cn.howxu.mmcr.util.IOType;

import java.util.Map;

/**
 * Bidirectional named-float capability backed by a smart interface.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SmartInterfaceCapability implements MachineCapability {
    private static final CapabilityType TYPE = new CapabilityType(MMCR.id("smart_interface"));
    private final FloatValueStorage storage;
    private final IOType ioType;
    private final CapabilityView view;

    public SmartInterfaceCapability(FloatValueStorage storage, IOType ioType) {
        if (storage == null) throw new IllegalArgumentException("storage must not be null");
        if (ioType == null) throw new IllegalArgumentException("ioType must not be null");
        this.storage = storage;
        this.ioType = ioType;
        this.view = CapabilityFactories.view(TYPE, ioType);
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
        if (!(request instanceof CapabilityRequests.SmartValueRequest smart)
                || !TYPE.equals(request.type())
                || smart.ioType() != ioType) {
            return ignored -> CapabilityResult.failure(new ExecutionStatus(TYPE.id(), StatusSeverity.BLOCKED,
                    TYPE.id(), Map.of("reason", "unsupported_request")));
        }
        return transaction -> storage.set(smart.interfaceType(), smart.value(), transaction)
                ? CapabilityResult.successful()
                : CapabilityResult.failure(new ExecutionStatus(TYPE.id(), StatusSeverity.BLOCKED,
                        TYPE.id(), Map.of("reason", "smart_value")));
    }
}
