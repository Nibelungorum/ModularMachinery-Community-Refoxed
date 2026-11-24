package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.internal.multiblock.StatusBlockCoordinator;
import cn.howxu.mmcr.internal.sync.MachineStructureSyncCodec;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import cn.howxu.mmcr.internal.registration.RuntimeContentCoordinator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import java.util.Objects;

/**
 * Builds and sends server-authoritative runtime content snapshots.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RuntimeContentSync {
    private static BiConsumer<MinecraftServer, RuntimeContentSnapshot> sender = RuntimeContentSync::sendToAllPlayers;
    private static boolean senderIsDefault = true;

    private RuntimeContentSync() {
    }

    public static RuntimeContentSnapshot createSnapshot() {
        return RuntimeContentCoordinator.createSnapshot();
    }

    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            // This runs inside the vanilla join path (placeNewPlayer). Vanilla wraps ANY exception there as
            // "multiplayer.disconnect.invalid_player_data", so a sync failure would look like corrupt player
            // data and kick the client. Never let that happen.
            try {
                sendTo(event.getPlayer());
            } catch (Throwable failure) {
                MMCR.LOG.error("Runtime content sync failed for {}; the player stays connected without it",
                        event.getPlayer().getGameProfile().getName(), failure);
            }
        } else {
            var server = event.getPlayerList().getServer();
            server.getAllLevels().forEach(level -> StatusBlockCoordinator.get(level).reload());
            sendToAll(server);
        }
    }

    public static void sendTo(ServerPlayer player) {
        sendTo(player, createSnapshot());
    }

    public static void sendTo(ServerPlayer player, RuntimeContentSnapshot snapshot) {
        if (player == null || snapshot == null) return;
        StructureSyncPlan plan;
        try {
            plan = planStructurePackets(snapshot, player.level().registryAccess());
        } catch (RuntimeException planning) {
            // Fall back to the metadata-only payload: the client still gets recipes and specs, and the join
            // is never aborted by a structure that cannot be serialized.
            MMCR.LOG.error("Could not plan machine structure chunks; sending metadata only", planning);
            RuntimeContentSnapshot metadataOnly = new RuntimeContentSnapshot(Map.of(), snapshot.recipes(),
                    snapshot.controllerSpecs(), snapshot.appearances(), snapshot.machineRecipePools(),
                    snapshot.contentVersion());
            PacketDistributor.sendToPlayer(player, new PktRuntimeContentPayload(metadataOnly, List.of()));
            return;
        }
        for (PktStructureChunkPayload chunk : plan.chunks()) {
            PacketDistributor.sendToPlayer(player, chunk);
        }
        PacketDistributor.sendToPlayer(player, plan.metadata());
    }

    /** The packets one player receives for a snapshot: structure slices first, then the metadata. */
    record StructureSyncPlan(List<PktStructureChunkPayload> chunks, PktRuntimeContentPayload metadata) {
    }

    /**
     * Plans the wire traffic for a snapshot.
     *
     * <p>Vanilla rejects a single custom payload above 1 MiB, and one large multiblock pattern encodes to
     * several megabytes, so every structure is deflated and sliced before the metadata payload that names
     * them. The client reassembles and applies the merged snapshot. Pure function of (snapshot, registries)
     * so the exact bytes the client will see can be produced in a test without a live player.
     */
    static StructureSyncPlan planStructurePackets(RuntimeContentSnapshot snapshot, RegistryAccess registries) {
        return planStructurePackets(snapshot, registries, PktStructureChunkPayload.MAX_CHUNK_BYTES);
    }

    /** Same plan with an explicit slice size; tests use a small one to exercise multi-slice transfers. */
    static StructureSyncPlan planStructurePackets(RuntimeContentSnapshot snapshot, RegistryAccess registries,
                                                  int maxChunkBytes) {
        List<ResourceLocation> chunked = new ArrayList<>();
        List<PktStructureChunkPayload> chunks = new ArrayList<>();
        int maxBlocks = MachineStructureSyncCodec.maximumBlockPatternCount();
        for (ResourceLocation machineId : snapshot.structures().keySet()) {
            MachineStructureDefinition definition = snapshot.structures().get(machineId);
            byte[] raw;
            try {
                raw = encodeStructure(definition, registries, maxBlocks);
            } catch (RuntimeException failure) {
                // One unserializable structure must never fail the whole sync or the join: report it and let
                // every other machine through. This covers the oversized case and anything else (for example
                // a symbol bound to a block from a mod that is not installed).
                MMCR.LOG.error("Skipping structure sync for {}: {} (that machine will not be available on "
                        + "this client)", machineId, failure.getMessage());
                continue;
            }
            List<byte[]> slices = StructureChunkAssembler.split(
                    StructureChunkAssembler.deflate(raw), maxChunkBytes);
            for (int index = 0; index < slices.size(); index++) {
                chunks.add(new PktStructureChunkPayload(
                        snapshot.contentVersion(), machineId, index, slices.size(), slices.get(index)));
            }
            chunked.add(machineId);
        }
        RuntimeContentSnapshot metadata = new RuntimeContentSnapshot(Map.of(), snapshot.recipes(),
                snapshot.controllerSpecs(), snapshot.appearances(), snapshot.machineRecipePools(),
                snapshot.contentVersion());
        long bytes = 0;
        for (PktStructureChunkPayload chunk : chunks) bytes += chunk.data().length;
        if (!chunks.isEmpty()) {
            MMCR.LOG.info("Sending {} machine structure(s) to a player in {} chunk(s), {} KiB compressed (version {})",
                    chunked.size(), chunks.size(), bytes / 1024, snapshot.contentVersion());
        }
        return new StructureSyncPlan(List.copyOf(chunks), new PktRuntimeContentPayload(metadata, chunked));
    }

    private static byte[] encodeStructure(MachineStructureDefinition definition, RegistryAccess registries,
                                          int maxBlocks) {
        ByteBuf buffer = Unpooled.buffer();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(buffer, registries);
        try {
            MachineStructureSyncCodec.encode(buf, definition, maxBlocks);
            byte[] raw = new byte[buf.writerIndex()];
            buffer.getBytes(0, raw);
            return raw;
        } finally {
            buf.release();
        }
    }

    public static void sendToAll(MinecraftServer server) {
        if (!senderIsDefault) {
            sender.accept(server, createSnapshot());
            return;
        }
        sendToAllPlayers(server, createSnapshot());
    }

    public static void sendToAll(MinecraftServer server, RuntimeContentSnapshot snapshot) {
        if (senderIsDefault) {
            sendToAllPlayers(server, snapshot);
        } else {
            sender.accept(server, snapshot);
        }
    }

    private static void sendToAllPlayers(MinecraftServer server) {
        if (server == null) return;
        sendToAllPlayers(server, createSnapshot());
    }

    private static void sendToAllPlayers(MinecraftServer server, RuntimeContentSnapshot snapshot) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTo(player, snapshot);
        }
    }

    public static void setSenderForTesting(Consumer<MinecraftServer> sender) {
        RuntimeContentSync.sender = (server, ignored) -> sender.accept(server);
        senderIsDefault = false;
    }

    public static void setSenderForTesting(BiConsumer<MinecraftServer, RuntimeContentSnapshot> sender) {
        RuntimeContentSync.sender = Objects.requireNonNull(sender);
        senderIsDefault = false;
    }

    public static void resetSenderForTesting() {
        sender = RuntimeContentSync::sendToAllPlayers;
        senderIsDefault = true;
    }
}
