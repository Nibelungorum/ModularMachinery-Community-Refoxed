package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.item.TerminalData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Canonical terminal state sent by the server after a terminal mutation.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktTerminalStatePayload(TerminalData data, boolean controllerAvailable, boolean storageAvailable,
                                      String statusKey) implements CustomPacketPayload {
    public static final Type<PktTerminalStatePayload> TYPE = new Type<>(MMCR.id("terminal_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktTerminalStatePayload> STREAM_CODEC = StreamCodec.composite(
            TerminalData.STREAM_CODEC, PktTerminalStatePayload::data,
            ByteBufCodecs.BOOL, PktTerminalStatePayload::controllerAvailable,
            ByteBufCodecs.BOOL, PktTerminalStatePayload::storageAvailable,
            ByteBufCodecs.STRING_UTF8, PktTerminalStatePayload::statusKey,
            PktTerminalStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
