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
import cn.howxu.mmcr.api.capability.facet.FluidHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
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
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Machine capability backed by a native fluid handler.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluidHatchCapability implements MachineCapability, FluidHandlerFacet, TransferFacet,
        OperationFacet, PresentationFacet, SyncFacet {
    private final IOPortBlockEntity port;
    private final IOType ioType;
    private final IFluidHandler fluidHandler;
    private final CapabilityView view;
    private final AsyncPlanningFacet asyncPlanning;

    public FluidHatchCapability(IFluidHandler fluidHandler, IOType ioType) { this(null, fluidHandler, ioType); }

    public FluidHatchCapability(IOPortBlockEntity port, IFluidHandler fluidHandler, IOType ioType) {
        if (fluidHandler == null) throw new IllegalArgumentException("fluidHandler must not be null");
        if (ioType == null) throw new IllegalArgumentException("ioType must not be null");
        this.port = port;
        this.ioType = ioType;
        this.fluidHandler = fluidHandler;
        this.asyncPlanning = new AsyncPlanningFacet() {
            @Override public Object planningIdentity() { return fluidHandler; }

            @Override
            protected AsyncCapabilitySnapshot captureSnapshotOnServerThread() {
                return new AsyncCapabilitySnapshot.Resource(type().id(), IntStream.range(0, fluidHandler.getTanks())
                        .mapToObj(tank -> {
                            FluidStack stack = fluidHandler.getFluidInTank(tank);
                            return new AsyncCapabilitySnapshot.ResourceSlot(stack.isEmpty() ? Optional.empty()
                                    : Optional.of(NativeAsyncResourceValues.fluid(stack)),
                                    stack.isEmpty() ? 0L : fluidAmount(tank), fluidCapacity(tank));
                        }).toList());
            }

            @Override protected AsyncCapabilityPlanner workerPlannerOnServerThread() {
                return new AsyncCapabilityPlanner.Resource(type().id());
            }

            @Override protected CapabilityResult commitNativeOnServerThread(AsyncCapabilityOperation operation) {
                return commitNativeAsync(operation);
            }
        };
        this.view = CapabilityFactories.view(type(), directions(), Set.of(FluidHandlerFacet.class, TransferFacet.class,
                OperationFacet.class, PresentationFacet.class, SyncFacet.class, AsyncPlanningFacet.class));
    }

    @Override public IFluidHandler fluidHandler() { return fluidHandler; }

    @Nullable @Override public Level level() { return port == null ? null : port.getLevel(); }

    @Override public BlockPos position() { return port == null ? BlockPos.ZERO : port.getBlockPos(); }

    @Override public long transferLimit() { return Integer.MAX_VALUE; }

    @Override public CapabilityType type() { return BuiltinCapabilityDefinitions.FLUID_TYPE; }

    @Override public CapabilityDirections directions() { return CapabilityDirections.of(ioType); }

    @Override public CapabilityView view() { return view; }

    @Override public int outputPriority() { return port == null ? 0 : port.kind().outputPriority(); }

    @Override
    public <F extends CapabilityFacet> Optional<F> facet(Class<F> facetType) {
        if (facetType == AsyncPlanningFacet.class) return Optional.of(facetType.cast(asyncPlanning));
        return MachineCapability.super.facet(facetType);
    }

    @Override public CapabilityOperation prepare(CapabilityRequest request) { return CapabilityFactories.operation(this, request); }

    @Override
    public List<CapabilityDisplay> displays(CapabilityView ignored) {
        return IntStream.range(0, fluidHandler.getTanks()).mapToObj(tank -> new CapabilityDisplay("fluid",
                Long.toString(fluidAmount(tank)), "mB", Optional.empty())).toList();
    }

    @Override
    public CapabilityOperation prepareOperation(CapabilityRequest request) {
        if (!(request instanceof CapabilityRequests.FluidRequest fluidRequest)) return () -> failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        return () -> commitNative(fluidRequest);
    }

    private CapabilityResult commitNative(CapabilityRequests.FluidRequest request) {
        for (CapabilityRequests.FluidAction action : request.actions()) {
            long remaining = action.amount();
            while (remaining > 0L) {
                int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
                FluidStack stack = action.stack().copyWithAmount(chunk);
                long simulated = action.insert() ? fluidHandler.fill(stack, IFluidHandler.FluidAction.SIMULATE)
                        : fluidHandler.drain(stack, IFluidHandler.FluidAction.SIMULATE).getAmount();
                if (simulated != chunk) return failure(action.insert() ? BuiltinFailureReasons.MISSING_OUTPUT
                        : BuiltinFailureReasons.MISSING_INPUT, movementDetails(chunk, simulated));
                long committed = action.insert() ? fluidHandler.fill(stack, IFluidHandler.FluidAction.EXECUTE)
                        : fluidHandler.drain(stack, IFluidHandler.FluidAction.EXECUTE).getAmount();
                if (committed != chunk) return failure(action.insert() ? BuiltinFailureReasons.MISSING_OUTPUT
                        : BuiltinFailureReasons.MISSING_INPUT, movementDetails(chunk, committed));
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
                cn.howxu.mmcr.api.capability.async.AsyncResourceValue value, long amount, boolean insert))
                || !type().id().equals(capabilityId)) return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        try {
            return commitNative(new CapabilityRequests.FluidRequest(type(), insert ? IOType.OUTPUT : IOType.INPUT, 1L,
                    List.of(new CapabilityRequests.FluidAction(tank, NativeAsyncResourceValues.fluid(value), amount, insert))));
        } catch (IllegalArgumentException exception) {
            return failure(BuiltinFailureReasons.WRONG_RESOURCE_TYPE);
        }
    }

    private long fluidAmount(int tank) {
        return fluidHandler instanceof cn.howxu.mmcr.internal.storage.LongFluidStorage storage ? storage.amount(tank)
                : fluidHandler instanceof NativeStackSync.Fluid storage ? storage.amount(tank)
                : fluidHandler.getFluidInTank(tank).getAmount();
    }

    private long fluidCapacity(int tank) {
        return fluidHandler instanceof cn.howxu.mmcr.internal.storage.LongFluidStorage storage ? storage.capacity(tank)
                : fluidHandler instanceof NativeStackSync.Fluid storage ? storage.capacity(tank) : fluidHandler.getTankCapacity(tank);
    }

    private CapabilityResult failure(FailureReason reason) { return failure(reason, Map.of()); }

    private static Map<String, String> movementDetails(long required, long moved) {
        return Map.of("required", Long.toString(required), "available", Long.toString(Math.max(0L, moved)),
                "shortfall", Long.toString(Math.max(0L, required - moved)));
    }

    private CapabilityResult failure(FailureReason reason, Map<String, String> details) {
        FailureOccurrence occurrence = FailureOccurrence.at(reason, type().id(), FailurePhase.CAPABILITY_COMMIT, null, null, details);
        return CapabilityResult.failure(ExecutionStatus.blocked(type().id(), type().id(), occurrence));
    }

    @Override
    public void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(fluidHandler.getTanks());
        for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
            FluidStack.STREAM_CODEC.encode(buffer, fluidHandler.getFluidInTank(tank).copyWithAmount(1));
            buffer.writeLong(fluidAmount(tank));
            buffer.writeLong(fluidCapacity(tank));
        }
    }

    @Override
    public void decode(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > 1024 || count != fluidHandler.getTanks()) throw new IllegalArgumentException("Invalid fluid sync state");
        for (int tank = 0; tank < count; tank++) {
            FluidStack stack = FluidStack.STREAM_CODEC.decode(buffer);
            long amount = buffer.readLong();
            long capacity = buffer.readLong();
            if (amount < 0L || capacity < amount || capacity != fluidCapacity(tank)) throw new IllegalArgumentException("Invalid fluid sync amount");
            if (fluidHandler instanceof cn.howxu.mmcr.internal.storage.LongFluidStorage storage) storage.setContents(tank, stack, amount);
            else if (fluidHandler instanceof NativeStackSync.Fluid storage) storage.setContents(tank, stack, amount);
            else throw new IllegalStateException("Fluid handler cannot apply synchronized state");
        }
    }
}
