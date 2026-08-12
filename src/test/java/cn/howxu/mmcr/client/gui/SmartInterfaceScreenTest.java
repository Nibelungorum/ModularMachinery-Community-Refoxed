package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmartInterfaceScreenTest {
    @Test
    void malformed_value_info_falls_back_to_the_default_value_display() {
        SmartInterfaceType type = new SmartInterfaceType("temperature", 8F, 0, "", "%q", "", "", "", 0);

        assertThat(SmartInterfaceScreen.valueInfo(type, 8F)).isEqualTo("Value: 8.0");
    }

    @Test
    void parse_value_respects_float_and_integer_types() {
        assertThat(SmartInterfaceScreen.parseValue("12.5", SmartInterfaceType.ValueType.FLOAT)).contains(12.5F);
        assertThat(SmartInterfaceScreen.parseValue("12", SmartInterfaceType.ValueType.INTEGER)).contains(12F);
        assertThat(SmartInterfaceScreen.parseValue("12.5", SmartInterfaceType.ValueType.INTEGER)).isEmpty();
        assertThat(SmartInterfaceScreen.parseValue("NaN", SmartInterfaceType.ValueType.FLOAT)).isEmpty();
        assertThat(SmartInterfaceScreen.parseValue("invalid", SmartInterfaceType.ValueType.FLOAT)).isEmpty();
    }

    @Test
    void value_info_formats_integer_without_decimal() {
        SmartInterfaceType integer = new SmartInterfaceType("batch", 2F, 0, "", "Batch: %d", "", "", "", 0,
                SmartInterfaceType.ValueType.INTEGER);

        assertThat(SmartInterfaceScreen.valueInfo(integer, 2F)).isEqualTo("Batch: 2");
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
    void clamp_page_uses_parameter_count() {
        assertThat(SmartInterfaceScreen.clampPage(3, 2)).isEqualTo(1);
        assertThat(SmartInterfaceScreen.clampPage(-1, 2)).isZero();
        assertThat(SmartInterfaceScreen.clampPage(0, 0)).isZero();
    }
}
