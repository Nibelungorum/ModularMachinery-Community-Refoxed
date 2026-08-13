package cn.howxu.mmcr.internal.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/**
 * Block item tooltip support for MMCR controllers and IO interfaces.
 *
 * @author howxu <dev@howxu.cn>
 */
public class InterfaceBlockItem extends BlockItem {

    public InterfaceBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, display, builder, tooltipFlag);
        for (Component line : InterfaceTooltips.tooltipLines(getBlock())) builder.accept(line);
    }
}
