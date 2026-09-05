package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;


/**
 * Multiblock build and demolish terminal.
 *
 * @author howxu <dev@howxu.cn>
 */
public class TerminalItem extends Item {

    public TerminalItem() {
        super(new Item.Properties()
                .stacksTo(1)
                .setId(ResourceKey.create(Registries.ITEM, MMCR.id("terminal"))));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer) {
            TerminalService.Result result = TerminalService.clear(serverPlayer, stack);
            if (result.accepted()) serverPlayer.sendSystemMessage(Component.translatable(result.messageKey()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        BlockEntity blockEntity = context.getLevel().getBlockEntity(context.getClickedPos());
        GlobalPos target = GlobalPos.of(context.getLevel().dimension(), context.getClickedPos());
        if (blockEntity instanceof MachineControllerBlockEntity) {
            TerminalService.Result result = TerminalService.bindController(serverPlayer, context.getItemInHand(), target);
            if (result.accepted()) serverPlayer.sendSystemMessage(Component.translatable(result.messageKey()));
            return result.accepted()
                    ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        if (TerminalData.from(context.getItemInHand()).inventoryMode() == TerminalInventoryMode.CONTAINER) {
            return TerminalService.bindContainer(serverPlayer, context.getItemInHand(), target).accepted()
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }
}
