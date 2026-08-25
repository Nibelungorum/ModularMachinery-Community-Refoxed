package cn.howxu.mmcr.client.gui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Energy hatch GUI behavior tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class EnergyHatchScreenTest {
    @Test
    void energy_tooltip_uses_exact_values_and_has_no_energy_prefix() {
        assertThat(EnergyHatchScreen.tooltipLines(1_200_123_543_243L, Long.MAX_VALUE))
                .extracting(component -> component.getString())
                .containsExactly("1,200,123,543,243 / 9,223,372,036,854,775,807 FE")
                .allMatch(line -> !line.contains("能量:") && !line.contains("能量：") && !line.contains("Energy:"));
    }

    @Test
    void energy_text_bounds_are_checked_against_absolute_mouse_coordinates() {
        assertThat(AbstractPortScreen.contains(30, 40, 80, 10, 109, 49)).isTrue();
        assertThat(AbstractPortScreen.contains(30, 40, 80, 10, 110, 49)).isFalse();
    }

    @Test
    void full_long_energy_capacity_fills_the_bar_without_overflowing() {
        assertThat(EnergyHatchScreen.filledHeight(Long.MAX_VALUE, Long.MAX_VALUE)).isEqualTo(61);
    }
}
