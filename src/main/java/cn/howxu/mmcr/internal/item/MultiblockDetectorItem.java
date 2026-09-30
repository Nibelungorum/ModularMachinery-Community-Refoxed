package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.util.ItemSpecialOperationUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Debug item for selecting a controller and export region in-world.
 *
 * @author howxu <dev@howxu.cn>
 */
public class MultiblockDetectorItem extends Item {

    public MultiblockDetectorItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        if (!level.isClientSide()) {
            ItemStack stack = context.getItemInHand();
            BlockPos pos = context.getClickedPos();
            MultiblockDetectorSelection selection = selection(stack);
            if (ItemSpecialOperationUtil.isSpecialOperated(player)) {
                stack.set(ModDataComponents.MULTIBLOCK_DETECTOR_SELECTION.get(), selection.withSecond(pos));
                player.sendSystemMessage(Component.translatable("message.mmcr.multiblock_detector.second_set", pos.toShortString()));
            } else {
                stack.set(ModDataComponents.MULTIBLOCK_DETECTOR_SELECTION.get(), selection.withFirst(pos));
                player.sendSystemMessage(Component.translatable("message.mmcr.multiblock_detector.first_set", pos.toShortString()));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!ItemSpecialOperationUtil.isSpecialOperated(player)) return InteractionResultHolder.pass(stack);

        if (!level.isClientSide()) {
            stack.remove(ModDataComponents.MULTIBLOCK_DETECTOR_SELECTION.get());
            player.sendSystemMessage(Component.translatable("message.mmcr.multiblock_detector.cleared"));
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> builder, TooltipFlag tooltipFlag) {
        MultiblockDetectorSelection selection = selection(stack);
        builder.add(controllerLine(context, selection.controllerPos(), selection.controllerFace()));
        builder.add(positionLine(context, "first", selection.firstPos()));
        builder.add(positionLine(context, "second", selection.secondPos()));
    }

    private Component controllerLine(Item.TooltipContext context, BlockPos pos, Direction face) {
        MutableComponent rt = Component.empty();
        if (pos == null) {
            rt.append(Component.translatable("tooltip.mmcr.multiblock_detector.controller").withStyle(ChatFormatting.RED));
            rt.append(Component.literal(": "));
            rt.append(Component.translatable("tooltip.mmcr.multiblock_detector.not_set"));
            return rt;
        }
        // pos not null
        rt.append(Component.translatable("tooltip.mmcr.multiblock_detector.controller").withStyle(ChatFormatting.GREEN));
        rt.append(Component.literal(": "));
        rt.append(Component.translatable("tooltip.mmcr.multiblock_detector.position", blockName(context, pos), pos.toShortString(), face == null ? Component.empty() : Component.translatable("tooltip.mmcr.multiblock_detector.face", face.getSerializedName())));
        return rt;
    }

    private Component positionLine(Item.TooltipContext context, String key, BlockPos pos) {
        MutableComponent rt = Component.empty();
        if (pos == null) {
            rt.append(Component.translatable("tooltip.mmcr.multiblock_detector." + key).withStyle(ChatFormatting.RED));
            rt.append(Component.literal(": "));
            rt.append(Component.translatable("tooltip.mmcr.multiblock_detector.not_set"));
            return rt;
        }

        rt.append(Component.translatable("tooltip.mmcr.multiblock_detector." + key).withStyle(ChatFormatting.GREEN));
        rt.append(Component.literal(": "));
        rt.append(Component.translatable("tooltip.mmcr.multiblock_detector.position", blockName(context, pos), pos.toShortString(), Component.empty()));
        return rt;
    }

    private static Component blockName(Item.TooltipContext context, BlockPos pos) {
        if (context.level() == null || !context.level().hasChunkAt(pos)) {
            return Component.translatable("tooltip.mmcr.multiblock_detector.unknown_block");
        }
        BlockState state = context.level().getBlockState(pos);
        return state.getBlock().getName();
    }

    public static MultiblockDetectorSelection selection(ItemStack stack) {
        MultiblockDetectorSelection selection = stack.get(ModDataComponents.MULTIBLOCK_DETECTOR_SELECTION.get());
        return selection == null ? MultiblockDetectorSelection.EMPTY : selection;
    }
}
