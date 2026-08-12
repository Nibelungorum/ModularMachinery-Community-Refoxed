package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.MultiblockMismatchHighlightClientHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-bound highlight for a multiblock structure mismatch.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktMultiblockMismatchHighlightPayload(ResourceKey<Level> dimension, BlockPos pos) implements CustomPacketPayload {
    public static final Type<PktMultiblockMismatchHighlightPayload> TYPE = new Type<>(MMCR.id("multiblock_mismatch_highlight"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktMultiblockMismatchHighlightPayload> STREAM_CODEC =
            StreamCodec.of(PktMultiblockMismatchHighlightPayload::write, PktMultiblockMismatchHighlightPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, PktMultiblockMismatchHighlightPayload payload) {
        Identifier.STREAM_CODEC.encode(buf, payload.dimension.identifier());
        buf.writeBlockPos(payload.pos);
    }

    private static PktMultiblockMismatchHighlightPayload read(RegistryFriendlyByteBuf buf) {
        Identifier dimension = Identifier.STREAM_CODEC.decode(buf);
        return new PktMultiblockMismatchHighlightPayload(ResourceKey.create(Registries.DIMENSION, dimension), buf.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> MultiblockMismatchHighlightClientHandler.show(dimension, pos));
    }
}
