package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.internal.multiblock.StatusBlockCoordinator;
import cn.howxu.mmcr.internal.registration.RuntimeContentCoordinator;
import cn.howxu.mmcr.internal.sync.MachineStructureSyncCodec;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Builds and sends server-authoritative runtime content snapshots.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RuntimeContentSync {
    private static BiConsumer<MinecraftServer, RuntimeContentSnapshot> sender = RuntimeContentSync::sendToAllPlayers;

    private RuntimeContentSync() {
    }

    public static RuntimeContentSnapshot createSnapshot() {
        return RuntimeContentCoordinator.createSnapshot();
    }

    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            try {
                sendTo(event.getPlayer());
            } catch (RuntimeException failure) {
                MMCR.LOG.error("Runtime content sync failed for {}", event.getPlayer().getGameProfile().name(), failure);
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
        planStructurePackets(snapshot, player.registryAccess(), PktStructureChunkPayload.MAX_CHUNK_BYTES).sendTo(player);
    }

    record StructureSyncPlan(List<PktStructureChunkPayload> chunks, PktRuntimeContentPayload metadata) {
        void sendTo(ServerPlayer player) {
            for (var chunk : chunks) PacketDistributor.sendToPlayer(player, chunk);
            PacketDistributor.sendToPlayer(player, metadata);
        }
    }

    static StructureSyncPlan planStructurePackets(RuntimeContentSnapshot snapshot, RegistryAccess registries, int chunkSize) {
        int maxBlocks = MachineStructureSyncCodec.maximumBlockPatternCount();
        List<Identifier> ids = new ArrayList<>();
        List<PktStructureChunkPayload> chunks = new ArrayList<>();
        for (var entry : snapshot.structures().entrySet()) {
            Identifier id = entry.getKey();
            MachineStructureDefinition definition = entry.getValue();
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(256, StructureChunkAssembler.MAX_BYTES), registries);
            try {
                if (!id.equals(definition.machineId())) throw new IllegalArgumentException("Structure id mismatch");
                MachineStructureSyncCodec.encode(buf, definition, maxBlocks);
                var slices = StructureChunkAssembler.split(buf, chunkSize);
                for (int i = 0; i < slices.size(); i++) {
                    chunks.add(new PktStructureChunkPayload(snapshot.contentVersion(), id, i, slices.size(), slices.get(i)));
                }
                ids.add(id);
            } catch (RuntimeException failure) {
                MMCR.LOG.error("Skipping structure {} during runtime sync: {}", id, failure.getMessage());
            } finally {
                buf.release();
            }
        }
        var metadata = new RuntimeContentSnapshot(Map.of(), snapshot.recipes(), snapshot.controllerSpecs(),
                snapshot.appearances(), snapshot.machineRecipePools(), snapshot.contentVersion());
        return new StructureSyncPlan(List.copyOf(chunks), new PktRuntimeContentPayload(metadata, ids, maxBlocks));
    }

    public static void sendToAll(MinecraftServer server) {
        sendToAll(server, createSnapshot());
    }

    public static void sendToAll(MinecraftServer server, RuntimeContentSnapshot snapshot) {
        sender.accept(server, snapshot);
    }

    private static void sendToAllPlayers(MinecraftServer server, RuntimeContentSnapshot snapshot) {
        if (server == null) return;
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        var plan = planStructurePackets(snapshot, server.registryAccess(), PktStructureChunkPayload.MAX_CHUNK_BYTES);
        players.forEach(plan::sendTo);
    }

    public static void setSenderForTesting(Consumer<MinecraftServer> sender) {
        RuntimeContentSync.sender = (server, ignored) -> sender.accept(server);
    }

    public static void setSenderForTesting(BiConsumer<MinecraftServer, RuntimeContentSnapshot> sender) {
        RuntimeContentSync.sender = Objects.requireNonNull(sender);
    }

    public static void resetSenderForTesting() {
        sender = RuntimeContentSync::sendToAllPlayers;
    }
}
