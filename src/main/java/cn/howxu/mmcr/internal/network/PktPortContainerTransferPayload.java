package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.transfer.ContainerResourceTransfer;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.internal.menu.CombinedPortMenu;
import cn.howxu.mmcr.internal.menu.FluidHatchMenu;
import cn.howxu.mmcr.internal.menu.MenuSupport;
import cn.howxu.mmcr.internal.tile.CombinedPortBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.transfer.RangedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * GUI request for the currently open ordinary port tank.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktPortContainerTransferPayload(int containerId, int tankIndex) implements CustomPacketPayload {
    public static final Type<PktPortContainerTransferPayload> TYPE = new Type<>(MMCR.id("port_container_transfer"));
    public static final StreamCodec<ByteBuf, PktPortContainerTransferPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PktPortContainerTransferPayload::containerId,
            ByteBufCodecs.VAR_INT, PktPortContainerTransferPayload::tankIndex,
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
        if (menu.containerId != payload.containerId || !menu.stillValid(player)
                || menu.getCarried().isEmpty()) return 0;
        int moved;
        try (Transaction transaction = Transaction.openRoot()) {
            if (menu instanceof FluidHatchMenu fluid) {
                FluidHatchBlockEntity port = fluid.owner();
                if (!validTarget(player, menu, port) || payload.tankIndex != 0
                        || port.kind().fluidHatchSize().isEmpty()
                        || port.kind().extendedFluidHatchSize().isPresent()) return 0;
                moved = transferFluid(player, menu, port, port.getResourceHandler(null),
                        payload.tankIndex, transaction);
            } else if (menu instanceof CombinedPortMenu combined) {
                CombinedPortBlockEntity port = combined.owner();
                if (!validTarget(player, menu, port)
                        || port.kind().combinedPortSize().isEmpty()
                        || payload.tankIndex >= combined.fluidTankCount()
                        || payload.tankIndex >= port.fluidStorage().size()) return 0;
                moved = transferFluid(player, menu, port, port.getResourceHandler(null),
                        payload.tankIndex, transaction);
            } else {
                moved = MekanismBridge.get().transferChemicalContainer(player, menu,
                        payload.tankIndex, transaction);
            }
            if (moved > 0) transaction.commit();
        }
        if (moved > 0) menu.broadcastChanges();
        return moved;
    }

    public static boolean validTarget(ServerPlayer player, AbstractContainerMenu menu, IOPortBlockEntity port) {
        return port != null && player.containerMenu == menu
                && port.getLevel() == player.level()
                && player.level().getBlockEntity(port.getBlockPos()) == port
                && menu.stillValid(player)
                && MenuSupport.stillValidWithin(player, port.getBlockPos());
    }

    private static int transferFluid(ServerPlayer player, AbstractContainerMenu menu,
                                     IOPortBlockEntity port, ResourceHandler<FluidResource> storage,
                                     int tankIndex, TransactionContext transaction) {
        ItemAccess access = ItemAccess.forPlayerCursor(player, menu);
        ResourceHandler<FluidResource> container = MekanismBridge.get()
                .manualFluidContainerHandler(access.getCapability(Capabilities.Fluid.ITEM));
        return ContainerResourceTransfer.transfer(port.ioType(), container,
                RangedResourceHandler.ofSingleIndex(storage, tankIndex), transaction);
    }
}
