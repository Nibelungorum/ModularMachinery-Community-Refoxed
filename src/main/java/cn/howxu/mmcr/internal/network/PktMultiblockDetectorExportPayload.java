package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.command.ExportCommand;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client request to export the multiblock detector screen state.
 * @author howxu <dev@howxu.cn>
 */
public record PktMultiblockDetectorExportPayload(boolean kubeJs) implements CustomPacketPayload {
    public static final Type<PktMultiblockDetectorExportPayload> TYPE =
            new Type<>(MMCR.id("multiblock_detector_export"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktMultiblockDetectorExportPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, PktMultiblockDetectorExportPayload::kubeJs,
                    PktMultiblockDetectorExportPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !PktMultiblockDetectorUpdatePayload.canUpdate(player)) return;
            ExportCommand.exportFromScreen(player, kubeJs);
        });
    }
}
