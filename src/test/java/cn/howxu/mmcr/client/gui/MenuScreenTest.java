package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MenuScreenTest {

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
}
