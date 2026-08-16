package cn.howxu.mmcr.compat.jei;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures JEI's reflective plugin discovery does not load a class from a Mixin-owned package.
 *
 * @author howxu <dev@howxu.cn>
 */
class JeiMixinConfigurationTest {

    @Test
    void mixins_are_scoped_to_the_dedicated_mixin_package() throws IOException {
        try (var stream = JeiMixinConfigurationTest.class.getClassLoader()
                .getResourceAsStream("mmcr.jei.mixins.json")) {
            assertThat(stream).isNotNull();
            String configuration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(configuration).contains("\"package\": \"cn.howxu.mmcr.mixin.compat.jei\"");
        }
    }

    @Test
    void mixins_match_current_jei_and_command_encoder_signatures() throws IOException {
        assertThat(source("JeiRecipeGuiLogicMixin.java"))
                .contains("mezz.jei.gui.recipes.lookups.ILookupState")
                .contains("CallbackInfoReturnable<Boolean>")
                .contains("mmcr$closeDiscardedPreviews(ILookupState state, boolean addToHistory");
        assertThat(source("GlCommandEncoderMixin.java"))
                .contains("@Mixin(targets = \"com.mojang.blaze3d.opengl.GlCommandEncoder\")")
                .contains("method = \"copyTextureToBuffer")
                .contains("@Inject(")
                .doesNotContain("DepthTextureReadbackBridge");
    }

    private static String source(String fileName) throws IOException {
        return java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/cn/howxu/mmcr/mixin")
                .resolve(fileName.equals("JeiRecipeGuiLogicMixin.java") ? "compat/jei/" : "client/preview/")
                .resolve(fileName));
    }
}
