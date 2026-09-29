package cn.howxu.mmcr.compat.mekanism.loaded;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityPlanner;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
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
import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.internal.capability.NativeAsyncResourceValues;
import cn.howxu.mmcr.util.IOType;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** MMCR capability backed by a loaded Mekanism chemical tank.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ChemicalPortCapability implements LoadedMekanismBridge.ChemicalPort,
        TransferFacet, OperationFacet, PresentationFacet, SyncFacet {
    private static final CapabilityType TYPE = new CapabilityType(MekanismRecipeTypes.CHEMICAL);

    private final ChemicalPortBlockEntity port;
    private final IChemicalTank chemicalTank;
    private final IOType ioType;
    private final CapabilityView view;
    private final AsyncPlanningFacet asyncPlanning;

    public ChemicalPortCapability(IChemicalTank chemicalTank, IOType ioType) {
        this(null, chemicalTank, ioType);
    }

    public ChemicalPortCapability(ChemicalPortBlockEntity port) {
        this(port, port.chemicalTank(), port.ioType());
    }

    public ChemicalPortCapability(@Nullable ChemicalPortBlockEntity port, IChemicalTank chemicalTank,
                                  IOType ioType) {
        if (chemicalTank == null) throw new IllegalArgumentException("chemicalTank must not be null");
        if (ioType == null) throw new IllegalArgumentException("ioType must not be null");
        this.port = port;
        this.chemicalTank = chemicalTank;
        this.ioType = ioType;
        this.asyncPlanning = new AsyncPlanningFacet() {
            @Override
            public Object planningIdentity() {
                return chemicalTank;
            }

            @Override
            protected AsyncCapabilitySnapshot captureSnapshotOnServerThread() {
                ChemicalStack stack = chemicalTank.getStack();
                boolean empty = stack.isEmpty();
                return new AsyncCapabilitySnapshot.Resource(type().id(), List.of(
                        new AsyncCapabilitySnapshot.ResourceSlot(empty ? Optional.empty()
                                : Optional.of(NativeAsyncResourceValues.chemical(identity(stack))),
                                empty ? 0L : chemicalTank.getStored(), chemicalTank.getCapacity())));
            }

            @Override
            protected AsyncCapabilityPlanner workerPlannerOnServerThread() {
                return new AsyncCapabilityPlanner.Resource(type().id());
            }

            @Override
            public boolean supportsNativeExecution() {
                return true;
            }

            @Override
            protected CapabilityResult commitNativeOnServerThread(AsyncCapabilityOperation operation) {
                return commitAsync(operation);
            }
        };
        this.view = CapabilityFactories.view(TYPE, directions(),
                Set.of(TransferFacet.class, OperationFacet.class, PresentationFacet.class,
                        SyncFacet.class, AsyncPlanningFacet.class));
    }

    @Override
    public IChemicalTank chemicalTank() {
        return chemicalTank;
    }

    @Override
    public boolean radioactive() {
        if (port == null) throw new IllegalStateException("radioactive() requires host ChemicalPortBlockEntity");
        return port.isRadioactive();
    }

    @Override
    public int outputPriority() {
        return port == null ? 0 : port.kind().outputPriority();
    }

    @Override
    @Nullable
    public Level level() {
        return port == null ? null : port.getLevel();
    }

    @Override
    public BlockPos position() {
        return port == null ? BlockPos.ZERO : port.getBlockPos();
    }

    @Override
    public long transferLimit() {
        return Long.MAX_VALUE;
    }

    @Override
    public CapabilityType type() {
        return TYPE;
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
    public <F extends CapabilityFacet> Optional<F> facet(Class<F> facetType) {
        if (facetType == AsyncPlanningFacet.class) return Optional.of(facetType.cast(asyncPlanning));
        return LoadedMekanismBridge.ChemicalPort.super.facet(facetType);
    }

    @Override
    public CapabilityOperation prepareOperation(CapabilityRequest request) {
        if (!(request instanceof CapabilityRequests.ResourceRequest<?> resourceRequest)) {
            return (NativeCapabilityOperation) () -> failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        return (NativeCapabilityOperation) () -> commitActions(resourceRequest.actions());
    }

    @Override
    public List<CapabilityDisplay> displays(CapabilityView ignored) {
        return List.of(new CapabilityDisplay("chemical", Long.toString(chemicalTank.getStored()),
                "mB", Optional.empty()));
    }

    @Override
    public void encode(RegistryFriendlyByteBuf buffer) {
        ChemicalStack stack = chemicalTank.getStack();
        buffer.writeBoolean(!stack.isEmpty());
        if (!stack.isEmpty()) Chemical.HOLDER_STREAM_CODEC.encode(buffer, stack.getChemicalHolder());
        buffer.writeLong(chemicalTank.getStored());
        buffer.writeLong(chemicalTank.getCapacity());
    }

    @Override
    public void decode(RegistryFriendlyByteBuf buffer) {
        ChemicalStack identity = buffer.readBoolean()
                ? new ChemicalStack(Chemical.HOLDER_STREAM_CODEC.decode(buffer), 1L) : ChemicalStack.EMPTY;
        long amount = buffer.readLong();
        long capacity = buffer.readLong();
        if (amount < 0L || capacity < amount || capacity != chemicalTank.getCapacity()
                || amount > 0L && identity.isEmpty() || amount == 0L && !identity.isEmpty()
                || !identity.isEmpty() && !chemicalTank.isValid(identity)) {
            throw new IllegalArgumentException("Invalid chemical sync state");
        }
        chemicalTank.setStack(identity.isEmpty() ? ChemicalStack.EMPTY : identity.copyWithAmount(amount));
    }

    private CapabilityResult commitActions(List<? extends CapabilityRequests.ResourceAction<?>> actions) {
        for (CapabilityRequests.ResourceAction<?> action : actions) {
            if (!(action.resource() instanceof ChemicalStack stack) || action.slot() != 0 || stack.isEmpty()) {
                return failure(MekanismFailureReasons.CHEMICAL_TYPE_MISMATCH);
            }
            long moved = LoadedMekanismBridge.transfer(chemicalTank, identity(stack), action.amount(),
                    action.insert(), Action.SIMULATE);
            if (moved != action.amount()) return unavailable(action, moved);
        }
        for (CapabilityRequests.ResourceAction<?> action : actions) {
            ChemicalStack stack = (ChemicalStack) action.resource();
            long moved = LoadedMekanismBridge.transfer(chemicalTank, identity(stack), action.amount(),
                    action.insert(), Action.EXECUTE);
            if (moved != action.amount()) return unavailable(action, moved);
        }
        return CapabilityResult.successful();
    }

    private CapabilityResult commitAsync(AsyncCapabilityOperation operation) {
        if (operation instanceof AsyncCapabilityOperation.Group(List<AsyncCapabilityOperation> operations)) {
            for (AsyncCapabilityOperation child : operations) {
                CapabilityResult result = commitAsync(child);
                if (!result.success()) return result;
            }
            return CapabilityResult.successful();
        }
        if (!(operation instanceof AsyncCapabilityOperation.Resource resource)
                || !type().id().equals(resource.capabilityId())) {
            return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        ChemicalStack stack;
        try {
            stack = NativeAsyncResourceValues.chemical(resource.resource());
        } catch (IllegalArgumentException exception) {
            return failure(MekanismFailureReasons.CHEMICAL_TYPE_MISMATCH);
        }
        return commitActions(List.of(new CapabilityRequests.ResourceAction<>(resource.slot(), stack,
                resource.amount(), resource.insert())));
    }

    private CapabilityResult unavailable(CapabilityRequests.ResourceAction<?> action, long moved) {
        return failure(action.insert() ? MekanismFailureReasons.CHEMICAL_OUTPUT_BLOCKED
                        : MekanismFailureReasons.CHEMICAL_INPUT_MISSING,
                Map.of("required", Long.toString(action.amount()),
                        "available", Long.toString(Math.max(0L, moved)),
                        "shortfall", Long.toString(Math.max(0L, action.amount() - moved))));
    }

    static ChemicalStack identity(ChemicalStack stack) {
        return stack.isEmpty() ? ChemicalStack.EMPTY : stack.copyWithAmount(1L);
    }

    private CapabilityResult failure(FailureReason reason) {
        return failure(reason, Map.of());
    }

    private CapabilityResult failure(FailureReason reason, Map<String, String> details) {
        FailureOccurrence occurrence = FailureOccurrence.at(reason, type().id(), FailurePhase.CAPABILITY_COMMIT,
                null, null, details);
        return CapabilityResult.failure(ExecutionStatus.blocked(type().id(), type().id(), occurrence));
    }
}
