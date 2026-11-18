package cn.howxu.mmcr.compat.mekanism.loaded;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.ResourceFacet;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityPlanner;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalViewFacet;
import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.util.IOType;
import mekanism.api.AutomationType;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalResource;
import mekanism.api.chemical.IChemicalTank;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.transaction.Transaction;
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
        ResourceFacet<ChemicalResource>, ChemicalViewFacet, TransferFacet, OperationFacet, PresentationFacet, SyncFacet {
    private static final CapabilityType TYPE = new CapabilityType(MekanismRecipeTypes.CHEMICAL);

    private final ChemicalPortBlockEntity port;
    private final IChemicalTank chemicalTank;
    private final ResourceStorage<ChemicalResource> storage;
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
        this.storage = new ChemicalStorage(chemicalTank);
        this.ioType = ioType;
        this.asyncPlanning = new AsyncPlanningFacet() {
            @Override
            public Object planningIdentity() {
                return chemicalTank;
            }

            @Override
            protected AsyncCapabilitySnapshot captureSnapshotOnServerThread() {
                ChemicalResource resource = chemicalTank.resource();
                boolean empty = resource.isEmpty();
                return new AsyncCapabilitySnapshot.Resource(type().id(), List.of(
                        new AsyncCapabilitySnapshot.ResourceSlot(empty ? Optional.empty()
                                : Optional.of(ChemicalResourceValues.chemical(resource)),
                                empty ? 0L : chemicalTank.amountAsLong(), chemicalTank.capacityAsLong(resource))));
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
        };
        this.view = CapabilityFactories.view(TYPE, directions(),
                Set.of(ResourceFacet.class, ChemicalViewFacet.class, TransferFacet.class, OperationFacet.class,
                        PresentationFacet.class, SyncFacet.class, AsyncPlanningFacet.class));
    }

    @Override
    public IChemicalTank chemicalTank() {
        return chemicalTank;
    }

    @Override
    public boolean radioactive() {
        if (port == null) {
            throw new IllegalStateException("radioactive() requires host ChemicalPortBlockEntity");
        }
        return port.isRadioactive();
    }

    @Override
    public ResourceStorage<ChemicalResource> storage() {
        return storage;
    }

    @Override
    public Class<ChemicalResource> resourceType() {
        return ChemicalResource.class;
    }

    @Override
    public Optional<Identifier> chemicalId() {
        ChemicalResource resource = chemicalTank.resource();
        return resource.isEmpty() ? Optional.empty()
                : Optional.of(Identifier.parse(resource.typeHolder().getRegisteredName()));
    }

    @Override
    public long amount() {
        return Math.max(0L, chemicalTank.amountAsLong());
    }

    @Override
    public boolean matchesTag(Identifier tagId) {
        if (tagId == null || chemicalTank.resource().isEmpty()) return false;
        return chemicalTank.resource().typeHolder().is(TagKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, tagId));
    }

    @Override
    public long outputCapacity(Identifier chemicalId) {
        if (chemicalId == null) return 0L;
        Optional<Holder.Reference<Chemical>> holder = MekanismAPI.CHEMICAL_REGISTRY.get(
                ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, chemicalId));
        if (holder.isEmpty()) return 0L;
        ChemicalResource resource = ChemicalResource.of(holder.get());
        ChemicalResource current = chemicalTank.resource();
        if ((!current.isEmpty() && !current.equals(resource)) || !chemicalTank.isValid(resource)) return 0L;
        return Math.max(0L, chemicalTank.capacityAsLong(resource) - chemicalTank.amountAsLong());
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
        return Integer.MAX_VALUE;
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
            return ignored -> failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        CapabilityOperation operation = transaction -> {
            for (CapabilityRequests.ResourceAction<?> action : resourceRequest.actions()) {
                if (!(action.resource() instanceof ChemicalResource resource)) {
                    return failure(MekanismFailureReasons.CHEMICAL_TYPE_MISMATCH);
                }
                long moved = action.insert()
                        ? storage.insert(action.slot(), resource, action.amount(), transaction)
                        : storage.extract(action.slot(), resource, action.amount(), transaction);
                if (moved != action.amount()) {
                    return failure(action.insert() ? MekanismFailureReasons.CHEMICAL_OUTPUT_BLOCKED
                                    : MekanismFailureReasons.CHEMICAL_INPUT_MISSING,
                            Map.of("required", Long.toString(action.amount()),
                                    "available", Long.toString(Math.max(0L, moved)),
                                    "shortfall", Long.toString(Math.max(0L, action.amount() - moved))));
                }
            }
            return CapabilityResult.successful();
        };
        return new CapabilityOperation() {
            public CapabilityResult commit(TransactionContext transaction) { return operation.commit(transaction); }
            public CapabilityOperation forParallelism(long parallelism) {
                return parallelism == resourceRequest.parallelism() ? this : null;
            }
        };
    }

    @Override
    public List<CapabilityDisplay> displays(CapabilityView ignored) {
        return List.of(new CapabilityDisplay("chemical", Long.toString(chemicalTank.amountAsLong()),
                "mB", Optional.empty()));
    }

    @Override
    public void encode(RegistryFriendlyByteBuf buffer) {
        ChemicalResource resource = chemicalTank.resource();
        ChemicalResource.STREAM_CODEC.encode(buffer, resource);
        buffer.writeLong(chemicalTank.amountAsLong());
        buffer.writeLong(chemicalTank.capacityAsLong(resource));
    }

    @Override
    public void decode(RegistryFriendlyByteBuf buffer) {
        ChemicalResource resource = ChemicalResource.STREAM_CODEC.decode(buffer);
        long amount = buffer.readLong();
        long capacity = buffer.readLong();
        if (amount < 0L || capacity < amount || capacity != chemicalTank.capacityAsLong(resource)
                || (!resource.isEmpty() && !chemicalTank.isValid(resource))) {
            throw new IllegalArgumentException("Invalid chemical sync state");
        }
        chemicalTank.setContents(resource, amount, null);
    }

    static ResourceHandler<ChemicalResource> resourceHandler(IChemicalTank tank) {
        return resourceHandler(tank, AutomationType.INTERNAL);
    }

    static ResourceHandler<ChemicalResource> resourceHandler(IChemicalTank tank, AutomationType automationType) {
        return resourceHandler(tank, automationType, null);
    }

    static ResourceHandler<ChemicalResource> resourceHandler(IChemicalTank tank, AutomationType automationType,
                                                              @Nullable IOType ioType) {
        return new ResourceHandler<>() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public ChemicalResource getResource(int slot) {
                checkSlot(slot);
                return tank.resource();
            }

            @Override
            public long getAmountAsLong(int slot) {
                checkSlot(slot);
                return tank.amountAsLong();
            }

            @Override
            public long getCapacityAsLong(int slot, ChemicalResource resource) {
                checkSlot(slot);
                return tank.capacityAsLong(resource == null ? tank.resource() : resource);
            }

            @Override
            public boolean isValid(int slot, ChemicalResource resource) {
                checkSlot(slot);
                return resource != null && !resource.isEmpty() && tank.isValid(resource);
            }

            @Override
            public int insert(int slot, ChemicalResource resource, int amount, TransactionContext transaction) {
                checkSlot(slot);
                if (ioType == IOType.OUTPUT) return 0;
                return tank.insert(resource, amount, transaction, automationType);
            }

            @Override
            public int extract(int slot, ChemicalResource resource, int amount, TransactionContext transaction) {
                checkSlot(slot);
                if (ioType == IOType.INPUT) return 0;
                return tank.extract(resource, amount, transaction, automationType);
            }
        };
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
        if (!(operation instanceof AsyncCapabilityOperation.Resource resource)
                || !type().id().equals(resource.capabilityId())) {
            return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        ChemicalResource nativeResource;
        try {
            nativeResource = ChemicalResourceValues.chemical(resource.resource());
        } catch (IllegalArgumentException exception) {
            return failure(MekanismFailureReasons.CHEMICAL_TYPE_MISMATCH);
        }
        ChemicalResource current = chemicalTank.resource();
        boolean matches = !current.isEmpty() && current.equals(nativeResource);
        if ((!resource.insert() && !matches) || (resource.insert() && !current.isEmpty() && !matches)) {
            return failure(resource.insert() ? MekanismFailureReasons.CHEMICAL_OUTPUT_BLOCKED
                    : MekanismFailureReasons.CHEMICAL_INPUT_MISSING);
        }
        long moved = resource.insert()
                ? storage.insert(0, nativeResource, resource.amount(), transaction)
                : storage.extract(0, nativeResource, resource.amount(), transaction);
        return moved == resource.amount() ? CapabilityResult.successful()
                : failure(resource.insert() ? MekanismFailureReasons.CHEMICAL_OUTPUT_BLOCKED
                : MekanismFailureReasons.CHEMICAL_INPUT_MISSING);
    }

    private static void checkSlot(int slot) {
        if (slot != 0) throw new IndexOutOfBoundsException("chemical port slot must be zero");
    }

    private record ChemicalStorage(IChemicalTank tank) implements ResourceStorage<ChemicalResource> {

        @Override
            public Class<ChemicalResource> resourceType() {
                return ChemicalResource.class;
            }

            @Override
            public int size() {
                return 1;
            }

            @Override
            public ChemicalResource resource(int slot) {
                checkSlot(slot);
                return tank.resource();
            }

            @Override
            public long amount(int slot) {
                checkSlot(slot);
                return tank.amountAsLong();
            }

            @Override
            public long capacity(int slot, @Nullable ChemicalResource resource) {
                checkSlot(slot);
                return tank.capacityAsLong(resource == null ? tank.resource() : resource);
            }

            @Override
            public boolean isValid(int slot, ChemicalResource resource) {
                checkSlot(slot);
                return !resource.isEmpty() && tank.isValid(resource);
            }

            @Override
            public long insert(int slot, ChemicalResource resource, long amount, TransactionContext transaction) {
                checkSlot(slot);
                if (amount <= 0L || !isValid(slot, resource)) return 0L;
                return transfer(tank, resource, amount, transaction, true);
            }

            @Override
            public long extract(int slot, ChemicalResource resource, long amount, TransactionContext transaction) {
                checkSlot(slot);
                if (amount <= 0L || resource.isEmpty()) return 0L;
                return transfer(tank, resource, amount, transaction, false);
            }

            private static long transfer(IChemicalTank tank, ChemicalResource resource, long amount,
                                         TransactionContext transaction, boolean insert) {
                long remaining = amount;
                long movedTotal = 0L;
                while (remaining > 0L) {
                    int request = (int) Math.min(remaining, Integer.MAX_VALUE);
                    int moved = insert
                            ? tank.insert(resource, request, transaction, AutomationType.INTERNAL)
                            : tank.extract(resource, request, transaction, AutomationType.INTERNAL);
                    if (moved <= 0) break;
                    movedTotal += moved;
                    remaining -= moved;
                }
                return movedTotal;
            }
        }
}
