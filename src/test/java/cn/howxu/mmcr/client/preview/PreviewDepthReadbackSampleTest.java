package cn.howxu.mmcr.client.preview;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies immutable depth readback samples retain the dimensions used for their texel lookup.
 *
 * @author howxu <dev@howxu.cn>
 */
class PreviewDepthReadbackSampleTest {
    @Test
    void sample_uses_actual_depth_texture_dimensions_for_texel_and_ndc() {
        PreviewFrameViewport frame = new PreviewFrameViewport(
                new PreviewViewport(10, 20, 100, 50),
                new PreviewViewport.FramebufferViewport(0, 0, 200, 100), 200, 100, 2);

        PreviewDepthReadbackSample sample = PreviewDepthReadbackSample.of(null, 333, 111, null, 7L,
                frame, 60, 45, null);

        assertThat(sample.texel()).isEqualTo(new PreviewFrameViewport.Pixel(166, 53));
        assertThat(sample.ndcX()).isEqualTo(0.0F);
        assertThat(sample.ndcY()).isEqualTo(2.0F * 53.5F / 111.0F - 1.0F);
    }
}
