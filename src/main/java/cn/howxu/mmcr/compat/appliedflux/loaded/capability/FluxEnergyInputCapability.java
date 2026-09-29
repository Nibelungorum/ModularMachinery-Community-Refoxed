package cn.howxu.mmcr.compat.appliedflux.loaded.capability;

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
import cn.howxu.mmcr.api.capability.facet.PersistenceFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.RecipeEnergyPrefetchFacet;
import cn.howxu.mmcr.api.capability.facet.ScalarFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.compat.appliedflux.loaded.storage.FluxEnergyBuffer;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Input energy capability backed solely by a persistent local Flux cache.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluxEnergyInputCapability implements MachineCapability, ScalarFacet, ValueFacet<LongValueStorage>,
        OperationFacet, PresentationFacet, PersistenceFacet, SyncFacet, RecipeEnergyPrefetchFacet {
    private final FluxEnergyBuffer buffer;
    private final String reservationKey;
    private final ExactPrefetchBridge prefetchBridge;
    private final BooleanSupplier prefetchAvailable;
    private final CapabilityView view;
    private final AsyncPlanningFacet asyncPlanning;

    public FluxEnergyInputCapability(FluxEnergyBuffer buffer, String reservationKey) {
        this(buffer, reservationKey, null);
    }

    public FluxEnergyInputCapability(FluxEnergyBuffer buffer, String reservationKey, ExactPrefetchBridge prefetchBridge) {
        this(buffer, reservationKey, prefetchBridge, () -> true);
    }

    public FluxEnergyInputCapability(FluxEnergyBuffer buffer, String reservationKey, ExactPrefetchBridge prefetchBridge,
                                     BooleanSupplier prefetchAvailable) {
        if (buffer == null) throw new IllegalArgumentException("buffer must not be null");
        if (reservationKey == null || reservationKey.isBlank()) throw new IllegalArgumentException("reservationKey must not be blank");
        if (prefetchAvailable == null) throw new IllegalArgumentException("prefetchAvailable must not be null");
        this.buffer = buffer;
        this.reservationKey = reservationKey;
        this.prefetchBridge = prefetchBridge;
        this.prefetchAvailable = prefetchAvailable;
        asyncPlanning = new AsyncPlanningFacet() {
            @Override
            public Object planningIdentity() {
                return buffer;
            }

            @Override
            protected AsyncCapabilitySnapshot captureSnapshotOnServerThread() {
                return new AsyncCapabilitySnapshot.Scalar(type().id(), buffer.amount(), Long.MAX_VALUE, Long.MAX_VALUE);
            }

            @Override
            protected AsyncCapabilityPlanner workerPlannerOnServerThread() {
                return new AsyncCapabilityPlanner.Scalar(type().id());
            }

            @Override
            protected CapabilityResult commitOnServerThread(AsyncCapabilityOperation operation,
                                                             TransactionContext transaction) {
                return commitAsync(operation, transaction);
            }
        };
        view = CapabilityFactories.view(type(), CapabilityDirections.input(), Set.of(ScalarFacet.class,
                ValueFacet.class, OperationFacet.class, PresentationFacet.class, PersistenceFacet.class,
                SyncFacet.class, AsyncPlanningFacet.class, RecipeEnergyPrefetchFacet.class));
    }

    @Override
    public CapabilityType type() {
        return BuiltinCapabilityDefinitions.ENERGY_TYPE;
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
    public LongValueStorage storage() {
        return buffer.storage();
    }

    @Override
    public <F extends CapabilityFacet> Optional<F> facet(Class<F> facetType) {
        if (facetType == AsyncPlanningFacet.class) return Optional.of(facetType.cast(asyncPlanning));
        if (facetType == RecipeEnergyPrefetchFacet.class && !prefetchAvailable.getAsBoolean()) return Optional.empty();
        return MachineCapability.super.facet(facetType);
    }

    @Override
    public CapabilityOperation prepare(CapabilityRequest request) {
        return CapabilityFactories.operation(this, request);
    }

    @Override
    public CapabilityOperation prepareOperation(CapabilityRequest request) {
        if (!(request instanceof CapabilityRequests.ValueRequest valueRequest) || valueRequest.insert()) {
            return ignored -> failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        return transaction -> extractLocal(valueRequest.amount(), transaction);
    }

    @Override
    public CapabilityOperation prepareScalar(CapabilityRequest request) {
        return prepareOperation(request);
    }

    @Override
    public List<CapabilityDisplay> displays(CapabilityView ignored) {
        return List.of(new CapabilityDisplay("energy", Long.toString(buffer.amount()), "FE", Optional.empty()));
    }

    @Override
    public String stateKey() {
        return "appflux_energy_input";
    }

    @Override
    public void save(ValueOutput output) {
        buffer.save(output);
    }

    @Override
    public void load(ValueInput input) {
        buffer.load(input);
    }

    @Override
    public void encode(RegistryFriendlyByteBuf output) {
        output.writeLong(buffer.amount());
    }

    @Override
    public void decode(RegistryFriendlyByteBuf input) {
        buffer.setAmount(input.readLong());
    }

    @Override
    public String reservationKey() {
        return reservationKey;
    }

    @Override
    public Optional<PrefetchPlan> planPrefetch(long requestedAmount) {
        if (requestedAmount <= 0L || prefetchBridge == null) return Optional.empty();
        return prefetchBridge.planExactExtract(requestedAmount).map(bridgeOperation -> new PrefetchPlan(requestedAmount,
                transaction -> commitPrefetch(bridgeOperation, requestedAmount, transaction)));
    }

    @Override
    public void restoreReservation(long amount) {
        // Prefetch reservations are added only after the injected bridge operation commits.
    }

    @Override
    public long releaseReservation(long amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            long released = buffer.releaseReservation(amount, transaction);
            transaction.commit();
            return released;
        }
    }

    @Override
    public CapabilityResult consumeReservation(long amount, TransactionContext transaction) {
        return extractLocal(amount, transaction);
    }

    private CapabilityResult commitPrefetch(CapabilityOperation bridgeOperation, long requestedAmount,
                                            TransactionContext transaction) {
        long cached = buffer.amount();
        long capacity = buffer.storage().capacity();
        if (requestedAmount > capacity - cached) return failure(BuiltinFailureReasons.MISSING_INPUT);
        try (Transaction nested = Transaction.open(transaction)) {
            CapabilityResult result = bridgeOperation.commit(nested);
            if (result == null || !result.success()) return result == null ? failure(BuiltinFailureReasons.MISSING_INPUT) : result;
            if (buffer.insert(requestedAmount, nested) != requestedAmount) return failure(BuiltinFailureReasons.MISSING_INPUT);
            buffer.reserve(requestedAmount, nested);
            nested.commit();
        }
        return CapabilityResult.successful();
    }

    private CapabilityResult commitAsync(AsyncCapabilityOperation operation, TransactionContext transaction) {
        if (operation instanceof AsyncCapabilityOperation.Group(List<AsyncCapabilityOperation> operations)) {
            try (Transaction nested = Transaction.open(transaction)) {
                for (AsyncCapabilityOperation child : operations) {
                    CapabilityResult result = commitAsync(child, nested);
                    if (!result.success()) return result;
                }
                nested.commit();
            }
            return CapabilityResult.successful();
        }
        if (!(operation instanceof AsyncCapabilityOperation.Scalar(
                net.minecraft.resources.ResourceLocation capabilityId, long amount, boolean insert
        ))
                || !type().id().equals(capabilityId) || insert) {
            return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        return extractLocal(amount, transaction);
    }

    private CapabilityResult extractLocal(long amount, TransactionContext transaction) {
        if (buffer.amount() < amount) return failure(BuiltinFailureReasons.MISSING_INPUT);
        return buffer.extract(amount, transaction) == amount
                ? CapabilityResult.successful() : failure(BuiltinFailureReasons.MISSING_INPUT);
    }

    private CapabilityResult failure(FailureReason reason) {
        return CapabilityResult.failure(ExecutionStatus.blocked(type().id(), type().id(),
                FailureOccurrence.at(reason, type().id(), FailurePhase.CAPABILITY_COMMIT, null, null, Map.of())));
    }

    /**
     * Task 4 supplies the real network operation here without leaking AE2/AppFlux types into this capability.
     *
     * @author howxu <dev@howxu.cn>
     */
    @FunctionalInterface
    public interface ExactPrefetchBridge {
        Optional<CapabilityOperation> planExactExtract(long requestedAmount);
    }
}
