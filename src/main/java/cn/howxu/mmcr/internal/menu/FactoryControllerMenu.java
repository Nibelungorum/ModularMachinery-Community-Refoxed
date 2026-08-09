package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.network.FactoryControllerSnapshot;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Dedicated controller menu for formed machines containing a factory scheduler.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FactoryControllerMenu extends AbstractMachineMenu {
    private static final int FACTORY_PLAYER_INVENTORY_X = 112;

    private final BlockPos controllerPos;
    private final ControllerMenuState state;
    private FactoryControllerSnapshot snapshot;
    private int selectedThreadIndex;

    public FactoryControllerMenu(int containerId, Inventory inventory, MachineControllerBlockEntity owner, ServerPlayer player) {
        super(ModUIs.FACTORY_CONTROLLER.get(), containerId);
        controllerPos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
        state = new ControllerMenuState(this, owner);
        ControllerMenuState.addControllerPlayerSlots(this, inventory, FACTORY_PLAYER_INVENTORY_X);
        snapshot = FactoryControllerSnapshot.empty(controllerPos);
        if (owner != null) {
            FactorySchedulerBlockEntity factory = owner.getFactoryController();
            if (factory != null) {
                factory.ensureBaseThreadFor(owner);
                factory.sendSnapshot(player, owner);
            }
        }
    }

    public FactoryControllerMenu(int containerId, Inventory inventory, MachineControllerBlockEntity owner) {
        this(containerId, inventory, owner, null);
    }

    private FactoryControllerMenu(int containerId, Inventory inventory, BlockPos controllerPos) {
        super(ModUIs.FACTORY_CONTROLLER.get(), containerId);
        this.controllerPos = controllerPos;
        state = new ControllerMenuState(this, null);
        ControllerMenuState.addControllerPlayerSlots(this, inventory, FACTORY_PLAYER_INVENTORY_X);
        snapshot = FactoryControllerSnapshot.empty(controllerPos);
    }

    public static FactoryControllerMenu clientOpen(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        return new FactoryControllerMenu(containerId, inventory, buffer.readBlockPos());
    }

    public static FactoryControllerMenu clientOpen(int containerId, Inventory inventory) {
        return new FactoryControllerMenu(containerId, inventory, BlockPos.ZERO);
    }

    public BlockPos controllerPos() { return controllerPos; }
    public boolean isFormed() { return snapshot.formed() || state.formed.get() != 0; }
    public boolean isRedstonePaused() { return snapshot.redstonePaused() || state.redstonePaused.get() != 0; }
    public int activeThreadCount() { return snapshot.activeThreadCount(); }
    public int threadCount() { return snapshot.threadCount(); }
    public int currentParallelism() { return snapshot.currentParallelism(); }
    public int maxParallelism() { return snapshot.maxParallelism(); }
    public String machineName() { return snapshot.machineName(); }
    public int parallelSlots() { return snapshot.parallelSlots(); }
    public List<FactoryRecipeScheduler.ThreadSnapshot> threads() { return snapshot.threads(); }

    public void applySnapshot(FactoryControllerSnapshot snapshot) {
        if (!controllerPos.equals(snapshot.controllerPos())) return;
        this.snapshot = snapshot;
        if (snapshot.threads().stream().noneMatch(thread -> thread.index() == selectedThreadIndex)) selectedThreadIndex = 0;
    }

    public FactoryRecipeScheduler.ThreadSnapshot selectedThread() {
        return snapshot.threads().stream().filter(thread -> thread.index() == selectedThreadIndex).findFirst()
                .orElseGet(() -> snapshot.threads().isEmpty()
                        ? FactoryRecipeScheduler.ThreadSnapshot.idleBase() : snapshot.threads().getFirst());
    }

    public void selectThread(int index) {
        if (snapshot.threads().stream().anyMatch(thread -> thread.index() == index)) selectedThreadIndex = index;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return MenuSupport.noopQuickMove();
    }

    @Override
    public boolean stillValid(Player player) {
        return MenuSupport.stillValidWithin(player, controllerPos);
    }
}
