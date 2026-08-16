package cn.howxu.mmcr.client.preview;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies structure-preview camera controls.
 *
 * @author howxu <dev@howxu.cn>
 */
class PreviewCameraTest {

    @Test
    void orbit_clamps_pitch_and_changes_rotation_version_without_changing_look_at() {
        PreviewCamera camera = new PreviewCamera();
        camera.reset(new Vector3f(1.5F, 2.5F, 3.5F), 8.0F);
        Vector3f center = new Vector3f(camera.lookAt());
        long before = camera.rotationVersion();

        camera.orbit(0.4F, 100F);

        assertThat(camera.lookAt()).isEqualTo(center);
        assertThat(camera.pitch()).isBetween(-1.55F, 1.55F);
        assertThat(camera.rotationVersion()).isGreaterThan(before);
    }

    @Test
    void zoom_and_pan_do_not_increment_rotation_version() {
        PreviewCamera camera = new PreviewCamera();
        camera.reset(new Vector3f(), 8.0F);
        long before = camera.rotationVersion();

        camera.zoom(0.5F);
        camera.pan(1.0F, -2.0F);

        assertThat(camera.rotationVersion()).isEqualTo(before);
    }

    @Test
    void zoom_clamps_distance_and_orbit_only_increments_for_actual_rotation() {
        PreviewCamera camera = new PreviewCamera();
        camera.reset(new Vector3f(), 8.0F);
        long before = camera.rotationVersion();

        camera.orbit(0.0F, 0.0F);
        camera.zoom(0.0F);
        assertThat(camera.distance()).isEqualTo(1.0F);
        camera.zoom(1_000.0F);

        assertThat(camera.distance()).isEqualTo(256.0F);
        assertThat(camera.rotationVersion()).isEqualTo(before);
    }

    @Test
    void reset_increments_rotation_version_only_when_it_restores_a_changed_rotation() {
        PreviewCamera camera = new PreviewCamera();
        camera.reset(new Vector3f(), 8.0F);
        long before = camera.rotationVersion();

        camera.reset(new Vector3f(1.0F, 2.0F, 3.0F), 4.0F);
        assertThat(camera.rotationVersion()).isEqualTo(before);
        camera.orbit(0.2F, 0.0F);
        long afterOrbit = camera.rotationVersion();

        camera.reset(new Vector3f(), 8.0F);

        assertThat(camera.rotationVersion()).isEqualTo(afterOrbit + 1);
    }
}
