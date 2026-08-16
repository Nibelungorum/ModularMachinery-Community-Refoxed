package cn.howxu.mmcr.client.preview;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies conversion of preview rectangles to framebuffer coordinates.
 *
 * @author howxu <dev@howxu.cn>
 */
class PreviewViewportTest {

    @Test
    void framebuffer_viewport_scales_and_flips_gui_coordinates_for_hidpi_output() {
        PreviewViewport viewport = new PreviewViewport(100, 50, 200, 100);

        PreviewViewport.FramebufferViewport framebuffer = viewport.framebufferViewport(960, 540, 1_920, 1_080);

        assertThat(framebuffer).isEqualTo(new PreviewViewport.FramebufferViewport(200, 780, 400, 200));
    }
}
