package cn.howxu.mmcr.internal.port;

import cn.howxu.mmcr.api.capability.transfer.ContainerResourceTransfer;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.RangedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.transaction.RootCommitJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Manual fluid transfers for ordinary tanks, distinct from sided automation.
 * @author howxu <dev@howxu.cn>
 */
public final class FluidContainerInteraction {
    private FluidContainerInteraction() {}

    public static int interact(ItemAccess access, Player player, IOPortBlockEntity port,
                               ResourceHandler<FluidResource> tanks, TransactionContext tx) {
        var result = ContainerResourceTransfer.interact(port.ioType(), container(access), tanks, tx);
        if (result == null) return 0;
        new RootCommitJournal(() -> FluidUtil.triggerSoundAndGameEvent(result.resource(), player.level(),
                port.getBlockPos().getCenter(), player, result.extract())).updateSnapshots(tx);
        return result.amount();
    }

    public static int transfer(ServerPlayer player, AbstractContainerMenu menu, IOType direction,
                               ResourceHandler<FluidResource> tanks, int slot, boolean extract, TransactionContext tx) {
        return ContainerResourceTransfer.transfer(extract ? IOType.OUTPUT : direction,
                container(ItemAccess.forPlayerCursor(player, menu)), RangedResourceHandler.ofSingleIndex(tanks, slot), tx);
    }

    private static ResourceHandler<FluidResource> container(ItemAccess access) {
        return MekanismBridge.get().manualFluidContainerHandler(access.getCapability(Capabilities.Fluid.ITEM));
    }
}
