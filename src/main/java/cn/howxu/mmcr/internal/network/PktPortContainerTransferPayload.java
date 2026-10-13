package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.transfer.ContainerResourceTransfer;
import cn.howxu.mmcr.api.capability.transfer.ContainerResourceTransfer.SelectedTank;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.internal.menu.CombinedPortMenu;
import cn.howxu.mmcr.internal.menu.FluidHatchMenu;
import cn.howxu.mmcr.internal.menu.MenuSupport;
import cn.howxu.mmcr.internal.tile.CombinedPortBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.util.IOType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;

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
        if (menu.containerId != payload.containerId || !menu.stillValid(player)
                || menu.getCarried().isEmpty()) return 0;
        int moved;
            if (menu instanceof FluidHatchMenu fluid) {
                FluidHatchBlockEntity port = fluid.owner();
                if (!validTarget(player, menu, port) || payload.tankIndex != 0
                        || port.kind().fluidHatchSize().isEmpty()
                        || port.kind().extendedFluidHatchSize().isPresent()) return 0;
                moved = transferFluid(player, menu, port, port.fluidHandler(null), payload.tankIndex, payload.extract);
            } else if (menu instanceof CombinedPortMenu combined) {
                CombinedPortBlockEntity port = combined.owner();
                if (!validTarget(player, menu, port)
                        || port.kind().combinedPortSize().isEmpty()
                        || payload.tankIndex >= combined.fluidTankCount()
                        || payload.tankIndex >= port.fluidHandler(null).size()) return 0;
                moved = transferFluid(player, menu, port, port.fluidHandler(null), payload.tankIndex, payload.extract);
            } else {
                moved = MekanismBridge.get().transferChemicalContainer(player, menu,
                        payload.tankIndex, payload.extract);
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
                                     IOPortBlockEntity port, LongFluidStorage storage, int tankIndex, boolean extract) {
        var result = ContainerResourceTransfer.transferCarried(port.ioType() == IOType.OUTPUT || extract ? IOType.OUTPUT : IOType.INPUT, menu.getCarried(),
                new SelectedTank(storage, tankIndex), false);
        if (result.amount() > 0) {
            ItemStack changed = result.carried();
            int cursorCount = Math.min(changed.getCount(), changed.getMaxStackSize());
            menu.setCarried(changed.copyWithCount(cursorCount));
            if (changed.getCount() > cursorCount) {
                ItemHandlerHelper.giveItemToPlayer(player, changed.copyWithCount(changed.getCount() - cursorCount));
            }
        }
        return result.amount();
    }

}
