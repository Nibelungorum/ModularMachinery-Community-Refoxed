package cn.howxu.mmcr.compat.ae2lt.loaded;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.NativeCapabilityOperation;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.compat.ae2lt.LightningViewFacet;
import cn.howxu.mmcr.compat.ae2lt.LightningFailureReasons;
import cn.howxu.mmcr.compat.ae2lt.LightningRecipeTypes;
import cn.howxu.mmcr.compat.ae2lt.LightningStorage;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Internal ME-only operations; deliberately exposes no adjacent transfer facet.
 * @author howxu <dev@howxu.cn>
 */
public final class LightningCapability implements MachineCapability, LightningViewFacet, OperationFacet {
    private final IOPortBlockEntity host;
    private final LightningStorage storage;
    private final IOType direction;
    private final CapabilityView view;

    public LightningCapability(LightningStorage storage, IOType direction) { this(null, storage, direction); }
    public LightningCapability(IOPortBlockEntity host, LightningStorage storage, IOType direction) {
        this.host = host;
        this.storage = Objects.requireNonNull(storage, "storage");
        this.direction = Objects.requireNonNull(direction, "direction");
        view = CapabilityFactories.view(type(), directions(), Set.of(LightningViewFacet.class, OperationFacet.class));
    }

    @Override public CapabilityType type() { return new CapabilityType(LightningRecipeTypes.LIGHTNING); }
    @Override public CapabilityDirections directions() { return CapabilityDirections.of(direction); }
    @Override public CapabilityView view() { return view; }
    @Override public LightningStorage storage() { return storage; }
    @Override public int outputPriority() { return host == null ? 0 : host.kind().outputPriority(); }
    @Override public CapabilityOperation prepare(CapabilityRequest request) { return CapabilityFactories.operation(this, request); }

    @Override public CapabilityOperation prepareOperation(CapabilityRequest request) {
        if (!(request instanceof CapabilityRequests.ResourceRequest<?> resources) || !type().equals(resources.type())
                || direction != resources.ioType()) return (NativeCapabilityOperation) () -> failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        return (NativeCapabilityOperation) () -> {
            for (var action : resources.actions()) {
                if (!(action.resource() instanceof LightningTier tier) || action.insert() != (direction == IOType.OUTPUT)
                        || !action.insert() && (action.slot() < 0 || action.slot() >= storage.size())) {
                    return failure(BuiltinFailureReasons.WRONG_RESOURCE_TYPE);
                }
                long simulated = action.insert() ? storage.insert(tier, action.amount(), true)
                        : storage.extract(action.slot(), tier, action.amount(), true);
                if (simulated != action.amount()) return failure(action.insert() ? LightningFailureReasons.OUTPUT_BLOCKED : LightningFailureReasons.INPUT_MISSING);
                long moved = action.insert() ? storage.insert(tier, action.amount(), false)
                        : storage.extract(action.slot(), tier, action.amount(), false);
                if (moved != action.amount()) return failure(action.insert() ? LightningFailureReasons.OUTPUT_BLOCKED : LightningFailureReasons.INPUT_MISSING);
            }
            return CapabilityResult.successful();
        };
    }

    private CapabilityResult failure(FailureReason reason) {
        return CapabilityResult.failure(ExecutionStatus.blocked(type().id(), type().id(),
                FailureOccurrence.at(reason, type().id(), FailurePhase.CAPABILITY_COMMIT, null, null, Map.of())));
    }
}
