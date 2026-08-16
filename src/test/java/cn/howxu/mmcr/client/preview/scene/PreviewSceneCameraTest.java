package cn.howxu.mmcr.client.preview.scene;

import cn.howxu.mmcr.client.preview.PreviewCamera;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the camera state used while rendering a structure preview scene.
 *
 * @author howxu <dev@howxu.cn>
 */
class PreviewSceneCameraTest {

    @Test
    void scene_camera_uses_preview_camera_not_game_camera() {
        PreviewCamera preview = new PreviewCamera();
        preview.reset(new Vector3f(2.0F, 3.0F, 4.0F), 10.0F);

        PreviewSceneCamera scene = PreviewSceneCamera.from(preview, 160, 92);

        assertThat(scene.eye()).isEqualTo(preview.position());
        assertThat(scene.lookAt()).isEqualTo(preview.lookAt());
        assertThat(scene.projection()).isNotEqualTo(new Matrix4f());
        assertThat(new Matrix4f(scene.view()).invert()).isNotEqualTo(new Matrix4f());
        assertThat(new Matrix4f(scene.projection()).invert()).isNotEqualTo(new Matrix4f());
    }

    @Test
    void scene_camera_context_clears_after_scoped_failure() {
        Matrix4f view = new Matrix4f().rotateY(0.25F);
        Matrix4f projection = new Matrix4f().perspective(1.0F, 1.5F, 0.1F, 100.0F);

        assertThatThrownBy(() -> PreviewSceneCameraContext.with(view, projection, () -> {
            assertThat(PreviewSceneCameraContext.isActive()).isTrue();
            assertThat(PreviewSceneCameraContext.viewRotation()).isEqualTo(view);
            assertThat(PreviewSceneCameraContext.projection()).isEqualTo(projection);
            throw new IllegalStateException("expected");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(PreviewSceneCameraContext.isActive()).isFalse();
        assertThat(PreviewSceneCameraContext.viewRotation()).isNull();
        assertThat(PreviewSceneCameraContext.projection()).isNull();
    }

    @Test
    void render_target_scope_restores_nested_overrides() {
        Object originalColor = new Object();
        Object originalDepth = new Object();
        Object outerColor = new Object();
        Object outerDepth = new Object();
        Object innerColor = new Object();
        Object innerDepth = new Object();
        PreviewRenderTargetScope.TestOverrides overrides = new PreviewRenderTargetScope.TestOverrides(originalColor, originalDepth);

        try (var outer = PreviewRenderTargetScope.redirect(overrides, outerColor, outerDepth)) {
            assertThat(overrides.color()).isSameAs(outerColor);
            assertThat(overrides.depth()).isSameAs(outerDepth);
            try (var inner = PreviewRenderTargetScope.redirect(overrides, innerColor, innerDepth)) {
                assertThat(overrides.color()).isSameAs(innerColor);
                assertThat(overrides.depth()).isSameAs(innerDepth);
            }
            assertThat(overrides.color()).isSameAs(outerColor);
            assertThat(overrides.depth()).isSameAs(outerDepth);
        }

        assertThat(overrides.color()).isSameAs(originalColor);
        assertThat(overrides.depth()).isSameAs(originalDepth);
    }
}
