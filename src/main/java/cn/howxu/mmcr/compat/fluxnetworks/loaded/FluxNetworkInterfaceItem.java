package cn.howxu.mmcr.compat.fluxnetworks.loaded;

import cn.howxu.mmcr.internal.item.InterfaceTooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import sonar.fluxnetworks.common.item.FluxDeviceItem;

import java.util.List;

/** Native saved-device details together with the MMCR interface description.
 * @author howxu <dev@howxu.cn>
 */
public final class FluxNetworkInterfaceItem extends FluxDeviceItem {
    public FluxNetworkInterfaceItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.addAll(InterfaceTooltips.tooltipLines(getBlock()));
    }
}
