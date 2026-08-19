package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.internal.menu.FactorySchedulerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen entry point for a factory scheduler menu.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FactorySchedulerScreen extends MachineMenuScreen<FactorySchedulerMenu> {

    public FactorySchedulerScreen(FactorySchedulerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
