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
import cn.howxu.mmcr.api.capability.facet.ItemHandlerFacet;
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
import cn.howxu.mmcr.api.publicapi.machine.DisplayStack;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Machine capability backed by a native item handler.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ItemBusCapability implements MachineCapability, ItemHandlerFacet, TransferFacet,
        OperationFacet, PresentationFacet, SyncFacet {
    private final IOPortBlockEntity port;
    private final IOType ioType;
    private final IItemHandler itemHandler;
    private final CapabilityView view;
    private final AsyncPlanningFacet asyncPlanning;

    public ItemBusCapability(IItemHandler itemHandler, IOType ioType) {
        this(null, itemHandler, ioType);
    }

    public ItemBusCapability(IOPortBlockEntity port, IItemHandler itemHandler, IOType ioType) {
        if (itemHandler == null) throw new IllegalArgumentException("itemHandler must not be null");
        if (ioType == null) throw new IllegalArgumentException("ioType must not be null");
        this.port = port;
        this.ioType = ioType;
        this.itemHandler = itemHandler;
        this.asyncPlanning = new AsyncPlanningFacet() {
            @Override public Object planningIdentity() { return itemHandler; }

            @Override
            protected AsyncCapabilitySnapshot captureSnapshotOnServerThread() {
                return new AsyncCapabilitySnapshot.Resource(type().id(), IntStream.range(0, itemHandler.getSlots())
                        .mapToObj(slot -> {
                            ItemStack stack = itemHandler.getStackInSlot(slot);
                            return new AsyncCapabilitySnapshot.ResourceSlot(stack.isEmpty() ? Optional.empty()
                                    : Optional.of(NativeAsyncResourceValues.item(stack)),
                                    stack.isEmpty() ? 0L : itemAmount(slot), itemCapacity(slot));
                        }).toList());
            }

            @Override protected AsyncCapabilityPlanner workerPlannerOnServerThread() {
                return new AsyncCapabilityPlanner.Resource(type().id());
            }

            @Override protected CapabilityResult commitNativeOnServerThread(AsyncCapabilityOperation operation) {
                return commitNativeAsync(operation);
            }
        };
        this.view = CapabilityFactories.view(type(), directions(), Set.of(ItemHandlerFacet.class, TransferFacet.class,
                OperationFacet.class, PresentationFacet.class, SyncFacet.class, AsyncPlanningFacet.class));
    }

    @Override public IItemHandler itemHandler() { return itemHandler; }

    @Override
    public boolean supportsLargeStacks() {
        return port != null && (port.kind().extendedItemBusSize().isPresent()
                || port.kind().extendedCombinedPortSize().isPresent());
    }

    @Nullable @Override public Level level() { return port == null ? null : port.getLevel(); }

    @Override public BlockPos position() { return port == null ? BlockPos.ZERO : port.getBlockPos(); }

    @Override public long transferLimit() { return 64L; }

    @Override public CapabilityType type() { return BuiltinCapabilityDefinitions.ITEM_TYPE; }

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
        return IntStream.range(0, itemHandler.getSlots()).mapToObj(slot -> new CapabilityDisplay("item",
                Long.toString(itemAmount(slot)), "item", DisplayStack.optional(itemHandler.getStackInSlot(slot).copyWithCount(1))))
                .toList();
    }

    @Override
    public CapabilityOperation prepareOperation(CapabilityRequest request) {
        if (!(request instanceof CapabilityRequests.ItemRequest itemRequest)) return () -> failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        return () -> commitNative(itemRequest);
    }

    private CapabilityResult commitNative(CapabilityRequests.ItemRequest request) {
        for (CapabilityRequests.ItemAction action : request.actions()) {
            long remaining = action.amount();
            while (remaining > 0L) {
                int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
                ItemStack stack = action.stack().copyWithCount(chunk);
                ItemStack simulated = action.insert() ? itemHandler.insertItem(action.slot(), stack, true)
                        : itemHandler.extractItem(action.slot(), chunk, true);
                long moved = action.insert() ? chunk - simulated.getCount() : simulated.getCount();
                if (moved != chunk) return failure(action.insert() ? BuiltinFailureReasons.MISSING_OUTPUT
                        : BuiltinFailureReasons.MISSING_INPUT, movementDetails(chunk, moved));
                ItemStack executed = action.insert() ? itemHandler.insertItem(action.slot(), stack, false)
                        : itemHandler.extractItem(action.slot(), chunk, false);
                long committed = action.insert() ? chunk - executed.getCount() : executed.getCount();
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
                net.minecraft.resources.ResourceLocation capabilityId, int slot,
                cn.howxu.mmcr.api.capability.async.AsyncResourceValue value, long amount, boolean insert))
                || !type().id().equals(capabilityId)) return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        try {
            return commitNative(new CapabilityRequests.ItemRequest(type(), insert ? IOType.OUTPUT : IOType.INPUT, 1L,
                    List.of(new CapabilityRequests.ItemAction(slot, NativeAsyncResourceValues.item(value), amount, insert))));
        } catch (IllegalArgumentException exception) {
            return failure(BuiltinFailureReasons.WRONG_RESOURCE_TYPE);
        }
    }

    private long itemAmount(int slot) {
        return itemHandler instanceof cn.howxu.mmcr.internal.storage.LongItemStorage storage ? storage.amount(slot)
                : itemHandler instanceof NativeStackSync.Item storage ? storage.amount(slot)
                : itemHandler.getStackInSlot(slot).getCount();
    }

    private long itemCapacity(int slot) {
        return itemHandler instanceof cn.howxu.mmcr.internal.storage.LongItemStorage storage ? storage.capacity(slot)
                : itemHandler instanceof NativeStackSync.Item storage ? storage.capacity(slot) : itemHandler.getSlotLimit(slot);
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
        buffer.writeVarInt(itemHandler.getSlots());
        for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
            ItemStack.STREAM_CODEC.encode(buffer, itemHandler.getStackInSlot(slot).copyWithCount(1));
            buffer.writeLong(itemAmount(slot));
            buffer.writeLong(itemCapacity(slot));
        }
    }

    @Override
    public void decode(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > 1024 || count != itemHandler.getSlots()) throw new IllegalArgumentException("Invalid item sync state");
        for (int slot = 0; slot < count; slot++) {
            ItemStack stack = ItemStack.STREAM_CODEC.decode(buffer);
            long amount = buffer.readLong();
            long capacity = buffer.readLong();
            if (amount < 0L || capacity < amount || capacity != itemCapacity(slot)) throw new IllegalArgumentException("Invalid item sync amount");
            if (itemHandler instanceof cn.howxu.mmcr.internal.storage.LongItemStorage storage) storage.setContents(slot, stack, amount);
            else if (itemHandler instanceof NativeStackSync.Item storage) storage.setContents(slot, stack, amount);
            else throw new IllegalStateException("Item handler cannot apply synchronized state");
        }
    }
}
