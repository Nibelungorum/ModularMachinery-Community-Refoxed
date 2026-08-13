package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
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
    void client_menu_uses_synced_progress_when_the_client_controller_has_an_active_recipe() throws Exception {
        MachineControllerBlockEntity controller = controllerWithMachine(MMCR.id("blast_furnace"));
        setField(MachineControllerBlockEntity.class, controller, "active", activeRecipe(MMCR.id("blast_furnace"), 0));
        MachineControllerMenu menu = new MachineControllerMenu(1, emptyInventory());
        setField(MachineControllerMenu.class, menu, "level", LevelStub.createWithBlockEntities(List.of(controller)));

        menu.setData(1, 1);
        menu.setData(2, 35);
        menu.setData(3, 100);

        assertThat(menu.hasActiveRecipe()).isTrue();
        assertThat(menu.activeRecipeTick()).isEqualTo(35);
        assertThat(menu.activeRecipeTotalTick()).isEqualTo(100);
    }

    @Test
    void server_menu_syncs_factory_base_thread_progress_when_controller_has_no_local_active_recipe() throws Exception {
        MachineControllerBlockEntity controller = controllerWithMachine(MMCR.id("blast_furnace"));
        FactorySchedulerBlockEntity factory = new FactorySchedulerBlockEntity(net.minecraft.core.BlockPos.ZERO,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        setField(MachineControllerBlockEntity.class, controller, "machine", factoryMachine(MMCR.id("blast_furnace")));
        setField(MachineControllerBlockEntity.class, controller, "components", List.of(new ProcessingComponent(
                null, factory, net.minecraft.core.BlockPos.ZERO, net.minecraft.core.BlockPos.ZERO, List.of())));
        setField(FactorySchedulerBlockEntity.class, factory, "scheduler", schedulerWithActiveBaseThread(MMCR.id("blast_furnace")));
        MachineControllerMenu menu = new MachineControllerMenu(1, emptyInventory(), controller);

        menu.broadcastChanges();

        assertThat(menu.hasActiveRecipe()).isTrue();
        assertThat(menu.activeRecipeTick()).isEqualTo(35);
        assertThat(menu.activeRecipeTotalTick()).isEqualTo(100);
    }

    @Test
    void client_menu_treats_factory_thread_state_as_active_even_without_recipe_progress() {
        MachineControllerMenu menu = new MachineControllerMenu(1, emptyInventory());

        menu.setData(0, 1);
        menu.setData(1, 1);

        assertThat(menu.isFormed()).isTrue();
        assertThat(menu.hasActiveRecipe()).isTrue();
        assertThat(menu.activeRecipeTick()).isZero();
        assertThat(menu.activeRecipeTotalTick()).isZero();
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
    void client_menu_decodes_level_failure_from_synced_data_slot() {
        MachineControllerMenu menu = new MachineControllerMenu(1, emptyInventory());

        menu.setData(4, 4);

        assertThat(menu.lastFailureMessage()).isEqualTo("gui.mmcr.controller.failure.level_insufficient");
    }

    @Test
    void client_menu_updates_parallel_display_from_synced_data_slots() {
        MachineControllerMenu menu = new MachineControllerMenu(1, emptyInventory());

        assertThat(menu.parallelControllerCount()).isZero();
        assertThat(menu.currentParallelism()).isZero();
        assertThat(menu.maxParallelism()).isEqualTo(1);

        menu.setData(7, 7);
        menu.setData(8, 524);

        assertThat(menu.currentParallelism()).isEqualTo(7);
        assertThat(menu.maxParallelism()).isEqualTo(524);
    }

    @Test
    void client_menu_updates_factory_thread_count_from_synced_data_slot() {
        MachineControllerMenu menu = new MachineControllerMenu(1, emptyInventory());

        assertThat(menu.factoryThreadCount()).isZero();
        menu.setData(10, 3);

        assertThat(menu.factoryThreadCount()).isEqualTo(3);
        assertThat(menu.factoryActiveThreadCount()).isZero();
        menu.setData(11, 2);

        assertThat(menu.factoryActiveThreadCount()).isEqualTo(2);
    }

    @Test
    void client_menu_updates_base_recipe_lock_from_synced_data_slot() {
        MachineControllerMenu menu = new MachineControllerMenu(1, emptyInventory());

        assertThat(menu.recipeLocked()).isFalse();
        menu.setData(12, 1);
        assertThat(menu.recipeLocked()).isTrue();
        assertThat(menu.lockedRecipeId()).isNull();
    }

    @Test
    void client_menu_uses_synced_parallel_data_when_the_client_controller_is_available() throws Exception {
        MachineControllerBlockEntity controller = controllerWithMachine(MMCR.id("blast_furnace"));
        MachineControllerMenu menu = new MachineControllerMenu(1, emptyInventory());
        setField(MachineControllerMenu.class, menu, "level", LevelStub.createWithBlockEntities(java.util.List.of(controller)));

        menu.setData(6, 1);
        menu.setData(7, 7);
        menu.setData(8, 524);

        assertThat(menu.parallelControllerCount()).isEqualTo(1);
        assertThat(menu.currentParallelism()).isEqualTo(7);
        assertThat(menu.maxParallelism()).isEqualTo(524);
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

    @Test
    void formed_controller_menu_becomes_invalid_when_structure_unforms() throws Exception {
        MachineControllerBlockEntity controller = controllerWithMachine(MMCR.id("blast_furnace"));
        controller.setLevel(LevelStub.createWithBlockEntities(List.of(controller)));
        controller.getLevel().setBlock(controller.getBlockPos(), controller.getBlockState().setValue(MachineControllerBlock.FORMED, true), 3);

        assertThat(MenuSupport.controllerStillPresentAndFormed(controller)).isTrue();
        controller.getLevel().setBlock(controller.getBlockPos(), controller.getBlockState().setValue(MachineControllerBlock.FORMED, false), 3);

        assertThat(MenuSupport.controllerStillPresentAndFormed(controller)).isFalse();
    }

    @Test
    void unformed_controller_menu_remains_valid_until_formation_is_observed() throws Exception {
        MachineControllerBlockEntity controller = controllerWithMachine(MMCR.id("blast_furnace"));
        controller.setLevel(LevelStub.createWithBlockEntities(List.of(controller)));
        MachineControllerMenu menu = new MachineControllerMenu(1, emptyInventory(), controller);

        assertThat(menu.wasFormedDuringSession()).isFalse();
    }

    @Test
    void controller_menu_becomes_invalid_after_formed_structure_unforms() throws Exception {
        MachineControllerBlockEntity controller = controllerWithMachine(MMCR.id("blast_furnace"));
        controller.setLevel(LevelStub.createWithBlockEntities(List.of(controller)));
        MachineControllerMenu menu = new MachineControllerMenu(1, emptyInventory(), controller);

        controller.getLevel().setBlock(controller.getBlockPos(), controller.getBlockState().setValue(MachineControllerBlock.FORMED, true), 3);
        assertThat(menu.wasFormedDuringSession()).isTrue();
        controller.getLevel().setBlock(controller.getBlockPos(), controller.getBlockState().setValue(MachineControllerBlock.FORMED, false), 3);

        assertThat(MenuSupport.controllerStillPresentAndFormed(controller)).isFalse();
    }

    @Test
    void formed_controller_menu_records_formation_on_open() throws Exception {
        MachineControllerBlockEntity controller = controllerWithMachine(MMCR.id("blast_furnace"));
        controller.setLevel(LevelStub.createWithBlockEntities(List.of(controller)));
        controller.getLevel().setBlock(controller.getBlockPos(), controller.getBlockState().setValue(MachineControllerBlock.FORMED, true), 3);

        MachineControllerMenu menu = new MachineControllerMenu(1, emptyInventory(), controller);

        assertThat(menu.wasFormedDuringSession()).isTrue();
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
        setField(net.minecraft.world.level.block.entity.BlockEntity.class, controller, "blockState", ModBlocks.controllerFor(id).get().defaultBlockState());
        setField(MachineControllerBlockEntity.class, controller, "machine", new DynamicMachine(id, "machine." + id.getPath(), new BlockArray(Map.of())));
        setField(MachineControllerBlockEntity.class, controller, "components", List.of());
        return controller;
    }

    private static DynamicMachine factoryMachine(net.minecraft.resources.Identifier id) {
        return new DynamicMachine(id, "machine." + id.getPath(), new BlockArray(Map.of()),
                cn.howxu.mmcr.api.machine.MachineControllerSpec.defaultsFor(id),
                cn.howxu.mmcr.api.machine.MachineAppearanceSpec.defaults(),
                cn.howxu.mmcr.api.machine.PortRequirementSpec.none(),
                cn.howxu.mmcr.api.machine.PortTierRequirementSpec.none(), List.of(), Map.of(),
                16, true, true, 1, List.of());
    }

    private static FactoryRecipeScheduler schedulerWithActiveBaseThread(net.minecraft.resources.Identifier machineId) throws Exception {
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(1, new RecipeCraftingContextPool());
        ActiveMachineRecipe active = activeRecipe(machineId, 35);
        scheduler.allThreads().getFirst().setActiveRecipeForTesting(active);
        return scheduler;
    }

    private static ActiveMachineRecipe activeRecipe(net.minecraft.resources.Identifier machineId, int tick) {
        MachineRecipe recipe = new MachineRecipe(MMCR.id("menu_progress_test"), machineId, 100, List.of(), List.of());
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, 16);
        active.setTick(tick);
        return active;
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
