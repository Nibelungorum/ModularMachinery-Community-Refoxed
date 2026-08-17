package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.MMCR;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Thread capacity item for factory schedulers.
 *
 * @author howxu <dev@howxu.cn>
 */
public class ThreadDisperserItem extends Item {

    public ThreadDisperserItem() {
        super(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, MMCR.id("thread_disperser"))));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable("tooltip.mmcr.thread_disperser.multithreading").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
