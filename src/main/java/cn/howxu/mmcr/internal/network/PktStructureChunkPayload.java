package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** One slice of a structure; the runtime content packet commits the complete snapshot. */
public record PktStructureChunkPayload(long contentVersion, ResourceLocation machineId,
                                       int chunkIndex, int chunkCount, byte[] data) implements CustomPacketPayload {
    public static final Type<PktStructureChunkPayload> TYPE = new Type<>(MMCR.id("structure_chunk"));
    public static final int MAX_CHUNK_BYTES = 400_000;
    public static final int MAX_CHUNKS = 4096;

    public static final StreamCodec<RegistryFriendlyByteBuf, PktStructureChunkPayload> STREAM_CODEC =
            StreamCodec.of(PktStructureChunkPayload::encode, PktStructureChunkPayload::decode);

    public PktStructureChunkPayload {
        if (contentVersion < 0 || chunkCount < 1 || chunkCount > MAX_CHUNKS || chunkIndex < 0 || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException("Invalid structure chunk " + chunkIndex + "/" + chunkCount);
        }
        if (data == null || data.length > MAX_CHUNK_BYTES) throw new IllegalArgumentException("Invalid structure chunk size");
        data = data.clone();
    }

    @Override public byte[] data() { return data.clone(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void encode(RegistryFriendlyByteBuf buf, PktStructureChunkPayload packet) {
        buf.writeVarLong(packet.contentVersion);
        ResourceLocation.STREAM_CODEC.encode(buf, packet.machineId);
        buf.writeVarInt(packet.chunkIndex);
        buf.writeVarInt(packet.chunkCount);
        buf.writeByteArray(packet.data);
    }

    private static PktStructureChunkPayload decode(RegistryFriendlyByteBuf buf) {
        return new PktStructureChunkPayload(buf.readVarLong(), ResourceLocation.STREAM_CODEC.decode(buf),
                buf.readVarInt(), buf.readVarInt(), buf.readByteArray(MAX_CHUNK_BYTES));
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientStructureChunkBuffer.accept(context.connection().channel(), this));
    }
}
