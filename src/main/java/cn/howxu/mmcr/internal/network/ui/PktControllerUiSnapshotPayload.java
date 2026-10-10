package cn.howxu.mmcr.internal.network.ui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.controller.ui.ControllerUiClientEvents;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;
import java.util.UUID;

/** Full baseline including owned output, storage, global text and all lane presentations.
 * @author howxu <dev@howxu.cn>
 */
public record PktControllerUiSnapshotPayload(int containerId, UUID sessionId, long revision,
                                             ControllerUiSnapshotData snapshotData) implements CustomPacketPayload {
    public static final Type<PktControllerUiSnapshotPayload> TYPE = new Type<>(MMCR.id("controller_ui_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktControllerUiSnapshotPayload> STREAM_CODEC =
            ControllerUiPayloadCodec.packetCodec(PktControllerUiSnapshotPayload::write,
                    PktControllerUiSnapshotPayload::read, ControllerUiPayloadCodec.SNAPSHOT_LIMIT);

    public PktControllerUiSnapshotPayload {
        ControllerUiPayloadCodec.identity(containerId, sessionId);
        ControllerUiPayloadCodec.sequence(revision);
        Objects.requireNonNull(snapshotData, "snapshotData");
        if (!sessionId.equals(snapshotData.sessionId()) || revision != snapshotData.revision()) {
            throw new IllegalArgumentException("Snapshot envelope identity differs from snapshot data");
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ControllerUiClientEvents.handle(this));
    }

    private static void write(RegistryFriendlyByteBuf b, PktControllerUiSnapshotPayload value) {
        b.writeVarInt(value.containerId);
        b.writeUUID(value.sessionId);
        b.writeLong(value.revision);
        ControllerUiPayloadCodec.writeSnapshot(b, value.snapshotData);
    }

    private static PktControllerUiSnapshotPayload read(RegistryFriendlyByteBuf b) {
        return new PktControllerUiSnapshotPayload(b.readVarInt(), b.readUUID(), b.readLong(),
                ControllerUiPayloadCodec.readSnapshot(b));
    }
}
