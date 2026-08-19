package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.List;
import java.util.Optional;

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

        MachineControllerScreen.clearRecipeLockButtonFocus(button);

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
    void module_status_lines_remain_visible_before_formation_and_mark_offline_modules_red() {
        List<MachineControllerScreen.ControllerStatusLine> hostLines = MachineControllerScreen.moduleStatusLines(
                true, false, 1, Optional.empty());
        List<MachineControllerScreen.ControllerStatusLine> offlineModuleLines = MachineControllerScreen.moduleStatusLines(
                false, true, 0, Optional.empty());
        List<MachineControllerScreen.ControllerStatusLine> connectedModuleLines = MachineControllerScreen.moduleStatusLines(
                false, true, 0, Optional.of(MMCR.id("test_host")));

        assertThat(hostLines).singleElement().satisfies(line ->
                assertThat(line.color()).isEqualTo(MachineControllerScreen.STATUS_LABEL_COLOR));
        assertThat(offlineModuleLines).singleElement().satisfies(line ->
                assertThat(line.color()).isEqualTo(MachineControllerScreen.UNFORMED_STATUS_COLOR));
        assertThat(connectedModuleLines).singleElement().satisfies(line ->
                assertThat(line.color()).isEqualTo(MachineControllerScreen.STATUS_LABEL_COLOR));
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
    void auto_io_toggle_and_eject_buttons_share_the_same_button_style() {
        assertThat(AbstractPortScreen.AutoIOToggleButton.class)
                .isAssignableTo(AbstractPortScreen.AutoIOStyledButton.class);
        assertThat(AbstractPortScreen.EjectButton.class)
                .isAssignableTo(AbstractPortScreen.AutoIOStyledButton.class);
    }

    @Test
    void controller_recipe_lock_state_uses_full_locked_recipe_id_from_menu() {
        MachineControllerMenu menu = MachineControllerMenu.clientOpen(1, new Inventory(null, null));
        menu.setData(12, 1);

        assertThat(MachineControllerScreen.recipeLockTooltip(menu.recipeLocked(), "mmcr:full_recipe_id"))
                .hasSize(2);
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
