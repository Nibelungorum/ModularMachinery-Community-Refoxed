package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
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
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MenuScreenTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.ITEM_BUS, new MenuType<>((containerId, playerInventory) -> new ItemBusMenu(containerId, playerInventory), FeatureFlags.VANILLA_SET));
        bind(ModUIs.MACHINE_CONTROLLER, new MenuType<>(MachineControllerMenu::clientOpen, FeatureFlags.VANILLA_SET));
    }

    @Test
    void hatchBarAtlasCoordinatesMatchMmce() {
        assertThat(MachineMenuScreen.fluidBarOverlayTexture()).isEqualTo(MMCR.id("textures/gui/guitank.png"));
        assertThat(MachineMenuScreen.fluidBarOverlaySourceX()).isEqualTo(176);
        assertThat(MachineMenuScreen.energyBarSourceX()).isEqualTo(196);
        assertThat(MachineMenuScreen.energyBarSourceY(0)).isEqualTo(61);
        assertThat(MachineMenuScreen.energyBarSourceY(1)).isEqualTo(60);
        assertThat(MachineMenuScreen.energyBarSourceY(61)).isZero();
    }

    @Test
    void layout_offsets_title_and_hides_inventory_label() {
        assertThat(MachineMenuScreen.titleX(8)).isEqualTo(10);
        assertThat(MachineMenuScreen.titleY(6)).isEqualTo(10);
        assertThat(MachineMenuScreen.titleX(8, true)).isEqualTo(40);
        assertThat(MachineMenuScreen.titleY(6, true)).isEqualTo(9);
        assertThat(MachineMenuScreen.titleX(8, false, true, false)).isEqualTo(40);
        assertThat(MachineMenuScreen.titleX(8, false, false, true)).isEqualTo(4);
        assertThat(MachineMenuScreen.titleY(6, false, true)).isEqualTo(4);
        assertThat(MachineMenuScreen.showsPortTitle(true, false, false)).isTrue();
        assertThat(MachineMenuScreen.showsPortTitle(false, true, false)).isTrue();
        assertThat(MachineMenuScreen.showsPortTitle(false, false, true)).isFalse();
        assertThat(MachineMenuScreen.hiddenInventoryLabelY()).isEqualTo(-1000);
        assertThat(MachineMenuScreen.TITLE_COLOR).isEqualTo(-12566464);
        assertThat(MachineMenuScreen.CONTROLLER_TITLE_COLOR).isEqualTo(0xFFE8E8E8);
        assertThat(MachineMenuScreen.titleColor(false)).isEqualTo(MachineMenuScreen.TITLE_COLOR);
        assertThat(MachineMenuScreen.titleColor(true)).isEqualTo(MachineMenuScreen.CONTROLLER_TITLE_COLOR);
        assertThat(MachineMenuScreen.controllerStatusX(10)).isEqualTo(10);
        assertThat(MachineMenuScreen.controllerStatusY(10)).isEqualTo(22);
    }

    @Test
    void auto_io_page_hides_port_titles_even_when_normal_ui_shows_them() {
        assertThat(MachineMenuScreen.shouldRenderTitle(true, false, false, false)).isTrue();
        assertThat(MachineMenuScreen.shouldRenderTitle(false, true, false, false)).isTrue();
        assertThat(MachineMenuScreen.shouldRenderTitle(true, false, false, true)).isFalse();
        assertThat(MachineMenuScreen.shouldRenderTitle(false, true, false, true)).isFalse();
        assertThat(MachineMenuScreen.shouldRenderTitle(false, false, true, true)).isFalse();
    }

    @Test
    void auto_io_direction_layout_is_fixed_north_centered() {
        assertThat(MachineMenuScreen.autoIODirectionAt(0, 0)).isNull();
        assertThat(MachineMenuScreen.autoIODirectionAt(1, 0)).isEqualTo(Direction.UP);
        assertThat(MachineMenuScreen.autoIODirectionAt(0, 1)).isEqualTo(Direction.WEST);
        assertThat(MachineMenuScreen.autoIODirectionAt(1, 1)).isEqualTo(Direction.NORTH);
        assertThat(MachineMenuScreen.autoIODirectionAt(2, 1)).isEqualTo(Direction.EAST);
        assertThat(MachineMenuScreen.autoIODirectionAt(1, 2)).isEqualTo(Direction.DOWN);
        assertThat(MachineMenuScreen.autoIODirectionAt(2, 2)).isEqualTo(Direction.SOUTH);
    }

    @Test
    void auto_io_center_grid_cell_has_shift_all_sides_action() {
        assertThat(MachineMenuScreen.isAutoIOShiftAllSidesCell(1, 1)).isTrue();
        assertThat(MachineMenuScreen.isAutoIOShiftAllSidesCell(0, 1)).isFalse();
    }

    @Test
    void auto_io_label_uses_resolved_port_io_type_before_owner_fallback() {
        assertThat(MachineMenuScreen.isOutputPort(IOType.OUTPUT, null)).isTrue();
        assertThat(MachineMenuScreen.isOutputPort(IOType.OUTPUT, IOType.INPUT)).isTrue();
        assertThat(MachineMenuScreen.isOutputPort(IOType.INPUT, IOType.OUTPUT)).isFalse();
        assertThat(MachineMenuScreen.isOutputPort(null, IOType.OUTPUT)).isTrue();
        assertThat(MachineMenuScreen.isOutputPort(null, null)).isFalse();
    }

    @Test
    void auto_io_toggle_label_includes_colored_state() {
        assertThat(MachineMenuScreen.autoIOToggleLabel(true, false).getString()).isEqualTo("mmcr.auto_io.auto_input: mmcr.auto_io.state.enabled");
        assertThat(MachineMenuScreen.autoIOToggleLabel(false, true).getString()).isEqualTo("mmcr.auto_io.auto_output: mmcr.auto_io.state.disabled");
        assertThat(MachineMenuScreen.autoIOToggleTypeLabel(false).getString()).isEqualTo("mmcr.auto_io.auto_input");
        assertThat(MachineMenuScreen.autoIOToggleStateLabel(true).getString()).isEqualTo("mmcr.auto_io.state.enabled");
    }

    @Test
    void auto_io_side_tooltip_uses_face_state_only() {
        TranslatableContents enabled = (TranslatableContents) MachineMenuScreen.autoIOSideTooltip(Direction.EAST, true).getContents();
        TranslatableContents disabled = (TranslatableContents) MachineMenuScreen.autoIOSideTooltip(Direction.DOWN, false).getContents();

        assertThat(enabled.getKey()).isEqualTo("mmcr.auto_io.side");
        assertThat(((TranslatableContents) ((Component) enabled.getArgs()[0]).getContents()).getKey()).isEqualTo("mmcr.direction.east");
        assertThat(((TranslatableContents) ((Component) enabled.getArgs()[1]).getContents()).getKey()).isEqualTo("mmcr.auto_io.enabled");
        assertThat(disabled.getKey()).isEqualTo("mmcr.auto_io.side");
        assertThat(((TranslatableContents) ((Component) disabled.getArgs()[0]).getContents()).getKey()).isEqualTo("mmcr.direction.down");
        assertThat(((TranslatableContents) ((Component) disabled.getArgs()[1]).getContents()).getKey()).isEqualTo("mmcr.auto_io.disabled");
    }

    @Test
    void auto_io_side_icon_is_empty_without_a_loaded_port() {
        assertThat(MachineMenuScreen.autoIOSideIcon(null, Direction.NORTH).isEmpty()).isTrue();
    }

    @Test
    void auto_io_page_hides_only_port_slots() {
        assertThat(MachineMenuScreen.isPortSlotIndex(0, 6)).isTrue();
        assertThat(MachineMenuScreen.isPortSlotIndex(5, 6)).isTrue();
        assertThat(MachineMenuScreen.isPortSlotIndex(6, 6)).isFalse();
    }

    @Test
    void auto_io_page_button_sits_at_top_right() {
        assertThat(MachineMenuScreen.autoIOPageButtonSize()).isEqualTo(12);
        assertThat(MachineMenuScreen.autoIOPageButtonX(10, 176)).isEqualTo(170);
    }

    @Test
    void auto_io_toggle_button_aligns_right_of_side_grid_third_row() {
        assertThat(MachineMenuScreen.autoIOToggleButtonX()).isEqualTo(86);
        assertThat(MachineMenuScreen.autoIOToggleButtonY()).isEqualTo(54);
        assertThat(MachineMenuScreen.autoIOToggleButtonWidth()).isEqualTo(69);
        assertThat(MachineMenuScreen.autoIOToggleButtonHeight()).isEqualTo(20);
        assertThat(MachineMenuScreen.autoIOToggleTextScale()).isEqualTo(0.85F);
    }

    @Test
    void auto_io_page_allows_player_hotbar_slots_with_low_backing_indices() throws Exception {
        MachineMenuScreen screen = screenForMenu(new ItemBusMenu(1, new Inventory(null, null)));
        Field autoIOPage = MachineMenuScreen.class.getDeclaredField("autoIOPage");
        autoIOPage.setAccessible(true);
        autoIOPage.setBoolean(screen, true);

        Method isAutoIOPortSlot = MachineMenuScreen.class.getDeclaredMethod("isAutoIOPortSlot", net.minecraft.world.inventory.Slot.class, int.class);
        isAutoIOPortSlot.setAccessible(true);

        Slot hotbarSlot = new Slot(new net.minecraft.world.SimpleContainer(9), 0, 8, 142);
        assertThat((boolean) isAutoIOPortSlot.invoke(screen, hotbarSlot, 33)).isFalse();
    }

    @Test
    void storage_text_x_aligns_with_title_x() {
        assertThat(MachineMenuScreen.storageTextX(40)).isEqualTo(40);
    }

    @Test
    void storage_text_y_aligns_below_title_y() {
        assertThat(MachineMenuScreen.storageTextY(9)).isEqualTo(21);
    }

    @Test
    void tank_storage_text_uses_visible_y_when_title_is_hidden() {
        assertThat(MachineMenuScreen.storageTextY(MachineMenuScreen.hiddenInventoryLabelY(), true)).isEqualTo(21);
    }

    @Test
    void controller_status_key_uses_single_three_state_value() {
        assertThat(MachineMenuScreen.controllerStatusKey(false, false)).isEqualTo("gui.mmcr.controller.unformed");
        assertThat(MachineMenuScreen.controllerStatusKey(true, true)).isEqualTo("gui.mmcr.controller.running");
        assertThat(MachineMenuScreen.controllerStatusKey(true, false)).isEqualTo("gui.mmcr.controller.idle");
    }

    @Test
    void module_status_lines_remain_visible_before_formation_and_mark_offline_modules_red() {
        List<MachineMenuScreen.ControllerStatusLine> hostLines = MachineMenuScreen.moduleStatusLines(
                true, false, 1, Optional.empty());
        List<MachineMenuScreen.ControllerStatusLine> offlineModuleLines = MachineMenuScreen.moduleStatusLines(
                false, true, 0, Optional.empty());
        List<MachineMenuScreen.ControllerStatusLine> connectedModuleLines = MachineMenuScreen.moduleStatusLines(
                false, true, 0, Optional.of(MMCR.id("test_host")));

        assertThat(hostLines).singleElement().satisfies(line -> {
            assertThat(line.text().getString()).isEqualTo("gui.mmcr.controller.installed_modules");
            assertThat(line.color()).isEqualTo(MachineMenuScreen.STATUS_LABEL_COLOR);
        });
        assertThat(offlineModuleLines).singleElement().satisfies(line -> {
            assertThat(line.text().getString()).isEqualTo("gui.mmcr.controller.module_unconnected");
            assertThat(line.color()).isEqualTo(MachineMenuScreen.UNFORMED_STATUS_COLOR);
        });
        assertThat(connectedModuleLines).singleElement().satisfies(line -> {
            assertThat(line.text().getString()).isEqualTo("gui.mmcr.controller.module_connected");
            assertThat(line.color()).isEqualTo(MachineMenuScreen.STATUS_LABEL_COLOR);
        });
    }

    @Test
    void progress_dots_add_one_dot_per_five_percent() {
        assertThat(MachineMenuScreen.progressPercent(35, 100)).isEqualTo(35);
        assertThat(MachineMenuScreen.progressPercent(150, 100)).isEqualTo(100);
        assertThat(MachineMenuScreen.progressPercent(10, 0)).isZero();
        assertThat(MachineMenuScreen.progressDots(0)).isEmpty();
        assertThat(MachineMenuScreen.progressDots(4)).isEmpty();
        assertThat(MachineMenuScreen.progressDots(5)).isEqualTo(".");
        assertThat(MachineMenuScreen.progressDots(20)).isEqualTo("....");
        assertThat(MachineMenuScreen.progressDots(25)).isEmpty();
        assertThat(MachineMenuScreen.progressDots(35)).isEqualTo("..");
        assertThat(MachineMenuScreen.progressDots(100)).isEmpty();
    }

    @Test
    void controller_detail_lines_use_parallel_and_parallel_slot_labels() {
        assertThat(MachineMenuScreen.parallelLine(3, 16).getString()).isEqualTo("gui.mmcr.controller.parallel");
        assertThat(MachineMenuScreen.parallelSlotLine(1).getString()).isEqualTo("gui.mmcr.controller.parallel_slots");
        assertThat(MachineMenuScreen.factoryThreadLine(0, 3).getString()).isEqualTo("gui.mmcr.controller.threads");
    }

    @Test
    void controller_work_line_uses_thread_count_when_a_factory_controller_is_present() {
        assertThat(MachineMenuScreen.controllerWorkLine(7, 524, true, 0, 3).getString())
                .isEqualTo("gui.mmcr.controller.threads");
    }

    @Test
    void controller_work_line_uses_parallelism_without_a_factory_controller() {
        assertThat(MachineMenuScreen.controllerWorkLine(7, 524, false, 0, 0).getString())
                .isEqualTo("gui.mmcr.controller.parallel");
    }

    @Test
    void controller_status_color_uses_single_three_state_value() {
        assertThat(MachineMenuScreen.controllerStatusColor(false, false)).isEqualTo(MachineMenuScreen.UNFORMED_STATUS_COLOR);
        assertThat(MachineMenuScreen.controllerStatusColor(true, true)).isEqualTo(MachineMenuScreen.FORMED_STATUS_COLOR);
        assertThat(MachineMenuScreen.controllerStatusColor(true, false)).isEqualTo(MachineMenuScreen.IDLE_STATUS_COLOR);
    }

    @Test
    void controller_status_colors_match_ui_semantics() {
        assertThat(MachineMenuScreen.STATUS_LABEL_COLOR).isEqualTo(MachineMenuScreen.CONTROLLER_TITLE_COLOR);
        assertThat(MachineMenuScreen.FORMED_STATUS_COLOR).isEqualTo(0xFF55FF55);
        assertThat(MachineMenuScreen.UNFORMED_STATUS_COLOR).isEqualTo(0xFFFF5555);
        assertThat(MachineMenuScreen.IDLE_STATUS_COLOR).isEqualTo(0xFFFFAA00);
    }

    @Test
    void controller_detail_lines_use_factory_controller_spacing() {
        assertThat(MachineMenuScreen.controllerDetailScale()).isEqualTo(1.0F);
        assertThat(MachineMenuScreen.nextControllerDetailY(20)).isEqualTo(34);
    }

    @Test
    void levelLineUsesRegisteredTypeAndMatchedBlockName() {
        var typeId = MMCR.id("test_coil");
        MachineLevelRegistry.beginRegistration();
        MachineLevelRegistry.registerType(new LevelType(typeId, net.minecraft.network.chat.Component.literal("Coils")));
        MachineLevel level = new MachineLevel(MMCR.id("test_iron_coil"), typeId, 0,
                new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()),
                new net.minecraft.world.item.ItemStack(Holder.direct(Blocks.IRON_BLOCK.asItem(), DataComponentMap.EMPTY)), LevelModifier.IDENTITY);
        MachineLevelRegistry.registerLevel(level);
        MachineLevelRegistry.freezeRegistration();

        assertThat(MachineMenuScreen.levelLine(level).getString()).isEqualTo("gui.mmcr.controller.level");
    }

    @Test
    void item_bus_background_blits_never_sample_beyond_texture_height() {
        int oversizedImageHeight = MachineMenuScreen.GUI_TEXTURE_SIZE + ItemBusMenu.SLOT_SIZE;

        assertThat(MachineMenuScreen.itemBusBackgroundBlits(oversizedImageHeight))
                .allSatisfy(blit -> assertThat(blit.sourceY() + blit.height()).isLessThanOrEqualTo(MachineMenuScreen.GUI_TEXTURE_SIZE))
                .extracting(MachineMenuScreen.BackgroundBlit::height)
                .containsExactly(166, 18, 18, 18, 18, 18, 18);
    }

    @Test
    void background_blits_use_full_texture_dimensions() {
        MachineMenuScreen.BackgroundBlit blit = MachineMenuScreen.backgroundBlit(0, 0, 176, 166);

        assertThat(blit.sourceWidth()).isEqualTo(MachineMenuScreen.GUI_TEXTURE_SIZE);
        assertThat(blit.sourceHeight()).isEqualTo(MachineMenuScreen.GUI_TEXTURE_SIZE);
    }

    @Test
    void factory_controller_menu_uses_tiny_inventory_texture() {
        assertThat(screenTextureFor(factoryMenuWithoutConstructor()))
                .isEqualTo(MMCR.id("textures/gui/inventory_tiny.png"));
    }

    @Test
    void auto_io_page_uses_smart_interface_texture_for_item_bus() {
        assertThat(screenTextureFor(new ItemBusMenu(1, new Inventory(null, null)), true))
                .isEqualTo(MMCR.id("textures/gui/guismartinterface.png"));
    }

    @Test
    void auto_io_page_uses_smart_interface_texture_for_all_port_menus() {
        assertThat(screenTextureFor(new ItemBusMenu(1, new Inventory(null, null)), true))
                .isEqualTo(MMCR.id("textures/gui/guismartinterface.png"));
        assertThat(screenTextureFor(menuWithoutConstructor(FluidHatchMenu.class), true))
                .isEqualTo(MMCR.id("textures/gui/guismartinterface.png"));
        assertThat(screenTextureFor(menuWithoutConstructor(EnergyHatchMenu.class), true))
                .isEqualTo(MMCR.id("textures/gui/guismartinterface.png"));
    }

    @Test
    void auto_io_page_hides_item_bus_port_slots() {
        ItemBusMenu menu = new ItemBusMenu(1, new Inventory(null, null));
        Slot portSlot = menu.getSlot(0);
        Slot playerSlot = menu.getSlot(menu.playerInventorySlotStart());

        assertThat(MachineMenuScreen.hidesSlotOnAutoIOPage(menu, true, portSlot, 0, menu.busSlotCount())).isTrue();
        assertThat(MachineMenuScreen.hidesSlotOnAutoIOPage(menu, true, playerSlot, menu.playerInventorySlotStart(), menu.busSlotCount())).isFalse();
        assertThat(MachineMenuScreen.hidesSlotOnAutoIOPage(menu, false, portSlot, 0, menu.busSlotCount())).isFalse();
    }

    @Test
    void normal_item_bus_page_does_not_hide_slots_after_auto_io_mode() {
        ItemBusMenu menu = new ItemBusMenu(1, new Inventory(null, null));
        Slot portSlot = menu.getSlot(0);

        assertThat(MachineMenuScreen.hidesSlotOnAutoIOPage(menu, false, portSlot, 0, menu.busSlotCount())).isFalse();
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
    void auto_io_side_line_uses_direction_and_block_name() {
        Component line = MachineMenuScreen.autoIOSideLine(Direction.EAST, Blocks.CHEST.getName());

        assertThat(line.getString()).isEqualTo("mmcr.auto_io.side_block");
    }

    @Test
    void auto_io_side_buttons_keep_unfolded_grid_positions() {
        assertThat(MachineMenuScreen.autoIOSideButtonSize()).isEqualTo(20);
        assertThat(MachineMenuScreen.autoIOSideButtonX(0)).isEqualTo(12);
        assertThat(MachineMenuScreen.autoIOSideButtonY(0)).isEqualTo(6);
        assertThat(MachineMenuScreen.autoIOSideButtonX(1)).isEqualTo(36);
        assertThat(MachineMenuScreen.autoIOSideButtonY(1)).isEqualTo(30);
        assertThat(MachineMenuScreen.autoIOSideButtonX(2)).isEqualTo(60);
        assertThat(MachineMenuScreen.autoIOSideButtonY(2)).isEqualTo(54);
    }

    @Test
    void factory_controller_title_offsets_match_tiny_item_bus() {
        assertThat(MachineMenuScreen.titleX(8, false, false, true)).isEqualTo(4);
        assertThat(MachineMenuScreen.titleY(6, false, true)).isEqualTo(4);
        assertThat(MachineMenuScreen.titleX(8, false, false, false, true)).isEqualTo(4);
        assertThat(MachineMenuScreen.titleY(6, false, false, true)).isEqualTo(4);
    }

    @Test
    void controller_recipe_lock_button_stays_outside_inventory_and_hotbar_bounds() {
        MachineMenuScreen.Rect button = MachineMenuScreen.recipeLockButtonRect(0, 0, 176, 213);
        MachineMenuScreen.Rect playerInventory = new MachineMenuScreen.Rect(8, 131, 162, 54);
        MachineMenuScreen.Rect hotbar = new MachineMenuScreen.Rect(8, 189, 162, 18);

        assertThat(button.width()).isEqualTo(button.height());
        assertThat(button.overlaps(playerInventory)).isFalse();
        assertThat(button.overlaps(hotbar)).isFalse();
    }

    @Test
    void controller_recipe_lock_tooltip_uses_translation_keys_and_full_recipe_id() {
        assertThat(MachineMenuScreen.recipeLockTooltip(true, "mmcr:full_recipe_id"))
                .extracting(component -> ((TranslatableContents) component.getContents()).getKey())
                .containsExactly("gui.mmcr.controller.recipe_lock.enabled", "gui.mmcr.controller.recipe_lock.recipe");
        assertThat(MachineMenuScreen.recipeLockTooltip(false, "mmcr:full_recipe_id"))
                .extracting(component -> ((TranslatableContents) component.getContents()).getKey())
                .containsExactly("gui.mmcr.controller.recipe_lock.disabled");
        assertThat(((TranslatableContents) MachineMenuScreen.recipeLockTooltip(true, "mmcr:full_recipe_id").get(1).getContents()).getArgs()[0].toString())
                .isEqualTo("literal{mmcr:full_recipe_id}");
    }

    @Test
    void controller_recipe_lock_state_uses_full_locked_recipe_id_from_menu() {
        MachineControllerMenu menu = MachineControllerMenu.clientOpen(1, new Inventory(null, null));
        menu.setData(12, 1);

        assertThat(MachineMenuScreen.recipeLockTooltip(menu.recipeLocked(), "mmcr:full_recipe_id"))
                .hasSize(2);
    }

    private static Identifier screenTextureFor(AbstractContainerMenu menu, boolean autoIOPage) {
        try {
            Method method = MachineMenuScreen.class.getDeclaredMethod("textureFor", AbstractContainerMenu.class, boolean.class);
            method.setAccessible(true);
            return (Identifier) method.invoke(null, menu, autoIOPage);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to resolve screen texture", e);
        }
    }

    private static Identifier screenTextureFor(AbstractContainerMenu menu) {
        return screenTextureFor(menu, false);
    }

    private static MachineMenuScreen screenForMenu(AbstractContainerMenu menu) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            MachineMenuScreen screen = (MachineMenuScreen) unsafe.allocateInstance(MachineMenuScreen.class);
            setField(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class, screen, "menu", menu);
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

    private static FactorySchedulerMenu factoryMenuWithoutConstructor() {
        return menuWithoutConstructor(FactorySchedulerMenu.class);
    }

    private static <T extends AbstractContainerMenu> T menuWithoutConstructor(Class<T> menuClass) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            return menuClass.cast(unsafe.allocateInstance(menuClass));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate menu", e);
        }
    }

    private static void bind(Object deferredHolder, MenuType<?> menuType) throws Exception {
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
