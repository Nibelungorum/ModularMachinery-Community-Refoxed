package cn.howxu.mmcr.internal.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

/**
 * 所有具体菜单类的基类,提供玩家物品栏布局的复用方法。
 * 具体子类决定使用哪个 {@link MenuType};槽位数据由服务端数据包同步给客户端实例。
 */
public abstract class AbstractMachineMenu extends AbstractContainerMenu {

    protected AbstractMachineMenu(MenuType<?> type, int containerId) {
        super(type, containerId);
    }

    protected void addPlayerSlots(Inventory playerInv) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }
}