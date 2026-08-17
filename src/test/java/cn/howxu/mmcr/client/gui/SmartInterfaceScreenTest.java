package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmartInterfaceScreenTest {
    @Test
    void parse_value_respects_float_and_integer_types() {
        assertThat(SmartInterfaceScreen.parseValue("12.5", SmartInterfaceType.ValueType.FLOAT)).contains(12.5F);
        assertThat(SmartInterfaceScreen.parseValue("12", SmartInterfaceType.ValueType.INTEGER)).contains(12F);
        assertThat(SmartInterfaceScreen.parseValue("12.5", SmartInterfaceType.ValueType.INTEGER)).isEmpty();
        assertThat(SmartInterfaceScreen.parseValue("NaN", SmartInterfaceType.ValueType.FLOAT)).isEmpty();
        assertThat(SmartInterfaceScreen.parseValue("invalid", SmartInterfaceType.ValueType.FLOAT)).isEmpty();
    }

    @Test
    void integer_input_accepts_only_whole_number_candidates() {
        assertThat(SmartInterfaceScreen.acceptsInputCandidate("8", SmartInterfaceType.ValueType.INTEGER)).isTrue();
        assertThat(SmartInterfaceScreen.acceptsInputCandidate("123", SmartInterfaceType.ValueType.INTEGER)).isTrue();
        assertThat(SmartInterfaceScreen.acceptsInputCandidate("12.3", SmartInterfaceType.ValueType.INTEGER)).isFalse();
        assertThat(SmartInterfaceScreen.acceptsInputCandidate("1E2", SmartInterfaceType.ValueType.INTEGER)).isFalse();
        assertThat(SmartInterfaceScreen.acceptsInputCandidate("abc", SmartInterfaceType.ValueType.INTEGER)).isFalse();
    }

    @Test
    void float_input_rejects_malformed_decimal_candidates() {
        assertThat(SmartInterfaceScreen.acceptsInputCandidate("", SmartInterfaceType.ValueType.FLOAT)).isTrue();
        assertThat(SmartInterfaceScreen.acceptsInputCandidate("1", SmartInterfaceType.ValueType.FLOAT)).isTrue();
        assertThat(SmartInterfaceScreen.acceptsInputCandidate("1.", SmartInterfaceType.ValueType.FLOAT)).isTrue();
        assertThat(SmartInterfaceScreen.acceptsInputCandidate("1.111", SmartInterfaceType.ValueType.FLOAT)).isTrue();
        assertThat(SmartInterfaceScreen.acceptsInputCandidate("1.111.1", SmartInterfaceType.ValueType.FLOAT)).isFalse();
        assertThat(SmartInterfaceScreen.acceptsInputCandidate("1E2", SmartInterfaceType.ValueType.FLOAT)).isTrue();
        assertThat(SmartInterfaceScreen.acceptsInputCandidate("1E", SmartInterfaceType.ValueType.FLOAT)).isTrue();
        assertThat(SmartInterfaceScreen.acceptsInputCandidate("1E2E3", SmartInterfaceType.ValueType.FLOAT)).isFalse();
        assertThat(SmartInterfaceScreen.acceptsInputCandidate("abc", SmartInterfaceType.ValueType.FLOAT)).isFalse();
    }

    @Test
    void clamp_page_uses_parameter_count() {
        assertThat(SmartInterfaceScreen.clampPage(3, 2)).isEqualTo(1);
        assertThat(SmartInterfaceScreen.clampPage(-1, 2)).isZero();
        assertThat(SmartInterfaceScreen.clampPage(0, 0)).isZero();
    }
}
