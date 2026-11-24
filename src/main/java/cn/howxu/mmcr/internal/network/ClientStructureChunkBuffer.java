package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.internal.sync.MachineStructureSyncCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side reassembly of chunked machine structures.
 *
 * <p>Structures are deflated and sliced on the server because a single multiblock pattern can exceed the
 * 1 MiB custom payload cap. Slices arrive before the metadata payload that names them, so by the time the
 * metadata is handled every slice is already buffered and reassembly is immediate.
 *
 * <p>State is reset whenever the content version changes, so a new sync never mixes with stale slices.
 *
 * @author howxu {@literal <dev@howxu.cn>}
 */
final class ClientStructureChunkBuffer {

    private static StructureChunkAssembler.Transfer transfer;
    private static RegistryAccess registryAccess;

    private ClientStructureChunkBuffer() {
    }

    /** Called from the chunk packet decoder, which is the only place with registry access. */
    static synchronized void acceptSlice(PktStructureChunkPayload payload, RegistryAccess access) {
        if (access != null) registryAccess = access;
        StructureChunkAssembler.Transfer current = transfer;
        if (current == null || current.contentVersion() != payload.contentVersion()) {
            if (current != null) current.clear();
            current = new StructureChunkAssembler.Transfer(payload.contentVersion());
            transfer = current;
        }
        current.accept(payload.machineId(), payload.chunkIndex(), payload.chunkCount(), payload.data());
    }

    /**
     * Reassembles every structure named in {@code chunkedStructureIds}.
     *
     * @return the decoded structures, or {@code null} while slices are still missing
     */
    static synchronized Map<ResourceLocation, MachineStructureDefinition> assemble(long contentVersion,
                                                                                  List<ResourceLocation> chunkedStructureIds,
                                                                                  int maxBlocks) {
        StructureChunkAssembler.Transfer current = transfer;
        if (current == null || current.contentVersion() != contentVersion) return null;
        if (registryAccess == null) return null;

        Map<ResourceLocation, MachineStructureDefinition> decoded = new LinkedHashMap<>();
        for (ResourceLocation machineId : chunkedStructureIds) {
            byte[] raw = current.assemble(machineId);
            if (raw == null) return null;
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(raw), registryAccess);
            try {
                MachineStructureDefinition definition = MachineStructureSyncCodec.decode(buf, maxBlocks);
                if (!machineId.equals(definition.machineId())) {
                    throw new IllegalArgumentException("Chunked structure id mismatch: " + machineId
                            + " decoded as " + definition.machineId());
                }
                decoded.put(machineId, definition);
            } finally {
                buf.release();
            }
        }
        return Map.copyOf(decoded);
    }

    /** Drops every buffered slice; called after a snapshot is applied or when a sync restarts. */
    static synchronized void reset() {
        if (transfer != null) transfer.clear();
        transfer = null;
    }
}
