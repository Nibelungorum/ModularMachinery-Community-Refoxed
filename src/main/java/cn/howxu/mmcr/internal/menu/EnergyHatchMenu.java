package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.EnergyStorage;

public class EnergyHatchMenu extends AbstractMachineMenu {

    private final EnergyHatchBlockEntity owner;
    private final Level level;
    private final BlockPos pos;
    private final DataSlot stored;
    private final DataSlot capacity;

    public EnergyHatchMenu(int containerId, Inventory playerInv, EnergyHatchBlockEntity owner) {
        super(ModUIs.ENERGY_HATCH.get(), containerId);
        this.owner = owner;
        this.level = playerInv.player.level();
        this.pos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
        this.stored = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return owner.getMutableEnergyStorage(null).getEnergyStored(); }
            @Override public void set(int value) {}
        });
        this.capacity = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return owner.getMutableEnergyStorage(null).getMaxEnergyStored(); }
            @Override public void set(int value) {}
        });
        addPlayerSlots(playerInv);
    }

    public EnergyHatchMenu(int containerId, Inventory playerInv, BlockPos pos) {
        super(ModUIs.ENERGY_HATCH.get(), containerId);
        this.owner = null;
        this.level = playerInv.player.level();
        this.pos = pos;
        this.stored = addDataSlot(DataSlot.standalone());
        this.capacity = addDataSlot(DataSlot.standalone());
        addPlayerSlots(playerInv);
    }

    public static EnergyHatchMenu clientOpen(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        return new EnergyHatchMenu(containerId, playerInv, buf.readBlockPos());
    }

    public EnergyHatchBlockEntity owner() {
        return owner;
    }

    public BlockPos pos() { return pos; }

    public EnergyStorage storage() {
        EnergyHatchBlockEntity hatch = resolvedOwner();
        return hatch == null ? null : hatch.getMutableEnergyStorage(null);
    }

    public int storedEnergy() {
        EnergyStorage storage = storage();
        return storage == null ? stored.get() : storage.getEnergyStored();
    }

    public int energyCapacity() {
        EnergyHatchBlockEntity hatch = resolvedOwner();
        return hatch == null ? capacity.get() : energyCapacity(hatch);
    }

    static int energyCapacity(EnergyHatchBlockEntity hatch) {
        return hatch.getMutableEnergyStorage(null).getMaxEnergyStored();
    }

    private EnergyHatchBlockEntity resolvedOwner() {
        if (owner != null) return owner;
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof EnergyHatchBlockEntity hatch ? hatch : null;
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
