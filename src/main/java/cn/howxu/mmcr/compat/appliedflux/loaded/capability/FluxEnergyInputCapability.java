package cn.howxu.mmcr.compat.appliedflux.loaded.capability;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
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
import cn.howxu.mmcr.api.capability.plan.NativeCapabilityOperation;
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
import java.util.function.BooleanSupplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * Input energy capability backed by a persistent local Flux cache.
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

    public FluxEnergyInputCapability(FluxEnergyBuffer buffer, String reservationKey) {
        this(buffer, reservationKey, null);
    }

    public FluxEnergyInputCapability(FluxEnergyBuffer buffer, String reservationKey, ExactPrefetchBridge prefetchBridge) {
        this(buffer, reservationKey, prefetchBridge, () -> true);
    }

    public FluxEnergyInputCapability(FluxEnergyBuffer buffer, String reservationKey, ExactPrefetchBridge prefetchBridge,
                                     BooleanSupplier prefetchAvailable) {
        if (buffer == null || reservationKey == null || reservationKey.isBlank() || prefetchAvailable == null) {
            throw new IllegalArgumentException("Invalid AppFlux input capability configuration");
        }
        this.buffer = buffer;
        this.reservationKey = reservationKey;
        this.prefetchBridge = prefetchBridge;
        this.prefetchAvailable = prefetchAvailable;
        view = CapabilityFactories.view(type(), CapabilityDirections.input(), Set.of(ScalarFacet.class,
                ValueFacet.class, OperationFacet.class, PresentationFacet.class, PersistenceFacet.class,
                SyncFacet.class, RecipeEnergyPrefetchFacet.class));
    }

    @Override public CapabilityType type() { return BuiltinCapabilityDefinitions.ENERGY_TYPE; }
    @Override public CapabilityDirections directions() { return CapabilityDirections.input(); }
    @Override public CapabilityView view() { return view; }
    @Override public LongValueStorage storage() { return buffer.storage(); }
    @Override public CapabilityOperation prepare(CapabilityRequest request) { return CapabilityFactories.operation(this, request); }
    @Override public List<CapabilityDisplay> displays(CapabilityView ignored) {
        return List.of(new CapabilityDisplay("energy", Long.toString(buffer.amount()), "FE", Optional.empty()));
    }
    @Override public String stateKey() { return "appflux_energy_input"; }
    @Override public void save(CompoundTag output, HolderLookup.Provider registries) { buffer.save(output); }
    @Override public void load(CompoundTag input, HolderLookup.Provider registries) { buffer.load(input); }
    @Override public void encode(RegistryFriendlyByteBuf output) { output.writeLong(buffer.amount()); }
    @Override public void decode(RegistryFriendlyByteBuf input) { buffer.setAmount(input.readLong()); }
    @Override public String reservationKey() { return reservationKey; }

    @Override
    public <F extends CapabilityFacet> Optional<F> facet(Class<F> facetType) {
        if (facetType == RecipeEnergyPrefetchFacet.class && !prefetchAvailable.getAsBoolean()) return Optional.empty();
        return MachineCapability.super.facet(facetType);
    }

    @Override
    public CapabilityOperation prepareOperation(CapabilityRequest request) {
        if (!(request instanceof CapabilityRequests.ValueRequest valueRequest) || valueRequest.insert()) {
            return (NativeCapabilityOperation) () -> failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        return (NativeCapabilityOperation) () -> extractLocal(valueRequest.amount());
    }

    @Override public CapabilityOperation prepareScalar(CapabilityRequest request) { return prepareOperation(request); }

    @Override
    public Optional<PrefetchPlan> planPrefetch(long requestedAmount) {
        if (requestedAmount <= 0L || prefetchBridge == null) return Optional.empty();
        return prefetchBridge.planExactExtract(requestedAmount).map(operation -> new PrefetchPlan(requestedAmount,
                (NativeCapabilityOperation) () -> commitPrefetch(operation, requestedAmount)));
    }

    @Override public void restoreReservation(long amount) { }
    @Override public long releaseReservation(long amount) { return buffer.releaseReservation(amount); }
    @Override public CapabilityResult consumeReservation(long amount) { return extractLocal(amount); }

    private CapabilityResult commitPrefetch(CapabilityOperation operation, long requestedAmount) {
        if (requestedAmount > buffer.storage().capacity() - buffer.amount()) return failure(BuiltinFailureReasons.MISSING_INPUT);
        CapabilityResult result = operation.commit();
        if (result == null || !result.success() || buffer.insert(requestedAmount) != requestedAmount) {
            return result == null ? failure(BuiltinFailureReasons.MISSING_INPUT) : result;
        }
        buffer.reserve(requestedAmount);
        return CapabilityResult.successful();
    }

    private CapabilityResult extractLocal(long amount) {
        return buffer.amount() >= amount && buffer.extract(amount) == amount
                ? CapabilityResult.successful() : failure(BuiltinFailureReasons.MISSING_INPUT);
    }

    private CapabilityResult failure(FailureReason reason) {
        return CapabilityResult.failure(ExecutionStatus.blocked(type().id(), type().id(),
                FailureOccurrence.at(reason, type().id(), FailurePhase.CAPABILITY_COMMIT, null, null, Map.of())));
    }

    @FunctionalInterface
    public interface ExactPrefetchBridge {
        Optional<CapabilityOperation> planExactExtract(long requestedAmount);
    }
}
