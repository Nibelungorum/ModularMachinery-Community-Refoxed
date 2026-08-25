package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.runtime.ControllerSyncRuntime;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;

import java.util.function.ToIntFunction;

/** Shared synchronized controller fields and player inventory placement. */
final class ControllerMenuState {
    private static final ControllerSyncRuntime SYNC_RUNTIME = new ControllerSyncRuntime();
    static final int PLAYER_INVENTORY_Y = 131;
    static final int HOTBAR_Y = 189;

    final DataSlot formed;
    final DataSlot active;
    final DataSlot lastFailure;
    final DataSlot redstonePaused;
    final DataSlot parallelControllerCount;
    final DataSlot currentParallelism;
    final DataSlot maxParallelism;

    ControllerMenuState(AbstractMachineMenu menu, MachineControllerBlockEntity owner) {
        formed = add(menu, owner, controller -> controller.runtimeSnapshot().structure().formed() ? 1 : 0);
        active = add(menu, owner, controller -> SYNC_RUNTIME.active(controller.runtimeSnapshot()) ? 1 : 0);
        lastFailure = add(menu, owner, controller -> failureCode(SYNC_RUNTIME.failureMessage(controller.runtimeSnapshot())));
        redstonePaused = add(menu, owner, controller -> {
            var state = controller.runtimeSnapshot();
            return state.crafting().status().isPaused() || state.factory().paused() ? 1 : 0;
        });
        parallelControllerCount = add(menu, owner, controller -> SYNC_RUNTIME.parallelControllerCount(controller.runtimeSnapshot()));
        currentParallelism = add(menu, owner, controller -> SYNC_RUNTIME.currentParallelism(controller.runtimeSnapshot()));
        maxParallelism = add(menu, owner, controller -> SYNC_RUNTIME.maxParallelism(controller.runtimeSnapshot()));
    }

    private static DataSlot add(AbstractMachineMenu menu, MachineControllerBlockEntity owner,
                                ToIntFunction<MachineControllerBlockEntity> getter) {
        return menu.addControllerDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return getter.applyAsInt(owner); }
            @Override public void set(int value) { }
        });
    }

    static void addControllerPlayerSlots(AbstractMachineMenu menu, Inventory inventory) {
        addControllerPlayerSlots(menu, inventory, 8);
    }

    static DataSlot addRecipeLockSlot(AbstractMachineMenu menu, MachineControllerBlockEntity owner) {
        return menu.addControllerDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() {
                return SYNC_RUNTIME.recipeLocked(owner.runtimeSnapshot()) ? 1 : 0;
            }

            @Override public void set(int value) { }
        });
    }

    static DataSlot addInstalledModuleCountSlot(AbstractMachineMenu menu, MachineControllerBlockEntity owner) {
        return add(menu, owner, controller -> controller.runtimeSnapshot().installedModuleCount());
    }

    static DataSlot addModuleConnectedSlot(AbstractMachineMenu menu, MachineControllerBlockEntity owner) {
        return add(menu, owner, controller -> controller.runtimeSnapshot().moduleConnectionStatus().connected() ? 1 : 0);
    }

    static DataSlot addControllerRoleSlot(AbstractMachineMenu menu, MachineControllerBlockEntity owner) {
        return add(menu, owner, MachineControllerMenu::controllerRoleSyncValue);
    }

    static void addControllerPlayerSlots(AbstractMachineMenu menu, Inventory inventory, int x) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                menu.addControllerSlot(new Slot(inventory, col + row * 9 + 9, x + col * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) menu.addControllerSlot(new Slot(inventory, col, x + col * 18, HOTBAR_Y));
    }

    static int failureCode(String key) {
        if ("gui.mmcr.controller.failure.missing_input".equals(key)) return 1;
        if ("gui.mmcr.controller.failure.missing_output".equals(key)) return 2;
        if ("gui.mmcr.controller.failure.missing_energy".equals(key)) return 3;
        if ("gui.mmcr.controller.failure.level_insufficient".equals(key)) return 4;
        return 0;
    }

    static String failureKey(int code) {
        return switch (code) {
            case 1 -> "gui.mmcr.controller.failure.missing_input";
            case 2 -> "gui.mmcr.controller.failure.missing_output";
            case 3 -> "gui.mmcr.controller.failure.missing_energy";
            case 4 -> "gui.mmcr.controller.failure.level_insufficient";
            default -> null;
        };
    }
}
