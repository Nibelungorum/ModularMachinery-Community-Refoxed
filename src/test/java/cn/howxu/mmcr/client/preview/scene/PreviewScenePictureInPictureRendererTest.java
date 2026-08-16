package cn.howxu.mmcr.client.preview.scene;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the PiP renderer owns the depth attachment used for hover readback.
 *
 * @author howxu <dev@howxu.cn>
 */
class PreviewScenePictureInPictureRendererTest {
    private static final Path SOURCE = Path.of("src/main/java/cn/howxu/mmcr/client/preview/scene/PreviewScenePictureInPictureRenderer.java");

    @Test
    void owns_a_copy_source_depth_target_for_hover_readback() throws IOException {
        String source = Files.readString(SOURCE);

        assertThat(source).contains("GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC");
        assertThat(source).contains("RenderSystem.outputColorTextureOverride = colorTextureView;");
        assertThat(source).contains("RenderSystem.outputDepthTextureOverride = depthTextureView;");
        assertThat(source).contains("state.owner().onPictureInPicturePrepared(depthTexture, camera,");
    }

    @Test
    void owns_a_renderable_and_sampleable_color_target() throws IOException {
        String source = Files.readString(SOURCE);

        assertThat(source).contains("GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8");
    }

    @Test
    void renders_scene_with_the_preview_camera_projection() throws IOException {
        String source = Files.readString(SOURCE);

        int camera = source.indexOf("PreviewSceneCamera camera = PreviewSceneCamera.from(");
        int projection = source.indexOf("projectionMatrixBuffer.getBuffer(camera.projection())");
        int render = source.indexOf("state.owner().renderScene(context, state.camera())");

        assertThat(camera).isGreaterThanOrEqualTo(0);
        assertThat(projection).isGreaterThan(camera);
        assertThat(render).isGreaterThan(projection);
    }
}
