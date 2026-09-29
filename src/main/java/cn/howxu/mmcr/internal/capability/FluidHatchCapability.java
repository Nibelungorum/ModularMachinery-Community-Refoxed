package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.FluidHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
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
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Machine capability backed by a long fluid hatch storage.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluidHatchCapability implements MachineCapability, FluidHandlerFacet, TransferFacet,
        OperationFacet, PresentationFacet, SyncFacet {
    private final IOPortBlockEntity port;
    private final IOType ioType;
    private final ResourceStorage<FluidResource> storage;
    private final IFluidHandler fluidHandler;
    private final CapabilityView view;
    private final AsyncPlanningFacet asyncPlanning;

    public FluidHatchCapability(ResourceStorage<FluidResource> storage, IOType ioType) {
        this(null, storage, ioType, true);
    }

    public FluidHatchCapability(IFluidHandler fluidHandler, IOType ioType) {
        this(null, null, fluidHandler, ioType, true);
    }

    public FluidHatchCapability(IOPortBlockEntity port, ResourceStorage<FluidResource> storage, IOType ioType) {
        this(port, storage, ioType, true);
    }

    public FluidHatchCapability(IOPortBlockEntity port, ResourceStorage<FluidResource> storage, IOType ioType,
                                 boolean exposeTransferFacet) {
        this(port, storage, null, ioType, exposeTransferFacet);
    }

    public FluidHatchCapability(IOPortBlockEntity port, IFluidHandler fluidHandler, IOType ioType) {
        this(port, null, fluidHandler, ioType, true);
    }

    private FluidHatchCapability(IOPortBlockEntity port, ResourceStorage<FluidResource> storage,
                                 IFluidHandler fluidHandler, IOType ioType, boolean exposeTransferFacet) {
        if (storage == null && fluidHandler == null) throw new IllegalArgumentException("storage must not be null");
        if (ioType == null) throw new IllegalArgumentException("ioType must not be null");
        this.port = port;
        this.ioType = ioType;
        this.storage = storage;
        this.fluidHandler = fluidHandler;
        this.asyncPlanning = new AsyncPlanningFacet() {
            @Override
            public Object planningIdentity() {
                return storage == null ? fluidHandler : storage.reservationIdentity();
            }

            @Override
            protected AsyncCapabilitySnapshot captureSnapshotOnServerThread() {
                if (storage == null) return new AsyncCapabilitySnapshot.Resource(type().id(), IntStream.range(0, fluidHandler.getTanks())
                        .mapToObj(tank -> {
                            net.neoforged.neoforge.fluids.FluidStack stack = fluidHandler.getFluidInTank(tank);
                            boolean empty = stack.isEmpty();
                            return new AsyncCapabilitySnapshot.ResourceSlot(empty ? Optional.empty()
                                    : Optional.of(NativeAsyncResourceValues.fluid(FluidResource.of(stack))),
                                    empty ? 0L : fluidAmount(tank), fluidCapacity(tank));
                        }).toList());
                return new AsyncCapabilitySnapshot.Resource(type().id(), IntStream.range(0, storage.size())
                        .mapToObj(slot -> {
                            FluidResource resource = storage.resource(slot);
                            boolean empty = resource == null || resource.isEmpty();
                            return new AsyncCapabilitySnapshot.ResourceSlot(empty
                                    ? Optional.empty() : Optional.of(NativeAsyncResourceValues.fluid(resource)),
                                    empty ? 0L : storage.amount(slot), storage.capacity(slot, resource));
                        }).toList());
            }

            @Override
            protected AsyncCapabilityPlanner workerPlannerOnServerThread() {
                return new AsyncCapabilityPlanner.Resource(type().id());
            }

            @Override
            protected CapabilityResult commitOnServerThread(AsyncCapabilityOperation operation,
                                                              TransactionContext transaction) {
                return commitAsync(operation, transaction);
            }

            @Override
            public boolean supportsNativeExecution() {
                return fluidHandler != null;
            }

            @Override
            protected CapabilityResult commitNativeOnServerThread(AsyncCapabilityOperation operation) {
                return commitNativeAsync(operation);
            }
        };
        Set<Class<? extends CapabilityFacet>> facets = new LinkedHashSet<>(Set.of(
                FluidHandlerFacet.class, OperationFacet.class, PresentationFacet.class, SyncFacet.class,
                AsyncPlanningFacet.class));
        if (exposeTransferFacet) facets.add(TransferFacet.class);
        this.view = CapabilityFactories.view(type(), directions(),
                Set.copyOf(facets));
    }

    public FluidHatchCapability(FluidHatchBlockEntity port) {
        this(port, port.nativeFluidHandler(), port.ioType());
    }

    public ResourceStorage<FluidResource> storage() {
        return storage;
    }

    @Override
    public IFluidHandler fluidHandler() {
        return fluidHandler;
    }

    public Class<FluidResource> resourceType() {
        return FluidResource.class;
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
        return Integer.MAX_VALUE;
    }

    @Override
    public CapabilityType type() {
        return BuiltinCapabilityDefinitions.FLUID_TYPE;
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
        if (storage == null) return IntStream.range(0, fluidHandler.getTanks())
                .mapToObj(tank -> new CapabilityDisplay("fluid", Long.toString(fluidAmount(tank)), "mB", Optional.empty()))
                .toList();
        return IntStream.range(0, storage.size())
                .mapToObj(slot -> new CapabilityDisplay("fluid", Long.toString(storage.amount(slot)), "mB", Optional.empty()))
                .toList();
    }

    @Override
    public CapabilityOperation prepareOperation(CapabilityRequest request) {
        if (request instanceof CapabilityRequests.FluidRequest fluidRequest && fluidHandler != null) {
            return (NativeCapabilityOperation) () -> commitNative(fluidRequest);
        }
        if (!(request instanceof CapabilityRequests.ResourceRequest<?> resourceRequest)) {
            return ignored -> failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        return transaction -> {
            for (CapabilityRequests.ResourceAction<?> action : resourceRequest.actions()) {
                if (!storage.resourceType().isInstance(action.resource())) {
                    return failure(BuiltinFailureReasons.WRONG_RESOURCE_TYPE);
                }
                long moved = action.insert()
                        ? storage.insertResource(action.slot(), action.resource(), action.amount(), transaction)
                        : storage.extractResource(action.slot(), action.resource(), action.amount(), transaction);
                if (moved != action.amount()) {
                    return failure(action.insert() ? BuiltinFailureReasons.MISSING_OUTPUT
                                    : BuiltinFailureReasons.MISSING_INPUT,
                            Map.of("required", Long.toString(action.amount()),
                                    "available", Long.toString(Math.max(0L, moved)),
                                    "shortfall", Long.toString(Math.max(0L, action.amount() - moved))));
                }
            }
            return CapabilityResult.successful();
        };
    }

    private CapabilityResult commitNative(CapabilityRequests.FluidRequest request) {
        for (CapabilityRequests.FluidAction action : request.actions()) {
            long remaining = action.amount();
            while (remaining > 0L) {
                int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
                net.neoforged.neoforge.fluids.FluidStack stack = action.stack().copyWithAmount(chunk);
                long simulated = action.insert() ? fluidHandler.fill(stack, IFluidHandler.FluidAction.SIMULATE)
                        : fluidHandler.drain(stack, IFluidHandler.FluidAction.SIMULATE).getAmount();
                if (simulated != chunk) return failure(action.insert()
                        ? BuiltinFailureReasons.MISSING_OUTPUT : BuiltinFailureReasons.MISSING_INPUT);
                long committed = action.insert() ? fluidHandler.fill(stack, IFluidHandler.FluidAction.EXECUTE)
                        : fluidHandler.drain(stack, IFluidHandler.FluidAction.EXECUTE).getAmount();
                if (committed != chunk) return failure(action.insert()
                        ? BuiltinFailureReasons.MISSING_OUTPUT : BuiltinFailureReasons.MISSING_INPUT);
                remaining -= chunk;
            }
        }
        return CapabilityResult.successful();
    }

    private CapabilityResult commitNativeAsync(AsyncCapabilityOperation operation) {
        if (operation instanceof AsyncCapabilityOperation.Group(List<AsyncCapabilityOperation> operations)) {
            for (AsyncCapabilityOperation child : operations) {
                CapabilityResult result = commitNativeAsync(child);
                if (!result.success()) return result;
            }
            return CapabilityResult.successful();
        }
        if (!(operation instanceof AsyncCapabilityOperation.Resource(
                net.minecraft.resources.ResourceLocation capabilityId, int tank,
                cn.howxu.mmcr.api.capability.async.AsyncResourceValue value, long amount, boolean insert
        )) || !type().id().equals(capabilityId)) return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        try {
            return commitNative(new CapabilityRequests.FluidRequest(type(), insert ? IOType.OUTPUT : IOType.INPUT, 1L,
                    List.of(new CapabilityRequests.FluidAction(tank, NativeAsyncResourceValues.fluid(value), amount, insert))));
        } catch (IllegalArgumentException exception) {
            return failure(BuiltinFailureReasons.WRONG_RESOURCE_TYPE);
        }
    }

    private long fluidAmount(int tank) {
        return fluidHandler instanceof cn.howxu.mmcr.internal.storage.LongFluidStorage storage
                ? storage.amount(tank) : fluidHandler.getFluidInTank(tank).getAmount();
    }

    private long fluidCapacity(int tank) {
        return fluidHandler instanceof cn.howxu.mmcr.internal.storage.LongFluidStorage storage
                ? storage.capacity(tank) : fluidHandler.getTankCapacity(tank);
    }

    private CapabilityResult failure(FailureReason reason) {
        return failure(reason, Map.of());
    }

    private CapabilityResult failure(FailureReason reason, Map<String, String> details) {
        FailureOccurrence occurrence = FailureOccurrence.at(reason, type().id(), FailurePhase.CAPABILITY_COMMIT,
                null, null, details);
        return CapabilityResult.failure(ExecutionStatus.blocked(type().id(), type().id(), occurrence));
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
        if (!(operation instanceof AsyncCapabilityOperation.Resource(
                net.minecraft.resources.ResourceLocation capabilityId, int slot,
                cn.howxu.mmcr.api.capability.async.AsyncResourceValue resource1, long amount, boolean insert
        ))
                || !type().id().equals(capabilityId)) {
            return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        FluidResource nativeResource;
        try {
            nativeResource = NativeAsyncResourceValues.fluid(resource1);
        } catch (IllegalArgumentException exception) {
            return failure(BuiltinFailureReasons.WRONG_RESOURCE_TYPE);
        }
        FluidResource current;
        try {
            current = storage.resource(slot);
        } catch (IndexOutOfBoundsException exception) {
            return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        boolean matches = current != null && !current.isEmpty() && current.equals(nativeResource);
        if ((!insert && !matches) || (insert && current != null && !current.isEmpty() && !matches)) {
            return failure(insert ? BuiltinFailureReasons.MISSING_OUTPUT : BuiltinFailureReasons.MISSING_INPUT);
        }
        long moved = insert
                ? storage.insertResource(slot, nativeResource, amount, transaction)
                : storage.extractResource(slot, nativeResource, amount, transaction);
        return moved == amount ? CapabilityResult.successful()
                : failure(insert ? BuiltinFailureReasons.MISSING_OUTPUT : BuiltinFailureReasons.MISSING_INPUT);
    }

    @Override
    public void encode(RegistryFriendlyByteBuf buffer) {
        int tanks = fluidHandler == null ? storage.size() : fluidHandler.getTanks();
        buffer.writeVarInt(tanks);
        for (int slot = 0; slot < tanks; slot++) {
            FluidResource resource = fluidHandler == null ? storage.resource(slot) : FluidResource.of(fluidHandler.getFluidInTank(slot));
            FluidResource.STREAM_CODEC.encode(buffer, resource == null ? FluidResource.EMPTY : resource);
            buffer.writeLong(fluidHandler == null ? storage.amount(slot) : fluidAmount(slot));
            buffer.writeLong(fluidHandler == null ? storage.capacity(slot, resource) : fluidCapacity(slot));
        }
    }

    @Override
    public void decode(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        int tanks = fluidHandler == null ? storage.size() : fluidHandler.getTanks();
        if (count < 0 || count > 1024 || count != tanks) throw new IllegalArgumentException("Invalid fluid sync state");
        if (fluidHandler instanceof cn.howxu.mmcr.internal.storage.LongFluidStorage nativeStorage) {
            for (int slot = 0; slot < count; slot++) {
                FluidResource resource = FluidResource.STREAM_CODEC.decode(buffer);
                long amount = buffer.readLong();
                long capacity = buffer.readLong();
                if (amount < 0 || capacity < amount || capacity != nativeStorage.capacity(slot)) {
                    throw new IllegalArgumentException("Invalid fluid sync amount");
                }
                nativeStorage.setContents(slot, resource.isEmpty() ? net.neoforged.neoforge.fluids.FluidStack.EMPTY
                        : resource.toStack(1), amount);
            }
            return;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (int slot = 0; slot < count; slot++) {
                FluidResource resource = FluidResource.STREAM_CODEC.decode(buffer);
                long amount = buffer.readLong();
                long capacity = buffer.readLong();
                if (amount < 0 || capacity < amount || capacity != storage.capacity(slot, resource)) {
                    throw new IllegalArgumentException("Invalid fluid sync amount");
                }
                FluidResource current = storage.resource(slot);
                if (current != null && !current.isEmpty()) storage.extract(slot, current, Long.MAX_VALUE, transaction);
                if (!resource.isEmpty() && storage.insert(slot, resource, amount, transaction) != amount) {
                    throw new IllegalArgumentException("Fluid sync state does not fit");
                }
            }
            transaction.commit();
        }
    }
}
