package cn.howxu.mmcr.client.gui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmartInterfaceScreenTest {
    @Test
    void malformed_value_info_falls_back_to_the_default_value_display() {
        assertThat(SmartInterfaceScreen.valueInfo("%q", 8F)).isEqualTo("Value: 8.0");
    }

    @Test
    void finite_input_is_required_before_sending_an_update() {
        assertThat(SmartInterfaceScreen.parseFiniteValue("8.5")).isEqualTo(8.5F);
        assertThat(SmartInterfaceScreen.parseFiniteValue("NaN")).isNull();
        assertThat(SmartInterfaceScreen.parseFiniteValue("invalid")).isNull();
    }
}
