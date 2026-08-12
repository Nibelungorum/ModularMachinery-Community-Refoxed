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

    @Test
    void input_accepts_only_the_mmce_numeric_characters() {
        assertThat(SmartInterfaceScreen.acceptsInputCharacter('8')).isTrue();
        assertThat(SmartInterfaceScreen.acceptsInputCharacter('.')).isTrue();
        assertThat(SmartInterfaceScreen.acceptsInputCharacter('E')).isTrue();
        assertThat(SmartInterfaceScreen.acceptsInputCharacter('-')).isFalse();
        assertThat(SmartInterfaceScreen.acceptsInputCharacter('e')).isFalse();
    }

    @Test
    void selected_page_is_clamped_to_available_bindings() {
        assertThat(SmartInterfaceScreen.clampPage(-1, 3)).isZero();
        assertThat(SmartInterfaceScreen.clampPage(1, 3)).isEqualTo(1);
        assertThat(SmartInterfaceScreen.clampPage(3, 3)).isEqualTo(2);
        assertThat(SmartInterfaceScreen.clampPage(0, 0)).isZero();
    }
}
