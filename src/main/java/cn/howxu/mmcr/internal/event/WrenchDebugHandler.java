package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 监听主手持扳手右键 IO 端口,在聊天栏打印内部储量。
 *
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber(modid = MMCR.MODID)
public final class WrenchDebugHandler {

    private WrenchDebugHandler() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        ItemStack held = event.getEntity().getItemInHand(event.getHand());
        if (!held.is(ModItems.WRENCH.get())) return;

        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (!(be instanceof IOPortBlockEntity port)) return;

        ServerPlayer player = (ServerPlayer) event.getEntity();
        BlockPos pos = event.getPos();

        if (player.isShiftKeyDown()
                && player.gameMode.getGameModeForPlayer() == GameType.CREATIVE
                && port instanceof EnergyHatchBlockEntity energyHatch) {
            energyHatch.getMutableEnergyStorage(null).receiveEnergy(1000, false);
            event.setUseItem(TriState.FALSE);
            event.setUseBlock(TriState.FALSE);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (port instanceof ItemBusBlockEntity bus) {
            printItemBus(player, pos, bus);
        } else if (port instanceof FluidHatchBlockEntity hatch) {
            printFluidHatch(player, pos, hatch);
        } else if (port instanceof EnergyHatchBlockEntity hatch) {
            printEnergyHatch(player, pos, hatch);
        }

        event.setUseItem(TriState.FALSE);
        event.setUseBlock(TriState.FALSE);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static void printItemBus(ServerPlayer player, BlockPos pos, ItemBusBlockEntity bus) {
        ItemStackHandler handler = bus.getItemStackHandler(null);
        player.sendSystemMessage(prefix(bus, pos));
        int total = 0;
        int occupied = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) {
                player.sendSystemMessage(Component.translatable("debug.mmcr.wrench.item_slot.empty", i));
            } else {
                total += stack.getCount();
                occupied++;
                player.sendSystemMessage(Component.translatable("debug.mmcr.wrench.item_slot.filled", i,
                        stack.getHoverName(), stack.getCount(), stack.getMaxStackSize()));
            }
        }
        player.sendSystemMessage(Component.translatable("debug.mmcr.wrench.item_summary", total, occupied, handler.getSlots()));
    }

    private static void printFluidHatch(ServerPlayer player, BlockPos pos, FluidHatchBlockEntity hatch) {
        FluidTank tank = hatch.getFluidTank(null);
        player.sendSystemMessage(prefix(hatch, pos));
        if (tank.getFluid().isEmpty()) {
            player.sendSystemMessage(Component.translatable("debug.mmcr.wrench.fluid.empty", tank.getCapacity()));
        } else {
            player.sendSystemMessage(Component.translatable("debug.mmcr.wrench.fluid.filled",
                    tank.getFluid().getHoverName(), tank.getFluid().getAmount(), tank.getCapacity()));
        }
    }

    private static void printEnergyHatch(ServerPlayer player, BlockPos pos, EnergyHatchBlockEntity hatch) {
        EnergyStorage storage = hatch.getMutableEnergyStorage(null);
        player.sendSystemMessage(prefix(hatch, pos));
        player.sendSystemMessage(Component.translatable("debug.mmcr.wrench.energy",
                storage.getEnergyStored(), storage.getMaxEnergyStored()));
    }

    private static Component prefix(IOPortBlockEntity port, BlockPos pos) {
        Component name = Component.translatable("container.mmcr." + port.kind().id());
        return Component.translatable("debug.mmcr.wrench.header", name, pos.getX(), pos.getY(), pos.getZ());
    }
}
