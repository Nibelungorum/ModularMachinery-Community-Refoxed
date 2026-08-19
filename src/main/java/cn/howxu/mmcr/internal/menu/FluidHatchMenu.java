package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

public class FluidHatchMenu extends AbstractMachineMenu {

    private final FluidHatchBlockEntity owner;
    private final Level level;
    private final BlockPos pos;
    private final LongDataSlot amount;
    private final LongDataSlot capacity;

    public FluidHatchMenu(int containerId, Inventory playerInv, FluidHatchBlockEntity owner) {
        super(ModUIs.FLUID_HATCH.get(), containerId);
        this.owner = owner;
        this.level = playerInv.player.level();
        this.pos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
        this.amount = addLongDataSlot(owner == null ? LongDataSlot.standalone()
                : new LongDataSlot(() -> owner.getResourceHandler(null).getAmountAsLong(0)));
        this.capacity = addLongDataSlot(owner == null ? LongDataSlot.standalone()
                : new LongDataSlot(() -> owner.getResourceHandler(null).getCapacityAsLong(0, FluidResource.EMPTY)));
        addPlayerSlots(playerInv);
    }

    public FluidHatchMenu(int containerId, Inventory playerInv, BlockPos pos) {
        super(ModUIs.FLUID_HATCH.get(), containerId);
        this.owner = null;
        this.level = playerInv.player.level();
        this.pos = pos;
        this.amount = addLongDataSlot(LongDataSlot.standalone());
        this.capacity = addLongDataSlot(LongDataSlot.standalone());
        addPlayerSlots(playerInv);
    }

    public static FluidHatchMenu clientOpen(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        return new FluidHatchMenu(containerId, playerInv, buf.readBlockPos());
    }

    public FluidHatchBlockEntity owner() {
        return owner;
    }

    public BlockPos pos() { return pos; }

    public ResourceHandler<FluidResource> storage() {
        FluidHatchBlockEntity hatch = resolvedOwner();
        return hatch == null ? null : hatch.getResourceHandler(null);
    }

    public long fluidAmount() {
        ResourceHandler<FluidResource> storage = storage();
        return storage == null ? amount.value() : storage.getAmountAsLong(0);
    }

    public long fluidCapacity() {
        FluidHatchBlockEntity hatch = resolvedOwner();
        return hatch == null ? capacity.value() : fluidCapacity(hatch);
    }

    static long fluidCapacity(FluidHatchBlockEntity hatch) {
        return hatch.getResourceHandler(null).getCapacityAsLong(0, FluidResource.EMPTY);
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
