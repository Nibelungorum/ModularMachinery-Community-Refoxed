package cn.howxu.mmcr.client.preview;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies adapter-owned preview frame and callback lifecycle contracts.
 *
 * @author howxu <dev@howxu.cn>
 */
class StructurePreviewRendererTest {
    @Test
    void frame_uses_actual_depth_size_for_a_scaled_nonzero_logical_viewport() {
        PreviewFrameViewport frame = new PreviewFrameViewport(80, 40, 400, 200, 10, 20, 100, 50, 300, 150);

        assertThat(frame.containsLogical(60, 45)).isTrue();
        assertThat(frame.depthX(60)).isEqualTo(150);
        assertThat(frame.depthY(45)).isEqualTo(75);
        assertThat(frame.containsLogical(9, 45)).isFalse();
    }

    @Test
    void frame_retains_framebuffer_and_logical_bounds_when_depth_attachment_size_arrives() {
        PreviewFrameViewport submitted = new PreviewFrameViewport(80, 40, 400, 200, 10, 20, 100, 50, 100, 50);

        PreviewFrameViewport frame = submitted.withDepthTextureSize(300, 150);

        assertThat(frame.framebufferX()).isEqualTo(80);
        assertThat(frame.framebufferY()).isEqualTo(40);
        assertThat(frame.logicalX()).isEqualTo(10);
        assertThat(frame.logicalY()).isEqualTo(20);
        assertThat(frame.depthX(60)).isEqualTo(150);
        assertThat(frame.depthY(45)).isEqualTo(75);
    }

    @Test
    void owner_rejects_late_callback_after_close_and_queues_one_release() {
        PreviewOwnerLifecycle lifecycle = new PreviewOwnerLifecycle();
        long token = lifecycle.nextReadback(2, 3, 100L);

        assertThat(lifecycle.queueRelease()).isTrue();
        assertThat(lifecycle.queueRelease()).isFalse();
        assertThat(lifecycle.accepts(token)).isFalse();
    }

    @Test
    void owner_coalesces_depth_reads_until_mouse_moves_or_fifty_milliseconds_pass() {
        PreviewOwnerLifecycle lifecycle = new PreviewOwnerLifecycle();
        lifecycle.nextReadback(2, 3, 100L);

        assertThat(lifecycle.shouldRead(2, 3, 149L)).isFalse();
        assertThat(lifecycle.shouldRead(3, 3, 149L)).isTrue();
        assertThat(lifecycle.shouldRead(2, 3, 150L)).isTrue();
    }
}
