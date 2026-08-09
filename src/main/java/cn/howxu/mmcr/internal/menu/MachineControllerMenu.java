package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public class MachineControllerMenu extends AbstractMachineMenu {

    private final MachineControllerBlockEntity owner;
    private final Level level;
    private final BlockPos pos;
    private final DataSlot formed;
    private final DataSlot active;
    private final DataSlot activeTick;
    private final DataSlot activeTotalTick;
    private final DataSlot lastFailure;
    private final DataSlot redstonePaused;
    private final DataSlot parallelControllerCount;
    private final DataSlot currentParallelism;
    private final DataSlot maxParallelism;

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
            @Override public int get() { return owner.isRuntimeActive() ? 1 : 0; }
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
        this.lastFailure = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return failureCode(owner.getLastFailureUnloc()); }
            @Override public void set(int value) {}
        });
        this.redstonePaused = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return owner.isRedstonePaused() ? 1 : 0; }
            @Override public void set(int value) {}
        });
        this.parallelControllerCount = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return owner.parallelControllerCount(); }
            @Override public void set(int value) {}
        });
        this.currentParallelism = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return owner.currentParallelism(); }
            @Override public void set(int value) {}
        });
        this.maxParallelism = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return owner.getMaxParallelism(); }
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
        this.lastFailure = addDataSlot(DataSlot.standalone());
        this.redstonePaused = addDataSlot(DataSlot.standalone());
        this.parallelControllerCount = addDataSlot(DataSlot.standalone());
        this.currentParallelism = addDataSlot(DataSlot.standalone());
        this.maxParallelism = addDataSlot(DataSlot.standalone());
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

    public @Nullable Identifier machineId() {
        MachineControllerBlockEntity controller = resolvedOwner();
        Machine machine = controller == null ? null : controller.getMachine();
        return machine == null ? null : machine.registryName();
    }

    public boolean isFormed() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? formed.get() != 0 : controller.isFormed();
    }

    public boolean hasActiveRecipe() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? active.get() != 0 : controller.isRuntimeActive() || controller.hasClientActiveRecipe() || active.get() != 0;
    }

    public int activeRecipeTick() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null || controller.getActiveRecipe() == null ? activeTick.get() : controller.getTickCounter();
    }

    public int activeRecipeTotalTick() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null || controller.getActive() == null ? activeTotalTick.get() : controller.getActive().getTotalTick();
    }

    public @Nullable String lastFailureMessage() {
        MachineControllerBlockEntity controller = resolvedOwner();
        if (controller != null && controller.getLastFailureUnloc() != null) return controller.getLastFailureUnloc();
        return failureKey(lastFailure.get());
    }

    public boolean isRedstonePaused() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return (controller != null && controller.isRedstonePaused()) || redstonePaused.get() != 0;
    }

    public int parallelControllerCount() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? parallelControllerCount.get() : controller.parallelControllerCount();
    }

    public int currentParallelism() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? currentParallelism.get() : controller.currentParallelism();
    }

    public int maxParallelism() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? Math.max(1, maxParallelism.get()) : controller.getMaxParallelism();
    }

    public long totalStoredEnergy() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? 0L : controller.totalStoredEnergy();
    }

    public long totalCapacityEnergy() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? 0L : controller.totalCapacityEnergy();
    }

    public FluidStack primaryFluid() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? FluidStack.EMPTY : controller.primaryFluid();
    }

    public FluidStack primaryOutputFluid() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? FluidStack.EMPTY : controller.primaryOutputFluid();
    }

    private static int failureCode(@Nullable String key) {
        if ("gui.mmcr.controller.failure.missing_input".equals(key)) return 1;
        if ("gui.mmcr.controller.failure.missing_output".equals(key)) return 2;
        if ("gui.mmcr.controller.failure.missing_energy".equals(key)) return 3;
        return 0;
    }

    private static @Nullable String failureKey(int code) {
        return switch (code) {
            case 1 -> "gui.mmcr.controller.failure.missing_input";
            case 2 -> "gui.mmcr.controller.failure.missing_output";
            case 3 -> "gui.mmcr.controller.failure.missing_energy";
            default -> null;
        };
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
