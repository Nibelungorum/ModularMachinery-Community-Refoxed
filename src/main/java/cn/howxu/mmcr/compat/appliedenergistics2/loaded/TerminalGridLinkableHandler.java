package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import appeng.api.features.IGridLinkableHandler;
import cn.howxu.mmcr.internal.item.TerminalData;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;

/**
 * Links MMCR terminals through the native AE2 wireless access point menu.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class TerminalGridLinkableHandler implements IGridLinkableHandler {
    @Override
    public boolean canLink(ItemStack stack) {
        return stack.is(ModItems.TERMINAL.get());
    }

    @Override
    public void link(ItemStack stack, GlobalPos pos) {
        stack.set(ModDataComponents.TERMINAL_DATA.get(), TerminalData.from(stack).withAe2AccessPoint(pos));
    }

    @Override
    public void unlink(ItemStack stack) {
        stack.set(ModDataComponents.TERMINAL_DATA.get(), TerminalData.from(stack).withAe2AccessPoint(null));
    }
}
