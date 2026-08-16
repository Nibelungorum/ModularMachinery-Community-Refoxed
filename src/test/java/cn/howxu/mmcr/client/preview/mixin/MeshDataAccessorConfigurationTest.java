package cn.howxu.mmcr.client.preview.mixin;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the preview mesh accessor is registered only for client loading.
 *
 * @author howxu <dev@howxu.cn>
 */
class MeshDataAccessorConfigurationTest {

    @Test
    void mesh_data_accessor_is_registered_as_a_client_mixin() {
        var resource = MeshDataAccessorConfigurationTest.class.getClassLoader().getResourceAsStream("mmcr.mixins.json");

        assertThat(resource).isNotNull();
        try (resource; var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            var config = JsonParser.parseReader(reader).getAsJsonObject();

            assertThat(config.get("package").getAsString()).isEqualTo("cn.howxu.mmcr.client.preview.mixin");
            assertThat(config.getAsJsonArray("client").asList())
                    .extracting(element -> element.getAsString())
                    .containsExactly("MeshDataAccessor", "GuiGraphicsExtractorAccessor", "PictureInPictureRendererAccessor",
                            "GlBufferAccessor", "GlCommandEncoderMixin");
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
