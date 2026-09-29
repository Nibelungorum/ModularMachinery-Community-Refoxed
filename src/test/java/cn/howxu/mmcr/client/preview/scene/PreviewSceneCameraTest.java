package cn.howxu.mmcr.client.preview.scene;

import cn.howxu.mmcr.client.preview.PreviewCamera;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(scene.rotationVersion()).isEqualTo(preview.rotationVersion());
        assertThat(scene.projection()).isNotEqualTo(new Matrix4f());
        assertThat(new Matrix4f(scene.view()).invert()).isNotEqualTo(new Matrix4f());
        assertThat(new Matrix4f(scene.projection()).invert()).isNotEqualTo(new Matrix4f());
    }

    @Test
    void scene_camera_exposes_the_inverse_view_projection_matrix() {
        PreviewCamera preview = new PreviewCamera();
        preview.reset(new Vector3f(2.0F, 3.0F, 4.0F), 10.0F);
        PreviewSceneCamera scene = PreviewSceneCamera.from(preview, 160, 92);

        Matrix4f expected = new Matrix4f(scene.projection()).mul(scene.view()).invert();

        assertThat(scene.inverseViewProjection()).isNotEqualTo(new Matrix4f());
        assertThat(scene.inverseViewProjection()).isEqualTo(expected);
    }

}
