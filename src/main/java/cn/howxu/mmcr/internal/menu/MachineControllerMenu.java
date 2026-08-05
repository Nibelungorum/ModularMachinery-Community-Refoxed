package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class MachineControllerMenu extends AbstractMachineMenu {

    private final MachineControllerBlockEntity owner;
    private final Level level;
    private final BlockPos pos;
    private final DataSlot formed;
    private final DataSlot active;
    private final DataSlot activeTick;
    private final DataSlot activeTotalTick;

    public MachineControllerMenu(int containerId, Inventory playerInv, MachineControllerBlockEntity owner) {
        super(ModUIs.MACHINE_CONTROLLER.get(), containerId);
        this.owner = owner;
        this.level = playerInv.player == null ? null : playerInv.player.level();
        this.pos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
        this.formed = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return owner.isFormed() ? 1 : 0; }
            @Override public void set(int value) {}
        });
        this.active = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return owner.getActiveRecipe() == null ? 0 : 1; }
            @Override public void set(int value) {}
        });
        this.activeTick = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return owner.getTickCounter(); }
            @Override public void set(int value) {}
        });
        this.activeTotalTick = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return owner.getActive() == null ? 0 : owner.getActive().getTotalTick(); }
            @Override public void set(int value) {}
        });
        addControllerPlayerSlots(playerInv);
    }

    public MachineControllerMenu(int containerId, Inventory playerInv, BlockPos pos) {
        super(ModUIs.MACHINE_CONTROLLER.get(), containerId);
        this.owner = null;
        this.level = playerInv.player == null ? null : playerInv.player.level();
        this.pos = pos;
        this.formed = addDataSlot(DataSlot.standalone());
        this.active = addDataSlot(DataSlot.standalone());
        this.activeTick = addDataSlot(DataSlot.standalone());
        this.activeTotalTick = addDataSlot(DataSlot.standalone());
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
        this(containerId, playerInv, (MachineControllerBlockEntity) null);
    }

    public static MachineControllerMenu clientOpen(int containerId, Inventory playerInv) {
        return new MachineControllerMenu(containerId, playerInv);
    }

    public static MachineControllerMenu clientOpen(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        return new MachineControllerMenu(containerId, playerInv, buf.readBlockPos());
    }

    public MachineControllerBlockEntity owner() {
        return owner;
    }

    public MachineControllerBlockEntity resolvedOwner() {
        if (owner != null) return owner;
        if (level == null) return null;
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof MachineControllerBlockEntity controller ? controller : null;
    }

    public boolean isFormed() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? formed.get() != 0 : controller.isFormed();
    }

    public boolean hasActiveRecipe() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? active.get() != 0 : controller.getActiveRecipe() != null || controller.hasClientActiveRecipe() || active.get() != 0;
    }

    public int activeRecipeTick() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null || controller.getActiveRecipe() == null ? activeTick.get() : controller.getTickCounter();
    }

    public int activeRecipeTotalTick() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null || controller.getActive() == null ? activeTotalTick.get() : controller.getActive().getTotalTick();
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
