package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.TerminalClientHandler;
import cn.howxu.mmcr.internal.item.TerminalData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Canonical terminal state sent by the server after a terminal mutation.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktTerminalStatePayload(TerminalData data, boolean controllerAvailable, boolean storageAvailable,
                                       List<Integer> stages, String statusKey) implements CustomPacketPayload {
    public static final Type<PktTerminalStatePayload> TYPE = new Type<>(MMCR.id("terminal_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktTerminalStatePayload> STREAM_CODEC = StreamCodec.composite(
            TerminalData.STREAM_CODEC, PktTerminalStatePayload::data,
            ByteBufCodecs.BOOL, PktTerminalStatePayload::controllerAvailable,
            ByteBufCodecs.BOOL, PktTerminalStatePayload::storageAvailable,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), PktTerminalStatePayload::stages,
            ByteBufCodecs.STRING_UTF8, PktTerminalStatePayload::statusKey,
            PktTerminalStatePayload::new);

    public PktTerminalStatePayload(TerminalData data, boolean controllerAvailable, boolean storageAvailable,
            String statusKey) {
        this(data, controllerAvailable, storageAvailable, List.of(), statusKey);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> TerminalClientHandler.applyState(data, controllerAvailable, storageAvailable, stages, statusKey));
    }
}
