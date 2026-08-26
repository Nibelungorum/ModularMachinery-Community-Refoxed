package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import sun.misc.Unsafe;

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
    void auto_io_control_tooltip_uses_capability_and_direction() {
        assertThat(AbstractPortScreen.autoIOControlTooltipKey(MMCR.id("item"), false))
                .isEqualTo("mmcr.auto_io.item_input_control");
        assertThat(AbstractPortScreen.autoIOControlTooltipKey(MMCR.id("item"), true))
                .isEqualTo("mmcr.auto_io.item_output_control");
        assertThat(AbstractPortScreen.autoIOControlTooltipKey(MMCR.id("fluid"), false))
                .isEqualTo("mmcr.auto_io.fluid_input_control");
        assertThat(AbstractPortScreen.autoIOControlTooltipKey(MMCR.id("fluid"), true))
                .isEqualTo("mmcr.auto_io.fluid_output_control");
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
    void selected_capability_state_can_choose_a_non_first_capability() throws Exception {
        CapabilitySelectionScreen screen = (CapabilitySelectionScreen) unsafe().allocateInstance(
                CapabilitySelectionScreen.class);
        Identifier item = MMCR.id("item");
        Identifier fluid = MMCR.id("fluid");

        screen.choose(fluid);

        assertThat(screen.resolve(List.of(item, fluid))).isEqualTo(fluid);
    }

    @Test
    void controller_recipe_lock_state_uses_full_locked_recipe_id_from_menu() {
        MachineControllerMenu menu = MachineControllerMenu.clientOpen(1, new Inventory(null, null));
        menu.setData(12, 1);

        assertThat(MachineControllerScreen.recipeLockTooltip(menu.recipeLocked(), "mmcr:full_recipe_id"))
                .hasSize(2);
    }

    @Test
    void controller_progress_percent_uses_active_recipe_ticks() {
        assertThat(MachineControllerScreen.progressPercent(25, 100)).isEqualTo(25);
        assertThat(MachineControllerScreen.progressPercent(0, 0)).isEqualTo(0);
    }

    @Test
    void controller_detail_rows_use_scaled_pose_coordinates() {
        assertThat(MachineControllerScreen.detailTextY(34)).isEqualTo(40);
    }

    @Test
    void controller_detail_lines_preserve_snapshot_order() {
        Identifier levelTypeId = MMCR.id("menu_test_level_type");
        Identifier levelId = MMCR.id("menu_test_level");
        LevelType levelType = new LevelType(levelTypeId, Component.literal("Menu Test Level"));
        MachineLevel level = new MachineLevel(levelId, levelTypeId, 1,
                new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()),
                ItemStack.EMPTY, LevelModifier.IDENTITY);
        TestBootstrap.registerType(levelType);
        TestBootstrap.registerLevel(level);

        MachineControllerMenu menu = MachineControllerMenu.clientOpen(1, new Inventory(null, null));
        ExecutionStatus failure = new ExecutionStatus(MMCR.id("menu_test_failure"), StatusSeverity.BLOCKED,
                MMCR.id("menu_test_source"), Map.of("reason", "missing_input"));
        menu.applyClientSnapshot(new PktMachineStatePayload(
                BlockPos.ZERO, "mmcr:recipe", true, true,
                List.of(levelId.toString()), false, "", "mmcr:test_cube", 2, 3, true,
                "mmcr:host", CraftingStatus.Status.CRAFTING, "", failure, true, true,
                4, 20, 6, 8, false, 0, 0, 2, 3, 0, 0,
                FluidStack.EMPTY, FluidStack.EMPTY));

        assertThat(MachineControllerScreen.detailLines(menu)).containsExactly(
                new MachineControllerScreen.ControllerStatusLine(MachineControllerScreen.levelLine(level),
                        MachineControllerScreen.STATUS_LABEL_COLOR),
                new MachineControllerScreen.ControllerStatusLine(Component.translatable(
                        "gui.mmcr.controller.last_failure",
                        Component.translatable("gui.mmcr.controller.failure.missing_input")),
                        MachineControllerScreen.STATUS_LABEL_COLOR),
                new MachineControllerScreen.ControllerStatusLine(Component.translatable(
                        "gui.mmcr.controller.module_connected", Component.literal("mmcr:host")),
                        MachineControllerScreen.STATUS_LABEL_COLOR),
                new MachineControllerScreen.ControllerStatusLine(MachineControllerScreen.parallelSlotLine(2),
                        MachineControllerScreen.STATUS_LABEL_COLOR),
                new MachineControllerScreen.ControllerStatusLine(MachineControllerScreen.parallelLine(6, 8),
                        MachineControllerScreen.STATUS_LABEL_COLOR),
                new MachineControllerScreen.ControllerStatusLine(Component.translatable(
                        "gui.mmcr.controller.progress", "20%"), -1),
                new MachineControllerScreen.ControllerStatusLine(Component.translatable(
                        "gui.mmcr.controller.redstone_stopped"), MachineControllerScreen.STATUS_LABEL_COLOR));
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

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final class CapabilitySelectionScreen extends AbstractPortScreen<ItemBusMenu> {
        private CapabilitySelectionScreen() {
            super(null, null, Component.empty(), 166);
        }

        private void choose(Identifier capabilityId) {
            selectCapability(capabilityId);
        }

        private Identifier resolve(List<Identifier> capabilityIds) {
            return selectedCapabilityId(capabilityIds);
        }

        @Override protected net.minecraft.core.BlockPos portPos() { return net.minecraft.core.BlockPos.ZERO; }
        @Override protected IOType ownerIOType() { return IOType.INPUT; }
        @Override protected int portSlotCount() { return 0; }
        @Override protected Identifier texture(boolean autoIOPage) { return MMCR.id("textures/gui/test.png"); }
    }
}
