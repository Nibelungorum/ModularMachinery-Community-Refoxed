package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.item.TerminalAction;
import cn.howxu.mmcr.internal.item.TerminalService;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

/**
 * Client request for a server-authoritative terminal action.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktTerminalActionPayload(TerminalAction action, int value, Identifier firstId, Identifier secondId)
        implements CustomPacketPayload {
    private static final int MIN_VALUE = -(1 << (BlockPos.PACKED_Y_LENGTH - 1));
    private static final int MAX_VALUE = (1 << (BlockPos.PACKED_Y_LENGTH - 1)) - 1;
    public static final Type<PktTerminalActionPayload> TYPE = new Type<>(MMCR.id("terminal_action"));
    public static final StreamCodec<ByteBuf, PktTerminalActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(index -> readAction(index), TerminalAction::ordinal), PktTerminalActionPayload::action,
            ByteBufCodecs.VAR_INT, PktTerminalActionPayload::value,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), payload -> Optional.ofNullable(payload.firstId),
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), payload -> Optional.ofNullable(payload.secondId),
            (action, value, firstId, secondId) -> new PktTerminalActionPayload(action, value,
                    firstId.orElse(null), secondId.orElse(null)));

    public PktTerminalActionPayload {
        if (value < MIN_VALUE || value > MAX_VALUE && value != Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Terminal action value is out of bounds");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            TerminalService.execute(player, player.getMainHandItem(), action, value, firstId, secondId);
        });
    }

    private static TerminalAction readAction(int index) {
        TerminalAction[] actions = TerminalAction.values();
        if (index < 0 || index >= actions.length) throw new IllegalArgumentException("Invalid terminal action: " + index);
        return actions[index];
    }
}
