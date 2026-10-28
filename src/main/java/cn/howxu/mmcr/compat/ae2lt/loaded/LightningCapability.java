package cn.howxu.mmcr.compat.ae2lt.loaded;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityPlanner;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
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
import cn.howxu.mmcr.internal.capability.NativeAsyncResourceValues;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/** Internal ME-only operations; deliberately exposes no adjacent transfer facet.
 * @author howxu <dev@howxu.cn>
 */
public final class LightningCapability implements MachineCapability, LightningViewFacet, OperationFacet {
    private final IOPortBlockEntity host;
    private final LightningStorage storage;
    private final IOType direction;
    private final CapabilityView view;
    private final AsyncPlanningFacet asyncPlanning;

    public LightningCapability(LightningStorage storage, IOType direction) { this(null, storage, direction); }
    public LightningCapability(IOPortBlockEntity host, LightningStorage storage, IOType direction) {
        this.host = host;
        this.storage = Objects.requireNonNull(storage, "storage");
        this.direction = Objects.requireNonNull(direction, "direction");
        asyncPlanning = new AsyncPlanningFacet() {
            @Override public Object planningIdentity() { return storage.identity(); }

            @Override
            protected AsyncCapabilitySnapshot captureSnapshotOnServerThread() {
                return new AsyncCapabilitySnapshot.Resource(type().id(), IntStream.range(0, storage.size())
                        .mapToObj(slot -> {
                            LightningTier tier = tier(slot);
                            long amount = storage.amount(slot);
                            long capacity = tier == null ? emptyCapacity(slot) : storage.capacity(slot, tier);
                            return new AsyncCapabilitySnapshot.ResourceSlot(tier == null ? Optional.empty()
                                    : Optional.of(NativeAsyncResourceValues.lightning(tier)), amount,
                                    Math.max(amount, capacity));
                        }).toList());
            }

            @Override protected AsyncCapabilityPlanner workerPlannerOnServerThread() {
                return new AsyncCapabilityPlanner.Resource(type().id());
            }

            @Override protected CapabilityResult commitNativeOnServerThread(AsyncCapabilityOperation operation) {
                return commitAsync(operation);
            }
        };
        view = CapabilityFactories.view(type(), directions(),
                Set.of(LightningViewFacet.class, OperationFacet.class, AsyncPlanningFacet.class));
    }

    @Override public CapabilityType type() { return new CapabilityType(LightningRecipeTypes.LIGHTNING); }
    @Override public CapabilityDirections directions() { return CapabilityDirections.of(direction); }
    @Override public CapabilityView view() { return view; }
    @Override public LightningStorage storage() { return storage; }
    @Override public int outputPriority() { return host == null ? 0 : host.kind().outputPriority(); }

    @Override
    public <F extends CapabilityFacet> Optional<F> facet(Class<F> facetType) {
        if (facetType == AsyncPlanningFacet.class) return Optional.of(facetType.cast(asyncPlanning));
        return MachineCapability.super.facet(facetType);
    }

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

    private CapabilityResult commitAsync(AsyncCapabilityOperation operation) {
        List<AsyncCapabilityOperation.Resource> committed = new ArrayList<>();
        CapabilityResult result = commitAsync(operation, committed);
        if (!result.success()) rollback(committed);
        return result;
    }

    private CapabilityResult commitAsync(AsyncCapabilityOperation operation,
                                         List<AsyncCapabilityOperation.Resource> committed) {
        if (operation instanceof AsyncCapabilityOperation.Group group) {
            for (AsyncCapabilityOperation child : group.operations()) {
                CapabilityResult result = commitAsync(child, committed);
                if (!result.success()) return result;
            }
            return CapabilityResult.successful();
        }
        if (!(operation instanceof AsyncCapabilityOperation.Resource resource)
                || !type().id().equals(resource.capabilityId())) return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        boolean insert = resource.insert();
        if (direction != (insert ? IOType.OUTPUT : IOType.INPUT) || resource.slot() >= storage.size()) {
            return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        LightningTier tier;
        try {
            tier = NativeAsyncResourceValues.lightning(resource.resource());
        } catch (IllegalArgumentException exception) {
            return failure(BuiltinFailureReasons.WRONG_RESOURCE_TYPE);
        }
        long simulated = insert ? storage.insert(tier, resource.amount(), true)
                : storage.extract(resource.slot(), tier, resource.amount(), true);
        if (simulated != resource.amount()) return failure(insert
                ? LightningFailureReasons.OUTPUT_BLOCKED : LightningFailureReasons.INPUT_MISSING);
        long moved = insert ? storage.insert(tier, resource.amount(), false)
                : storage.extract(resource.slot(), tier, resource.amount(), false);
        if (moved > 0L) committed.add(new AsyncCapabilityOperation.Resource(resource.capabilityId(), resource.slot(),
                resource.resource(), moved, insert));
        if (moved != resource.amount()) return failure(insert
                ? LightningFailureReasons.OUTPUT_BLOCKED : LightningFailureReasons.INPUT_MISSING);
        return CapabilityResult.successful();
    }

    private void rollback(List<AsyncCapabilityOperation.Resource> committed) {
        for (int index = committed.size() - 1; index >= 0; index--) {
            var operation = committed.get(index);
            LightningTier tier = NativeAsyncResourceValues.lightning(operation.resource());
            long restored = operation.insert() ? storage.extract(operation.slot(), tier, operation.amount(), false)
                    : storage.insert(tier, operation.amount(), false);
            if (restored != operation.amount()) throw new IllegalStateException("Unable to roll back lightning commit");
        }
    }

    private LightningTier tier(int slot) {
        Object stored = storage.storedKey(slot);
        for (LightningTier tier : LightningTier.values()) {
            if (Objects.equals(storage.key(tier), stored)) return tier;
        }
        return null;
    }

    private long emptyCapacity(int slot) {
        long capacity = 0L;
        for (LightningTier tier : LightningTier.values()) {
            // Slot-specific output capacities are captured separately for each search rule.
            capacity = Math.max(capacity, storage.capacity(slot, tier));
        }
        return capacity;
    }

    private CapabilityResult failure(FailureReason reason) {
        return CapabilityResult.failure(ExecutionStatus.blocked(type().id(), type().id(),
                FailureOccurrence.at(reason, type().id(), FailurePhase.CAPABILITY_COMMIT, null, null, Map.of())));
    }
}
