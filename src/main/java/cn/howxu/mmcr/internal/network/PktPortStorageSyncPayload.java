package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.config.CommonConfig;
import cn.howxu.mmcr.internal.menu.CombinedPortMenu;
import cn.howxu.mmcr.internal.menu.ExtendedCombinedMenu;
import cn.howxu.mmcr.internal.menu.ExtendedFluidMenu;
import cn.howxu.mmcr.internal.menu.ExtendedItemMenu;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.PortKinds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-authoritative resource snapshot for an open extended or combined port menu.
 * Resource identity is normalized to one stack unit; the long quantity is encoded separately.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktPortStorageSyncPayload(BlockPos pos, String kind, List<ItemStorageEntry> itemEntries,
                                        List<FluidStorageEntry> fluidEntries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 1024;
    private static final int MAX_KIND_LENGTH = 256;
    public static final Type<PktPortStorageSyncPayload> TYPE = new Type<>(MMCR.id("port_storage_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktPortStorageSyncPayload> STREAM_CODEC =
            StreamCodec.of(PktPortStorageSyncPayload::write, PktPortStorageSyncPayload::read);

    public PktPortStorageSyncPayload {
        pos = pos == null ? BlockPos.ZERO : pos.immutable();
        kind = requireKind(kind).id();
        if (itemEntries == null || fluidEntries == null || itemEntries.size() + fluidEntries.size() > maxEntries()) {
            throw new IllegalArgumentException("Invalid port storage entry count");
        }
        itemEntries = List.copyOf(itemEntries);
        fluidEntries = List.copyOf(fluidEntries);
    }

    public record ItemStorageEntry(int slot, ItemStack resource, long amount, long capacity) {
        public ItemStorageEntry {
            resource = resource == null || resource.isEmpty() ? ItemStack.EMPTY : resource.copyWithCount(1);
            if (slot < 0 || amount < 0L || capacity < amount || amount > 0L && resource.isEmpty()) {
                throw new IllegalArgumentException("Invalid item presentation state");
            }
        }
    }

    public record FluidStorageEntry(int slot, FluidStack resource, long amount, long capacity) {
        public FluidStorageEntry {
            resource = resource == null || resource.isEmpty() ? FluidStack.EMPTY : resource.copyWithAmount(1);
            if (slot < 0 || amount < 0L || capacity < amount || amount > 0L && resource.isEmpty()) {
                throw new IllegalArgumentException("Invalid fluid presentation state");
            }
        }
    }

    public static IOPortKindView requireKind(String kindId) {
        if (kindId == null || kindId.isBlank() || kindId.length() > MAX_KIND_LENGTH) {
            throw new IllegalArgumentException("Invalid port kind: " + kindId);
        }
        return PortKinds.all().stream().filter(kind -> kind.id().equals(kindId)).map(IOPortKindView::new).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown port kind: " + kindId));
    }

    public static PktPortStorageSyncPayload from(IOPortBlockEntity port) {
        if (port == null) throw new IllegalArgumentException("Port must not be null");
        IOPortKindView kind = requireKind(port.kind().id());
        return new PktPortStorageSyncPayload(port.getBlockPos(), port.kind().id(),
                kind.itemSlotCount() == 0 ? List.of() : itemEntries(port.nativeItemHandler()),
                kind.fluidTankCount() == 0 ? List.of() : fluidEntries(port.nativeFluidHandler()));
    }

    public static void sendTo(ServerPlayer player, IOPortBlockEntity port) {
        if (player != null && port != null) PacketDistributor.sendToPlayer(player, from(port));
    }

    public static void sendTo(Player player, IOPortBlockEntity port) {
        if (player instanceof ServerPlayer serverPlayer) sendTo(serverPlayer, port);
    }

    public static void sendToViewers(IOPortBlockEntity port) {
        if (port == null || port.getLevel() == null || port.getLevel().isClientSide()) return;
        List<ServerPlayer> viewers = port.getLevel().players().stream().filter(ServerPlayer.class::isInstance)
                .map(ServerPlayer.class::cast).filter(player -> ownsMenu(player, port)).toList();
        if (!viewers.isEmpty()) {
            PktPortStorageSyncPayload payload = from(port);
            viewers.forEach(player -> PacketDistributor.sendToPlayer(player, payload));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().level().getBlockEntity(pos) instanceof IOPortBlockEntity port)
                    || !port.kind().id().equals(kind)) throw new IllegalArgumentException("Port sync target does not match packet");
            if (context.player().containerMenu instanceof ExtendedItemMenu menu && menu.matches(pos, kind)) menu.applySnapshot(this);
            else if (context.player().containerMenu instanceof ExtendedFluidMenu menu && menu.matches(pos, kind)) menu.applySnapshot(this);
            else if (context.player().containerMenu instanceof CombinedPortMenu menu && menu.matches(pos, kind)) menu.applySnapshot(this);
            else if (context.player().containerMenu instanceof ExtendedCombinedMenu menu && menu.matches(pos, kind)) menu.applySnapshot(this);
        });
    }

    private static void write(RegistryFriendlyByteBuf buffer, PktPortStorageSyncPayload payload) {
        buffer.writeBlockPos(payload.pos);
        buffer.writeUtf(payload.kind, MAX_KIND_LENGTH);
        buffer.writeVarInt(payload.itemEntries.size());
        for (ItemStorageEntry entry : payload.itemEntries) {
            buffer.writeVarInt(entry.slot());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, entry.resource());
            buffer.writeVarLong(entry.amount());
            buffer.writeVarLong(entry.capacity());
        }
        buffer.writeVarInt(payload.fluidEntries.size());
        for (FluidStorageEntry entry : payload.fluidEntries) {
            buffer.writeVarInt(entry.slot());
            FluidStack.OPTIONAL_STREAM_CODEC.encode(buffer, entry.resource());
            buffer.writeVarLong(entry.amount());
            buffer.writeVarLong(entry.capacity());
        }
    }

    private static PktPortStorageSyncPayload read(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        String kind = buffer.readUtf(MAX_KIND_LENGTH);
        int itemCount = readCount(buffer);
        List<ItemStorageEntry> items = new ArrayList<>(itemCount);
        for (int index = 0; index < itemCount; index++) {
            items.add(new ItemStorageEntry(buffer.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                    buffer.readVarLong(), buffer.readVarLong()));
        }
        int fluidCount = readCount(buffer);
        if (itemCount + fluidCount > maxEntries()) throw new IllegalArgumentException("Invalid port storage entry count");
        List<FluidStorageEntry> fluids = new ArrayList<>(fluidCount);
        for (int index = 0; index < fluidCount; index++) {
            fluids.add(new FluidStorageEntry(buffer.readVarInt(), FluidStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                    buffer.readVarLong(), buffer.readVarLong()));
        }
        return new PktPortStorageSyncPayload(pos, kind, items, fluids);
    }

    private static int readCount(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maxEntries()) throw new IllegalArgumentException("Invalid port storage entry count");
        return count;
    }

    public static List<ItemStorageEntry> itemEntries(IItemHandler storage) {
        if (storage == null) return List.of();
        List<ItemStorageEntry> entries = new ArrayList<>(storage.getSlots());
        for (int slot = 0; slot < storage.getSlots(); slot++) {
            ItemStack resource = storage.getStackInSlot(slot);
            if (resource.isEmpty()) continue;
            long amount = storage instanceof LongItemStorage longStorage ? longStorage.amount(slot) : resource.getCount();
            long capacity = storage instanceof LongItemStorage longStorage ? longStorage.capacity(slot) : storage.getSlotLimit(slot);
            entries.add(new ItemStorageEntry(slot, resource, amount, capacity));
        }
        return List.copyOf(entries);
    }

    public static List<FluidStorageEntry> fluidEntries(IFluidHandler storage) {
        if (storage == null) return List.of();
        List<FluidStorageEntry> entries = new ArrayList<>(storage.getTanks());
        for (int slot = 0; slot < storage.getTanks(); slot++) {
            FluidStack resource = storage.getFluidInTank(slot);
            if (resource.isEmpty()) continue;
            long amount = storage instanceof LongFluidStorage longStorage ? longStorage.amount(slot) : resource.getAmount();
            long capacity = storage instanceof LongFluidStorage longStorage ? longStorage.capacity(slot) : storage.getTankCapacity(slot);
            entries.add(new FluidStorageEntry(slot, resource, amount, capacity));
        }
        return List.copyOf(entries);
    }

    private static boolean ownsMenu(ServerPlayer player, IOPortBlockEntity port) {
        return player.containerMenu instanceof ExtendedItemMenu menu && menu.owner() == port
                || player.containerMenu instanceof ExtendedFluidMenu fluidMenu && fluidMenu.owner() == port
                || player.containerMenu instanceof CombinedPortMenu menu && menu.owner() == port
                || player.containerMenu instanceof ExtendedCombinedMenu menu && menu.owner() == port;
    }

    private static int maxEntries() { return CommonConfig.valueOrDefault(CommonConfig.PORT_STORAGE_MAX_ENTRIES, MAX_ENTRIES); }

    public record IOPortKindView(IOPortKind kind) {
        public String id() { return kind.id(); }
        public int itemSlotCount() { return kind.itemBusSize().map(size -> size.slots()).orElseGet(() -> kind.extendedItemBusSize().map(size -> size.slots()).orElseGet(() -> kind.combinedPortSize().map(size -> size.itemTypes()).orElseGet(() -> kind.extendedCombinedPortSize().map(size -> size.itemTypes()).orElse(0)))); }
        public int fluidTankCount() { return kind.fluidHatchSize().map(size -> 1).orElseGet(() -> kind.extendedFluidHatchSize().map(size -> size.slots()).orElseGet(() -> kind.combinedPortSize().map(size -> size.fluidTypes()).orElseGet(() -> kind.extendedCombinedPortSize().map(size -> size.fluidTypes()).orElse(0)))); }
        public boolean isExtendedItem() { return kind.extendedItemBusSize().isPresent(); }
        public boolean isExtendedFluid() { return kind.extendedFluidHatchSize().isPresent(); }
        public boolean isCombined() { return kind.combinedPortSize().isPresent(); }
        public boolean isExtendedCombined() { return kind.extendedCombinedPortSize().isPresent(); }
    }
}
