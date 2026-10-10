package cn.howxu.mmcr.internal.network.ui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.controller.ui.ControllerUiClientEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** One viewer's revisioned, owned custom state projection.
 * @author howxu <dev@howxu.cn>
 */
public record PktControllerUiCustomStatePayload(int containerId, UUID sessionId, Identifier stateId,
                                                int version, long providerRevision, byte[] body)
        implements CustomPacketPayload {
    public static final Type<PktControllerUiCustomStatePayload> TYPE = new Type<>(MMCR.id("controller_ui_custom_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktControllerUiCustomStatePayload> STREAM_CODEC =
            ControllerUiPayloadCodec.packetCodec(PktControllerUiCustomStatePayload::write,
                    PktControllerUiCustomStatePayload::read, ControllerUiPayloadCodec.STATE_LIMIT + 2048);

    public PktControllerUiCustomStatePayload {
        ControllerUiPayloadCodec.identity(containerId, sessionId);
        ControllerUiPayloadCodec.id(stateId);
        ControllerUiPayloadCodec.version(version);
        if (providerRevision < 0) throw new IllegalArgumentException("Negative provider revision");
        body = ControllerUiPayloadCodec.ownBody(body, ControllerUiPayloadCodec.STATE_LIMIT);
    }

    @Override public byte[] body() { return body.clone(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ControllerUiClientEvents.handle(this));
    }

    private static void write(RegistryFriendlyByteBuf b, PktControllerUiCustomStatePayload value) {
        b.writeVarInt(value.containerId);
        b.writeUUID(value.sessionId);
        ControllerUiPayloadCodec.writeId(b, value.stateId);
        b.writeVarInt(value.version);
        b.writeLong(value.providerRevision);
        ControllerUiPayloadCodec.writeBody(b, value.body, ControllerUiPayloadCodec.STATE_LIMIT);
    }

    private static PktControllerUiCustomStatePayload read(RegistryFriendlyByteBuf b) {
        return new PktControllerUiCustomStatePayload(b.readVarInt(), b.readUUID(), ControllerUiPayloadCodec.readId(b),
                b.readVarInt(), b.readLong(), ControllerUiPayloadCodec.readBody(b, ControllerUiPayloadCodec.STATE_LIMIT));
    }
}
