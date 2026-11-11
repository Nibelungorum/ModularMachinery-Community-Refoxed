package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.config.CommonConfig;
import cn.howxu.mmcr.client.TerminalClientHandler;
import cn.howxu.mmcr.client.TerminalHighlightClientHandler;
import cn.howxu.mmcr.internal.item.TerminalData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Canonical terminal state sent by the server after a terminal mutation.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktTerminalStatePayload(TerminalData data, boolean controllerAvailable, boolean storageAvailable,
                                        boolean ae2Available, List<Integer> stages, Component machineName,
                                        List<Integer> previewLayers, String statusKey,
                                        @Nullable Identifier machineId, Direction facing) implements CustomPacketPayload {
    public static final int MAX_PREVIEW_LAYERS = 128;
    public static final Type<PktTerminalStatePayload> TYPE = new Type<>(MMCR.id("terminal_state"));
    private static final StreamCodec<ByteBuf, List<Integer>> STAGES_CODEC =
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list());
    public static final StreamCodec<RegistryFriendlyByteBuf, PktTerminalStatePayload> STREAM_CODEC =
            StreamCodec.of(PktTerminalStatePayload::write, PktTerminalStatePayload::read);

    public PktTerminalStatePayload {
        data = Objects.requireNonNull(data, "data");
        stages = List.copyOf(stages == null ? List.of() : stages);
        machineName = Objects.requireNonNull(machineName, "machineName");
        previewLayers = List.copyOf(previewLayers == null ? List.of() : previewLayers);
        if (previewLayers.size() > maxPreviewLayers()) throw new IllegalArgumentException("Too many preview layers");
        statusKey = Objects.requireNonNull(statusKey, "statusKey");
        facing = Objects.requireNonNull(facing, "facing");
    }

    public PktTerminalStatePayload(TerminalData data, boolean controllerAvailable, boolean storageAvailable,
            List<Integer> stages, String statusKey) {
        this(data, controllerAvailable, storageAvailable, true, stages, Component.empty(), List.of(), statusKey);
    }

    public PktTerminalStatePayload(TerminalData data, boolean controllerAvailable, boolean storageAvailable,
            String statusKey) {
        this(data, controllerAvailable, storageAvailable, true, List.of(), Component.empty(), List.of(), statusKey);
    }

    public PktTerminalStatePayload(TerminalData data, boolean controllerAvailable, boolean storageAvailable,
            List<Integer> stages, Component machineName, List<Integer> previewLayers, String statusKey) {
        this(data, controllerAvailable, storageAvailable, true, stages, machineName, previewLayers, statusKey);
    }

    public PktTerminalStatePayload(TerminalData data, boolean controllerAvailable, boolean storageAvailable,
            boolean ae2Available, List<Integer> stages, Component machineName, List<Integer> previewLayers, String statusKey) {
        this(data, controllerAvailable, storageAvailable, ae2Available, stages, machineName, previewLayers, statusKey,
                null, Direction.SOUTH);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            TerminalClientHandler.applyState(data, controllerAvailable, storageAvailable,
                    ae2Available, stages, machineName, previewLayers, statusKey);
            TerminalHighlightClientHandler.applyState(data, controllerAvailable ? machineId : null, facing);
        });
    }

    private static void write(RegistryFriendlyByteBuf buffer, PktTerminalStatePayload payload) {
        TerminalData.STREAM_CODEC.encode(buffer, payload.data);
        ByteBufCodecs.BOOL.encode(buffer, payload.controllerAvailable);
        ByteBufCodecs.BOOL.encode(buffer, payload.storageAvailable);
        ByteBufCodecs.BOOL.encode(buffer, payload.ae2Available);
        STAGES_CODEC.encode(buffer, payload.stages);
        ComponentSerialization.STREAM_CODEC.encode(buffer, payload.machineName);
        buffer.writeVarInt(payload.previewLayers.size());
        for (int layer : payload.previewLayers) buffer.writeVarInt(layer);
        ByteBufCodecs.STRING_UTF8.encode(buffer, payload.statusKey);
        ByteBufCodecs.optional(Identifier.STREAM_CODEC).encode(buffer, Optional.ofNullable(payload.machineId));
        Direction.STREAM_CODEC.encode(buffer, payload.facing);
    }

    private static PktTerminalStatePayload read(RegistryFriendlyByteBuf buffer) {
        return new PktTerminalStatePayload(TerminalData.STREAM_CODEC.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer), ByteBufCodecs.BOOL.decode(buffer), ByteBufCodecs.BOOL.decode(buffer),
                STAGES_CODEC.decode(buffer), ComponentSerialization.STREAM_CODEC.decode(buffer), readPreviewLayers(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.optional(Identifier.STREAM_CODEC).decode(buffer).orElse(null),
                Direction.STREAM_CODEC.decode(buffer));
    }

    private static List<Integer> readPreviewLayers(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maxPreviewLayers()) throw new IllegalArgumentException("Too many preview layers");
        List<Integer> layers = new ArrayList<>(count);
        for (int index = 0; index < count; index++) layers.add(buffer.readVarInt());
        return List.copyOf(layers);
    }

    public static int maxPreviewLayers() {
        return CommonConfig.valueOrDefault(CommonConfig.TERMINAL_STATE_MAX_PREVIEW_LAYERS, MAX_PREVIEW_LAYERS);
    }
}
