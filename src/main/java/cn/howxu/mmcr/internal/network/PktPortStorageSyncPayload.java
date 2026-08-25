package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.menu.CombinedPortMenu;
import cn.howxu.mmcr.internal.menu.ExtendedCombinedMenu;
import cn.howxu.mmcr.internal.menu.ExtendedFluidMenu;
import cn.howxu.mmcr.internal.menu.ExtendedItemMenu;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.PortKinds;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-authoritative resource snapshot for an open extended or combined port menu.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktPortStorageSyncPayload(BlockPos pos, String kind,
                                        List<ItemStorageEntry> itemEntries,
                                        List<FluidStorageEntry> fluidEntries)
        implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 1024;
    private static final int MAX_KIND_LENGTH = 256;

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStorageEntry> ITEM_ENTRY_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ItemStorageEntry::slot,
                    ItemResource.STREAM_CODEC, ItemStorageEntry::resource,
                    ByteBufCodecs.LONG, ItemStorageEntry::amount,
                    ByteBufCodecs.LONG, ItemStorageEntry::capacity,
                    ItemStorageEntry::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, FluidStorageEntry> FLUID_ENTRY_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FluidStorageEntry::slot,
                    FluidResource.STREAM_CODEC, FluidStorageEntry::resource,
                    ByteBufCodecs.LONG, FluidStorageEntry::amount,
                    ByteBufCodecs.LONG, FluidStorageEntry::capacity,
                    FluidStorageEntry::new);

    public static final Type<PktPortStorageSyncPayload> TYPE = new Type<>(MMCR.id("port_storage_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktPortStorageSyncPayload> STREAM_CODEC =
            StreamCodec.of(PktPortStorageSyncPayload::write, PktPortStorageSyncPayload::read);

    public PktPortStorageSyncPayload {
        pos = pos == null ? BlockPos.ZERO : pos.immutable();
        kind = requireKind(kind).id();
        itemEntries = validateEntries(itemEntries, "item");
        fluidEntries = validateEntries(fluidEntries, "fluid");
    }

    public record ItemStorageEntry(int slot, ItemResource resource, long amount, long capacity) {
        public ItemStorageEntry {
            if (slot < 0) throw new IllegalArgumentException("Item slot must be non-negative");
            if (resource == null) throw new IllegalArgumentException("Item resource must not be null");
            validateAmounts(amount, capacity);
        }
    }

    public record FluidStorageEntry(int slot, FluidResource resource, long amount, long capacity) {
        public FluidStorageEntry {
            if (slot < 0) throw new IllegalArgumentException("Fluid slot must be non-negative");
            if (resource == null) throw new IllegalArgumentException("Fluid resource must not be null");
            validateAmounts(amount, capacity);
        }
    }

    public static IOPortKindView requireKind(String kindId) {
        if (kindId == null || kindId.isBlank() || kindId.length() > MAX_KIND_LENGTH) {
            throw new IllegalArgumentException("Invalid port kind: " + kindId);
        }
        return PortKinds.all().stream()
                .filter(kind -> kind.id().equals(kindId))
                .map(IOPortKindView::new)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown port kind: " + kindId));
    }

    public static PktPortStorageSyncPayload from(IOPortBlockEntity port) {
        if (port == null) throw new IllegalArgumentException("Port must not be null");
        List<ItemStorageEntry> items = itemEntries(port);
        List<FluidStorageEntry> fluids = fluidEntries(port);
        return new PktPortStorageSyncPayload(port.getBlockPos(), port.kind().id(), items, fluids);
    }

    public static void sendTo(ServerPlayer player, IOPortBlockEntity port) {
        if (player == null || port == null) return;
        PacketDistributor.sendToPlayer(player, from(port));
    }

    public static void sendTo(Player player, IOPortBlockEntity port) {
        if (player instanceof ServerPlayer serverPlayer) sendTo(serverPlayer, port);
    }

    public static void sendToViewers(IOPortBlockEntity port) {
        if (port == null || port.getLevel() == null || port.getLevel().isClientSide()) return;
        PktPortStorageSyncPayload payload = from(port);
        port.getLevel().players().stream()
                .filter(ServerPlayer.class::isInstance)
                .map(ServerPlayer.class::cast)
                .filter(player -> ownsMenu(player, port))
                .forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() == null) return;
            if (context.player().containerMenu instanceof ExtendedItemMenu menu
                    && menu.matches(pos, kind)) {
                menu.applySnapshot(this);
            } else if (context.player().containerMenu instanceof ExtendedFluidMenu menu
                    && menu.matches(pos, kind)) {
                menu.applySnapshot(this);
            } else if (context.player().containerMenu instanceof CombinedPortMenu menu
                    && menu.matches(pos, kind)) {
                menu.applySnapshot(this);
            } else if (context.player().containerMenu instanceof ExtendedCombinedMenu menu
                    && menu.matches(pos, kind)) {
                menu.applySnapshot(this);
            }
        });
    }

    private static void write(RegistryFriendlyByteBuf buffer, PktPortStorageSyncPayload payload) {
        buffer.writeBlockPos(payload.pos);
        buffer.writeUtf(payload.kind, MAX_KIND_LENGTH);
        writeItems(buffer, payload.itemEntries);
        writeFluids(buffer, payload.fluidEntries);
    }

    private static PktPortStorageSyncPayload read(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        String kind = buffer.readUtf(MAX_KIND_LENGTH);
        return new PktPortStorageSyncPayload(pos, kind, readItems(buffer), readFluids(buffer));
    }

    private static void writeItems(RegistryFriendlyByteBuf buffer, List<ItemStorageEntry> entries) {
        writeCount(buffer, entries.size(), "item");
        for (ItemStorageEntry entry : entries) ITEM_ENTRY_CODEC.encode(buffer, entry);
    }

    private static void writeFluids(RegistryFriendlyByteBuf buffer, List<FluidStorageEntry> entries) {
        writeCount(buffer, entries.size(), "fluid");
        for (FluidStorageEntry entry : entries) FLUID_ENTRY_CODEC.encode(buffer, entry);
    }

    private static List<ItemStorageEntry> readItems(RegistryFriendlyByteBuf buffer) {
        int count = readCount(buffer, "item");
        List<ItemStorageEntry> entries = new ArrayList<>(count);
        for (int slot = 0; slot < count; slot++) entries.add(ITEM_ENTRY_CODEC.decode(buffer));
        return entries;
    }

    private static List<FluidStorageEntry> readFluids(RegistryFriendlyByteBuf buffer) {
        int count = readCount(buffer, "fluid");
        List<FluidStorageEntry> entries = new ArrayList<>(count);
        for (int slot = 0; slot < count; slot++) entries.add(FLUID_ENTRY_CODEC.decode(buffer));
        return entries;
    }

    private static void writeCount(RegistryFriendlyByteBuf buffer, int count, String name) {
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid " + name + " entry count");
        buffer.writeVarInt(count);
    }

    private static int readCount(RegistryFriendlyByteBuf buffer, String name) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid " + name + " entry count");
        return count;
    }

    private static List<ItemStorageEntry> itemEntries(IOPortBlockEntity port) {
        return port.capabilitySnapshot().capabilities().stream()
                .filter(ItemBusCapability.class::isInstance)
                .map(ItemBusCapability.class::cast)
                .findFirst()
                .map(ItemBusCapability::storage)
                .map(PktPortStorageSyncPayload::itemEntries)
                .orElseGet(List::of);
    }

    private static List<FluidStorageEntry> fluidEntries(IOPortBlockEntity port) {
        return port.capabilitySnapshot().capabilities().stream()
                .filter(FluidHatchCapability.class::isInstance)
                .map(FluidHatchCapability.class::cast)
                .findFirst()
                .map(FluidHatchCapability::storage)
                .map(PktPortStorageSyncPayload::fluidEntries)
                .orElseGet(List::of);
    }

    private static List<ItemStorageEntry> itemEntries(ResourceStorage<ItemResource> storage) {
        List<ItemStorageEntry> entries = new ArrayList<>(storage.size());
        for (int slot = 0; slot < storage.size(); slot++) {
            ItemResource resource = storage.resource(slot);
            if (resource == null) resource = ItemResource.EMPTY;
            entries.add(new ItemStorageEntry(slot, resource, storage.amount(slot), storage.capacity(slot, resource)));
        }
        return entries;
    }

    private static List<FluidStorageEntry> fluidEntries(ResourceStorage<FluidResource> storage) {
        List<FluidStorageEntry> entries = new ArrayList<>(storage.size());
        for (int slot = 0; slot < storage.size(); slot++) {
            FluidResource resource = storage.resource(slot);
            if (resource == null) resource = FluidResource.EMPTY;
            entries.add(new FluidStorageEntry(slot, resource, storage.amount(slot), storage.capacity(slot, resource)));
        }
        return entries;
    }

    private static <T> List<T> validateEntries(List<T> entries, String name) {
        if (entries == null || entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid " + name + " entry count");
        }
        List<T> copy = List.copyOf(entries);
        int previousSlot = -1;
        for (T value : copy) {
            Object entry = value;
            int slot = entry instanceof ItemStorageEntry item ? item.slot()
                    : ((FluidStorageEntry) entry).slot();
            if (slot <= previousSlot) throw new IllegalArgumentException("Invalid " + name + " entry order");
            previousSlot = slot;
        }
        return copy;
    }

    private static void validateAmounts(long amount, long capacity) {
        if (amount < 0L || capacity < 0L || amount > capacity) {
            throw new IllegalArgumentException("Invalid resource amount/capacity");
        }
    }

    private static boolean ownsMenu(ServerPlayer player, IOPortBlockEntity port) {
        if (player.containerMenu instanceof ExtendedItemMenu menu) return menu.owner() == port;
        if (player.containerMenu instanceof ExtendedFluidMenu menu) return menu.owner() == port;
        if (player.containerMenu instanceof CombinedPortMenu menu) return menu.owner() == port;
        if (player.containerMenu instanceof ExtendedCombinedMenu menu) return menu.owner() == port;
        return false;
    }

    /** Narrow view used by menu open-data validation without exposing registry implementation details. */
    public record IOPortKindView(IOPortKind kind) {
        public String id() {
            return kind.id();
        }

        public int itemSlotCount() {
            return kind.extendedItemBusSize().map(size -> size.slots())
                    .orElseGet(() -> kind.combinedPortSize().map(size -> size.itemTypes())
                            .orElseGet(() -> kind.extendedCombinedPortSize().map(size -> size.itemTypes()).orElse(0)));
        }

        public int fluidTankCount() {
            return kind.extendedFluidHatchSize().map(size -> size.slots())
                    .orElseGet(() -> kind.combinedPortSize().map(size -> size.fluidTypes())
                            .orElseGet(() -> kind.extendedCombinedPortSize().map(size -> size.fluidTypes()).orElse(0)));
        }

        public boolean isExtendedItem() {
            return kind.extendedItemBusSize().isPresent();
        }

        public boolean isExtendedFluid() {
            return kind.extendedFluidHatchSize().isPresent();
        }

        public boolean isCombined() {
            return kind.combinedPortSize().isPresent();
        }

        public boolean isExtendedCombined() {
            return kind.extendedCombinedPortSize().isPresent();
        }
    }
}
