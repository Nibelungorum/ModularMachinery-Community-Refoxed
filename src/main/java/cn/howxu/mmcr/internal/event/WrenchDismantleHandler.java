package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.ItemSpecialOperationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

/**
 * Handles wrench dismantling for MMCR blocks.
 *
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber(modid = MMCR.MODID)
public final class WrenchDismantleHandler {
    private WrenchDismantleHandler() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) return;

        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer) || player.isSpectator()
                || !ItemSpecialOperationUtil.isSpecialOperated(player)
                || !player.getMainHandItem().is(Tags.Items.TOOLS_WRENCH)) return;

        Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (!ModBlocks.BLOCKS.values().stream().anyMatch(holder -> holder.isBound() && holder.get() == block)) return;

        MekanismBridge mekanism = MekanismBridge.get();
        if (mekanism.available() && mekanism.isNonEmptyRadioactiveChemicalPort(event.getLevel().getBlockEntity(event.getPos()))) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(dismantle(serverPlayer, event.getPos())
                ? InteractionResult.SUCCESS : InteractionResult.FAIL);
    }

    private static boolean dismantle(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        GameType gameMode = player.gameMode.getGameModeForPlayer();
        if (CommonHooks.fireBlockBreak(level, gameMode, player, pos, state).isCanceled()
                || block instanceof GameMasterBlock && !player.canUseGameMasterBlocks()
                || player.blockActionRestricted(level, pos, gameMode)) {
            return false;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(state, level, pos, blockEntity, player, player.getMainHandItem());
        block.playerWillDestroy(level, pos, state, player);
        drops.forEach(player.getInventory()::placeItemBackInInventory);
        level.removeBlock(pos, false);
        block.destroy(level, pos, state);
        player.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }
}
