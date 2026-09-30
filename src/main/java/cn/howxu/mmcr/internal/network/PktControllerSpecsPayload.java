package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
import cn.howxu.mmcr.config.CommonConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;

/**
 * @author howxu <dev@howxu.cn>
 */
public record PktControllerSpecsPayload(Map<ResourceLocation, MachineControllerSpec> specs) implements CustomPacketPayload {
    private static final int MAX_SPECS = 4096;
    private static final StreamCodec<RegistryFriendlyByteBuf, MachineControllerSpec> SPEC_CODEC = StreamCodec.of(
            PktControllerSpecsPayload::writeSpec, PktControllerSpecsPayload::readSpec);

    private static void writeSpec(RegistryFriendlyByteBuf buffer, MachineControllerSpec spec) {
        ResourceLocation.STREAM_CODEC.encode(buffer, spec.id());
        ResourceLocation.STREAM_CODEC.encode(buffer, spec.frontTexture());
        ResourceLocation.STREAM_CODEC.encode(buffer, spec.sideTexture());
        ResourceLocation.STREAM_CODEC.encode(buffer, spec.topTexture());
        ResourceLocation.STREAM_CODEC.encode(buffer, spec.bottomTexture());
        ByteBufCodecs.BOOL.encode(buffer, spec.allowVerticalFacing());
        ByteBufCodecs.BOOL.encode(buffer, spec.fullyRotationallySymmetric());
        ByteBufCodecs.BOOL.encode(buffer, spec.requireVerticalFacing());
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8)
                .encode(buffer, new ArrayList<>(spec.tooltip()));
    }

    private static MachineControllerSpec readSpec(RegistryFriendlyByteBuf buffer) {
        return new MachineControllerSpec(
                ResourceLocation.STREAM_CODEC.decode(buffer),
                ResourceLocation.STREAM_CODEC.decode(buffer),
                ResourceLocation.STREAM_CODEC.decode(buffer),
                ResourceLocation.STREAM_CODEC.decode(buffer),
                ResourceLocation.STREAM_CODEC.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer),
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8).decode(buffer));
    }

    public static final Type<PktControllerSpecsPayload> TYPE = new Type<>(MMCR.id("controller_specs"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktControllerSpecsPayload> STREAM_CODEC =
            StreamCodec.of(PktControllerSpecsPayload::write, PktControllerSpecsPayload::read);

    public PktControllerSpecsPayload {
        specs = Map.copyOf(specs);
        if (specs.size() > maxSpecs()) {
            throw new IllegalArgumentException("Too many controller specs");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ControllerSpecCache.replaceSnapshot(specs));
    }

    private static void write(RegistryFriendlyByteBuf buffer, PktControllerSpecsPayload payload) {
        buffer.writeVarInt(payload.specs.size());
        for (var entry : payload.specs.entrySet()) {
            ResourceLocation.STREAM_CODEC.encode(buffer, entry.getKey());
            SPEC_CODEC.encode(buffer, entry.getValue());
        }
    }

    private static PktControllerSpecsPayload read(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maxSpecs()) throw new IllegalArgumentException("Too many controller specs");
        Map<ResourceLocation, MachineControllerSpec> specs = new HashMap<>(count);
        for (int index = 0; index < count; index++) {
            ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buffer);
            if (specs.put(id, SPEC_CODEC.decode(buffer)) != null) {
                throw new IllegalArgumentException("Duplicate controller spec");
            }
        }
        return new PktControllerSpecsPayload(specs);
    }

    private static int maxSpecs() {
        return CommonConfig.valueOrDefault(CommonConfig.RUNTIME_CONTENT_MAX_SPECS, MAX_SPECS);
    }
}
