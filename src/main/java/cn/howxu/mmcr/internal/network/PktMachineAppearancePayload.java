package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.client.model.MachineAppearanceCache;
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
public record PktMachineAppearancePayload(Map<Identifier, MachineAppearanceSpec> specs) implements CustomPacketPayload {
    private static final int MAX_SPECS = 4096;
    private static final StreamCodec<RegistryFriendlyByteBuf, MachineAppearanceSpec> SPEC_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, MachineAppearanceSpec::machineBasicBlock,
            Identifier.STREAM_CODEC, MachineAppearanceSpec::controllerBaseTexture,
            Identifier.STREAM_CODEC, MachineAppearanceSpec::formedPortBaseTexture,
            MachineAppearanceSpec::new);

    public static final Type<PktMachineAppearancePayload> TYPE = new Type<>(MMCR.id("machine_appearance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktMachineAppearancePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, SPEC_CODEC, MAX_SPECS),
            PktMachineAppearancePayload::specs,
            PktMachineAppearancePayload::new);

    public PktMachineAppearancePayload {
        specs = Map.copyOf(specs);
        if (specs.size() > MAX_SPECS) {
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
}
