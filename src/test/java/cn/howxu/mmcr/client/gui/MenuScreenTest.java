package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.internal.menu.FactorySchedulerMenu;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class MenuScreenTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
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
        assertThat(MachineMenuScreen.hiddenInventoryLabelY()).isEqualTo(-1000);
        assertThat(MachineMenuScreen.TITLE_COLOR).isEqualTo(-12566464);
        assertThat(MachineMenuScreen.CONTROLLER_TITLE_COLOR).isEqualTo(0xFFE8E8E8);
        assertThat(MachineMenuScreen.titleColor(false)).isEqualTo(MachineMenuScreen.TITLE_COLOR);
        assertThat(MachineMenuScreen.titleColor(true)).isEqualTo(MachineMenuScreen.CONTROLLER_TITLE_COLOR);
        assertThat(MachineMenuScreen.controllerStatusX(10)).isEqualTo(10);
        assertThat(MachineMenuScreen.controllerStatusY(10)).isEqualTo(22);
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
    void controller_status_key_uses_single_three_state_value() {
        assertThat(MachineMenuScreen.controllerStatusKey(false, false)).isEqualTo("gui.mmcr.controller.unformed");
        assertThat(MachineMenuScreen.controllerStatusKey(true, true)).isEqualTo("gui.mmcr.controller.running");
        assertThat(MachineMenuScreen.controllerStatusKey(true, false)).isEqualTo("gui.mmcr.controller.idle");
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

        assertThat(MachineMenuScreen.levelLine(level).getString()).isEqualTo("Coils: Block of Iron");
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
    void factory_controller_title_offsets_match_tiny_item_bus() {
        assertThat(MachineMenuScreen.titleX(8, false, false, true)).isEqualTo(4);
        assertThat(MachineMenuScreen.titleY(6, false, true)).isEqualTo(4);
        assertThat(MachineMenuScreen.titleX(8, false, false, false, true)).isEqualTo(4);
        assertThat(MachineMenuScreen.titleY(6, false, false, true)).isEqualTo(4);
    }

    private static Identifier screenTextureFor(AbstractContainerMenu menu) {
        try {
            Method method = MachineMenuScreen.class.getDeclaredMethod("textureFor", AbstractContainerMenu.class);
            method.setAccessible(true);
            return (Identifier) method.invoke(null, menu);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to resolve screen texture", e);
        }
    }

    private static FactorySchedulerMenu factoryMenuWithoutConstructor() {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            return (FactorySchedulerMenu) unsafe.allocateInstance(FactorySchedulerMenu.class);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate factory menu", e);
        }
    }
}
