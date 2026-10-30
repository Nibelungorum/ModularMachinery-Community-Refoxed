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
import cn.howxu.mmcr.api.capability.facet.ResourceFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.compat.ae2lt.LightningRecipeTypes;
import cn.howxu.mmcr.compat.ae2lt.LightningFailureReasons;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.internal.capability.FixedExecutionFacets;
import cn.howxu.mmcr.internal.capability.NativeAsyncResourceValues;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.Set;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Internal ME-only lightning resource operations; deliberately exposes no adjacent transfer facet.
 * @author howxu <dev@howxu.cn> */
public final class LightningCapability implements MachineCapability, ResourceFacet<LightningTier>, OperationFacet, FixedExecutionFacets {
    private final IOPortBlockEntity host;
    private final ResourceStorage<LightningTier> storage;
    private final IOType ioType;
    private final CapabilityView view;
    private final AsyncPlanningFacet asyncPlanning;

    public LightningCapability(ResourceStorage<LightningTier> storage, IOType ioType) {
        this(null, storage, ioType);
    }

    public LightningCapability(IOPortBlockEntity host, ResourceStorage<LightningTier> storage, IOType ioType) {
        this.host = host;
        this.storage = Objects.requireNonNull(storage, "storage");
        this.ioType = Objects.requireNonNull(ioType, "ioType");
        this.asyncPlanning = new AsyncPlanningFacet() {
            @Override
            public Object planningIdentity() { return storage.reservationIdentity(); }

            @Override
            protected AsyncCapabilitySnapshot captureSnapshotOnServerThread() {
                return new AsyncCapabilitySnapshot.Resource(type().id(), IntStream.range(0, storage.size())
                        .mapToObj(slot -> {
                            LightningTier tier = storage.resource(slot);
                            return new AsyncCapabilitySnapshot.ResourceSlot(tier == null ? Optional.empty()
                                    : Optional.of(NativeAsyncResourceValues.lightning(tier)),
                                    tier == null ? 0L : storage.amount(slot), storage.capacity(slot, tier));
                        }).toList());
            }

            @Override
            protected AsyncCapabilityPlanner workerPlannerOnServerThread() {
                return new AsyncCapabilityPlanner.Resource(type().id());
            }

            @Override
            protected CapabilityResult commitOnServerThread(AsyncCapabilityOperation operation, TransactionContext transaction) {
                return commitAsync(operation, transaction);
            }
        };
        this.view = CapabilityFactories.view(type(), directions(),
                Set.of(ResourceFacet.class, OperationFacet.class, AsyncPlanningFacet.class));
    }

    public CapabilityType type() { return new CapabilityType(LightningRecipeTypes.LIGHTNING); }
    public CapabilityDirections directions() { return CapabilityDirections.of(ioType); }
    public CapabilityView view() { return view; }
    public ResourceStorage<LightningTier> storage() { return storage; }
    public Class<LightningTier> resourceType() { return LightningTier.class; }
    public int outputPriority() { return host == null ? 0 : host.kind().outputPriority(); }

    @Override
    public <F extends CapabilityFacet> Optional<F> facet(Class<F> facetType) {
        if (facetType == AsyncPlanningFacet.class) return Optional.of(facetType.cast(asyncPlanning));
        return MachineCapability.super.facet(facetType);
    }

    @Override
    public CapabilityOperation prepare(CapabilityRequest request) {
        return CapabilityFactories.operation(this, request);
    }

    @Override
    public CapabilityOperation prepareOperation(CapabilityRequest request) {
        if (!(request instanceof CapabilityRequests.ResourceRequest<?> resources)) {
            return ignored -> failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        return new CapabilityOperation() {
            @Override
            public CapabilityResult commit(TransactionContext transaction) {
                try (Transaction nested = Transaction.open(transaction)) {
                    for (var action : resources.actions()) {
                        if (!(action.resource() instanceof LightningTier tier)) {
                            return failure(BuiltinFailureReasons.WRONG_RESOURCE_TYPE);
                        }
                        long moved = action.insert()
                                ? storage.insert(action.slot(), tier, action.amount(), nested)
                                : storage.extract(action.slot(), tier, action.amount(), nested);
                        if (moved != action.amount()) return failure(action.insert()
                                ? LightningFailureReasons.OUTPUT_BLOCKED : LightningFailureReasons.INPUT_MISSING);
                    }
                    nested.commit();
                }
                return CapabilityResult.successful();
            }

            public CapabilityOperation forParallelism(long parallelism) {
                return parallelism == resources.parallelism() ? this : null;
            }
        };
    }

    private CapabilityResult commitAsync(AsyncCapabilityOperation operation, TransactionContext transaction) {
        try (Transaction nested = Transaction.open(transaction)) {
            if (operation instanceof AsyncCapabilityOperation.Group group) {
                for (AsyncCapabilityOperation child : group.operations()) {
                    CapabilityResult result = commitAsync(child, nested);
                    if (!result.success()) return result;
                }
            } else if (operation instanceof AsyncCapabilityOperation.Resource resource
                    && type().id().equals(resource.capabilityId())
                    && directions().supports(resource.insert() ? IOType.OUTPUT : IOType.INPUT)
                    && resource.slot() < storage.size()) {
                LightningTier tier;
                try {
                    tier = NativeAsyncResourceValues.lightning(resource.resource());
                } catch (IllegalArgumentException exception) {
                    return failure(BuiltinFailureReasons.WRONG_RESOURCE_TYPE);
                }
                long moved = resource.insert()
                        ? storage.insert(resource.slot(), tier, resource.amount(), nested)
                        : storage.extract(resource.slot(), tier, resource.amount(), nested);
                if (moved != resource.amount()) return failure(resource.insert()
                        ? LightningFailureReasons.OUTPUT_BLOCKED : LightningFailureReasons.INPUT_MISSING);
            } else return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
            nested.commit();
            return CapabilityResult.successful();
        }
    }

    private CapabilityResult failure(FailureReason reason) {
        return CapabilityResult.failure(ExecutionStatus.blocked(type().id(), type().id(),
                FailureOccurrence.at(reason, type().id(), FailurePhase.CAPABILITY_COMMIT, null, null, Map.of())));
    }
}
