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
import cn.howxu.mmcr.api.capability.facet.EnergyOutputAdmissionFacet;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.PersistenceFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.ScalarFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Output energy capability that admits recipe output into a persistent local pending cache.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluxEnergyOutputCapability implements MachineCapability, ScalarFacet, ValueFacet<LongValueStorage>,
        OperationFacet, PresentationFacet, PersistenceFacet, SyncFacet, EnergyOutputAdmissionFacet {
    private static final String ADMISSION_KEY = "energy";

    private final FluxEnergyBuffer pending;
    private final LongValueStorage admissionBudget = new LongValueStorage(Long.MAX_VALUE, Long.MAX_VALUE, null);
    private final CapabilityView view;
    private final AsyncPlanningFacet asyncPlanning;

    public FluxEnergyOutputCapability(FluxEnergyBuffer pending) {
        if (pending == null) throw new IllegalArgumentException("pending must not be null");
        this.pending = pending;
        asyncPlanning = new AsyncPlanningFacet() {
            @Override
            public Object planningIdentity() {
                return pending;
            }

            @Override
            protected AsyncCapabilitySnapshot captureSnapshotOnServerThread() {
                long available = admissionAvailable();
                return new AsyncCapabilitySnapshot.Scalar(type().id(), 0L, available, available);
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
        view = CapabilityFactories.view(type(), CapabilityDirections.output(), Set.of(ScalarFacet.class,
                ValueFacet.class, OperationFacet.class, PresentationFacet.class, PersistenceFacet.class,
                SyncFacet.class, AsyncPlanningFacet.class, EnergyOutputAdmissionFacet.class));
    }

    /** Receives Task 4's exact network capacity snapshot; this class performs no network access itself. */
    public void refreshAdmissionBudget(long networkCapacity) {
        admissionBudget.setAmount(networkCapacity);
    }

    public long admissionBudget() {
        return admissionBudget.amount();
    }

    @Override
    public CapabilityType type() {
        return BuiltinCapabilityDefinitions.ENERGY_TYPE;
    }

    @Override
    public CapabilityDirections directions() {
        return CapabilityDirections.output();
    }

    @Override
    public int outputPriority() {
        return Integer.MAX_VALUE;
    }

    @Override
    public CapabilityView view() {
        return view;
    }

    @Override
    public LongValueStorage storage() {
        return pending.storage();
    }

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
        if (!(request instanceof CapabilityRequests.ValueRequest valueRequest) || !valueRequest.insert()) {
            return ignored -> failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        return transaction -> pending.insert(valueRequest.amount(), transaction) == valueRequest.amount()
                ? CapabilityResult.successful() : failure(BuiltinFailureReasons.MISSING_OUTPUT);
    }

    @Override
    public CapabilityOperation prepareScalar(CapabilityRequest request) {
        return prepareOperation(request);
    }

    @Override
    public List<CapabilityDisplay> displays(CapabilityView ignored) {
        return List.of(new CapabilityDisplay("energy", Long.toString(pending.amount()), "FE/t", Optional.empty()));
    }

    @Override
    public String stateKey() {
        return "appflux_energy_output";
    }

    @Override
    public void save(ValueOutput output) {
        pending.save(output);
    }

    @Override
    public void load(ValueInput input) {
        pending.load(input);
        admissionBudget.setAmount(0L);
    }

    @Override
    public void encode(RegistryFriendlyByteBuf output) {
        output.writeLong(pending.amount());
    }

    @Override
    public void decode(RegistryFriendlyByteBuf input) {
        pending.setAmount(input.readLong());
    }

    @Override
    public long outputCapacity(PlanningReservations reservations) {
        if (reservations == null) throw new IllegalArgumentException("reservations must not be null");
        return reservations.outputAvailable(this, ADMISSION_KEY, admissionAvailable());
    }

    @Override
    public OutputPlan planOutput(long requestedAmount, PlanningReservations reservations, boolean materialize) {
        if (requestedAmount < 0L) throw new IllegalArgumentException("requestedAmount must be non-negative");
        if (requestedAmount == 0L) return new OutputPlan(0L, null);
        if (outputCapacity(reservations) < requestedAmount
                || !reservations.reserveOutput(this, ADMISSION_KEY, requestedAmount)) {
            return new OutputPlan(0L, null);
        }
        CapabilityOperation operation = materialize ? transaction -> commitAdmission(requestedAmount, transaction) : null;
        return new OutputPlan(requestedAmount, operation);
    }

    private CapabilityResult commitAdmission(long amount, TransactionContext transaction) {
        if (admissionAvailable() < amount) {
            return failure(BuiltinFailureReasons.MISSING_OUTPUT);
        }
        try (Transaction nested = Transaction.open(transaction)) {
            if (admissionBudget.extract(amount, nested) != amount || pending.insert(amount, nested) != amount) {
                return failure(BuiltinFailureReasons.MISSING_OUTPUT);
            }
            nested.commit();
            return CapabilityResult.successful();
        }
    }

    private long admissionAvailable() {
        long pendingAmount = pending.amount();
        long budget = admissionBudget.amount();
        return budget <= pendingAmount ? 0L : budget - pendingAmount;
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
                || !type().id().equals(capabilityId) || !insert) {
            return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        return commitAdmission(amount, transaction);
    }

    private CapabilityResult failure(FailureReason reason) {
        return CapabilityResult.failure(ExecutionStatus.blocked(type().id(), type().id(),
                FailureOccurrence.at(reason, type().id(), FailurePhase.CAPABILITY_COMMIT, null, null, Map.of())));
    }
}
