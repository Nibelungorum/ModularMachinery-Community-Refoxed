package cn.howxu.mmcr.internal.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Thread capacity item for factory schedulers.
 *
 * @author howxu <dev@howxu.cn>
 */
public class ThreadDisperserItem extends Item {

    public ThreadDisperserItem() {
        super(new Item.Properties());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.mmcr.thread_disperser.multithreading").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
