package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.ScalarFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.facet.EnergyStorageFacet;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.NativeCapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityPlanner;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Machine capability backed by a long energy value storage.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class EnergyHatchCapability implements MachineCapability, ScalarFacet, ValueFacet<LongValueStorage>,
        EnergyStorageFacet, TransferFacet, OperationFacet, PresentationFacet, SyncFacet {
    private final IOPortBlockEntity port;
    private final IOType ioType;
    private final LongValueStorage storage;
    private final IEnergyStorage energyStorage;
    private final CapabilityView view;
    private final AsyncPlanningFacet asyncPlanning;

    public EnergyHatchCapability(LongValueStorage storage, IOType ioType) {
        this(null, storage, null, ioType);
    }

    public EnergyHatchCapability(IOPortBlockEntity port, LongValueStorage storage, IOType ioType) {
        this(port, storage, null, ioType);
    }

    public EnergyHatchCapability(IOPortBlockEntity port, LongValueStorage storage, IEnergyStorage energyStorage,
                                 IOType ioType) {
        if (storage == null) throw new IllegalArgumentException("storage must not be null");
        if (ioType == null) throw new IllegalArgumentException("ioType must not be null");
        this.port = port;
        this.ioType = ioType;
        this.storage = storage;
        this.energyStorage = energyStorage;
        this.asyncPlanning = new AsyncPlanningFacet() {
            @Override
            public Object planningIdentity() {
                return storage;
            }

            @Override
            protected AsyncCapabilitySnapshot captureSnapshotOnServerThread() {
                return new AsyncCapabilitySnapshot.Scalar(type().id(), storage.amount(), storage.capacity(),
                        storage.transferLimit());
            }

            @Override
            protected AsyncCapabilityPlanner workerPlannerOnServerThread() {
                return new AsyncCapabilityPlanner.Scalar(type().id());
            }

            @Override
            protected CapabilityResult commitOnServerThread(AsyncCapabilityOperation operation,
                                                              net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
                return commitAsync(operation, transaction);
            }

            @Override
            public boolean supportsNativeExecution() {
                return energyStorage != null;
            }

            @Override
            protected CapabilityResult commitNativeOnServerThread(AsyncCapabilityOperation operation) {
                if (!(operation instanceof AsyncCapabilityOperation.Scalar(
                        net.minecraft.resources.ResourceLocation capabilityId, long amount, boolean insert
                )) || !type().id().equals(capabilityId)) return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
                return commitNative(new CapabilityRequests.ValueRequest(type(), insert ? IOType.OUTPUT : IOType.INPUT,
                        1L, amount, insert));
            }
        };
        this.view = CapabilityFactories.view(type(), directions(),
                Set.of(ScalarFacet.class, ValueFacet.class, EnergyStorageFacet.class, TransferFacet.class, OperationFacet.class,
                        PresentationFacet.class, SyncFacet.class, AsyncPlanningFacet.class));
    }

    public EnergyHatchCapability(EnergyHatchBlockEntity port) {
        this(port, port.getEnergyStorage(), port.nativeEnergyStorage(), port.ioType());
    }

    public LongValueStorage storage() {
        return storage;
    }

    @Override
    public IEnergyStorage energyStorage() {
        return energyStorage;
    }

    @Nullable
    public Level level() {
        return port == null ? null : port.getLevel();
    }

    public BlockPos position() {
        return port == null ? BlockPos.ZERO : port.getBlockPos();
    }

    @Override
    public long transferLimit() {
        return storage.transferLimit();
    }

    @Override
    public CapabilityType type() {
        return BuiltinCapabilityDefinitions.ENERGY_TYPE;
    }

    @Override
    public CapabilityDirections directions() {
        return CapabilityDirections.of(ioType);
    }

    @Override
    public CapabilityView view() {
        return view;
    }

    @Override
    public int outputPriority() {
        return port == null ? 0 : port.kind().outputPriority();
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
    public List<CapabilityDisplay> displays(CapabilityView ignored) {
        return List.of(new CapabilityDisplay("energy", Long.toString(storage.amount()), "FE", Optional.empty()));
    }

    @Override
    public CapabilityOperation prepareOperation(CapabilityRequest request) {
        if (request instanceof CapabilityRequests.ValueRequest valueRequest && energyStorage != null) {
            return (NativeCapabilityOperation) () -> commitNative(valueRequest);
        }
        if (!(request instanceof CapabilityRequests.ValueRequest valueRequest)) {
            return ignored -> failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        return transaction -> {
            long moved = valueRequest.insert()
                    ? storage.insert(valueRequest.amount(), transaction)
                    : storage.extract(valueRequest.amount(), transaction);
            return moved == valueRequest.amount()
                    ? CapabilityResult.successful() : failure(
                    valueRequest.insert() ? BuiltinFailureReasons.MISSING_OUTPUT : BuiltinFailureReasons.MISSING_INPUT,
                    Map.of("required", Long.toString(valueRequest.amount()),
                            "available", Long.toString(Math.max(0L, moved)),
                            "shortfall", Long.toString(Math.max(0L, valueRequest.amount() - moved))));
        };
    }

    private CapabilityResult commitNative(CapabilityRequests.ValueRequest request) {
        long remaining = request.amount();
        while (remaining > 0L) {
            int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
            long simulated = moveEnergy(chunk, request.insert(), true);
            if (simulated != chunk) return failure(request.insert()
                    ? BuiltinFailureReasons.MISSING_OUTPUT : BuiltinFailureReasons.MISSING_INPUT);
            long committed = moveEnergy(chunk, request.insert(), false);
            if (committed != chunk) return failure(request.insert()
                    ? BuiltinFailureReasons.MISSING_OUTPUT : BuiltinFailureReasons.MISSING_INPUT);
            remaining -= chunk;
        }
        return CapabilityResult.successful();
    }

    private long moveEnergy(long amount, boolean insert, boolean simulate) {
        if (energyStorage instanceof cn.howxu.mmcr.internal.storage.LongEnergyHandler storage) {
            return insert ? storage.insertLong(amount, simulate) : storage.extractLong(amount, simulate);
        }
        int requested = (int) Math.min(amount, Integer.MAX_VALUE);
        return insert ? energyStorage.receiveEnergy(requested, simulate) : energyStorage.extractEnergy(requested, simulate);
    }

    @Override
    public CapabilityOperation prepareScalar(CapabilityRequest request) {
        return prepareOperation(request);
    }

    private CapabilityResult failure(FailureReason reason) {
        return failure(reason, Map.of());
    }

    private CapabilityResult failure(FailureReason reason, Map<String, String> details) {
        FailureOccurrence occurrence = FailureOccurrence.at(reason, type().id(), FailurePhase.CAPABILITY_COMMIT,
                null, null, details);
        return CapabilityResult.failure(ExecutionStatus.blocked(type().id(), type().id(), occurrence));
    }

    private CapabilityResult commitAsync(AsyncCapabilityOperation operation,
                                          net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
        if (operation instanceof AsyncCapabilityOperation.Group(List<AsyncCapabilityOperation> operations)) {
            try (net.neoforged.neoforge.transfer.transaction.Transaction nested =
                         net.neoforged.neoforge.transfer.transaction.Transaction.open(transaction)) {
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
                || !type().id().equals(capabilityId)) {
            return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        long moved = insert ? storage.insert(amount, transaction)
                : storage.extract(amount, transaction);
        return moved == amount ? CapabilityResult.successful()
                : failure(insert ? BuiltinFailureReasons.MISSING_OUTPUT : BuiltinFailureReasons.MISSING_INPUT);
    }

    @Override
    public void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeLong(storage.amount());
        buffer.writeLong(storage.capacity());
    }

    @Override
    public void decode(RegistryFriendlyByteBuf buffer) {
        long amount = buffer.readLong();
        long capacity = buffer.readLong();
        if (amount < 0L || amount > capacity || capacity != storage.capacity()) {
            throw new IllegalArgumentException("Invalid energy sync state");
        }
        storage.setAmount(amount);
    }
}
