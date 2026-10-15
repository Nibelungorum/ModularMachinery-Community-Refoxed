package cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu;

import appeng.helpers.InterfaceLogicHost;
import appeng.helpers.InventoryAction;
import appeng.menu.SlotSemantic;
import appeng.menu.implementations.InterfaceMenu;
import cn.howxu.mmcr.internal.menu.InputIsolationMenuState;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

/**
 * Uses the native interface menu with host-local storage and configuration policies.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class AE2InterfaceMenu extends InterfaceMenu {
    private final InputIsolationMenuState inputIsolation;
    public AE2InterfaceMenu(MenuType<? extends AE2InterfaceMenu> type, int id,
                            Inventory inventory, InterfaceLogicHost host) {
        super(type, id, inventory, host);
        inputIsolation = InputIsolationMenuState.forPort(host instanceof IOPortBlockEntity port ? port : null);
        addDataSlot(inputIsolation.dataSlot());
    }

    public InputIsolationMenuState inputIsolation() { return inputIsolation; }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == InputIsolationMenuState.DISABLE || buttonId == InputIsolationMenuState.ENABLE) {
            return inputIsolation.handleClick(this, player, buttonId);
        }
        return super.clickMenuButton(player, buttonId);
    }

    @Override
    protected Slot addSlot(Slot slot, SlotSemantic semantic) {
        return super.addSlot(InterfaceMenuSlots.adapt(getTarget(), slot), semantic);
    }

    @Override
    public void doAction(ServerPlayer player, InventoryAction action, int slot, long id) {
        if (slot < 0 || slot >= slots.size()) return;
        if (InterfaceMenuSlots.allowsAction(getTarget(), action, getSlot(slot))) {
            super.doAction(player, action, slot, id);
        }
    }

    @Override
    public void openSetAmountMenu(int configSlot) {
        if (InterfaceMenuSlots.canSetAmount(getHost(), configSlot)) {
            super.openSetAmountMenu(configSlot);
        }
    }
}
