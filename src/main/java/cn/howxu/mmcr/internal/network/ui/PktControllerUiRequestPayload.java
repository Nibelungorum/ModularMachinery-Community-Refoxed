package cn.howxu.mmcr.internal.network.ui;

import cn.howxu.mmcr.MMCR;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Client request: opaque owned body, decoded only after actual-menu authorization.
 * @author howxu <dev@howxu.cn>
 */
public record PktControllerUiRequestPayload(int containerId, UUID sessionId, long requestId,
                                            Identifier messageId, int version, Optional<String> laneId, byte[] body)
        implements CustomPacketPayload {
    public static final Type<PktControllerUiRequestPayload> TYPE = new Type<>(MMCR.id("controller_ui_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktControllerUiRequestPayload> STREAM_CODEC =
            ControllerUiPayloadCodec.packetCodec(PktControllerUiRequestPayload::write,
                    PktControllerUiRequestPayload::read, 32 * 1024 - 256);

    public PktControllerUiRequestPayload {
        ControllerUiPayloadCodec.identity(containerId, sessionId);
        ControllerUiPayloadCodec.sequence(requestId);
        ControllerUiPayloadCodec.id(messageId);
        ControllerUiPayloadCodec.version(version);
        Objects.requireNonNull(laneId, "laneId").ifPresent(ControllerUiPayloadCodec::laneId);
        body = ControllerUiPayloadCodec.ownBody(body, ControllerUiPayloadCodec.REQUEST_LIMIT);
    }

    @Override public byte[] body() { return body.clone(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) ControllerUiRequestDispatcher.dispatch(player, this);
        });
    }

    private static void write(RegistryFriendlyByteBuf b, PktControllerUiRequestPayload value) {
        b.writeVarInt(value.containerId);
        b.writeUUID(value.sessionId);
        b.writeLong(value.requestId);
        ControllerUiPayloadCodec.writeId(b, value.messageId);
        b.writeVarInt(value.version);
        b.writeBoolean(value.laneId.isPresent());
        value.laneId.ifPresent(lane -> b.writeUtf(lane, ControllerUiPayloadCodec.ID_LIMIT));
        ControllerUiPayloadCodec.writeBody(b, value.body, ControllerUiPayloadCodec.REQUEST_LIMIT);
    }

    private static PktControllerUiRequestPayload read(RegistryFriendlyByteBuf b) {
        return new PktControllerUiRequestPayload(b.readVarInt(), b.readUUID(), b.readLong(),
                ControllerUiPayloadCodec.readId(b), b.readVarInt(),
                b.readBoolean() ? Optional.of(b.readUtf(ControllerUiPayloadCodec.ID_LIMIT)) : Optional.empty(),
                ControllerUiPayloadCodec.readBody(b, ControllerUiPayloadCodec.REQUEST_LIMIT));
    }
}
