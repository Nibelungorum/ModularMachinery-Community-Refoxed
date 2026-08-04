package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class FluidHatchMenu extends AbstractMachineMenu {

    private final FluidHatchBlockEntity owner;

    public FluidHatchMenu(int containerId, Inventory playerInv, FluidHatchBlockEntity owner) {
        super(ModUIs.FLUID_HATCH.get(), containerId);
        this.owner = owner;
        addPlayerSlots(playerInv);
    }

    public FluidHatchMenu(int containerId, Inventory playerInv) {
        this(containerId, playerInv, null);
    }

    public static FluidHatchMenu clientOpen(int containerId, Inventory playerInv) {
        return new FluidHatchMenu(containerId, playerInv);
    }

    public FluidHatchBlockEntity owner() {
        return owner;
    }

    public FluidTank tank() {
        return owner == null ? null : owner.getFluidTank(null);
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