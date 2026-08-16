package cn.howxu.mmcr.compat.jei;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the JEI structure category allocates one full preview per page.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineStructureCategoryTest {
    @Test
    void category_uses_a_single_full_page_preview() throws IOException {
        String source = Files.readString(Path.of("src/main/java/cn/howxu/mmcr/compat/jei/MachineStructureCategory.java"));

        assertThat(source).contains("private static final int PREVIEW_X = 4;");
        assertThat(source).contains("private static final int PREVIEW_Y = 20;");
        assertThat(source).contains("private static final int PREVIEW_WIDTH = 160;");
        assertThat(source).contains("private static final int PREVIEW_HEIGHT = 92;");
        assertThat(source).contains("@Override public int getWidth() { return 168; }");
        assertThat(source).contains("@Override public int getHeight() { return 128; }");
    }
}
