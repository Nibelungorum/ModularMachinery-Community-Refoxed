package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.sync.CapabilitySyncEntry;
import cn.howxu.mmcr.api.capability.sync.CapabilitySyncRegistry;
import cn.howxu.mmcr.internal.menu.CombinedPortMenu;
import cn.howxu.mmcr.internal.menu.ExtendedCombinedMenu;
import cn.howxu.mmcr.internal.menu.ExtendedFluidMenu;
import cn.howxu.mmcr.internal.menu.ExtendedItemMenu;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.PortKinds;
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
public record PktPortStorageSyncPayload(BlockPos pos, String kind, List<CapabilitySyncEntry> entries)
        implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 1024;
    public static final int MAX_TOTAL_PAYLOAD_BYTES = 1_048_576;
    private static final int MAX_KIND_LENGTH = 256;

    public static final Type<PktPortStorageSyncPayload> TYPE = new Type<>(MMCR.id("port_storage_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktPortStorageSyncPayload> STREAM_CODEC =
            StreamCodec.of(PktPortStorageSyncPayload::write, PktPortStorageSyncPayload::read);

    public PktPortStorageSyncPayload {
        pos = pos == null ? BlockPos.ZERO : pos.immutable();
        IOPortKindView kindView = requireKind(kind);
        kind = kindView.id();
        if (entries == null || entries.size() > MAX_ENTRIES || totalPayloadBytes(entries) > MAX_TOTAL_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Invalid capability sync entry count");
        }
        entries = List.copyOf(entries);
    }

    public record ItemStorageEntry(int slot, ItemResource resource, long amount, long capacity) {
        public ItemStorageEntry {
            if (slot < 0 || resource == null || amount < 0L || capacity < amount) {
                throw new IllegalArgumentException("Invalid item presentation state");
            }
        }
    }

    public record FluidStorageEntry(int slot, FluidResource resource, long amount, long capacity) {
        public FluidStorageEntry {
            if (slot < 0 || resource == null || amount < 0L || capacity < amount) {
                throw new IllegalArgumentException("Invalid fluid presentation state");
            }
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
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                port.getLevel().registryAccess());
        return new PktPortStorageSyncPayload(port.getBlockPos(), port.kind().id(),
                CapabilitySyncRegistry.encode(port.capabilitySnapshot(), buffer));
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
        List<ServerPlayer> viewers = port.getLevel().players().stream()
                .filter(ServerPlayer.class::isInstance)
                .map(ServerPlayer.class::cast)
                .filter(player -> ownsMenu(player, port))
                .toList();
        if (viewers.isEmpty()) return;
        PktPortStorageSyncPayload payload = from(port);
        viewers.forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() == null) return;
            if (!(context.player().level().getBlockEntity(pos) instanceof IOPortBlockEntity port)
                    || !port.kind().id().equals(kind)) {
                throw new IllegalArgumentException("Port sync target does not match packet");
            }
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                    context.player().level().registryAccess());
            for (CapabilitySyncEntry entry : entries) {
                CapabilitySyncRegistry.decode(port.capabilitySnapshot(), entry, buffer);
            }
            if (context.player().containerMenu instanceof ExtendedItemMenu menu
                    && menu.matches(pos, kind)) {
                menu.applySnapshot(this, port);
            } else if (context.player().containerMenu instanceof ExtendedFluidMenu menu
                    && menu.matches(pos, kind)) {
                menu.applySnapshot(this, port);
            } else if (context.player().containerMenu instanceof CombinedPortMenu menu
                    && menu.matches(pos, kind)) {
                menu.applySnapshot(this, port);
            } else if (context.player().containerMenu instanceof ExtendedCombinedMenu menu
                    && menu.matches(pos, kind)) {
                menu.applySnapshot(this, port);
            }
        });
    }

    private static void write(RegistryFriendlyByteBuf buffer, PktPortStorageSyncPayload payload) {
        buffer.writeBlockPos(payload.pos);
        buffer.writeUtf(payload.kind, MAX_KIND_LENGTH);
        writeEntries(buffer, payload.entries);
    }

    private static PktPortStorageSyncPayload read(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        String kind = buffer.readUtf(MAX_KIND_LENGTH);
        return new PktPortStorageSyncPayload(pos, kind, readEntries(buffer));
    }

    private static void writeEntries(RegistryFriendlyByteBuf buffer, List<CapabilitySyncEntry> entries) {
        writeCount(buffer, entries.size(), "capability");
        int totalBytes = 0;
        for (CapabilitySyncEntry entry : entries) {
            totalBytes = checkedPayloadTotal(totalBytes, entry.payload().length);
            buffer.writeIdentifier(entry.typeId());
            buffer.writeVarInt(entry.capabilityIndex());
            buffer.writeByteArray(entry.payload());
        }
    }

    private static List<CapabilitySyncEntry> readEntries(RegistryFriendlyByteBuf buffer) {
        int count = readCount(buffer, "capability");
        List<CapabilitySyncEntry> entries = new ArrayList<>(count);
        int totalBytes = 0;
        for (int index = 0; index < count; index++) {
            Identifier typeId = buffer.readIdentifier();
            int capabilityIndex = buffer.readVarInt();
            int payloadLength = buffer.readVarInt();
            totalBytes = checkedPayloadTotal(totalBytes, payloadLength);
            byte[] payload = new byte[payloadLength];
            buffer.readBytes(payload);
            entries.add(new CapabilitySyncEntry(typeId, capabilityIndex, payload));
        }
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

    private static int totalPayloadBytes(List<CapabilitySyncEntry> entries) {
        int totalBytes = 0;
        for (CapabilitySyncEntry entry : entries) totalBytes = checkedPayloadTotal(totalBytes, entry.payload().length);
        return totalBytes;
    }

    private static int checkedPayloadTotal(int totalBytes, int payloadLength) {
        if (payloadLength < 0 || payloadLength > CapabilitySyncEntry.MAX_PAYLOAD_BYTES
                || payloadLength > MAX_TOTAL_PAYLOAD_BYTES - totalBytes) {
            throw new IllegalArgumentException("Invalid total capability sync payload size");
        }
        return totalBytes + payloadLength;
    }

    public static List<ItemStorageEntry> itemEntries(ResourceStorage<ItemResource> storage) {
        List<ItemStorageEntry> entries = new ArrayList<>(storage.size());
        for (int slot = 0; slot < storage.size(); slot++) {
            ItemResource resource = storage.resource(slot);
            if (resource == null) resource = ItemResource.EMPTY;
            entries.add(new ItemStorageEntry(slot, resource, storage.amount(slot), storage.capacity(slot, resource)));
        }
        return List.copyOf(entries);
    }

    public static List<FluidStorageEntry> fluidEntries(ResourceStorage<FluidResource> storage) {
        List<FluidStorageEntry> entries = new ArrayList<>(storage.size());
        for (int slot = 0; slot < storage.size(); slot++) {
            FluidResource resource = storage.resource(slot);
            if (resource == null) resource = FluidResource.EMPTY;
            entries.add(new FluidStorageEntry(slot, resource, storage.amount(slot), storage.capacity(slot, resource)));
        }
        return List.copyOf(entries);
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
            return kind.itemBusSize().map(size -> size.slots())
                    .orElseGet(() -> kind.extendedItemBusSize().map(size -> size.slots())
                            .orElseGet(() -> kind.combinedPortSize().map(size -> size.itemTypes())
                                    .orElseGet(() -> kind.extendedCombinedPortSize().map(size -> size.itemTypes()).orElse(0))));
        }

        public int fluidTankCount() {
            return kind.fluidHatchSize().map(size -> 1)
                    .orElseGet(() -> kind.extendedFluidHatchSize().map(size -> size.slots())
                            .orElseGet(() -> kind.combinedPortSize().map(size -> size.fluidTypes())
                                    .orElseGet(() -> kind.extendedCombinedPortSize().map(size -> size.fluidTypes()).orElse(0))));
        }

        public boolean allowsItems() {
            return itemSlotCount() > 0;
        }

        public boolean allowsFluids() {
            return fluidTankCount() > 0;
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
