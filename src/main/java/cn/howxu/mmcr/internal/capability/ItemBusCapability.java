package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.ResourceFacet;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.publicapi.machine.DisplayStack;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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

    public ItemBusCapability(ResourceStorage<ItemResource> storage, IOType ioType) {
        this(null, storage, ioType);
    }

    public ItemBusCapability(IOPortBlockEntity port, ResourceStorage<ItemResource> storage, IOType ioType) {
        if (storage == null) throw new IllegalArgumentException("storage must not be null");
        if (ioType == null) throw new IllegalArgumentException("ioType must not be null");
        this.port = port;
        this.ioType = ioType;
        this.storage = storage;
        this.view = CapabilityFactories.view(type(), ioType,
                Set.of(ResourceFacet.class, TransferFacet.class, OperationFacet.class, PresentationFacet.class,
                        SyncFacet.class));
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
    public IOType ioType() {
        return ioType;
    }

    @Override
    public CapabilityView view() {
        return view;
    }

    @Override
    public CapabilityOperation prepare(CapabilityRequest request) {
        return CapabilityFactories.operation(this, request);
    }

    @Override
    public List<CapabilityDisplay> displays(CapabilityView ignored) {
        return java.util.stream.IntStream.range(0, storage.size())
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
            return ignored -> failure("unsupported_request");
        }
        return transaction -> {
            for (CapabilityRequests.ResourceAction<?> action : resourceRequest.actions()) {
                if (!storage.resourceType().isInstance(action.resource())) return failure("wrong_resource_type");
                long moved = action.insert()
                        ? storage.insertResource(action.slot(), action.resource(), action.amount(), transaction)
                        : storage.extractResource(action.slot(), action.resource(), action.amount(), transaction);
                if (moved != action.amount()) return failure("insufficient_resource");
            }
            return CapabilityResult.successful();
        };
    }

    private CapabilityResult failure(String reason) {
        return CapabilityResult.failure(new ExecutionStatus(type().id(), StatusSeverity.BLOCKED,
                type().id(), Map.of("reason", reason)));
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
