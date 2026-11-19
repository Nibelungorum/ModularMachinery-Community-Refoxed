package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.PortMenu;
import cn.howxu.mmcr.internal.menu.MenuSupport;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * GUI request for the currently open ordinary port tank.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktPortContainerTransferPayload(int containerId, int tankIndex, boolean extract) implements CustomPacketPayload {
    public static final Type<PktPortContainerTransferPayload> TYPE = new Type<>(MMCR.id("port_container_transfer"));
    public static final StreamCodec<ByteBuf, PktPortContainerTransferPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PktPortContainerTransferPayload::containerId,
            ByteBufCodecs.VAR_INT, PktPortContainerTransferPayload::tankIndex,
            ByteBufCodecs.BOOL, PktPortContainerTransferPayload::extract,
            PktPortContainerTransferPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) interactOnServer(player, this);
        });
    }

    public static int interactOnServer(ServerPlayer player, PktPortContainerTransferPayload payload) {
        if (player == null || payload == null || payload.tankIndex < 0) return 0;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != payload.containerId || menu.getCarried().isEmpty()
                || !(menu instanceof PortMenu opened)) return 0;
        IOPortBlockEntity owner = opened.owner();
        if (!validTarget(player, menu, owner) || !owner.getBlockPos().equals(opened.pos())) return 0;
        int moved;
        try (Transaction transaction = Transaction.openRoot()) {
            moved = opened.transferContainer(player, payload.tankIndex, payload.extract, transaction);
            if (moved > 0) transaction.commit();
        }
        if (moved > 0) menu.broadcastChanges();
        return moved;
    }

    public static boolean validTarget(ServerPlayer player, AbstractContainerMenu menu, IOPortBlockEntity port) {
        return port != null && !port.isRemoved() && player.containerMenu == menu
                && port.getLevel() == player.level()
                && player.level().getBlockEntity(port.getBlockPos()) == port
                && menu.stillValid(player)
                && MenuSupport.stillValidWithin(player, port.getBlockPos());
    }

}
