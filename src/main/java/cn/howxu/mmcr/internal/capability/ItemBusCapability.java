package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.facet.ResourceFacet;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityPlanner;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.publicapi.machine.DisplayStack;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Machine capability backed by an item bus slot storage.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ItemBusCapability implements MachineCapability, ResourceFacet<ItemResource>, TransferFacet,
        OperationFacet, PresentationFacet, SyncFacet {
    private final IOPortBlockEntity port;
    private final IOType ioType;
    private final ResourceStorage<ItemResource> storage;
    private final CapabilityView view;
    private final AsyncPlanningFacet asyncPlanning;

    public ItemBusCapability(ResourceStorage<ItemResource> storage, IOType ioType) {
        this(null, storage, ioType, true);
    }

    public ItemBusCapability(IOPortBlockEntity port, ResourceStorage<ItemResource> storage, IOType ioType) {
        this(port, storage, ioType, true);
    }

    public ItemBusCapability(IOPortBlockEntity port, ResourceStorage<ItemResource> storage, IOType ioType,
                             boolean exposeTransferFacet) {
        if (storage == null) throw new IllegalArgumentException("storage must not be null");
        if (ioType == null) throw new IllegalArgumentException("ioType must not be null");
        this.port = port;
        this.ioType = ioType;
        this.storage = storage;
        this.asyncPlanning = new AsyncPlanningFacet() {
            @Override
            public Object planningIdentity() {
                return storage.reservationIdentity();
            }

            @Override
            protected AsyncCapabilitySnapshot captureSnapshotOnServerThread() {
                return new AsyncCapabilitySnapshot.Resource(type().id(), IntStream.range(0, storage.size())
                        .mapToObj(slot -> {
                            ItemResource resource = storage.resource(slot);
                            boolean empty = resource == null || resource.isEmpty();
                            return new AsyncCapabilitySnapshot.ResourceSlot(empty
                                    ? Optional.empty() : Optional.of(NativeAsyncResourceValues.item(resource)),
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
        };
        Set<Class<? extends CapabilityFacet>> facets = new LinkedHashSet<>(Set.of(
                ResourceFacet.class, OperationFacet.class, PresentationFacet.class, SyncFacet.class,
                AsyncPlanningFacet.class));
        if (exposeTransferFacet) facets.add(TransferFacet.class);
        this.view = CapabilityFactories.view(type(), directions(),
                Set.copyOf(facets));
    }

    public ItemBusCapability(ItemBusBlockEntity port) {
        this(port, port.itemStorage(), port.ioType());
    }

    public ResourceStorage<ItemResource> storage() {
        return storage;
    }

    @Override
    public Class<ItemResource> resourceType() {
        return ItemResource.class;
    }

    @Nullable
    public Level level() {
        return port == null ? null : port.getLevel();
    }

    public BlockPos position() {
        return port == null ? BlockPos.ZERO : port.getBlockPos();
    }

    @Override
    public boolean supportsLargeStacks() {
        return port != null && (port.kind().extendedItemBusSize().isPresent()
                || port.kind().extendedCombinedPortSize().isPresent());
    }

    @Override
    public long transferLimit() {
        return 64;
    }

    @Override
    public CapabilityType type() {
        return BuiltinCapabilityDefinitions.ITEM_TYPE;
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
        return IntStream.range(0, storage.size())
                .mapToObj(slot -> {
                    ItemResource resource = storage.resource(slot);
                    return new CapabilityDisplay("item", Long.toString(storage.amount(slot)), "item",
                            resource == null ? Optional.empty() : DisplayStack.optional(resource.toStack(1)));
                })
                .toList();
    }

    @Override
    public CapabilityOperation prepareOperation(CapabilityRequest request) {
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
        ItemResource nativeResource;
        try {
            nativeResource = NativeAsyncResourceValues.item(resource1);
        } catch (IllegalArgumentException exception) {
            return failure(BuiltinFailureReasons.WRONG_RESOURCE_TYPE);
        }
        ItemResource current;
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
        buffer.writeVarInt(storage.size());
        for (int slot = 0; slot < storage.size(); slot++) {
            ItemResource resource = storage.resource(slot);
            ItemResource.STREAM_CODEC.encode(buffer, resource == null ? ItemResource.EMPTY : resource);
            buffer.writeLong(storage.amount(slot));
            buffer.writeLong(storage.capacity(slot, resource));
        }
    }

    @Override
    public void decode(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > 1024 || count != storage.size()) throw new IllegalArgumentException("Invalid item sync state");
        try (Transaction transaction = Transaction.openRoot()) {
            for (int slot = 0; slot < count; slot++) {
                ItemResource resource = ItemResource.STREAM_CODEC.decode(buffer);
                long amount = buffer.readLong();
                long capacity = buffer.readLong();
                if (amount < 0 || capacity < amount || capacity != storage.capacity(slot, resource)) {
                    throw new IllegalArgumentException("Invalid item sync amount");
                }
                ItemResource current = storage.resource(slot);
                if (current != null && !current.isEmpty()) storage.extract(slot, current, Long.MAX_VALUE, transaction);
                if (!resource.isEmpty() && storage.insert(slot, resource, amount, transaction) != amount) {
                    throw new IllegalArgumentException("Item sync state does not fit");
                }
            }
            transaction.commit();
        }
    }
}
