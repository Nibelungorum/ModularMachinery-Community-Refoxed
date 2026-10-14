package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.menu.MenuSupport;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client request for a server-authoritative controller structure check.
 * @author howxu <dev@howxu.cn>
 */
public record PktControllerStructureCheckPayload(BlockPos controllerPos) implements CustomPacketPayload {
    public static final Type<PktControllerStructureCheckPayload> TYPE =
            new Type<>(MMCR.id("controller_structure_check"));
    public static final StreamCodec<ByteBuf, PktControllerStructureCheckPayload> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(PktControllerStructureCheckPayload::new,
                    PktControllerStructureCheckPayload::controllerPos);

    public PktControllerStructureCheckPayload {
        if (controllerPos == null) throw new IllegalArgumentException("controllerPos is required");
        controllerPos = controllerPos.immutable();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) checkOnServer(player, this);
        });
    }

    static boolean checkOnServer(ServerPlayer player, PktControllerStructureCheckPayload payload) {
        if (player == null || payload == null || player.level().isClientSide()) return false;
        if (!(player.level().getBlockEntity(payload.controllerPos()) instanceof MachineControllerBlockEntity controller)
                || !MenuSupport.stillValidWithin(player, payload.controllerPos())
                || !hasAccessToMenu(player, controller, payload.controllerPos())) return false;
        controller.requestImmediateStructureCheck(player);
        return true;
    }

    private static boolean hasAccessToMenu(ServerPlayer player, MachineControllerBlockEntity controller, BlockPos pos) {
        if (player.containerMenu instanceof FactoryControllerMenu menu) {
            return menu.controllerPos().equals(pos) && menu.resolvedOwner() == controller && menu.stillValid(player);
        }
        if (player.containerMenu instanceof MachineControllerMenu menu) {
            return menu.controllerPos().equals(pos) && menu.resolvedOwner() == controller && menu.stillValid(player);
        }
        return false;
    }
}
