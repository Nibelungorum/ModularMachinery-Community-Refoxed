package cn.howxu.mmcr.internal.network.ui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration;
import cn.howxu.mmcr.api.presentation.ComponentSnapshots;
import cn.howxu.mmcr.client.controller.ui.ControllerUiClientEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Correlated response; only SUCCESS carries the registered response body's value.
 * @author howxu <dev@howxu.cn>
 */
public record PktControllerUiResponsePayload(int containerId, UUID sessionId, long requestId,
                                             UiProtocolRegistration.Status status, Optional<Component> reason, byte[] body)
        implements CustomPacketPayload {
    public static final Type<PktControllerUiResponsePayload> TYPE = new Type<>(MMCR.id("controller_ui_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktControllerUiResponsePayload> STREAM_CODEC =
            ControllerUiPayloadCodec.packetCodec(PktControllerUiResponsePayload::write,
                    PktControllerUiResponsePayload::read, ControllerUiPayloadCodec.RESPONSE_LIMIT + 64 * 1024 + 256);

    public PktControllerUiResponsePayload {
        ControllerUiPayloadCodec.identity(containerId, sessionId);
        ControllerUiPayloadCodec.sequence(requestId);
        Objects.requireNonNull(status, "status");
        reason = Objects.requireNonNull(reason, "reason").map(ComponentSnapshots::copy);
        body = ControllerUiPayloadCodec.ownBody(body, ControllerUiPayloadCodec.RESPONSE_LIMIT);
        if (status != UiProtocolRegistration.Status.SUCCESS && body.length != 0) {
            throw new IllegalArgumentException("Failed controller UI response must not contain a body");
        }
    }

    @Override public byte[] body() { return body.clone(); }
    @Override public Optional<Component> reason() { return reason.map(ComponentSnapshots::copy); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ControllerUiClientEvents.handle(this));
    }

    private static void write(RegistryFriendlyByteBuf b, PktControllerUiResponsePayload value) {
        b.writeVarInt(value.containerId);
        b.writeUUID(value.sessionId);
        b.writeLong(value.requestId);
        b.writeVarInt(value.status.ordinal());
        b.writeBoolean(value.reason.isPresent());
        value.reason.ifPresent(reason -> ControllerUiPayloadCodec.writeComponent(b, reason));
        ControllerUiPayloadCodec.writeBody(b, value.body, ControllerUiPayloadCodec.RESPONSE_LIMIT);
    }

    private static PktControllerUiResponsePayload read(RegistryFriendlyByteBuf b) {
        return new PktControllerUiResponsePayload(b.readVarInt(), b.readUUID(), b.readLong(),
                ControllerUiPayloadCodec.readEnum(b, UiProtocolRegistration.Status.values()),
                b.readBoolean() ? Optional.of(ControllerUiPayloadCodec.readComponent(b)) : Optional.empty(),
                ControllerUiPayloadCodec.readBody(b, ControllerUiPayloadCodec.RESPONSE_LIMIT));
    }
}
