package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.EnergyHatchMenu;
import cn.howxu.mmcr.internal.menu.FactorySchedulerMenu;
import cn.howxu.mmcr.internal.menu.FluidHatchMenu;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.SimpleContainer;
import static org.assertj.core.api.Assertions.assertThat;

class MenuScreenTest {
    private static Language previousLanguage;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        bindDeferredHolder(NeoForgeMod.WATER_TYPE, new FluidType(FluidType.Properties.create().descriptionId("block.minecraft.water")));
        injectTranslations();
        bind(ModUIs.ITEM_BUS, new MenuType<>((containerId, playerInventory) -> new ItemBusMenu(containerId, playerInventory), FeatureFlags.VANILLA_SET));
        bind(ModUIs.MACHINE_CONTROLLER, new MenuType<>(MachineControllerMenu::clientOpen, FeatureFlags.VANILLA_SET));
    }

    @AfterAll
    static void restoreLanguage() {
        Language.inject(previousLanguage);
    }

    @Test
    void recipe_lock_button_loses_focus_after_click() {
        Button button = Button.builder(Component.empty(), ignored -> {}).bounds(0, 0, 20, 20).build();
        button.setFocused(true);

        MachineMenuScreen.clearRecipeLockButtonFocus(button);

        assertThat(button.isFocused()).isFalse();
    }

    @Test
    void auto_io_label_uses_resolved_port_io_type_before_owner_fallback() {
        assertThat(AbstractPortScreen.isOutputPort(IOType.OUTPUT, null)).isTrue();
        assertThat(AbstractPortScreen.isOutputPort(IOType.OUTPUT, IOType.INPUT)).isTrue();
        assertThat(AbstractPortScreen.isOutputPort(IOType.INPUT, IOType.OUTPUT)).isFalse();
        assertThat(AbstractPortScreen.isOutputPort(null, IOType.OUTPUT)).isTrue();
        assertThat(AbstractPortScreen.isOutputPort(null, null)).isFalse();
    }

    @Test
    void fluid_info_hides_empty_fluids_and_localizes_water() {
        FluidStack water = new FluidStack(Fluids.WATER, 1);

        assertThat(MachineMenuScreen.shouldRenderFluidInfo(null)).isFalse();
        assertThat(MachineMenuScreen.shouldRenderFluidInfo(FluidStack.EMPTY)).isFalse();
        assertThat(MachineMenuScreen.shouldRenderFluidInfo(water)).isTrue();
        assertThat(MachineMenuScreen.fluidInfoLine(water).getContents().toString()).contains("gui.mmcr.fluid");
        assertThat(MachineMenuScreen.fluidInfoLine(water).getString()).contains("Fluid").contains("Water");
    }

    @Test
    void fluid_capacity_text_starts_after_the_fluid_name_line() {
        int titleY = MachineMenuScreen.titleY(6, true);

        assertThat(MachineMenuScreen.fluidStorageTextY(titleY))
                .isGreaterThanOrEqualTo(MachineMenuScreen.fluidInfoTextY(titleY) + 9);
    }

    @Test
    void empty_fluid_capacity_text_does_not_reserve_a_name_line() {
        int titleY = MachineMenuScreen.titleY(6, true);

        assertThat(MachineMenuScreen.fluidStorageTextY(titleY, false))
                .isEqualTo(MachineMenuScreen.storageTextY(titleY, true));
    }

    @Test
    void auto_io_page_allows_player_hotbar_slots_with_low_backing_indices() throws Exception {
        MachineMenuScreen screen = screenForMenu(new ItemBusMenu(1, new Inventory(null, null)));
        Field autoIOPage = MachineMenuScreen.class.getDeclaredField("autoIOPage");
        autoIOPage.setAccessible(true);
        autoIOPage.setBoolean(screen, true);

        Method isAutoIOPortSlot = MachineMenuScreen.class.getDeclaredMethod("isAutoIOPortSlot", Slot.class, int.class);
        isAutoIOPortSlot.setAccessible(true);

        Slot hotbarSlot = new Slot(new SimpleContainer(9), 0, 8, 142);
        assertThat((boolean) isAutoIOPortSlot.invoke(screen, hotbarSlot, 33)).isFalse();
    }

    @Test
    void module_status_lines_remain_visible_before_formation_and_mark_offline_modules_red() {
        List<MachineMenuScreen.ControllerStatusLine> hostLines = MachineMenuScreen.moduleStatusLines(
                true, false, 1, Optional.empty());
        List<MachineMenuScreen.ControllerStatusLine> offlineModuleLines = MachineMenuScreen.moduleStatusLines(
                false, true, 0, Optional.empty());
        List<MachineMenuScreen.ControllerStatusLine> connectedModuleLines = MachineMenuScreen.moduleStatusLines(
                false, true, 0, Optional.of(MMCR.id("test_host")));

        assertThat(hostLines).singleElement().satisfies(line ->
                assertThat(line.color()).isEqualTo(MachineMenuScreen.STATUS_LABEL_COLOR));
        assertThat(offlineModuleLines).singleElement().satisfies(line ->
                assertThat(line.color()).isEqualTo(MachineMenuScreen.UNFORMED_STATUS_COLOR));
        assertThat(connectedModuleLines).singleElement().satisfies(line ->
                assertThat(line.color()).isEqualTo(MachineMenuScreen.STATUS_LABEL_COLOR));
    }

    @Test
    void auto_io_page_hides_item_bus_port_slots() {
        ItemBusMenu menu = new ItemBusMenu(1, new Inventory(null, null));
        Slot portSlot = menu.getSlot(0);
        Slot playerSlot = menu.getSlot(menu.playerInventorySlotStart());

        assertThat(AbstractPortScreen.hidesSlotOnAutoIOPage(menu, true, portSlot, 0)).isTrue();
        assertThat(AbstractPortScreen.hidesSlotOnAutoIOPage(menu, true, playerSlot, menu.playerInventorySlotStart())).isFalse();
        assertThat(AbstractPortScreen.hidesSlotOnAutoIOPage(menu, false, portSlot, 0)).isFalse();
    }

    @Test
    void normal_item_bus_page_does_not_hide_slots_after_auto_io_mode() {
        ItemBusMenu menu = new ItemBusMenu(1, new Inventory(null, null));
        Slot portSlot = menu.getSlot(0);

        assertThat(AbstractPortScreen.hidesSlotOnAutoIOPage(menu, false, portSlot, 0)).isFalse();
    }

    @Test
    void auto_io_page_moves_item_bus_port_slots_offscreen_and_restores_them() throws Exception {
        ItemBusMenu menu = new ItemBusMenu(1, new Inventory(null, null));
        MachineMenuScreen screen = screenForMenu(menu);
        Slot portSlot = menu.getSlot(0);
        Slot playerSlot = menu.getSlot(menu.playerInventorySlotStart());
        int portX = portSlot.x;
        int portY = portSlot.y;
        int playerX = playerSlot.x;
        int playerY = playerSlot.y;

        setAutoIOPage(screen, true);
        invokeUpdateAutoIOWidgets(screen);

        assertThat(menu.getSlot(0).x).isLessThan(-900);
        assertThat(menu.getSlot(0).y).isLessThan(-900);
        assertThat(playerSlot.x).isEqualTo(playerX);
        assertThat(playerSlot.y).isEqualTo(playerY);

        setAutoIOPage(screen, false);
        invokeUpdateAutoIOWidgets(screen);

        assertThat(menu.getSlot(0)).isSameAs(portSlot);
        assertThat(menu.getSlot(0).x).isEqualTo(portX);
        assertThat(menu.getSlot(0).y).isEqualTo(portY);
    }

    @Test
    void controller_recipe_lock_state_uses_full_locked_recipe_id_from_menu() {
        MachineControllerMenu menu = MachineControllerMenu.clientOpen(1, new Inventory(null, null));
        menu.setData(12, 1);

        assertThat(MachineMenuScreen.recipeLockTooltip(menu.recipeLocked(), "mmcr:full_recipe_id"))
                .hasSize(2);
    }

    private static MachineMenuScreen screenForMenu(AbstractContainerMenu menu) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            MachineMenuScreen screen = (MachineMenuScreen) unsafe.allocateInstance(MachineMenuScreen.class);
            setField(AbstractContainerScreen.class, screen, "menu", menu);
            setField(MachineMenuScreen.class, screen, "autoIOSideButtons", new EnumMap<>(Direction.class));
            setField(MachineMenuScreen.class, screen, "hiddenSlotPositions", new ArrayList<>());
            return screen;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate menu screen", e);
        }
    }

    private static void setAutoIOPage(MachineMenuScreen screen, boolean value) throws ReflectiveOperationException {
        Field autoIOPage = MachineMenuScreen.class.getDeclaredField("autoIOPage");
        autoIOPage.setAccessible(true);
        autoIOPage.setBoolean(screen, value);
    }

    private static void invokeUpdateAutoIOWidgets(MachineMenuScreen screen) throws ReflectiveOperationException {
        Method method = MachineMenuScreen.class.getDeclaredMethod("updateAutoIOWidgets");
        method.setAccessible(true);
        method.invoke(screen);
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void bind(Object deferredHolder, MenuType<?> menuType) throws Exception {
        bindDeferredHolder(deferredHolder, menuType);
    }

    private static void bindDeferredHolder(Object deferredHolder, Object value) throws Exception {
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
        holder.set(deferredHolder, Holder.direct(value));
    }

    private static void injectTranslations() throws Exception {
        previousLanguage = Language.getInstance();
        var constructor = ClientLanguage.class.getDeclaredConstructor(Map.class, boolean.class);
        constructor.setAccessible(true);
        Language.inject(constructor.newInstance(Map.of(
                "gui.mmcr.fluid", "Fluid: %s",
                "block.minecraft.water", "Water"), false));
    }
}
