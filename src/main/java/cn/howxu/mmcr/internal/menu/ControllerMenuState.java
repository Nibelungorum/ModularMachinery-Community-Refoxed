package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.runtime.ControllerSyncRuntime;
import cn.howxu.mmcr.internal.runtime.MachineStateSnapshot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;

import java.util.function.BooleanSupplier;
import java.util.function.ToIntFunction;

/** Shared synchronized controller fields and player inventory placement. */
final class ControllerMenuState {
    private static final ControllerSyncRuntime SYNC_RUNTIME = new ControllerSyncRuntime();
    static final int PLAYER_INVENTORY_Y = 131;
    static final int HOTBAR_Y = 189;

    final DataSlot formed;
    final DataSlot active;
    final DataSlot redstonePaused;
    final DataSlot parallelControllerCount;

    ControllerMenuState(AbstractMachineMenu menu, MachineControllerBlockEntity owner) {
        formed = add(menu, owner, state -> state.formed() ? 1 : 0);
        active = add(menu, owner, state -> state.active() ? 1 : 0);
        redstonePaused = add(menu, owner, state -> state.redstonePaused() ? 1 : 0);
        parallelControllerCount = add(menu, owner, MachineStateSnapshot::parallelControllerCount);
    }

    private static DataSlot add(AbstractMachineMenu menu, MachineControllerBlockEntity owner,
                                ToIntFunction<MachineStateSnapshot> getter) {
        return menu.addControllerDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return getter.applyAsInt(SYNC_RUNTIME.machineState(owner.runtimeSnapshot())); }
            @Override public void set(int value) { }
        });
    }

    static void addControllerPlayerSlots(AbstractMachineMenu menu, Inventory inventory) {
        addControllerPlayerSlots(menu, inventory, 8);
    }

    static DataSlot addInstalledModuleCountSlot(AbstractMachineMenu menu, MachineControllerBlockEntity owner) {
        return add(menu, owner, MachineStateSnapshot::installedModuleCount);
    }

    static DataSlot addModuleConnectedSlot(AbstractMachineMenu menu, MachineControllerBlockEntity owner) {
        return add(menu, owner, state -> state.moduleConnected() ? 1 : 0);
    }

    static DataSlot addControllerRoleSlot(AbstractMachineMenu menu, MachineControllerBlockEntity owner) {
        return add(menu, owner, MachineStateSnapshot::controllerRole);
    }

    static void addControllerPlayerSlots(AbstractMachineMenu menu, Inventory inventory, int x) {
        addControllerPlayerSlots(menu, inventory, x, 0);
    }

    static void addControllerPlayerSlots(AbstractMachineMenu menu, Inventory inventory, int x, int yOffset) {
        BooleanSupplier visible = inventory.player != null && !inventory.player.level().isClientSide()
                ? () -> true : ((ControllerUiMenu) menu)::playerInventoryVisible;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                menu.addControllerSlot(new ControllerPlayerSlot(inventory, col + row * 9 + 9,
                        x + col * 18, PLAYER_INVENTORY_Y + yOffset + row * 18, visible));
            }
        }
        for (int col = 0; col < 9; col++) {
            menu.addControllerSlot(new ControllerPlayerSlot(inventory, col, x + col * 18, HOTBAR_Y + yOffset, visible));
        }
    }

}
