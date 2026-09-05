package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.MMCR;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SmartInterfaceTypeTest {

    @Test
    void registration_keeps_interface_types_in_declaration_order() {
        var mode = new SmartInterfaceType("mode", 1.5F, 20);
        var speed = new SmartInterfaceType("speed", 2F, 10);
        var registration = MachineRegistration.builder(MMCR.id("interface_test"))
                .smartInterfaceType(mode).smartInterfaceType(speed).build();

        assertThat(registration.smartInterfaceTypes()).containsEntry("mode", mode).containsEntry("speed", speed);
        assertThat(registration.smartInterfaceTypes().keySet()).containsExactly("mode", "speed");
    }

    @Test
    void type_rejects_blank_name_and_non_finite_default_value() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new SmartInterfaceType(" ", 0F, 0));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new SmartInterfaceType("mode", Float.NaN, 0));
    }

    @Test
    void default_value_type_is_float_for_existing_constructor() {
        SmartInterfaceType type = new SmartInterfaceType("temperature", 20F, 0);

        assertThat(type.valueType()).isEqualTo(SmartInterfaceType.ValueType.FLOAT);
        assertThat(type.accepts(20.5F)).isTrue();
    }

    @Test
    void range_constructor_uses_minimum_as_default_and_invalid_fallback_value() {
        SmartInterfaceType type = new SmartInterfaceType("temperature", 400F, 6800F, 1,
                SmartInterfaceType.ValueType.INTEGER);

        assertThat(type.defaultValue()).isEqualTo(400F);
        assertThat(type.minValue()).isEqualTo(400F);
        assertThat(type.maxValue()).isEqualTo(6800F);
        assertThat(type.accepts(400F)).isTrue();
        assertThat(type.accepts(6800F)).isTrue();
        assertThat(type.accepts(399F)).isFalse();
        assertThat(type.accepts(6801F)).isFalse();
        assertThat(type.accepts(400.5F)).isFalse();
        assertThat(type.validatedValue(6801F)).isEqualTo(400F);
        assertThat(type.validatedValue(Float.NaN)).isEqualTo(400F);
    }

    @Test
    void range_constructor_rejects_invalid_bounds() {
        assertThatThrownBy(() -> new SmartInterfaceType("temperature", 100F, 0F, 0,
                SmartInterfaceType.ValueType.FLOAT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("range");
        assertThatThrownBy(() -> new SmartInterfaceType("ratio", 0.5F, 2F, 0,
                SmartInterfaceType.ValueType.INTEGER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("integer");
    }

    @Test
    void integer_value_type_accepts_only_integral_finite_values() {
        SmartInterfaceType type = new SmartInterfaceType("ratio", 2F, 0, SmartInterfaceType.ValueType.INTEGER);

        assertThat(type.accepts(2F)).isTrue();
        assertThat(type.accepts(2.25F)).isFalse();
        assertThat(type.accepts(Float.NaN)).isFalse();
    }

    @Test
    void integer_value_type_rejects_non_integer_default() {
        assertThatThrownBy(() -> new SmartInterfaceType("ratio", 2.5F, 0, SmartInterfaceType.ValueType.INTEGER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("integer");
    }

    @Test
    void exposes_i18n_keys_derived_from_type() {
        SmartInterfaceType type = new SmartInterfaceType("Temperature", 400F, 6800F, 1,
                SmartInterfaceType.ValueType.INTEGER);

        assertThat(type.translationKey()).isEqualTo("mmcr.smart_interface.type.Temperature");
        assertThat(type.descriptionKey()).isEqualTo("mmcr.smart_interface.type.Temperature.description");
    }
}
