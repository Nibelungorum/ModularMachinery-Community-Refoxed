package cn.howxu.mmcr.compat.botania;

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
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.ScalarFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.NativeCapabilityOperation;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.compat.botania.ManaViewFacet;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Prepares mana transfers against the pool's shared physical store.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ManaPortCapability implements MachineCapability, ManaViewFacet, ScalarFacet,
        OperationFacet, PresentationFacet, SyncFacet {
    private final @Nullable IOPortBlockEntity host;
    private final ManaStorage storage;
    private final IOType ioType;
    private final CapabilityView view;
    private final AsyncPlanningFacet asyncPlanning;

    public ManaPortCapability(@Nullable IOPortBlockEntity host, ManaStorage storage, IOType ioType) {
        if (storage == null) throw new IllegalArgumentException("storage must not be null");
        if (ioType == null) throw new IllegalArgumentException("ioType must not be null");
        this.host = host;
        this.storage = storage;
        this.ioType = ioType;
        asyncPlanning = new AsyncPlanningFacet() {
            @Override public Object planningIdentity() { return storage.identity(); }

            @Override
            protected AsyncCapabilitySnapshot captureSnapshotOnServerThread() {
                return new AsyncCapabilitySnapshot.Scalar(BotaniaManaIds.MANA, amount(), capacity(), capacity());
            }

            @Override protected AsyncCapabilityPlanner workerPlannerOnServerThread() {
                return new AsyncCapabilityPlanner.Scalar(BotaniaManaIds.MANA);
            }

            @Override protected CapabilityResult commitNativeOnServerThread(AsyncCapabilityOperation operation) {
                return commitAsync(operation);
            }
        };
        view = CapabilityFactories.view(type(), directions(), Set.of(ManaViewFacet.class, ScalarFacet.class,
                OperationFacet.class, PresentationFacet.class, SyncFacet.class, AsyncPlanningFacet.class));
    }

    @Override public CapabilityType type() { return BotaniaManaIds.TYPE; }
    @Override public CapabilityDirections directions() { return CapabilityDirections.of(ioType); }
    @Override public CapabilityView view() { return view; }
    @Override public long amount() { return storage.amount(); }
    @Override public long capacity() { return storage.capacity(); }
    @Override public Object queryIdentity() { return storage.identity(); }
    @Override public int outputPriority() { return host == null ? 0 : host.kind().outputPriority(); }
    @Override public CapabilityOperation prepare(CapabilityRequest request) { return CapabilityFactories.operation(this, request); }
    @Override public CapabilityOperation prepareScalar(CapabilityRequest request) { return prepareOperation(request); }

    @Override
    public <F extends CapabilityFacet> Optional<F> facet(Class<F> facetType) {
        if (facetType == AsyncPlanningFacet.class) return Optional.of(facetType.cast(asyncPlanning));
        return MachineCapability.super.facet(facetType);
    }

    @Override
    public CapabilityOperation prepareOperation(CapabilityRequest request) {
        if (!(request instanceof CapabilityRequests.ValueRequest valueRequest)) {
            return (NativeCapabilityOperation) () -> failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        return (NativeCapabilityOperation) () -> commitNative(valueRequest);
    }

    @Override
    public List<CapabilityDisplay> displays(CapabilityView ignored) {
        return List.of(new CapabilityDisplay(BotaniaManaIds.MANA.toString(), Long.toString(amount()), "",
                Optional.empty()));
    }

    @Override
    public void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(storage.amount());
        buffer.writeInt(storage.capacity());
    }

    @Override
    public void decode(RegistryFriendlyByteBuf buffer) {
        int amount = buffer.readInt();
        int capacity = buffer.readInt();
        if (amount < 0 || capacity < amount || capacity != storage.capacity()) {
            throw new IllegalArgumentException("Invalid mana sync state");
        }
        storage.setAmount(amount);
    }

    private CapabilityResult commitAsync(AsyncCapabilityOperation operation) {
        List<AsyncCapabilityOperation> operations = operation instanceof AsyncCapabilityOperation.Group group
                ? group.operations() : List.of(operation);
        boolean insert = ioType == IOType.OUTPUT;
        long amount = 0L;
        for (AsyncCapabilityOperation child : operations) {
            if (!(child instanceof AsyncCapabilityOperation.Scalar scalar)
                    || !BotaniaManaIds.MANA.equals(scalar.capabilityId()) || scalar.insert() != insert) {
                return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
            }
            amount = RequirementHandlerSupport.saturatingAdd(amount, scalar.amount());
        }
        return commitNative(new CapabilityRequests.ValueRequest(type(), ioType, 1L, amount, insert));
    }

    private CapabilityResult commitNative(CapabilityRequests.ValueRequest request) {
        boolean insert = ioType == IOType.OUTPUT;
        if (!request.type().equals(BotaniaManaIds.TYPE) || request.ioType() != ioType || request.insert() != insert) {
            return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        long possible = storage.move(request.amount(), insert, true);
        if (possible != request.amount()) {
            return failure(insert ? ManaFailureReasons.OUTPUT_BLOCKED : ManaFailureReasons.INPUT_MISSING,
                    request.amount(), possible);
        }
        storage.move(request.amount(), insert, false);
        return CapabilityResult.successful();
    }

    private CapabilityResult failure(FailureReason reason) {
        return CapabilityResult.failure(ExecutionStatus.blocked(BotaniaManaIds.MANA, BotaniaManaIds.MANA,
                FailureOccurrence.at(reason, BotaniaManaIds.MANA, FailurePhase.CAPABILITY_COMMIT,
                        null, null, Map.of())));
    }

    private CapabilityResult failure(FailureReason reason, long required, long available) {
        Map<String, String> details = Map.of("required", Long.toString(required),
                "available", Long.toString(Math.max(0L, available)),
                "shortfall", Long.toString(Math.max(0L, required - available)));
        return CapabilityResult.failure(ExecutionStatus.blocked(BotaniaManaIds.MANA, BotaniaManaIds.MANA,
                FailureOccurrence.at(reason, BotaniaManaIds.MANA, FailurePhase.CAPABILITY_COMMIT,
                        null, null, details)));
    }
}
