package cn.howxu.mmcr.compat.appliedflux.loaded.capability;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
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
import cn.howxu.mmcr.api.capability.plan.NativeCapabilityOperation;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * Output energy capability that retains accepted recipe energy locally until
 * AppFlux accepts it through an immediate AE2 modulation.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluxEnergyOutputCapability implements MachineCapability, ScalarFacet, ValueFacet<LongValueStorage>,
        OperationFacet, PresentationFacet, PersistenceFacet, SyncFacet, EnergyOutputAdmissionFacet {
    private static final String ADMISSION_KEY = "energy";

    private final FluxEnergyBuffer pending;
    private final LongValueStorage admissionBudget = new LongValueStorage(Long.MAX_VALUE, Long.MAX_VALUE, null);
    private final CapabilityView view;

    public FluxEnergyOutputCapability(FluxEnergyBuffer pending) {
        if (pending == null) throw new IllegalArgumentException("pending must not be null");
        this.pending = pending;
        view = CapabilityFactories.view(type(), CapabilityDirections.output(), Set.of(ScalarFacet.class,
                ValueFacet.class, OperationFacet.class, PresentationFacet.class, PersistenceFacet.class,
                SyncFacet.class, EnergyOutputAdmissionFacet.class));
    }

    public void refreshAdmissionBudget(long networkCapacity) { admissionBudget.setAmount(networkCapacity); }
    public long admissionBudget() { return admissionBudget.amount(); }
    @Override public CapabilityType type() { return BuiltinCapabilityDefinitions.ENERGY_TYPE; }
    @Override public CapabilityDirections directions() { return CapabilityDirections.output(); }
    @Override public int outputPriority() { return Integer.MAX_VALUE; }
    @Override public CapabilityView view() { return view; }
    @Override public LongValueStorage storage() { return pending.storage(); }
    @Override public CapabilityOperation prepare(CapabilityRequest request) { return CapabilityFactories.operation(this, request); }
    @Override public CapabilityOperation prepareScalar(CapabilityRequest request) { return prepareOperation(request); }
    @Override public List<CapabilityDisplay> displays(CapabilityView ignored) {
        return List.of(new CapabilityDisplay("energy", Long.toString(pending.amount()), "FE/t", Optional.empty()));
    }
    @Override public String stateKey() { return "appflux_energy_output"; }
    @Override public void save(CompoundTag output, HolderLookup.Provider registries) { pending.save(output); }
    @Override public void load(CompoundTag input, HolderLookup.Provider registries) {
        pending.load(input);
        admissionBudget.setAmount(0L);
    }
    @Override public void encode(RegistryFriendlyByteBuf output) { output.writeLong(pending.amount()); }
    @Override public void decode(RegistryFriendlyByteBuf input) { pending.setAmount(input.readLong()); }

    @Override
    public CapabilityOperation prepareOperation(CapabilityRequest request) {
        if (!(request instanceof CapabilityRequests.ValueRequest valueRequest) || !valueRequest.insert()) {
            return (NativeCapabilityOperation) () -> failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        return (NativeCapabilityOperation) () -> commitAdmission(valueRequest.amount());
    }

    @Override
    public long outputCapacity(PlanningReservations reservations) {
        if (reservations == null) throw new IllegalArgumentException("reservations must not be null");
        return reservations.outputAvailable(this, ADMISSION_KEY, admissionAvailable());
    }

    @Override
    public OutputPlan planOutput(long requestedAmount, PlanningReservations reservations, boolean materialize) {
        if (requestedAmount <= 0L || outputCapacity(reservations) < requestedAmount
                || !reservations.reserveOutput(this, ADMISSION_KEY, requestedAmount)) return new OutputPlan(0L, null);
        return new OutputPlan(requestedAmount, materialize
                ? (NativeCapabilityOperation) () -> commitAdmission(requestedAmount) : null);
    }

    private CapabilityResult commitAdmission(long amount) {
        if (admissionAvailable() < amount || admissionBudget.extract(amount, false) != amount
                || pending.insert(amount) != amount) return failure(BuiltinFailureReasons.MISSING_OUTPUT);
        return CapabilityResult.successful();
    }

    private long admissionAvailable() {
        return Math.max(0L, admissionBudget.amount() - pending.amount());
    }

    private CapabilityResult failure(FailureReason reason) {
        return CapabilityResult.failure(ExecutionStatus.blocked(type().id(), type().id(),
                FailureOccurrence.at(reason, type().id(), FailurePhase.CAPABILITY_COMMIT, null, null, Map.of())));
    }
}
