package cn.howxu.mmcr.client.gui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests scrollable text screen behavior.
 *
 * @author howxu <dev@howxu.cn>
 */
class ScrollableTextScreenTest {

    @Test
    void visible_line_count_uses_scaled_font_height_and_spacing() {
        assertThat(AbstractScrollableTextScreen.visibleLineCount(100, 0.85F, 10, 9)).isEqualTo(9);
    }

    @Test
    void visible_line_count_always_allows_one_line() {
        assertThat(AbstractScrollableTextScreen.visibleLineCount(1, 0.85F, 10, 9)).isEqualTo(1);
    }

    @Test
    void max_scroll_offset_is_zero_when_content_fits() {
        assertThat(AbstractScrollableTextScreen.maxScrollOffset(5, 6)).isZero();
    }

    @Test
    void content_that_fits_does_not_consume_wheel_scrolling() {
        assertThat(AbstractScrollableTextScreen.hasScrollableOverflow(5, 6)).isFalse();
        assertThat(AbstractScrollableTextScreen.hasScrollableOverflow(7, 6)).isTrue();
    }

    @Test
    void scroll_offset_is_clamped_to_content_range() {
        assertThat(AbstractScrollableTextScreen.clampScrollOffset(-1, 12, 5)).isZero();
        assertThat(AbstractScrollableTextScreen.clampScrollOffset(99, 12, 5)).isEqualTo(7);
    }

    @Test
    void wheel_moves_one_line_and_uses_minecraft_scroll_direction() {
        assertThat(AbstractScrollableTextScreen.scrollOffsetAfter(0, 12, 5, -1)).isEqualTo(1);
        assertThat(AbstractScrollableTextScreen.scrollOffsetAfter(7, 12, 5, 1)).isEqualTo(6);
    }

    @Test
    void viewport_hit_test_excludes_edges_after_the_viewport() {
        AbstractScrollableTextScreen.TextViewport viewport =
                new AbstractScrollableTextScreen.TextViewport(12, 24, 152, 103, 0.85F, 10);

        assertThat(AbstractScrollableTextScreen.containsViewport(viewport, 0, 0, 12, 24)).isTrue();
        assertThat(AbstractScrollableTextScreen.containsViewport(viewport, 0, 0, 164, 126)).isFalse();
    }
}
