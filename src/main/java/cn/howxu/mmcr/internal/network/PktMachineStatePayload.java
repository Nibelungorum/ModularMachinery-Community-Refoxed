package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record PktMachineStatePayload(BlockPos pos, String recipeName, boolean formed, boolean active,
                                     List<String> foundLevelIds, boolean recipeLocked, String lockedRecipeId) implements CustomPacketPayload {

    public static boolean stateChanged(boolean formed, boolean active, boolean recipeLocked, String lockedRecipeId,
                                       boolean lastFormed, boolean lastActive, boolean lastRecipeLocked,
                                       String lastLockedRecipeId) {
        return formed != lastFormed || active != lastActive || recipeLocked != lastRecipeLocked
                || !lockedRecipeId.equals(lastLockedRecipeId);
    }

    public PktMachineStatePayload(BlockPos pos, String recipeName, boolean formed, boolean active,
                                  List<String> foundLevelIds) {
        this(pos, recipeName, formed, active, foundLevelIds, false, "");
    }

    public static final Type<PktMachineStatePayload> TYPE = new Type<>(MMCR.id("machine_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PktMachineStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PktMachineStatePayload::pos,
                    ByteBufCodecs.STRING_UTF8, PktMachineStatePayload::recipeName,
                    ByteBufCodecs.BOOL, PktMachineStatePayload::formed,
                     ByteBufCodecs.BOOL, PktMachineStatePayload::active,
                     ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), PktMachineStatePayload::foundLevelIds,
                     ByteBufCodecs.BOOL, PktMachineStatePayload::recipeLocked,
                     ByteBufCodecs.STRING_UTF8, PktMachineStatePayload::lockedRecipeId,
                     PktMachineStatePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            if (player == null) return;
            if (player.level().getBlockEntity(pos) instanceof MachineControllerBlockEntity controller) {
                 controller.applyClientState(recipeName, formed, active, foundLevelIds, recipeLocked, lockedRecipeId);
            }
        });
    }
}
