package cn.howxu.mmcr.compat.fluxnetworks.loaded;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.RecipeEnergyPrefetchFacet;
import cn.howxu.mmcr.api.capability.facet.ScalarFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
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
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.util.IOType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Internal recipe input over the native Flux Point buffer, with no external transfer exposure.
 * The reservation key supplier also checks that mutations run on the server thread.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluxNetworkInputCapability implements MachineCapability, ScalarFacet, ValueFacet<LongValueStorage>,
        OperationFacet, PresentationFacet, RecipeEnergyPrefetchFacet {
    private final FluxNetworkPointHandler handler;
    private final Supplier<String> reservationKey;
    private final CapabilityView view;

    public FluxNetworkInputCapability(FluxNetworkPointHandler handler, Supplier<String> reservationKey) {
        this.handler = handler;
        this.reservationKey = reservationKey;
        view = CapabilityFactories.view(type(), CapabilityDirections.input(), Set.of(ScalarFacet.class,
                ValueFacet.class, OperationFacet.class, PresentationFacet.class, RecipeEnergyPrefetchFacet.class));
    }

    @Override public CapabilityType type() { return BuiltinCapabilityDefinitions.ENERGY_TYPE; }
    @Override public CapabilityDirections directions() { return CapabilityDirections.input(); }
    @Override public CapabilityView view() { return view; }
    @Override public LongValueStorage storage() { return handler.storage(); }
    @Override public String reservationKey() { return reservationKey.get(); }
    @Override public CapabilityOperation prepare(CapabilityRequest request) { return CapabilityFactories.operation(this, request); }
    @Override public CapabilityOperation prepareScalar(CapabilityRequest request) { return prepareOperation(request); }
    @Override public List<CapabilityDisplay> displays(CapabilityView ignored) {
        return List.of(new CapabilityDisplay("energy", Long.toString(handler.getBuffer()), "FE", Optional.empty()));
    }

    @Override
    public Optional<PrefetchPlan> planPrefetch(long amount) {
        reservationKey();
        if (amount <= 0L) return Optional.empty();
        if (!handler.reserveCandidate(amount)) {
            handler.requestWarmup(amount);
            return Optional.empty();
        }
        boolean[] committed = {false};
        return Optional.of(new PrefetchPlan(amount, (NativeCapabilityOperation) () -> {
            reservationKey();
            if (committed[0] || !handler.commitCandidate(amount)) return failure(BuiltinFailureReasons.MISSING_INPUT);
            committed[0] = true;
            return CapabilityResult.successful();
        }));
    }

    @Override
    public void restoreReservation(long amount) {
        reservationKey();
        handler.restoreCandidateOrSavedReservation(amount);
    }

    @Override
    public long releaseReservation(long amount) {
        reservationKey();
        return handler.releaseReserved(amount);
    }

    @Override
    public CapabilityResult consumeReservation(long amount) {
        reservationKey();
        return handler.consumeReserved(amount) ? CapabilityResult.successful() : failure(BuiltinFailureReasons.MISSING_INPUT);
    }

    @Override public long availableForConsumption() { return handler.availableForPrefetch(); }

    @Override
    public void onRecipeReservationChanged(Object owner, long remaining, Runnable cancelOwner) {
        reservationKey();
        handler.updateRecipeOwner(owner, remaining, cancelOwner);
    }

    @Override
    public CapabilityOperation prepareOperation(CapabilityRequest request) {
        if (!(request instanceof CapabilityRequests.ValueRequest value) || value.insert()
                || value.ioType() != IOType.INPUT || !type().equals(value.type())) {
            return (NativeCapabilityOperation) () -> failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        return (NativeCapabilityOperation) () -> {
            reservationKey();
            return handler.consumeUnreserved(value.amount()) ? CapabilityResult.successful()
                    : failure(BuiltinFailureReasons.MISSING_INPUT);
        };
    }

    private CapabilityResult failure(FailureReason reason) {
        return CapabilityResult.failure(ExecutionStatus.blocked(type().id(), type().id(),
                FailureOccurrence.at(reason, type().id(), FailurePhase.CAPABILITY_COMMIT, null, null, Map.of())));
    }
}
