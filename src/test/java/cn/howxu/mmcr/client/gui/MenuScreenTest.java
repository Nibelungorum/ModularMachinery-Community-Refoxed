package cn.howxu.mmcr.client.gui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MenuScreenTest {

    @Test
    void layout_offsets_title_and_hides_inventory_label() {
        assertThat(MMCRMenuScreen.titleX(8)).isEqualTo(10);
        assertThat(MMCRMenuScreen.titleY(6)).isEqualTo(10);
        assertThat(MMCRMenuScreen.titleX(8, true)).isEqualTo(40);
        assertThat(MMCRMenuScreen.titleY(6, true)).isEqualTo(9);
        assertThat(MMCRMenuScreen.titleX(8, false, true, false)).isEqualTo(40);
        assertThat(MMCRMenuScreen.titleX(8, false, false, true)).isEqualTo(4);
        assertThat(MMCRMenuScreen.titleY(6, false, true)).isEqualTo(4);
        assertThat(MMCRMenuScreen.TANK_Y).isEqualTo(17);
        assertThat(MMCRMenuScreen.ENERGY_Y).isEqualTo(18);
        assertThat(MMCRMenuScreen.hiddenInventoryLabelY()).isEqualTo(-1000);
        assertThat(MMCRMenuScreen.TITLE_COLOR).isEqualTo(-12566464);
    }
}
