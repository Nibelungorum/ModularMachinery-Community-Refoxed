package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.client.preview.scene.SceneCompileKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the structure-preview adapter lifecycle state.
 *
 * @author howxu <dev@howxu.cn>
 */
class StructurePreviewRendererStateTest {

    @Test
    void visibility_and_reload_request_full_rebuild_but_pan_and_zoom_do_not() {
        StructurePreviewRendererState state = new StructurePreviewRendererState();
        long start = state.generation();

        state.setVisibility(PreviewVisibility.singleLayer(3));
        assertThat(state.generation()).isGreaterThan(start);
        long afterVisibility = state.generation();
        state.onCameraPanOrZoom();

        assertThat(state.generation()).isEqualTo(afterVisibility);
    }

    @Test
    void rotation_after_full_cache_requests_only_translucent_resort() {
        StructurePreviewRendererState state = new StructurePreviewRendererState();
        state.markFullCachePublished();
        state.onCameraRotation(1L);

        assertThat(state.pendingKind()).isEqualTo(SceneCompileKind.TRANSLUCENT_ONLY);
    }

    @Test
    void close_is_idempotent_and_rejects_late_scene_results() {
        StructurePreviewRendererState state = new StructurePreviewRendererState();
        state.close();
        state.close();

        assertThat(state.accepts(state.generation(), SceneCompileKind.FULL)).isFalse();
    }

    @Test
    void close_cancels_outstanding_depth_readback_and_ignores_late_completion() {
        StructurePreviewRendererState state = new StructurePreviewRendererState();
        long token = state.beginDepthReadback();

        state.close();

        assertThat(state.acceptsDepthReadback(token)).isFalse();
    }

    @Test
    void depth_readback_is_throttled_until_mouse_moves_or_fifty_milliseconds_elapse() {
        StructurePreviewRendererState state = new StructurePreviewRendererState();

        assertThat(state.shouldReadDepth(4, 6, 100L)).isTrue();
        state.markDepthReadbackRequested(4, 6, 100L);
        assertThat(state.shouldReadDepth(4, 6, 120L)).isFalse();
        assertThat(state.shouldReadDepth(5, 6, 120L)).isTrue();
        assertThat(state.shouldReadDepth(4, 6, 150L)).isTrue();
    }

    @Test
    void frame_coordinates_scale_mouse_to_pip_texture_and_reject_viewport_misses() {
        StructurePreviewRendererState state = new StructurePreviewRendererState();
        state.setFrame(200, 100, 20, 10, 20, 10);

        assertThat(state.textureMouseX(30)).isEqualTo(100);
        assertThat(state.textureMouseY(15)).isEqualTo(50);
        assertThat(state.containsMouse(30, 15)).isTrue();
        assertThat(state.containsMouse(19, 15)).isFalse();
    }

    @Test
    void matching_hover_and_selection_emit_one_outline() {
        StructurePreviewRendererState state = new StructurePreviewRendererState();

        assertThat(state.outlineCount(true, true, true)).isEqualTo(1);
        assertThat(state.outlineCount(true, true, false)).isEqualTo(2);
    }

    @Test
    void off_thread_close_dispatches_one_render_thread_release() {
        StructurePreviewRendererState state = new StructurePreviewRendererState();

        assertThat(state.requestCloseRelease()).isTrue();
        assertThat(state.requestCloseRelease()).isFalse();
    }
}
