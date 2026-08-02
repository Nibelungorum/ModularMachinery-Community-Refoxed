package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PktMachineStatePayload(BlockPos pos, String recipeName, boolean formed) implements CustomPacketPayload {

    public static final Type<PktMachineStatePayload> TYPE = new Type<>(MMCR.id("machine_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PktMachineStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PktMachineStatePayload::pos,
                    ByteBufCodecs.STRING_UTF8, PktMachineStatePayload::recipeName,
                    ByteBufCodecs.BOOL, PktMachineStatePayload::formed,
                    PktMachineStatePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
