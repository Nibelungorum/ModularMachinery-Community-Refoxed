package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;

/** Shared synchronized controller fields and player inventory placement. */
final class ControllerMenuState {
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
        formed = add(menu, owner, controller -> controller.isFormed() ? 1 : 0);
        active = add(menu, owner, controller -> controller.isRuntimeActive() ? 1 : 0);
        lastFailure = add(menu, owner, controller -> failureCode(controller.getLastFailureUnloc()));
        redstonePaused = add(menu, owner, controller -> controller.isRedstonePaused() ? 1 : 0);
        parallelControllerCount = add(menu, owner, MachineControllerBlockEntity::parallelControllerCount);
        currentParallelism = add(menu, owner, MachineControllerBlockEntity::currentParallelism);
        maxParallelism = add(menu, owner, MachineControllerBlockEntity::getMaxParallelism);
    }

    private static DataSlot add(AbstractMachineMenu menu, MachineControllerBlockEntity owner,
                                java.util.function.ToIntFunction<MachineControllerBlockEntity> getter) {
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
                var factory = owner.getFactoryController();
                if (factory == null) return owner.recipeLocked() ? 1 : 0;
                return factory.threadSnapshots(owner).stream().findFirst()
                        .map(thread -> thread.locked() ? 1 : 0).orElse(0);
            }

            @Override public void set(int value) { }
        });
    }

    static DataSlot addInstalledModuleCountSlot(AbstractMachineMenu menu, MachineControllerBlockEntity owner) {
        return add(menu, owner, MachineControllerBlockEntity::installedModuleCount);
    }

    static DataSlot addModuleConnectedSlot(AbstractMachineMenu menu, MachineControllerBlockEntity owner) {
        return add(menu, owner, controller -> controller.connectedHostId().isPresent() ? 1 : 0);
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
