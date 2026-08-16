package cn.howxu.mmcr.client.preview.scene;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the PiP depth-readback handoff occurs after vanilla flushes its buffers.
 *
 * @author howxu <dev@howxu.cn>
 */
class PreviewScenePictureInPictureRendererTest {
    @Test
    void requests_depth_readback_only_after_super_prepare() throws IOException {
        String source = Files.readString(Path.of("src/main/java/cn/howxu/mmcr/client/preview/scene/PreviewScenePictureInPictureRenderer.java"));

        int superPrepare = source.indexOf("super.prepare(state, guiRenderState, guiScale);");
        int request = source.indexOf("state.owner().onPictureInPicturePrepared(");

        assertThat(superPrepare).isGreaterThanOrEqualTo(0);
        assertThat(request).isGreaterThan(superPrepare);
    }
}
