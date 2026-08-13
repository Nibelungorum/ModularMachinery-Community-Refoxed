package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmartInterfaceScreenTest {
    @Test
    void current_value_label_uses_translatable_type_name_and_raw_value() {
        SmartInterfaceType type = new SmartInterfaceType("temperature", 8F, 0);

        var contents = SmartInterfaceScreen.currentValueLabel(type, 8F).getContents();

        assertThat(contents).isInstanceOf(net.minecraft.network.chat.contents.TranslatableContents.class);
        var translatable = (net.minecraft.network.chat.contents.TranslatableContents) contents;
        assertThat(translatable.getKey()).isEqualTo("mmcr.smart_interface.value");
        assertThat(((net.minecraft.network.chat.contents.TranslatableContents)
                ((net.minecraft.network.chat.Component) translatable.getArgs()[0]).getContents()).getKey())
                .isEqualTo("mmcr.smart_interface.type.temperature");
        assertThat(translatable.getArgs()[1]).isEqualTo("8.0");
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
    void current_value_label_formats_integer_without_decimal() {
        SmartInterfaceType integer = new SmartInterfaceType("batch", 2F, 0, SmartInterfaceType.ValueType.INTEGER);

        var contents = (net.minecraft.network.chat.contents.TranslatableContents)
                SmartInterfaceScreen.currentValueLabel(integer, 2F).getContents();

        assertThat(contents.getArgs()[1]).isEqualTo("2");
    }

    @Test
    void description_label_uses_required_i18n_key() {
        SmartInterfaceType type = new SmartInterfaceType("Mode", 1F, 0, SmartInterfaceType.ValueType.INTEGER);

        var contents = (net.minecraft.network.chat.contents.TranslatableContents)
                SmartInterfaceScreen.descriptionLabel(type).getContents();

        assertThat(contents.getKey()).isEqualTo("mmcr.smart_interface.type.Mode.description");
    }

    @Test
    void control_layout_starts_below_info_line_height() {
        SmartInterfaceScreen.ControlLayout layout = SmartInterfaceScreen.controlLayout();

        assertThat(layout.inputY()).isEqualTo(38);
        assertThat(layout.navigationY()).isEqualTo(60);
        assertThat(layout.previousX()).isEqualTo(7);
        assertThat(layout.nextX()).isEqualTo(119);
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
