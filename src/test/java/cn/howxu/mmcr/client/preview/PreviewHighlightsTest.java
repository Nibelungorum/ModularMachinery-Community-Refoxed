package cn.howxu.mmcr.client.preview;

import org.junit.jupiter.api.Test;

import static cn.howxu.mmcr.client.preview.PreviewHighlights.INTERFACE;
import static cn.howxu.mmcr.client.preview.PreviewHighlights.LEVEL;
import static cn.howxu.mmcr.client.preview.PreviewHighlights.MODIFIER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies additive role colors without accumulating opacity.
 *
 * @author howxu <dev@howxu.cn>
 */
class PreviewHighlightsTest {
    @Test
    void individual_roles_use_their_primary_color_and_unmarked_positions_are_transparent() {
        assertThat(PreviewHighlights.color(0)).isZero();
        assertThat(PreviewHighlights.color(INTERFACE)).isEqualTo(0x44FF0000);
        assertThat(PreviewHighlights.color(LEVEL)).isEqualTo(0x4400FF00);
        assertThat(PreviewHighlights.color(MODIFIER)).isEqualTo(0x440000FF);
    }

    @Test
    void overlapping_roles_mix_once_with_stable_alpha() {
        assertThat(PreviewHighlights.color(INTERFACE | MODIFIER)).isEqualTo(0x44FF00FF);
        assertThat(PreviewHighlights.color(INTERFACE | LEVEL)).isEqualTo(0x44FFFF00);
        assertThat(PreviewHighlights.color(LEVEL | MODIFIER)).isEqualTo(0x4400FFFF);
        assertThat(PreviewHighlights.color(INTERFACE | LEVEL | MODIFIER)).isEqualTo(0x44FFFFFF);
    }
}
