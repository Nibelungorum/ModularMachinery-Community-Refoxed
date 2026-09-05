package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.TerminalClientHandler;
import cn.howxu.mmcr.internal.item.TerminalData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Canonical terminal state sent by the server after a terminal mutation.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktTerminalStatePayload(TerminalData data, boolean controllerAvailable, boolean storageAvailable,
                                       List<Integer> stages, Component machineName, List<Integer> previewLayers,
                                       String statusKey) implements CustomPacketPayload {
    public static final int MAX_PREVIEW_LAYERS = 128;
    public static final Type<PktTerminalStatePayload> TYPE = new Type<>(MMCR.id("terminal_state"));
    private static final StreamCodec<ByteBuf, List<Integer>> STAGES_CODEC =
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, List<Integer>> PREVIEW_LAYERS_CODEC =
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT, MAX_PREVIEW_LAYERS);
    public static final StreamCodec<RegistryFriendlyByteBuf, PktTerminalStatePayload> STREAM_CODEC =
            StreamCodec.of(PktTerminalStatePayload::write, PktTerminalStatePayload::read);

    public PktTerminalStatePayload {
        data = Objects.requireNonNull(data, "data");
        stages = List.copyOf(stages == null ? List.of() : stages);
        machineName = Objects.requireNonNull(machineName, "machineName");
        previewLayers = List.copyOf(previewLayers == null ? List.of() : previewLayers);
        if (previewLayers.size() > MAX_PREVIEW_LAYERS) throw new IllegalArgumentException("Too many preview layers");
        statusKey = Objects.requireNonNull(statusKey, "statusKey");
    }

    public PktTerminalStatePayload(TerminalData data, boolean controllerAvailable, boolean storageAvailable,
            List<Integer> stages, String statusKey) {
        this(data, controllerAvailable, storageAvailable, stages, Component.empty(), List.of(), statusKey);
    }

    public PktTerminalStatePayload(TerminalData data, boolean controllerAvailable, boolean storageAvailable,
            String statusKey) {
        this(data, controllerAvailable, storageAvailable, List.of(), Component.empty(), List.of(), statusKey);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> TerminalClientHandler.applyState(data, controllerAvailable, storageAvailable, stages,
                machineName, previewLayers, statusKey));
    }

    private static void write(RegistryFriendlyByteBuf buffer, PktTerminalStatePayload payload) {
        TerminalData.STREAM_CODEC.encode(buffer, payload.data);
        ByteBufCodecs.BOOL.encode(buffer, payload.controllerAvailable);
        ByteBufCodecs.BOOL.encode(buffer, payload.storageAvailable);
        STAGES_CODEC.encode(buffer, payload.stages);
        ComponentSerialization.STREAM_CODEC.encode(buffer, payload.machineName);
        PREVIEW_LAYERS_CODEC.encode(buffer, payload.previewLayers);
        ByteBufCodecs.STRING_UTF8.encode(buffer, payload.statusKey);
    }

    private static PktTerminalStatePayload read(RegistryFriendlyByteBuf buffer) {
        return new PktTerminalStatePayload(TerminalData.STREAM_CODEC.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer), ByteBufCodecs.BOOL.decode(buffer), STAGES_CODEC.decode(buffer),
                ComponentSerialization.STREAM_CODEC.decode(buffer), PREVIEW_LAYERS_CODEC.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer));
    }
}
