package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class MachineControllerMenu extends MMCRMenuBase {

    private final MachineControllerBlockEntity owner;

    public MachineControllerMenu(int containerId, Inventory playerInv, MachineControllerBlockEntity owner) {
        super(ModUIs.MACHINE_CONTROLLER.get(), containerId);
        this.owner = owner;
        addControllerPlayerSlots(playerInv);
    }

    private void addControllerPlayerSlots(Inventory playerInv) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 131 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 189));
        }
    }

    public MachineControllerMenu(int containerId, Inventory playerInv) {
        this(containerId, playerInv, null);
    }

    public static MachineControllerMenu clientOpen(int containerId, Inventory playerInv) {
        return new MachineControllerMenu(containerId, playerInv);
    }

    public MachineControllerBlockEntity owner() {
        return owner;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return MMCRMenu.noopQuickMove();
    }

    @Override
    public boolean stillValid(Player player) {
        return owner == null || MMCRMenu.stillValidWithin(player, owner.getBlockPos());
    }
}