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
}
