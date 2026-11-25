package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * One slice of one deflated machine structure.
 *
 * <p>A single multiblock pattern can exceed vanilla's 1 MiB limit for
 * {@code minecraft:custom_payload} (the largest lotus ritual encodes to roughly 4.8 MB), which fails the
 * whole packet with {@code EncoderException: Failed to encode packet}. Structures are therefore deflated
 * and split into slices that each fit the limit, and the client reassembles them before applying the
 * snapshot.
 */
public record PktStructureChunkPayload(long contentVersion, ResourceLocation machineId,
                                       int chunkIndex, int chunkCount, byte[] data)
        implements CustomPacketPayload {

    public static final Type<PktStructureChunkPayload> TYPE = new Type<>(MMCR.id("structure_chunk"));

    /** Compressed slice size; well under vanilla's 1 MiB payload cap once headers and framing are added. */
    public static final int MAX_CHUNK_BYTES = 400_000;
    public static final int MAX_CHUNKS = 4096;

    public static final StreamCodec<RegistryFriendlyByteBuf, PktStructureChunkPayload> STREAM_CODEC =
            StreamCodec.of(PktStructureChunkPayload::encode, PktStructureChunkPayload::decode);

    public PktStructureChunkPayload {
        if (chunkCount < 1 || chunkCount > MAX_CHUNKS) {
            throw new IllegalArgumentException("Invalid structure chunk count: " + chunkCount);
        }
        if (chunkIndex < 0 || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException("Invalid structure chunk index: " + chunkIndex + "/" + chunkCount);
        }
        if (data == null || data.length > MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Invalid structure chunk size: " + (data == null ? -1 : data.length));
        }
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, PktStructureChunkPayload payload) {
        buf.writeVarLong(payload.contentVersion);
        ResourceLocation.STREAM_CODEC.encode(buf, payload.machineId);
        buf.writeVarInt(payload.chunkIndex);
        buf.writeVarInt(payload.chunkCount);
        buf.writeVarInt(payload.data.length);
        buf.writeBytes(payload.data);
    }

    private static PktStructureChunkPayload decode(RegistryFriendlyByteBuf buf) {
        long contentVersion = buf.readVarLong();
        ResourceLocation machineId = ResourceLocation.STREAM_CODEC.decode(buf);
        int chunkIndex = buf.readVarInt();
        int chunkCount = buf.readVarInt();
        int length = buf.readVarInt();
        if (length < 0 || length > MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Invalid structure chunk size: " + length);
        }
        byte[] data = new byte[length];
        buf.readBytes(data);
        PktStructureChunkPayload payload =
                new PktStructureChunkPayload(contentVersion, machineId, chunkIndex, chunkCount, data);
        // Slices are buffered while the packet buffer still carries the registry access needed to decode
        // the reassembled structure later.
        ClientStructureChunkBuffer.acceptSlice(payload, buf.registryAccess());
        return payload;
    }

    /**
     * Slices are buffered by the decoder, which is the only place that still holds the registry access
     * needed to decode the reassembled structure. Handling therefore has no work left to do.
     */
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
        });
    }

    /** Log-safe short description; keeps chunk payload dumps out of the log. */
    public String describe() {
        return "chunk " + (chunkIndex + 1) + "/" + chunkCount + " of " + machineId + " ("
                + data.length + " B)";
    }
}
