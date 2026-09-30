package cn.howxu.mmcr.internal.capability;

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
import cn.howxu.mmcr.api.capability.facet.EnergyStorageFacet;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.ScalarFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import cn.howxu.mmcr.internal.storage.LongEnergyHandler;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Machine capability backed by a native energy handler.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class EnergyHatchCapability implements MachineCapability, ScalarFacet, EnergyStorageFacet,
        TransferFacet, OperationFacet, PresentationFacet, SyncFacet {
    private final IOPortBlockEntity port;
    private final IOType ioType;
    private final IEnergyStorage energyStorage;
    private final CapabilityView view;
    private final AsyncPlanningFacet asyncPlanning;

    public EnergyHatchCapability(IEnergyStorage energyStorage, IOType ioType) { this(null, energyStorage, ioType); }

    public EnergyHatchCapability(IOPortBlockEntity port, IEnergyStorage energyStorage, IOType ioType) {
        if (energyStorage == null) throw new IllegalArgumentException("energyStorage must not be null");
        if (ioType == null) throw new IllegalArgumentException("ioType must not be null");
        this.port = port;
        this.ioType = ioType;
        this.energyStorage = energyStorage;
        this.asyncPlanning = new AsyncPlanningFacet() {
            @Override public Object planningIdentity() { return energyStorage; }

            @Override
            protected AsyncCapabilitySnapshot captureSnapshotOnServerThread() {
                return new AsyncCapabilitySnapshot.Scalar(type().id(), amount(), capacity(), transferLimit());
            }

            @Override protected AsyncCapabilityPlanner workerPlannerOnServerThread() {
                return new AsyncCapabilityPlanner.Scalar(type().id());
            }

            @Override protected CapabilityResult commitNativeOnServerThread(AsyncCapabilityOperation operation) {
                return commitAsync(operation);
            }
        };
        this.view = CapabilityFactories.view(type(), directions(), Set.of(ScalarFacet.class, EnergyStorageFacet.class,
                TransferFacet.class, OperationFacet.class, PresentationFacet.class, SyncFacet.class, AsyncPlanningFacet.class));
    }

    @Override public IEnergyStorage energyStorage() { return energyStorage; }

    @Nullable @Override public Level level() { return port == null ? null : port.getLevel(); }

    @Override public BlockPos position() { return port == null ? BlockPos.ZERO : port.getBlockPos(); }

    @Override public long transferLimit() {
        return energyStorage instanceof LongEnergyHandler storage ? storage.getTransferLimit() : Integer.MAX_VALUE;
    }

    @Override public CapabilityType type() { return BuiltinCapabilityDefinitions.ENERGY_TYPE; }

    @Override public CapabilityDirections directions() { return CapabilityDirections.of(ioType); }

    @Override public CapabilityView view() { return view; }

    @Override public int outputPriority() { return port == null ? 0 : port.kind().outputPriority(); }

    @Override
    public <F extends CapabilityFacet> Optional<F> facet(Class<F> facetType) {
        if (facetType == AsyncPlanningFacet.class) return Optional.of(facetType.cast(asyncPlanning));
        return MachineCapability.super.facet(facetType);
    }

    @Override public CapabilityOperation prepare(CapabilityRequest request) { return CapabilityFactories.operation(this, request); }

    @Override public List<CapabilityDisplay> displays(CapabilityView ignored) {
        return List.of(new CapabilityDisplay("energy", Long.toString(amount()), "FE", Optional.empty()));
    }

    @Override
    public CapabilityOperation prepareOperation(CapabilityRequest request) {
        if (!(request instanceof CapabilityRequests.ValueRequest valueRequest)) return () -> failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        return () -> commitNative(valueRequest);
    }

    private CapabilityResult commitNative(CapabilityRequests.ValueRequest request) {
        long remaining = request.amount();
        long transferLimit = transferLimit();
        long available = request.insert() ? capacity() - amount() : amount();
        if (remaining > available || transferLimit <= 0L
                || remaining > RequirementHandlerSupport.scaled(transferLimit, request.parallelism())) {
            return failure(request.insert() ? BuiltinFailureReasons.MISSING_OUTPUT : BuiltinFailureReasons.MISSING_INPUT);
        }
        for (long batch = 0L; batch < request.parallelism() && remaining > 0L; batch++) {
            long chunk = Math.min(remaining, transferLimit);
            if (moveEnergy(chunk, request.insert(), true) != chunk) return failure(request.insert()
                    ? BuiltinFailureReasons.MISSING_OUTPUT : BuiltinFailureReasons.MISSING_INPUT);
            if (moveEnergy(chunk, request.insert(), false) != chunk) return failure(request.insert()
                    ? BuiltinFailureReasons.MISSING_OUTPUT : BuiltinFailureReasons.MISSING_INPUT);
            remaining -= chunk;
        }
        return remaining == 0L ? CapabilityResult.successful()
                : failure(request.insert() ? BuiltinFailureReasons.MISSING_OUTPUT : BuiltinFailureReasons.MISSING_INPUT);
    }

    private long moveEnergy(long amount, boolean insert, boolean simulate) {
        if (energyStorage instanceof LongEnergyHandler storage) {
            return insert ? storage.insertLong(amount, simulate) : storage.extractLong(amount, simulate);
        }
        int requested = (int) Math.min(amount, Integer.MAX_VALUE);
        return insert ? energyStorage.receiveEnergy(requested, simulate) : energyStorage.extractEnergy(requested, simulate);
    }

    @Override public CapabilityOperation prepareScalar(CapabilityRequest request) { return prepareOperation(request); }

    private CapabilityResult commitAsync(AsyncCapabilityOperation operation) {
        if (operation instanceof AsyncCapabilityOperation.Group(List<AsyncCapabilityOperation> operations)) {
            long amount = 0L;
            Boolean insert = null;
            for (AsyncCapabilityOperation child : operations) {
                if (!(child instanceof AsyncCapabilityOperation.Scalar(
                        net.minecraft.resources.ResourceLocation capabilityId, long childAmount, boolean childInsert))
                        || !type().id().equals(capabilityId) || insert != null && insert != childInsert) {
                    return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
                }
                insert = childInsert;
                amount = RequirementHandlerSupport.saturatingAdd(amount, childAmount);
            }
            if (insert == null) return CapabilityResult.successful();
            return commitNative(new CapabilityRequests.ValueRequest(type(), insert ? IOType.OUTPUT : IOType.INPUT,
                    operations.size(), amount, insert));
        }
        if (!(operation instanceof AsyncCapabilityOperation.Scalar(
                net.minecraft.resources.ResourceLocation capabilityId, long amount, boolean insert))
                || !type().id().equals(capabilityId)) return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        return commitNative(new CapabilityRequests.ValueRequest(type(), insert ? IOType.OUTPUT : IOType.INPUT,
                1L, amount, insert));
    }

    private CapabilityResult failure(FailureReason reason) {
        FailureOccurrence occurrence = FailureOccurrence.at(reason, type().id(), FailurePhase.CAPABILITY_COMMIT,
                null, null, Map.of());
        return CapabilityResult.failure(ExecutionStatus.blocked(type().id(), type().id(), occurrence));
    }

    private long amount() {
        return energyStorage instanceof LongEnergyHandler storage ? storage.getAmountAsLong() : energyStorage.getEnergyStored();
    }

    private long capacity() {
        return energyStorage instanceof LongEnergyHandler storage ? storage.getCapacityAsLong() : energyStorage.getMaxEnergyStored();
    }

    @Override public void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeLong(amount());
        buffer.writeLong(capacity());
    }

    @Override
    public void decode(RegistryFriendlyByteBuf buffer) {
        long amount = buffer.readLong();
        long capacity = buffer.readLong();
        if (amount < 0L || amount > capacity || capacity != capacity()) throw new IllegalArgumentException("Invalid energy sync state");
        if (energyStorage instanceof LongEnergyStorage storage) storage.setAmount(amount);
        else throw new IllegalStateException("Energy storage cannot apply synchronized state");
    }
}
