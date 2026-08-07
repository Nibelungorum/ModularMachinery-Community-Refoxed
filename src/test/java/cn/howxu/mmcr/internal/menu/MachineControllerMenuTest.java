package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MachineControllerMenuTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.MACHINE_CONTROLLER, new MenuType<>(MachineControllerMenu::clientOpen, FeatureFlags.VANILLA_SET));
    }

    @Test
    void client_menu_updates_formed_state_from_synced_data_slot() {
        MachineControllerMenu menu = new MachineControllerMenu(1, emptyInventory());

        assertThat(menu.isFormed()).isFalse();
        menu.setData(0, 1);

        assertThat(menu.isFormed()).isTrue();
    }

    @Test
    void client_menu_updates_recipe_progress_from_synced_data_slots() {
        MachineControllerMenu menu = new MachineControllerMenu(1, emptyInventory());

        assertThat(menu.hasActiveRecipe()).isFalse();
        assertThat(menu.activeRecipeTick()).isZero();
        assertThat(menu.activeRecipeTotalTick()).isZero();

        menu.setData(1, 1);
        menu.setData(2, 35);
        menu.setData(3, 100);

        assertThat(menu.hasActiveRecipe()).isTrue();
        assertThat(menu.activeRecipeTick()).isEqualTo(35);
        assertThat(menu.activeRecipeTotalTick()).isEqualTo(100);
    }

    @Test
    void client_menu_updates_failure_and_redstone_state_from_synced_data_slots() {
        MachineControllerMenu menu = new MachineControllerMenu(1, emptyInventory());

        assertThat(menu.lastFailureMessage()).isNull();
        assertThat(menu.isRedstonePaused()).isFalse();

        menu.setData(4, 1);
        menu.setData(5, 1);

        assertThat(menu.lastFailureMessage()).isEqualTo("gui.mmcr.controller.failure.missing_input");
        assertThat(menu.isRedstonePaused()).isTrue();
    }

    @Test
    void client_menu_returns_zero_energy_and_empty_fluid_when_owner_missing() {
        MachineControllerMenu menu = new MachineControllerMenu(1, emptyInventory());

        assertThat(menu.totalStoredEnergy()).isZero();
        assertThat(menu.totalCapacityEnergy()).isZero();
        assertThat(menu.primaryFluid().isEmpty()).isTrue();
        assertThat(menu.primaryOutputFluid().isEmpty()).isTrue();
    }

    @Test
    void machine_id_comes_from_the_resolved_controller() throws Exception {
        MachineControllerBlockEntity controller = controllerWithMachine(MMCR.id("blast_furnace"));
        MachineControllerMenu menu = new MachineControllerMenu(1, emptyInventory(), controller);

        assertThat(menu.machineId()).isEqualTo(MMCR.id("blast_furnace"));
    }

    @Test
    void machine_id_is_null_without_a_resolved_controller() {
        MachineControllerMenu menu = new MachineControllerMenu(1, emptyInventory());

        assertThat(menu.machineId()).isNull();
    }

    private static Inventory emptyInventory() {
        return new Inventory(null, null);
    }

    private static MachineControllerBlockEntity controllerWithMachine(net.minecraft.resources.Identifier id) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        MachineControllerBlockEntity controller = (MachineControllerBlockEntity) unsafe.allocateInstance(MachineControllerBlockEntity.class);
        setField(net.minecraft.world.level.block.entity.BlockEntity.class, controller, "worldPosition", net.minecraft.core.BlockPos.ZERO);
        setField(MachineControllerBlockEntity.class, controller, "machine", new DynamicMachine(id, "machine." + id.getPath(), new BlockArray(Map.of())));
        return controller;
    }

    private static void setField(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void bind(Object deferredHolder, MenuType<MachineControllerMenu> menuType) throws Exception {
        Class<?> type = deferredHolder.getClass();
        Field holder = null;
        while (type != null && holder == null) {
            try {
                holder = type.getDeclaredField("holder");
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        if (holder == null) throw new NoSuchFieldException("holder");
        holder.setAccessible(true);
        holder.set(deferredHolder, Holder.direct(menuType));
    }
}
