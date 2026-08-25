package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

public class EnergyHatchMenu extends AbstractMachineMenu {

    private final EnergyHatchBlockEntity owner;
    private final Level level;
    private final BlockPos pos;
    private final LongDataSlot stored;
    private final LongDataSlot capacity;

    public EnergyHatchMenu(int containerId, Inventory playerInv, EnergyHatchBlockEntity owner) {
        super(ModUIs.ENERGY_HATCH.get(), containerId);
        this.owner = owner;
        this.level = playerInv.player.level();
        this.pos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
        this.stored = addLongDataSlot(owner == null ? LongDataSlot.standalone()
                : new LongDataSlot(() -> owner.getEnergyHandler(null).getAmountAsLong()));
        this.capacity = addLongDataSlot(owner == null ? LongDataSlot.standalone()
                : new LongDataSlot(() -> owner.getEnergyHandler(null).getCapacityAsLong()));
        addPlayerSlots(playerInv);
    }

    public EnergyHatchMenu(int containerId, Inventory playerInv, BlockPos pos) {
        super(ModUIs.ENERGY_HATCH.get(), containerId);
        this.owner = null;
        this.level = playerInv.player.level();
        this.pos = pos;
        this.stored = addLongDataSlot(LongDataSlot.standalone());
        this.capacity = addLongDataSlot(LongDataSlot.standalone());
        addPlayerSlots(playerInv);
    }

    public static EnergyHatchMenu clientOpen(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        return new EnergyHatchMenu(containerId, playerInv, buf.readBlockPos());
    }

    public EnergyHatchBlockEntity owner() {
        return owner;
    }

    public BlockPos pos() { return pos; }

    public EnergyHandler storage() {
        EnergyHatchBlockEntity hatch = resolvedOwner();
        return hatch == null ? null : hatch.getEnergyHandler(null);
    }

    public long storedEnergy() {
        EnergyHandler storage = storage();
        return storage == null ? stored.value() : storage.getAmountAsLong();
    }

    public long energyCapacity() {
        EnergyHatchBlockEntity hatch = resolvedOwner();
        return hatch == null ? capacity.value() : energyCapacity(hatch);
    }

    static long energyCapacity(EnergyHatchBlockEntity hatch) {
        return hatch.getEnergyHandler(null).getCapacityAsLong();
    }

    private EnergyHatchBlockEntity resolvedOwner() {
        return owner;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return MenuSupport.noopQuickMove();
    }

    @Override
    public boolean stillValid(Player player) {
        return owner == null || owner.getLevel() != null
                && owner.getLevel().getBlockEntity(pos) == owner
                && MenuSupport.stillValidWithin(player, owner.getBlockPos());
    }
}
