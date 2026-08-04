package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.EnergyStorage;

public class EnergyHatchMenu extends AbstractMachineMenu {

    private final EnergyHatchBlockEntity owner;

    public EnergyHatchMenu(int containerId, Inventory playerInv, EnergyHatchBlockEntity owner) {
        super(ModUIs.ENERGY_HATCH.get(), containerId);
        this.owner = owner;
        addPlayerSlots(playerInv);
    }

    public EnergyHatchMenu(int containerId, Inventory playerInv) {
        this(containerId, playerInv, null);
    }

    public static EnergyHatchMenu clientOpen(int containerId, Inventory playerInv) {
        return new EnergyHatchMenu(containerId, playerInv);
    }

    public EnergyHatchBlockEntity owner() {
        return owner;
    }

    public EnergyStorage storage() {
        return owner == null ? null : owner.getMutableEnergyStorage(null);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return MenuSupport.noopQuickMove();
    }

    @Override
    public boolean stillValid(Player player) {
        return owner == null || MenuSupport.stillValidWithin(player, owner.getBlockPos());
    }
}