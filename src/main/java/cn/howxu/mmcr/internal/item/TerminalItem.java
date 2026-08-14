package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.config.Config;
import cn.howxu.mmcr.internal.assembly.MultiblockAssemblyService;
import cn.howxu.mmcr.internal.assembly.PlayerInventoryStructureItemSink;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModDataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Consumer;

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
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        TerminalMode next = mode(stack).next();
        if (!level.isClientSide()) {
            stack.set(ModDataComponents.TERMINAL_MODE.get(), next);
            player.sendSystemMessage(Component.translatable("message.mmcr.terminal.switched", next.modeComponent()));
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
        if (!(blockEntity instanceof MachineControllerBlockEntity controller)) return InteractionResult.SUCCESS;

        TerminalMode mode = mode(context.getItemInHand());
        MultiblockAssemblyService.Result result = mode == TerminalMode.BUILD
                ? MultiblockAssemblyService.build(serverPlayer, controller, serverPlayer.isCreative())
                : MultiblockAssemblyService.demolish(serverPlayer, controller, Config.TERMINAL_MAX_DEMOLISH_BLOCKS.get(),
                        serverPlayer.isCreative() ? stack -> {} : new PlayerInventoryStructureItemSink(serverPlayer));
        serverPlayer.sendSystemMessage(Component.translatable(result.message().key(), result.message().args()));
        return result.interactionResult();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(mode(stack).tooltipComponent());
    }

    private static TerminalMode mode(ItemStack stack) {
        TerminalMode mode = stack.get(ModDataComponents.TERMINAL_MODE.get());
        return mode == null ? TerminalMode.defaultMode() : mode;
    }
}
