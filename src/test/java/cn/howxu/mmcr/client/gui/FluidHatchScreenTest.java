package cn.howxu.mmcr.client.gui;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fluid hatch GUI behavior tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class FluidHatchScreenTest {
    @Test
    void fluid_tooltip_uses_exact_values_and_has_no_fluid_prefix() {
        List<Component> lines = FluidHatchScreen.tooltipLines(
                1_200_123_543_243L, 9_000_000_000L, Component.literal("Water"));

        assertThat(lines).extracting(Component::getString)
                .containsExactly("Water", "1,200,123,543,243 / 9,000,000,000 mB");
        assertThat(lines).allMatch(line -> !line.getString().contains("流体:")
                && !line.getString().contains("流体：")
                && !line.getString().contains("Fluid:"));
    }

    @Test
    void fluid_text_bounds_include_the_drawn_line_and_exclude_the_next_line() {
        assertThat(AbstractPortScreen.contains(10, 20, 100, 10, 10, 20)).isTrue();
        assertThat(AbstractPortScreen.contains(10, 20, 100, 10, 10, 30)).isFalse();
    }
}
