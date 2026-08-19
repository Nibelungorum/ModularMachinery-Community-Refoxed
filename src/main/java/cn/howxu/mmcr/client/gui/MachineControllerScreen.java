package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen entry point for a machine controller menu.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineControllerScreen extends MachineMenuScreen<MachineControllerMenu> {

    public MachineControllerScreen(MachineControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
