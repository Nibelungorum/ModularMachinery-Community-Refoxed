package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.internal.sync.MachineStructureSyncCodec;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import io.netty.buffer.Unpooled;
import io.netty.util.AttributeKey;
import io.netty.util.AttributeMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Both packet handlers run on the game thread. Pending slices belong only to their connection. */
final class ClientStructureChunkBuffer {
    private static final AttributeKey<ClientStructureChunkBuffer> KEY = AttributeKey.valueOf("mmcr:structure_chunks");

    private final long version;
    private final Map<ResourceLocation, StructureChunkAssembler> structures = new HashMap<>();

    private ClientStructureChunkBuffer(long version) {
        this.version = version;
    }

    static void accept(AttributeMap connection, PktStructureChunkPayload chunk) {
        var attribute = connection.attr(KEY);
        var pending = attribute.get();
        if (pending != null && pending.version > chunk.contentVersion()) return;
        if (pending == null || pending.version != chunk.contentVersion()) {
            pending = new ClientStructureChunkBuffer(chunk.contentVersion());
            attribute.set(pending);
        }
        var structure = pending.structures.get(chunk.machineId());
        if (structure == null) {
            if (pending.structures.size() >= PktRuntimeContentPayload.maxStructures()) {
                throw new IllegalArgumentException("Too many chunked structures");
            }
            structure = new StructureChunkAssembler(chunk);
            pending.structures.put(chunk.machineId(), structure);
        }
        structure.accept(chunk);
    }

    /** Metadata ends the transfer. Never expose a partial snapshot or consume a newer transfer. */
    static @Nullable RuntimeContentSnapshot finish(AttributeMap connection, PktRuntimeContentPayload metadata,
                                                  RegistryAccess registries) {
        RuntimeContentSnapshot snapshot = metadata.snapshot();
        var attribute = connection.attr(KEY);
        var pending = attribute.get();
        if (pending != null) {
            if (pending.version > snapshot.contentVersion()) return null;
            attribute.set(null);
        }
        if (metadata.chunkedStructureIds().isEmpty()) return snapshot;
        if (pending == null || pending.version != snapshot.contentVersion()) return null;

        Map<ResourceLocation, MachineStructureDefinition> decoded = new LinkedHashMap<>(snapshot.structures());
        for (ResourceLocation id : metadata.chunkedStructureIds()) {
            var parts = pending.structures.remove(id);
            if (parts == null) return null;
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
            try {
                if (!parts.inflateInto(buf)) return null;
                var definition = MachineStructureSyncCodec.decode(buf, metadata.maxStructureBlocks());
                if (!id.equals(definition.machineId()) || decoded.putIfAbsent(id, definition) != null) {
                    throw new IllegalArgumentException("Invalid chunked structure id: " + id);
                }
            } finally {
                buf.release();
            }
        }
        return new RuntimeContentSnapshot(decoded, snapshot.recipes(), snapshot.controllerSpecs(),
                snapshot.appearances(), snapshot.machineRecipePools(), snapshot.contentVersion());
    }
}
