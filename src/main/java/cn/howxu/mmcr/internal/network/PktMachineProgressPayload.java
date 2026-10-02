package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-bound progress delta; never establishes a machine state baseline.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktMachineProgressPayload(BlockPos pos, String recipeName, int tick, int totalTick)
        implements CustomPacketPayload {
    public static final Type<PktMachineProgressPayload> TYPE = new Type<>(MMCR.id("machine_progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktMachineProgressPayload> STREAM_CODEC =
            StreamCodec.of(PktMachineProgressPayload::write, PktMachineProgressPayload::read);

    public PktMachineProgressPayload {
        pos = pos == null ? BlockPos.ZERO : pos.immutable();
        if (recipeName == null || recipeName.length() > PktMachineStatePayload.maxStringLength()
                || tick < 0 || totalTick < 0 || tick > totalTick) {
            throw new IllegalArgumentException("Invalid machine progress");
        }
    }

    private static void write(RegistryFriendlyByteBuf buf, PktMachineProgressPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeUtf(payload.recipeName, PktMachineStatePayload.maxStringLength());
        buf.writeVarInt(payload.tick);
        buf.writeVarInt(payload.totalTick);
    }

    private static PktMachineProgressPayload read(RegistryFriendlyByteBuf buf) {
        return new PktMachineProgressPayload(buf.readBlockPos(),
                buf.readUtf(PktMachineStatePayload.maxStringLength()), buf.readVarInt(), buf.readVarInt());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            if (player == null) return;
            if (player.level().getBlockEntity(pos) instanceof MachineControllerBlockEntity controller) {
                controller.applyClientProgress(recipeName, tick, totalTick);
            }
            if (player.containerMenu instanceof MachineControllerMenu menu && menu.controllerPos().equals(pos)) {
                menu.applyClientProgress(recipeName, tick, totalTick);
            }
        });
    }
}
