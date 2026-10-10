package cn.howxu.mmcr.internal.network.ui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.controller.ui.ControllerUiClientEvents;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.LaneProgress;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Progress delta tied to the last delivered full baseline, never a standalone baseline.
 * @author howxu <dev@howxu.cn>
 */
public record PktControllerUiProgressPayload(int containerId, UUID sessionId, long revision,
                                             long fullBaselineRevision, List<LaneProgress> progress)
        implements CustomPacketPayload {
    public static final Type<PktControllerUiProgressPayload> TYPE = new Type<>(MMCR.id("controller_ui_progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktControllerUiProgressPayload> STREAM_CODEC =
            ControllerUiPayloadCodec.packetCodec(PktControllerUiProgressPayload::write,
                    PktControllerUiProgressPayload::read, ControllerUiPayloadCodec.SNAPSHOT_LIMIT);

    public PktControllerUiProgressPayload {
        ControllerUiPayloadCodec.identity(containerId, sessionId);
        ControllerUiPayloadCodec.sequence(fullBaselineRevision);
        if (revision <= fullBaselineRevision) throw new IllegalArgumentException("Progress must advance full baseline");
        progress = List.copyOf(progress);
        ControllerUiPayloadCodec.checkCount(progress.size(), ControllerUiPayloadCodec.MAX_LANES);
        var seen = new HashSet<String>();
        for (LaneProgress value : progress) {
            ControllerUiPayloadCodec.laneId(value.laneId());
            if (!seen.add(value.laneId())) throw new IllegalArgumentException("Duplicate progress lane");
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ControllerUiClientEvents.handle(this));
    }

    private static void write(RegistryFriendlyByteBuf b, PktControllerUiProgressPayload value) {
        b.writeVarInt(value.containerId);
        b.writeUUID(value.sessionId);
        b.writeLong(value.revision);
        b.writeLong(value.fullBaselineRevision);
        b.writeVarInt(value.progress.size());
        value.progress.forEach(progress -> ControllerUiPayloadCodec.writeProgress(b, progress));
    }

    private static PktControllerUiProgressPayload read(RegistryFriendlyByteBuf b) {
        int containerId = b.readVarInt();
        UUID sessionId = b.readUUID();
        long revision = b.readLong(), baseline = b.readLong();
        int size = ControllerUiPayloadCodec.count(b, ControllerUiPayloadCodec.MAX_LANES);
        List<LaneProgress> progress = new ArrayList<>(size);
        for (int i = 0; i < size; i++) progress.add(ControllerUiPayloadCodec.readProgress(b));
        return new PktControllerUiProgressPayload(containerId, sessionId, revision, baseline, progress);
    }
}
