package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * @author howxu <dev@howxu.cn>
 */
public record PktControllerSpecsPayload(Map<Identifier, MachineControllerSpec> specs) implements CustomPacketPayload {
    private static final int MAX_SPECS = 4096;
    private static final StreamCodec<RegistryFriendlyByteBuf, MachineControllerSpec> SPEC_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, MachineControllerSpec::id,
            Identifier.STREAM_CODEC, MachineControllerSpec::frontTexture,
            Identifier.STREAM_CODEC, MachineControllerSpec::sideTexture,
            Identifier.STREAM_CODEC, MachineControllerSpec::topTexture,
            Identifier.STREAM_CODEC, MachineControllerSpec::bottomTexture,
            ByteBufCodecs.BOOL, MachineControllerSpec::allowVerticalFacing,
            ByteBufCodecs.BOOL, MachineControllerSpec::fullyRotationallySymmetric,
            ByteBufCodecs.BOOL, MachineControllerSpec::requireVerticalFacing,
            MachineControllerSpec::new);

    public static final Type<PktControllerSpecsPayload> TYPE = new Type<>(MMCR.id("controller_specs"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktControllerSpecsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, SPEC_CODEC, MAX_SPECS),
            PktControllerSpecsPayload::specs,
            PktControllerSpecsPayload::new);

    public PktControllerSpecsPayload {
        specs = Map.copyOf(specs);
        if (specs.size() > MAX_SPECS) {
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
}
