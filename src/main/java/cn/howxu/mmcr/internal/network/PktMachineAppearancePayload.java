package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.client.model.MachineAppearanceCache;
import cn.howxu.mmcr.config.CommonConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author howxu <dev@howxu.cn>
 */
public record PktMachineAppearancePayload(Map<ResourceLocation, MachineAppearanceSpec> specs) implements CustomPacketPayload {
    private static final int MAX_SPECS = 4096;
    private static final StreamCodec<RegistryFriendlyByteBuf, MachineAppearanceSpec> SPEC_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, MachineAppearanceSpec::machineBasicBlock,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), spec -> Optional.ofNullable(spec.controllerBaseTexture()),
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), spec -> Optional.ofNullable(spec.formedPortBaseTexture()),
            ResourceLocation.STREAM_CODEC, MachineAppearanceSpec::controllerIdleOverlayTexture,
            ResourceLocation.STREAM_CODEC, MachineAppearanceSpec::controllerActiveOverlayTexture,
            (blockId, controllerTexture, portTexture, idleOverlay, activeOverlay) -> new MachineAppearanceSpec(blockId,
                    controllerTexture.orElse(null), portTexture.orElse(null), idleOverlay, activeOverlay));

    public static final Type<PktMachineAppearancePayload> TYPE = new Type<>(MMCR.id("machine_appearance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktMachineAppearancePayload> STREAM_CODEC =
            StreamCodec.of(PktMachineAppearancePayload::write, PktMachineAppearancePayload::read);

    public PktMachineAppearancePayload {
        if (specs == null) {
            throw new IllegalArgumentException("specs null");
        }
        specs = Map.copyOf(specs);
        if (specs.size() > maxSpecs()) {
            throw new IllegalArgumentException("Too many machine appearance specs");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> MachineAppearanceCache.replaceSnapshot(specs));
    }

    private static void write(RegistryFriendlyByteBuf buffer, PktMachineAppearancePayload payload) {
        buffer.writeVarInt(payload.specs.size());
        for (var entry : payload.specs.entrySet()) {
            ResourceLocation.STREAM_CODEC.encode(buffer, entry.getKey());
            SPEC_CODEC.encode(buffer, entry.getValue());
        }
    }

    private static PktMachineAppearancePayload read(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maxSpecs()) throw new IllegalArgumentException("Too many machine appearance specs");
        Map<ResourceLocation, MachineAppearanceSpec> specs = new HashMap<>(count);
        for (int index = 0; index < count; index++) {
            ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buffer);
            if (specs.put(id, SPEC_CODEC.decode(buffer)) != null) {
                throw new IllegalArgumentException("Duplicate machine appearance spec");
            }
        }
        return new PktMachineAppearancePayload(specs);
    }

    private static int maxSpecs() {
        return CommonConfig.valueOrDefault(CommonConfig.RUNTIME_CONTENT_MAX_SPECS, MAX_SPECS);
    }
}
