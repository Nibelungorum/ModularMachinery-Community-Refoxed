package cn.howxu.mmcr.client.gui;

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
        assertThat(MachineMenuScreen.TANK_Y).isEqualTo(17);
        assertThat(MachineMenuScreen.ENERGY_Y).isEqualTo(18);
        assertThat(MachineMenuScreen.hiddenInventoryLabelY()).isEqualTo(-1000);
        assertThat(MachineMenuScreen.TITLE_COLOR).isEqualTo(0xFFE8E8E8);
        assertThat(MachineMenuScreen.controllerStatusY(10)).isEqualTo(22);
    }

    @Test
    void controller_status_key_uses_single_three_state_value() {
        assertThat(MachineMenuScreen.controllerStatusKey(false, false)).isEqualTo("gui.mmcr.controller.unformed");
        assertThat(MachineMenuScreen.controllerStatusKey(true, true)).isEqualTo("gui.mmcr.controller.formed");
        assertThat(MachineMenuScreen.controllerStatusKey(true, false)).isEqualTo("gui.mmcr.controller.idle");
    }

    @Test
    void controller_status_color_uses_single_three_state_value() {
        assertThat(MachineMenuScreen.controllerStatusColor(false, false)).isEqualTo(MachineMenuScreen.UNFORMED_STATUS_COLOR);
        assertThat(MachineMenuScreen.controllerStatusColor(true, true)).isEqualTo(MachineMenuScreen.FORMED_STATUS_COLOR);
        assertThat(MachineMenuScreen.controllerStatusColor(true, false)).isEqualTo(MachineMenuScreen.IDLE_STATUS_COLOR);
    }

    @Test
    void controller_status_colors_match_ui_semantics() {
        assertThat(MachineMenuScreen.STATUS_LABEL_COLOR).isEqualTo(MachineMenuScreen.TITLE_COLOR);
        assertThat(MachineMenuScreen.FORMED_STATUS_COLOR).isEqualTo(0xFF55FF55);
        assertThat(MachineMenuScreen.UNFORMED_STATUS_COLOR).isEqualTo(0xFFFF5555);
        assertThat(MachineMenuScreen.IDLE_STATUS_COLOR).isEqualTo(0xFFFFAA00);
    }
}
