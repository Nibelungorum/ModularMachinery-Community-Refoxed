package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class FluidHatchMenu extends AbstractMachineMenu {

    private final FluidHatchBlockEntity owner;
    private final Level level;
    private final BlockPos pos;
    private final DataSlot amount;
    private final DataSlot capacity;

    public FluidHatchMenu(int containerId, Inventory playerInv, FluidHatchBlockEntity owner) {
        super(ModUIs.FLUID_HATCH.get(), containerId);
        this.owner = owner;
        this.level = playerInv.player.level();
        this.pos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
        this.amount = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return owner.getFluidTank(null).getFluidAmount(); }
            @Override public void set(int value) {}
        });
        this.capacity = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return owner.getFluidTank(null).getCapacity(); }
            @Override public void set(int value) {}
        });
        addPlayerSlots(playerInv);
    }

    public FluidHatchMenu(int containerId, Inventory playerInv, BlockPos pos) {
        super(ModUIs.FLUID_HATCH.get(), containerId);
        this.owner = null;
        this.level = playerInv.player.level();
        this.pos = pos;
        this.amount = addDataSlot(DataSlot.standalone());
        this.capacity = addDataSlot(DataSlot.standalone());
        addPlayerSlots(playerInv);
    }

    public static FluidHatchMenu clientOpen(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        return new FluidHatchMenu(containerId, playerInv, buf.readBlockPos());
    }

    public FluidHatchBlockEntity owner() {
        return owner;
    }

    public FluidTank tank() {
        FluidHatchBlockEntity hatch = resolvedOwner();
        return hatch == null ? null : hatch.getFluidTank(null);
    }

    public int fluidAmount() {
        FluidTank tank = tank();
        return tank == null ? amount.get() : tank.getFluidAmount();
    }

    public int fluidCapacity() {
        FluidHatchBlockEntity hatch = resolvedOwner();
        return hatch == null ? capacity.get() : fluidCapacity(hatch);
    }

    static int fluidCapacity(FluidHatchBlockEntity hatch) {
        return hatch.getFluidTank(null).getCapacity();
    }

    private FluidHatchBlockEntity resolvedOwner() {
        if (owner != null) return owner;
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof FluidHatchBlockEntity hatch ? hatch : null;
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
