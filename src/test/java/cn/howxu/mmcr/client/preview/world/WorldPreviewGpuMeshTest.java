package cn.howxu.mmcr.client.preview.world;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Verifies the camera-relative coordinates consumed by the block shader without a GPU context.
 *
 * @author howxu <dev@howxu.cn>
 */
class WorldPreviewGpuMeshTest {
    @ParameterizedTest
    @CsvSource({"0, 64, 0", "0, 190, 0", "0, 200, 0", "0, 300, 0", "12000, 200, -8000"})
    void nearbyPreviewHasSameFogDistanceAtEveryWorldPosition(double x, double y, double z) {
        Vec3 camera = new Vec3(x + 0.25, y + 0.5, z + 0.75);
        var transform = WorldPreviewGpuMesh.cameraRelativeTransform(new Matrix4f(), camera);
        Vector3f position = new Vector3f((float) camera.x + 3, (float) camera.y + 4, (float) camera.z);

        // Match block.vsh: fog distance is computed before the model-view matrix is applied.
        position.add(transform.modelOffset());
        assertThat(position.length()).isCloseTo(5.0F, within(0.0001F));
        assertThat(Math.max(new Vector3f(position.x, 0, position.z).length(), Math.abs(position.y)))
                .isCloseTo(4.0F, within(0.0001F));
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "30, 70", "-45, 180"})
    void cameraOffsetPreservesProjectedPositionWithoutDoubleTranslation(float pitch, float yaw) {
        Vec3 camera = new Vec3(12000.25, 200.5, -8000.75);
        Matrix4f view = new Matrix4f().rotateX((float) Math.toRadians(pitch))
                .rotateY((float) Math.toRadians(yaw));
        Matrix4f originalView = new Matrix4f(view);
        Vector3f worldPosition = new Vector3f(12003, 204, -7998);
        Vector3f expected = new Matrix4f(view)
                .translate((float) -camera.x, (float) -camera.y, (float) -camera.z)
                .transformPosition(new Vector3f(worldPosition));

        var transform = WorldPreviewGpuMesh.cameraRelativeTransform(view, camera);
        Vector3f actual = transform.modelView().transformPosition(
                new Vector3f(worldPosition).add(transform.modelOffset()));

        assertThat(actual.x).isCloseTo(expected.x, within(0.002F));
        assertThat(actual.y).isCloseTo(expected.y, within(0.002F));
        assertThat(actual.z).isCloseTo(expected.z, within(0.002F));
        assertThat(view).isEqualTo(originalView);
    }
}
